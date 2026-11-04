package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.StationDetailCacheDao;
import com.example.findcheapgas.data.local.StationDetailCacheEntity;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetRemoteDataSource;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 주유소 상세정보를 어디서 가져올지 정한다. 상세 화면을 열 때만 부른다.
 * 1. Room 캐시 (같은 주유소를 30분 안에 조회한 결과)
 * 2. 캐시가 없으면 USE_MOCK_DATA에 따라 가짜 데이터(assets/mock/detail) 또는 실제 오피넷 API
 *
 * 앱 전체에서 하나만 쓰고 요청을 한 스레드에서 차례로 처리한다. 그래서 상세 화면을 닫았다가 바로 다시 열어
 * 같은 주유소 요청이 겹쳐도, 뒤 요청은 앞 요청이 저장한 캐시를 읽고 API를 다시 부르지 않는다.
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 */
public class StationDetailRepository {

    private static final String TAG = "StationDetailRepo";

    private static final long CACHE_TTL_MS = 30 * 60 * 1000;

    public interface Callback {
        void onSuccess(StationDetail detail);

        void onError(StationError error);
    }

    private static volatile StationDetailRepository instance;

    private final StationDetailCacheDao cacheDao;
    private final ApiCallCounter callCounter;
    private final MockOpinetDataSource mockDataSource;
    private final OpinetRemoteDataSource remoteDataSource;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static StationDetailRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (StationDetailRepository.class) {
                if (instance == null) {
                    instance = new StationDetailRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private StationDetailRepository(Context context) {
        cacheDao = AppDatabase.getInstance(context).stationDetailCacheDao();
        callCounter = new ApiCallCounter(context);
        mockDataSource = new MockOpinetDataSource(context);
        remoteDataSource = new OpinetRemoteDataSource();
    }

    public void fetchDetail(String stationId, Callback callback) {
        executor.execute(() -> {
            try {
                StationDetail detail = load(stationId);
                mainHandler.post(() -> callback.onSuccess(detail));
            } catch (IOException e) {
                Log.w(TAG, "상세 조회 실패 (네트워크)", e);
                mainHandler.post(() -> callback.onError(StationError.NETWORK));
            } catch (OpinetException e) {
                Log.w(TAG, "상세 조회 실패 (오피넷): " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError(StationError.OPINET));
            } catch (RuntimeException e) {
                Log.e(TAG, "상세 조회 중 알 수 없는 오류", e);
                mainHandler.post(() -> callback.onError(StationError.OPINET));
            }
        });
    }

    private StationDetail load(String stationId) throws IOException, OpinetException {
        long now = System.currentTimeMillis();
        long minSavedAt = now - CACHE_TTL_MS;
        cacheDao.deleteOlderThan(minSavedAt);

        String key = cacheKey(stationId);
        StationDetail cached = readCache(key, minSavedAt);
        if (cached != null) {
            Log.d(TAG, "캐시 사용: " + key);
            return cached;
        }

        StationDetail detail;
        if (BuildConfig.USE_MOCK_DATA) {
            detail = mockDataSource.fetchDetail(stationId);
            Log.d(TAG, "가짜 데이터 사용: " + key);
        } else {
            if (!OpinetRemoteDataSource.hasApiKey()) {
                throw new OpinetException("local.properties에 OPINET_API_KEY가 없습니다.");
            }
            int count = callCounter.increment();
            Log.i(TAG, "오피넷 API 호출 (상세): 오늘 " + count + "회 / " + ApiCallCounter.DAILY_LIMIT + "회");
            detail = remoteDataSource.fetchDetail(stationId);
        }

        cacheDao.save(new StationDetailCacheEntity(key, gson.toJson(detail), now));
        return detail;
    }

    /**
     * 가짜 데이터와 실제 API 결과가 섞이지 않도록 출처를 넣는다.
     * 예: detail|mock|A0900001
     */
    private static String cacheKey(String stationId) {
        return "detail|" + (BuildConfig.USE_MOCK_DATA ? "mock" : "api") + "|" + stationId;
    }

    private StationDetail readCache(String key, long minSavedAt) {
        StationDetailCacheEntity entity = cacheDao.find(key, minSavedAt);
        if (entity == null) {
            return null;
        }
        try {
            return gson.fromJson(entity.detailJson, StationDetail.class);
        } catch (JsonParseException e) {
            Log.w(TAG, "캐시를 읽지 못해 새로 조회합니다.", e);
            return null;
        }
    }
}
