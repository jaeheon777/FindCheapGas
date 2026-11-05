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

    // ---------- 이 지역에서 다시 검색 (지도 중심) ----------

    @Test
    public void mapCenter_staysWhenMyLocationChanges() {
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(HOME, false);
        SearchOrigin center = state.selectMapCenter(37.5100, 127.0400);
        assertTrue(center.isMapCenter());
        assertTrue(center.isFixed());
        assertNull(center.getName());

        assertNull(state.onMyLocation(MOVED, false));
        assertEquals(center, state.getOrigin());
    }

    @Test
    public void mapCenter_returnToMyLocation() {
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(HOME, false);
        state.selectMapCenter(37.5100, 127.0400);

        SearchOrigin origin = state.onMyLocation(MOVED, true);
        assertNotNull(origin);
        assertFalse(origin.isFixed());

        // 권한이 없을 때도 마지막 내 위치로 돌아간다.
        state.selectMapCenter(37.5100, 127.0400);
        SearchOrigin last = state.returnToLastMyLocation();
        assertNotNull(last);
        assertFalse(last.isFixed());
    }

    @Test
    public void nameMapCenter_keepsPosition() {
        SearchOriginState state = new SearchOriginState();
        SearchOrigin center = state.selectMapCenter(37.5006, 127.0364);
        SearchOrigin named = state.nameMapCenter(center, "역삼1동");

        assertNotNull(named);
        assertEquals("역삼1동", named.getName());
        assertTrue(named.isSamePosition(center));
        assertEquals(named, state.getOrigin());
    }

    @Test
    public void nameMapCenter_ignoredWhenOriginChanged() {
        SearchOriginState state = new SearchOriginState();
        SearchOrigin first = state.selectMapCenter(37.5006, 127.0364);
        // 이름이 오기 전에 다른 곳에서 다시 검색하거나 장소를 골랐으면 늦게 온 이름은 버린다.
        state.selectMapCenter(37.5200, 127.0500);
        assertNull(state.nameMapCenter(first, "역삼1동"));
        assertNull(state.getOrigin().getName());

        state.selectPlace("강남역", 37.5006, 127.0364);
        assertNull(state.nameMapCenter(first, "역삼1동"));
        assertEquals("강남역", state.getOrigin().getName());
    }

    @Test
    public void shouldOfferResearch_fromOneKilometer() {
        SearchOriginState state = new SearchOriginState();
        // 조회 위치가 없으면 보여주지 않는다.
        assertFalse(state.shouldOfferResearch(37.4979, 127.0276));

        state.onMyLocation(HOME, false);
        // 위도 0.001도는 약 111m
        assertFalse(state.shouldOfferResearch(37.4979, 127.0276));
        assertFalse(state.shouldOfferResearch(37.4979 + 0.0085, 127.0276)); // 약 940m
        assertTrue(state.shouldOfferResearch(37.4979 + 0.0095, 127.0276));  // 약 1,050m
        assertTrue(state.shouldOfferResearch(37.4979, 127.0276 + 0.02));    // 경도 0.02도는 약 1.8km
    }

    @Test
    public void shouldOfferResearch_followsNewOrigin() {
        SearchOriginState state = new SearchOriginState();
        state.onMyLocation(HOME, false);
        double lat = 37.5100;
        double lng = 127.0400;
        assertTrue(state.shouldOfferResearch(lat, lng));

        // 그 자리에서 다시 검색을 누르면 버튼이 사라진다.
        state.selectMapCenter(lat, lng);
        assertFalse(state.shouldOfferResearch(lat, lng));
        // 원래 내 위치 근처로 지도를 옮기면 이제 다시 보인다.
        assertTrue(state.shouldOfferResearch(HOME.getLatitude(), HOME.getLongitude()));
    }
}
