package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.local.StationFilterPreferences;
import com.example.findcheapgas.data.repository.LocationRepository;
import com.example.findcheapgas.data.repository.StationRepository;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.LocationPermissionState;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.util.StationListBuilder;

import java.util.Collections;
import java.util.List;

/**
 * 지도 탭의 화면 상태를 가진다.
 * - 위치: 현재 좌표, 권한 상태, 위치 조회 중 여부, 기기 위치 켜짐 여부
 * - 주유소: 주변 주유소 목록, 조회 중 여부, 오류, 선택한 주유소
 * - 선택: 유종, 반경, 정렬 (마지막 선택을 StationFilterPreferences에 저장)
 *
 * API 호출은 유종이 바뀌거나 위치를 새로 받았을 때만 한다. 오피넷에서 받은 5km 안 전체 목록(fetchedStations)을
 * 들고 있다가, 반경이나 정렬이 바뀌면 StationListBuilder로 화면 목록(stations)만 다시 만든다.
 */
public class MapViewModel extends AndroidViewModel {

    private final LocationRepository repository;
    private final StationRepository stationRepository;
    private final StationFilterPreferences filterPreferences;

    private final MutableLiveData<UserLocation> location = new MutableLiveData<>();
    private final MutableLiveData<LocationPermissionState> permissionState =
            new MutableLiveData<>(LocationPermissionState.UNKNOWN);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> locationEnabled = new MutableLiveData<>(true);

    private final MutableLiveData<FuelType> fuelType = new MutableLiveData<>();
    private final MutableLiveData<SearchRadius> radius = new MutableLiveData<>();
    private final MutableLiveData<SortOrder> sortOrder = new MutableLiveData<>();

    // 화면에 보여줄 목록 (반경 필터, 정렬 적용). 아직 조회하지 않았으면 null
    private final MutableLiveData<List<GasStation>> stations = new MutableLiveData<>();
    private final MutableLiveData<Boolean> stationsLoading = new MutableLiveData<>(false);
    // 오류가 없으면 null
    private final MutableLiveData<StationError> stationsError = new MutableLiveData<>();
    // 선택한 주유소 ID. 없으면 null
    private final MutableLiveData<String> selectedStationId = new MutableLiveData<>();

    // 지도 탭을 처음 열 때 한 번만 권한을 묻기 위한 표시
    private boolean permissionRequested = false;

    // 마지막 조회 위치. 다시 시도하거나 유종을 바꿀 때 이 위치로 조회한다. 지도를 움직여도 바뀌지 않는다.
    private UserLocation searchLocation;
    // 마지막으로 받은 5km 안 전체 목록 (정렬 전). 조회 전이거나 실패했으면 null
    private List<GasStation> fetchedStations;
    // 조회가 겹치면 마지막 요청의 결과만 반영한다.
    private int stationRequestId = 0;

    public MapViewModel(@NonNull Application application) {
        super(application);
        repository = new LocationRepository(application);
        stationRepository = new StationRepository(application);
        filterPreferences = new StationFilterPreferences(application);
        fuelType.setValue(filterPreferences.getFuelType());
        radius.setValue(filterPreferences.getRadius());
        sortOrder.setValue(filterPreferences.getSortOrder());
    }

    public LiveData<UserLocation> getLocation() {
        return location;
    }

