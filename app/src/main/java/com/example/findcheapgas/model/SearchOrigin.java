package com.example.findcheapgas.model;

/**
 * 조회 위치. 주변 주유소를 이 위치 기준으로 조회하고, 거리, 이동 비용, 기준 주유소, 예상 이득도 모두
 * 이 위치에서 출발한다고 보고 계산한다. 위치는 WGS84 위도·경도다.
 * - MY_LOCATION: 현재 위치 (위치를 못 받았으면 기본 위치). 내 위치를 새로 받으면 따라 바뀐다.
 * - PLACE: 검색해서 고른 장소나 주유소. 내 위치가 바뀌어도 그대로 있다.
 * "이 지역에서 다시 검색"(지도 중심)을 넣을 때는 Kind에 값을 하나 더 두면 된다.
 */
public final class SearchOrigin {

    public enum Kind {
        MY_LOCATION,
        PLACE
    }

    private final Kind kind;
    // PLACE일 때 장소 이름 (예: 강남역). MY_LOCATION이면 null
    private final String name;
    private final double latitude;
    private final double longitude;
    // MY_LOCATION일 때 위치를 어디서 얻었는지. PLACE면 null
    private final UserLocation.Source locationSource;

    private SearchOrigin(Kind kind, String name, double latitude, double longitude,
                         UserLocation.Source locationSource) {
        this.kind = kind;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationSource = locationSource;
    }

    public static SearchOrigin myLocation(UserLocation location) {
        return new SearchOrigin(Kind.MY_LOCATION, null, location.getLatitude(), location.getLongitude(),
                location.getSource());
    }

    public static SearchOrigin place(String name, double latitude, double longitude) {
        return new SearchOrigin(Kind.PLACE, name, latitude, longitude, null);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isPlace() {
        return kind == Kind.PLACE;
    }

    /** 장소 이름. MY_LOCATION이면 null */
    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /** 내 위치를 못 받아 기본 위치(서울시청)로 조회하는 중인지 */
    public boolean isDefaultLocation() {
        return kind == Kind.MY_LOCATION && locationSource == UserLocation.Source.DEFAULT;
    }
}
