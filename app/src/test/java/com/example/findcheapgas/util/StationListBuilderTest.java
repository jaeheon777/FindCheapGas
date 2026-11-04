package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;

import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StationListBuilderTest {

    private static GasStation station(String id, int price, double distance) {
        return new GasStation(id, "SKE", id, price, distance, 0, 0, 0, 0);
    }

    private static List<String> ids(List<GasStation> stations) {
        List<String> result = new ArrayList<>();
        for (GasStation station : stations) {
            result.add(station.getId());
        }
        return result;
    }

    private static final List<GasStation> ALL = Arrays.asList(
            station("a", 1700, 400),
            station("b", 1650, 2500),
            station("c", 1650, 900),
            station("d", 1600, 4200),
            station("e", 1700, 1000),   // 1km 경계: 포함
            station("f", 1600, 3000.5)  // 3km 바로 밖
    );

    @Test
    public void radius_keepsOnlyStationsWithinRadius() {
        assertEquals(3, StationListBuilder.build(ALL, SearchRadius.KM_1, SortOrder.PRICE).size());
        assertEquals(4, StationListBuilder.build(ALL, SearchRadius.KM_3, SortOrder.PRICE).size());
        assertEquals(6, StationListBuilder.build(ALL, SearchRadius.KM_5, SortOrder.PRICE).size());
    }

    @Test
    public void priceOrder_samePriceSortedByDistance() {
        List<GasStation> result = StationListBuilder.build(ALL, SearchRadius.KM_5, SortOrder.PRICE);
        assertEquals(Arrays.asList("f", "d", "c", "b", "a", "e"), ids(result));
    }

    @Test
    public void distanceOrder_sameDistanceSortedByPrice() {
        List<GasStation> sameDistance = Arrays.asList(
                station("x", 1700, 500),
                station("y", 1600, 500),
                station("z", 1650, 300));
        List<GasStation> result = StationListBuilder.build(sameDistance, SearchRadius.KM_1, SortOrder.DISTANCE);
        assertEquals(Arrays.asList("z", "y", "x"), ids(result));
    }

    @Test
    public void build_doesNotChangeInput() {
        List<GasStation> input = new ArrayList<>(ALL);
        StationListBuilder.build(input, SearchRadius.KM_1, SortOrder.DISTANCE);
        assertEquals(ids(ALL), ids(input));
    }

    @Test
    public void emptyWhenNothingInRadius() {
        List<GasStation> far = Arrays.asList(station("far", 1600, 2000));
        assertEquals(0, StationListBuilder.build(far, SearchRadius.KM_1, SortOrder.PRICE).size());
    }
}
