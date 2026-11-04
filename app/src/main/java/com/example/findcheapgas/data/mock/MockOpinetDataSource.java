package com.example.findcheapgas.data.mock;

import android.content.Context;

import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetDetailResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.CoordinateConverter;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 가짜 데이터 (강남역 주변 주유소).
 * - 주변 주유소: assets/mock/around_{유종코드}.json. 실제 API처럼 요청 위치에서 거리를 다시 계산하고,
 *   최대 반경(5km) 밖 주유소는 뺀다. 화면 반경 필터와 정렬은 StationListBuilder가 한다.
 * - 상세정보: assets/mock/detail/{주유소 ID}.json. 주변 데이터의 모든 주유소가 있고, 가격은 주변 데이터와 같다.
 */
public class MockOpinetDataSource {

    // 로딩 표시를 확인할 수 있도록 실제 통신처럼 잠깐 기다린다.
    private static final long FAKE_DELAY_MS = 300;

    private final Context appContext;

    public MockOpinetDataSource(Context context) {
        appContext = context.getApplicationContext();
    }

    public List<OpinetAroundResponse.Oil> fetchAround(StationQuery query, CoordinateConverter.Katec katec)
            throws IOException, OpinetException {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String fileName = "mock/around_" + query.getFuelType().getCode() + ".json";
        List<OpinetAroundResponse.Oil> all = OpinetAroundResponse.parse(readAsset(fileName));

        List<OpinetAroundResponse.Oil> inRadius = new ArrayList<>();
        for (OpinetAroundResponse.Oil oil : all) {
            double distance = CoordinateConverter.distanceMeters(katec, oil.katecX, oil.katecY);
            if (distance <= StationQuery.FETCH_RADIUS_METERS) {
                oil.distance = distance;
                inRadius.add(oil);
            }
        }
        return inRadius;
    }

    public StationDetail fetchDetail(String stationId) throws IOException, OpinetException {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String body;
        try {
            body = readAsset("mock/detail/" + stationId + ".json");
        } catch (FileNotFoundException e) {
            // 실제 API에서 없는 주유소를 조회한 것과 같게 오피넷 오류로 처리한다.
            throw new OpinetException("가짜 상세 데이터 없음: " + stationId, e);
        }
        return OpinetDetailResponse.parse(body);
    }

    private String readAsset(String fileName) throws IOException {
        try (InputStream in = appContext.getAssets().open(fileName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
