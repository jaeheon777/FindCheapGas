package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;

/**
 * 지도 탭에서 마지막으로 고른 유종, 반경, 정렬 (SharedPreferences).
 * 앱을 다시 켜도 이 값으로 시작한다. 설정 탭의 기본값 기능은 나중에 만든다.
 */
public class StationFilterPreferences {

    public static final FuelType DEFAULT_FUEL_TYPE = FuelType.GASOLINE;
    public static final SearchRadius DEFAULT_RADIUS = SearchRadius.KM_3;
    public static final SortOrder DEFAULT_SORT_ORDER = SortOrder.PRICE;

    private static final String PREFS_NAME = "station_filter";
    private static final String KEY_FUEL_TYPE = "fuel_type";      // 유종 코드 (B027 등)
    private static final String KEY_RADIUS = "radius_meters";      // 1000, 3000, 5000
    private static final String KEY_SORT_ORDER = "sort_order";     // SortOrder.name()

    private final SharedPreferences prefs;

    public StationFilterPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public FuelType getFuelType() {
        return FuelType.fromCode(prefs.getString(KEY_FUEL_TYPE, null), DEFAULT_FUEL_TYPE);
    }

    public SearchRadius getRadius() {
        return SearchRadius.fromMeters(prefs.getInt(KEY_RADIUS, 0), DEFAULT_RADIUS);
    }

    public SortOrder getSortOrder() {
        return SortOrder.fromName(prefs.getString(KEY_SORT_ORDER, null), DEFAULT_SORT_ORDER);
    }

    public void setFuelType(FuelType fuelType) {
        prefs.edit().putString(KEY_FUEL_TYPE, fuelType.getCode()).apply();
    }

    public void setRadius(SearchRadius radius) {
        prefs.edit().putInt(KEY_RADIUS, radius.getMeters()).apply();
    }

    public void setSortOrder(SortOrder sortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, sortOrder.name()).apply();
    }
}
