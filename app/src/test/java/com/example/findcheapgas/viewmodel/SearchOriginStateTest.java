package com.example.findcheapgas.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.UserLocation;

import org.junit.Test;

/** 조회 위치 전환: 내 위치 ↔ 검색한 장소 */
public class SearchOriginStateTest {

    private static final UserLocation HOME = new UserLocation(37.4979, 127.0276, UserLocation.Source.CURRENT);
    private static final UserLocation MOVED = new UserLocation(37.5000, 127.0300, UserLocation.Source.CURRENT);

    @Test
    public void firstLocation_becomesOrigin() {
        SearchOriginState state = new SearchOriginState();
        SearchOrigin origin = state.onMyLocation(HOME, false);

        assertNotNull(origin);
        assertFalse(origin.isPlace());
        assertEquals(HOME.getLatitude(), origin.getLatitude(), 0);
        assertEquals(origin, state.getOrigin());
    }

    @Test
    public void myLocationOrigin_followsNewLocation() {
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(HOME, false);
        SearchOrigin origin = state.onMyLocation(MOVED, false);

        assertNotNull(origin);
        assertEquals(MOVED.getLatitude(), origin.getLatitude(), 0);
    }

    @Test
    public void place_staysWhenMyLocationUpdates() {
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(HOME, false);
        state.selectPlace("선릉역", 37.5045, 127.0490);

        // 장소 기준일 때 위치를 새로 받으면 파란 점만 바뀌고 조회 위치는 그대로다.
        assertNull(state.onMyLocation(MOVED, false));
        assertTrue(state.getOrigin().isPlace());
        assertEquals("선릉역", state.getOrigin().getName());
        assertEquals(MOVED, state.getMyLocation());
    }

    @Test
    public void returnToMyLocation_fromPlaceWithFreshLocation() {
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(HOME, false);
        state.selectPlace("선릉역", 37.5045, 127.0490);

        SearchOrigin origin = state.onMyLocation(MOVED, true);
        assertNotNull(origin);
        assertFalse(origin.isPlace());
        assertNull(origin.getName());
        assertEquals(MOVED.getLongitude(), origin.getLongitude(), 0);
    }

    @Test
    public void returnToLastMyLocation_withoutPermission() {
        UserLocation defaultLocation = new UserLocation(37.5666, 126.9784, UserLocation.Source.DEFAULT);
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(defaultLocation, false);
        assertTrue(state.getOrigin().isDefaultLocation());

        state.selectPlace("강남역", 37.4979, 127.0276);
        SearchOrigin origin = state.returnToLastMyLocation();
        assertNotNull(origin);
        assertTrue(origin.isDefaultLocation());

        // 이미 내 위치 기준이면 바뀌지 않는다 (다시 조회하지 않음).
        assertNull(state.returnToLastMyLocation());
    }

    @Test
    public void returnToLastMyLocation_unknownLocation_returnsNull() {
        SearchOriginState state = new SearchOriginState();
        state.selectPlace("강남역", 37.4979, 127.0276);
        assertNull(state.returnToLastMyLocation());
        assertTrue(state.getOrigin().isPlace());
    }

    @Test
    public void selectAnotherPlace_replacesPlace() {
        SearchOriginState state = new SearchOriginState();
        state.selectPlace("강남역", 37.4979, 127.0276);
        SearchOrigin origin = state.selectPlace("역삼셀프주유소", 37.5001, 127.0333);
        assertEquals("역삼셀프주유소", origin.getName());
        assertEquals(origin, state.getOrigin());
    }
}
