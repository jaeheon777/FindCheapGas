package com.example.findcheapgas.data.mock;

import android.content.Context;

import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.CoordinateConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 가짜 데이터. assets/mock/around_{유종코드}.json을 읽는다 (강남역 주변 주유소).
 * 실제 API처럼 요청 위치에서 거리를 다시 계산하고, 반경 밖 주유소는 뺀다. 정렬은 Repository가 한다.
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
            if (distance <= query.getRadiusMeters()) {
                oil.distance = distance;
                inRadius.add(oil);
            }
        }
        return inRadius;
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
