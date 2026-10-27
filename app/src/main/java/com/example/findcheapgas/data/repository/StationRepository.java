package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.StationCacheDao;
import com.example.findcheapgas.data.local.StationCacheEntity;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetRemoteDataSource;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.CoordinateConverter;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 주변 주유소를 어디서 가져올지 정한다.
 * 1. Room 캐시 (같은 위치·유종·반경·정렬로 20분 안에 조회한 결과)
 * 2. 캐시가 없으면 USE_MOCK_DATA에 따라 가짜 데이터(assets) 또는 실제 오피넷 API
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 */
public class StationRepository {

    private static final String TAG = "StationRepository";

    private static final long CACHE_TTL_MS = 20 * 60 * 1000;

    private static final Type STATION_LIST_TYPE = new TypeToken<List<GasStation>>() {
    }.getType();

    public interface Callback {
        void onSuccess(List<GasStation> stations);

        void onError(StationError error);
    }

    private final StationCacheDao cacheDao;
    private final ApiCallCounter callCounter;
    private final MockOpinetDataSource mockDataSource;
    private final OpinetRemoteDataSource remoteDataSource;
    private final Gson gson = new Gson();
    // DB, 파일, 네트워크 작업은 모두 이 스레드에서 차례로 처리한다.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StationRepository(Context context) {
        cacheDao = AppDatabase.getInstance(context).stationCacheDao();
        callCounter = new ApiCallCounter(context);
        mockDataSource = new MockOpinetDataSource(context);
        remoteDataSource = new OpinetRemoteDataSource();
    }

    public void fetchAroundStations(StationQuery query, Callback callback) {
        executor.execute(() -> {
            try {
                List<GasStation> stations = load(query);
                mainHandler.post(() -> callback.onSuccess(stations));
            } catch (IOException e) {
                Log.w(TAG, "주유소 조회 실패 (네트워크)", e);
                mainHandler.post(() -> callback.onError(StationError.NETWORK));
            } catch (OpinetException e) {
                Log.w(TAG, "주유소 조회 실패 (오피넷): " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(StationError.OPINET));
            } catch (RuntimeException e) {
                Log.e(TAG, "주유소 조회 중 알 수 없는 오류", e);
                mainHandler.post(() -> callback.onError(StationError.OPINET));
            }
        });
    }

    /** ViewModel이 사라질 때 호출한다. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private List<GasStation> load(StationQuery query) throws IOException, OpinetException {
        long now = System.currentTimeMillis();
        long minSavedAt = now - CACHE_TTL_MS;
        cacheDao.deleteOlderThan(minSavedAt);

        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(query.getLatitude(), query.getLongitude());
        String key = cacheKey(query);

        List<GasStation> cached = readCache(key, minSavedAt);
        if (cached != null) {
            Log.d(TAG, "캐시 사용: " + key + " (" + cached.size() + "곳)");
            // 캐시는 약 100m 단위로 묶여 있으므로 거리는 지금 위치 기준으로 다시 계산한다.
            List<GasStation> stations = new ArrayList<>(cached.size());
            for (GasStation station : cached) {
                stations.add(station.withDistance(
                        CoordinateConverter.distanceMeters(katec, station.getKatecX(), station.getKatecY())));
            }
            return sorted(stations, query.getSortOrder());
        }

        List<OpinetAroundResponse.Oil> oils;
        if (BuildConfig.USE_MOCK_DATA) {
            oils = mockDataSource.fetchAround(query, katec);
            Log.d(TAG, "가짜 데이터 사용: " + key + " (" + oils.size() + "곳)");
        } else {
            if (!OpinetRemoteDataSource.hasApiKey()) {
                throw new OpinetException("local.properties에 OPINET_API_KEY가 없습니다.");
            }
            int count = callCounter.increment();
            Log.i(TAG, "오피넷 API 호출: 오늘 " + count + "회 / " + ApiCallCounter.DAILY_LIMIT + "회");
            oils = remoteDataSource.fetchAround(query, katec);
            Log.d(TAG, "오피넷 응답: " + key + " (" + oils.size() + "곳)");
        }

        List<GasStation> stations = sorted(toStations(oils), query.getSortOrder());
        cacheDao.save(new StationCacheEntity(key, gson.toJson(stations, STATION_LIST_TYPE), now));
        return stations;
    }

    /**
     * 위도·경도를 소수점 셋째 자리(위도 약 111m, 경도 약 88m)로 반올림해서 가까운 위치끼리 같은 캐시를 쓴다.
     * 가짜 데이터와 실제 API 결과가 섞이지 않도록 출처도 넣는다.
     */
    private static String cacheKey(StationQuery query) {
        return String.format(Locale.US, "%s|%.3f,%.3f|%s|%d|%d",
                BuildConfig.USE_MOCK_DATA ? "mock" : "api",
                query.getLatitude(), query.getLongitude(),
                query.getFuelType().getCode(),
                query.getRadiusMeters(),
                query.getSortOrder().getCode());
    }

    private List<GasStation> readCache(String key, long minSavedAt) {
        StationCacheEntity entity = cacheDao.find(key, minSavedAt);
        if (entity == null) {
            return null;
        }
        try {
            return gson.fromJson(entity.stationsJson, STATION_LIST_TYPE);
        } catch (JsonParseException e) {
            Log.w(TAG, "캐시를 읽지 못해 새로 조회합니다.", e);
            return null;
        }
    }

    /** 오피넷 응답(KATEC)을 지도에 쓸 수 있게 WGS84 좌표를 붙여 바꾼다. */
    private static List<GasStation> toStations(List<OpinetAroundResponse.Oil> oils) {
        List<GasStation> stations = new ArrayList<>(oils.size());
        for (OpinetAroundResponse.Oil oil : oils) {
            if (oil.uniId == null || oil.price <= 0) {
                continue; // 가격이 없는 항목은 표시하지 않는다.
            }
            CoordinateConverter.Wgs84 wgs84 = CoordinateConverter.toWgs84(oil.katecX, oil.katecY);
            stations.add(new GasStation(oil.uniId, oil.brandCode, oil.name, oil.price, oil.distance,
                    oil.katecX, oil.katecY, wgs84.latitude, wgs84.longitude));
        }
        return stations;
    }

    private static List<GasStation> sorted(List<GasStation> stations, SortOrder sortOrder) {
        Comparator<GasStation> byPrice = Comparator.comparingInt(GasStation::getPrice);
        Comparator<GasStation> byDistance = Comparator.comparingDouble(GasStation::getDistance);
        Collections.sort(stations, sortOrder == SortOrder.DISTANCE
                ? byDistance.thenComparing(byPrice)
                : byPrice.thenComparing(byDistance));
        return stations;
    }
}