    public LiveData<LocationPermissionState> getPermissionState() {
        return permissionState;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getLocationEnabled() {
        return locationEnabled;
    }

    public LiveData<List<GasStation>> getStations() {
        return stations;
    }

    public LiveData<Boolean> getStationsLoading() {
        return stationsLoading;
    }

    public LiveData<StationError> getStationsError() {
        return stationsError;
    }

    public LiveData<String> getSelectedStationId() {
        return selectedStationId;
    }

    public LiveData<FuelType> getFuelType() {
        return fuelType;
    }

    public LiveData<SearchRadius> getRadius() {
        return radius;
    }

    public LiveData<SortOrder> getSortOrder() {
        return sortOrder;
    }

    public boolean hasPermission() {
        return repository.hasPermission();
    }

    public boolean isPermissionRequested() {
        return permissionRequested;
    }

    public void markPermissionRequested() {
        permissionRequested = true;
    }

    public void onPermissionGranted() {
        permissionState.setValue(LocationPermissionState.GRANTED);
        refreshLocation();
    }

    public void onPermissionDenied(boolean permanently) {
        permissionState.setValue(permanently
                ? LocationPermissionState.PERMANENTLY_DENIED
                : LocationPermissionState.DENIED);
        if (location.getValue() == null) {
            // 권한이 없어도 기본 위치(서울시청) 주변 주유소는 보여준다.
            UserLocation defaultLocation = repository.getDefaultLocation();
            location.setValue(defaultLocation);
            loadStations(defaultLocation);
        }
    }

    /**
     * 위치를 다시 가져오고, 그 위치 기준으로 주변 주유소를 조회한다.
     * 결과는 매번 새 값으로 넣어서 같은 좌표여도 카메라가 다시 이동한다.
     */
    public void refreshLocation() {
        if (Boolean.TRUE.equals(loading.getValue())) {
            return;
        }
        locationEnabled.setValue(repository.isLocationEnabled());
        loading.setValue(true);
        repository.fetchCurrentLocation(result -> {
            loading.setValue(false);
            location.setValue(result);
            loadStations(result);
        });
    }

    /** 화면으로 돌아올 때 호출. 기기 위치가 꺼져 있다가 켜졌으면 위치를 다시 가져온다. */
    public void checkLocationEnabled() {
        boolean wasDisabled = Boolean.FALSE.equals(locationEnabled.getValue());
        boolean enabled = repository.isLocationEnabled();
        locationEnabled.setValue(enabled);
        if (wasDisabled && enabled && permissionState.getValue() == LocationPermissionState.GRANTED) {
            refreshLocation();
        }
    }

    /** 오류 화면의 다시 시도 버튼 */
    public void retryStations() {
        if (searchLocation != null) {
            loadStations(searchLocation);
        }
    }

    /** 유종을 바꾸면 그 유종으로 다시 조회한다 (20분 안에 같은 위치·유종을 조회했으면 캐시 사용). */
    public void setFuelType(FuelType newFuelType) {
        if (newFuelType == fuelType.getValue()) {
            return;
        }
        fuelType.setValue(newFuelType);
        filterPreferences.setFuelType(newFuelType);
        // 위치를 아직 못 받았으면 위치를 받은 뒤 이 유종으로 조회된다.
        if (searchLocation != null) {
            loadStations(searchLocation);
        }
    }

    /** 반경을 바꾸면 API를 부르지 않고 받아 둔 목록에서 다시 고른다. */
    public void setRadius(SearchRadius newRadius) {
        if (newRadius == radius.getValue()) {
            return;
        }
        radius.setValue(newRadius);
        filterPreferences.setRadius(newRadius);
        updateDisplayedStations();
    }

    /** 정렬을 바꾸면 API를 부르지 않고 받아 둔 목록을 다시 정렬한다. */
    public void setSortOrder(SortOrder newSortOrder) {
        if (newSortOrder == sortOrder.getValue()) {
            return;
        }
        sortOrder.setValue(newSortOrder);
        filterPreferences.setSortOrder(newSortOrder);
        updateDisplayedStations();
    }

    /** 마커나 목록 항목을 눌렀을 때 */
    public void selectStation(String stationId) {
        selectedStationId.setValue(stationId);
    }

    /** 지금 화면 목록에 있는 주유소. 없으면 null */
    public GasStation findStation(String stationId) {
        List<GasStation> list = stations.getValue();
        if (list == null || stationId == null) {
            return null;
        }
        for (GasStation station : list) {
            if (station.getId().equals(stationId)) {
                return station;
            }
        }
        return null;
    }

    private void loadStations(UserLocation center) {
        searchLocation = center;
        int requestId = ++stationRequestId;
        stationsLoading.setValue(true);
        stationsError.setValue(null);

        StationQuery query = new StationQuery(center.getLatitude(), center.getLongitude(), fuelType.getValue());
        stationRepository.fetchAroundStations(query, new StationRepository.Callback() {
            @Override
            public void onSuccess(List<GasStation> result) {
                if (requestId != stationRequestId) {
                    return;
                }
                fetchedStations = result;
                stationsLoading.setValue(false);
                updateDisplayedStations();
            }

            @Override
            public void onError(StationError error) {
                if (requestId != stationRequestId) {
                    return;
                }
                fetchedStations = null;
                selectedStationId.setValue(null);
                stationsLoading.setValue(false);
                stationsError.setValue(error);
                stations.setValue(Collections.emptyList());
            }
        });
    }

    /**
     * 받아 둔 전체 목록 → 반경 필터 → 정렬 → 화면. 목록이 바뀌는 곳은 모두 여기를 거친다.
     * 선택한 주유소가 새 목록에 없으면 선택을 먼저 해제해서, 화면이 지워진 주유소를 선택한 채로 그리지 않게 한다.
     */
    private void updateDisplayedStations() {
        if (fetchedStations == null) {
            return;
        }
        List<GasStation> result = StationListBuilder.build(fetchedStations, radius.getValue(), sortOrder.getValue());
        String selectedId = selectedStationId.getValue();
        if (selectedId != null && !containsStation(result, selectedId)) {
            selectedStationId.setValue(null);
        }
        stations.setValue(result);
    }

    private static boolean containsStation(List<GasStation> list, String stationId) {
        for (GasStation station : list) {
            if (station.getId().equals(stationId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stationRepository.shutdown();
    }
}
