package com.example.findcheapgas.model;

/** 주변 주유소 조회 조건. 위치는 WGS84 위도·경도다. */
public class StationQuery {

    private final double latitude;
    private final double longitude;
    private final FuelType fuelType;
    private final int radiusMeters;
    private final SortOrder sortOrder;

    public StationQuery(double latitude, double longitude, FuelType fuelType, int radiusMeters, SortOrder sortOrder) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.fuelType = fuelType;
        this.radiusMeters = radiusMeters;
        this.sortOrder = sortOrder;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }
}
