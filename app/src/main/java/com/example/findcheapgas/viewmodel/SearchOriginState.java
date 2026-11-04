package com.example.findcheapgas.viewmodel;

import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.UserLocation;

/**
 * 조회 위치를 정하는 규칙. MapViewModel이 쓰고, Android에 의존하지 않아서 단위 테스트로 확인한다.
 * - 처음에는 내 위치를 받으면 그 위치가 조회 위치가 된다.
 * - 장소를 고르면 그 장소가 조회 위치가 되고, 그 뒤로 내 위치를 새로 받아도 조회 위치는 그대로다.
 * - 내 위치로 돌아가면(칩 X, 내 위치 버튼) 다시 내 위치가 조회 위치가 된다.
 * 내 위치(지도 위 파란 점)는 조회 위치와 상관없이 항상 따로 기억한다.
 */
final class SearchOriginState {

    private SearchOrigin origin;
    private UserLocation myLocation;

    /** 지금 조회 위치. 아직 없으면 null */
    SearchOrigin getOrigin() {
        return origin;
    }

    /** 마지막으로 받은 내 위치. 아직 없으면 null */
    UserLocation getMyLocation() {
        return myLocation;
    }

    /**
     * 내 위치를 새로 받았을 때.
     *
     * @param returnToMyLocation 장소 기준이어도 내 위치 기준으로 돌아갈지 (내 위치 버튼, 칩 X)
     * @return 조회 위치가 바뀌었으면 새 조회 위치, 장소 기준이라 그대로면 null
     */
    SearchOrigin onMyLocation(UserLocation location, boolean returnToMyLocation) {
        myLocation = location;
        if (origin != null && origin.isPlace() && !returnToMyLocation) {
            return null;
        }
        origin = SearchOrigin.myLocation(location);
        return origin;
    }

    /** 검색한 장소나 주유소를 조회 위치로 */
    SearchOrigin selectPlace(String name, double latitude, double longitude) {
        origin = SearchOrigin.place(name, latitude, longitude);
        return origin;
    }

    /**
     * 위치를 새로 받지 않고 마지막 내 위치로 돌아간다 (위치 권한이 없을 때).
     *
     * @return 새 조회 위치. 이미 내 위치 기준이거나 내 위치를 아직 모르면 null
     */
    SearchOrigin returnToLastMyLocation() {
        if (myLocation == null || (origin != null && !origin.isPlace())) {
            return null;
        }
        origin = SearchOrigin.myLocation(myLocation);
        return origin;
    }
}
