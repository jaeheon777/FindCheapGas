package com.example.findcheapgas.util;

import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 받아 둔 전체 목록(최대 반경 5km)으로 화면에 보여줄 목록을 만든다. API는 부르지 않는다.
 * 처리 순서: 전체 목록 → 반경 필터 → 정렬 → 화면
 * 2단계에서 반경 필터와 정렬 사이에 브랜드 필터(숨기기, 빠른 필터)와 예상 이득 계산이 들어간다.
 * 예상 이득 계산에는 내 차 정보(CarInfo: 연비, 주유량)를 넘겨받아 쓴다.
 */
public final class StationListBuilder {

    private StationListBuilder() {
    }

    /** 입력 목록은 바꾸지 않고 새 목록을 돌려준다. */
    public static List<GasStation> build(List<GasStation> all, SearchRadius radius, SortOrder sortOrder) {
        List<GasStation> stations = filterByRadius(all, radius);
        Collections.sort(stations, sortOrder.getComparator());
        return Collections.unmodifiableList(stations);
    }

    private static List<GasStation> filterByRadius(List<GasStation> all, SearchRadius radius) {
        List<GasStation> result = new ArrayList<>(all.size());
        for (GasStation station : all) {
            if (station.getDistance() <= radius.getMeters()) {
                result.add(station);
            }
        }
        return result;
    }
}
