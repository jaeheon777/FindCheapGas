package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.repository.LocationRepository;
import com.example.findcheapgas.data.repository.StationRepository;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.LocationPermissionState;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.UserLocation;

import java.util.Collections;
import java.util.List;

/**
 * 지도 탭의 화면 상태를 가진다.
 * - 위치: 현재 좌표, 권한 상태, 위치 조회 중 여부, 기기 위치 켜짐 여부
 * - 주유소: 주변 주유소 목록, 조회 중 여부, 오류, 선택한 주유소
 */
public class MapViewModel extends AndroidViewModel {

    // 유종·반경·정렬 선택 UI를 만들기 전까지 쓰는 기본값
    private static final FuelType DEFAULT_FUEL_TYPE = FuelType.GASOLINE;
    private static final int DEFAULT_RADIUS_METERS = 3000;
    private static final SortOrder DEFAULT_SORT_ORDER = SortOrder.PRICE;

    private final LocationRepository repository;
    private final StationRepository stationRepository;

    private final MutableLiveData<UserLocation> location = new MutableLiveData<>();
    private final MutableLiveData<LocationPermissionState> permissionState =
            new MutableLiveData<>(LocationPermissionState.UNKNOWN);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> locationEnabled = new MutableLiveData<>(true);

    // 아직 조회하지 않았으면 null
    private final MutableLiveData<List<GasStation>> stations = new MutableLiveData<>();
    private final MutableLiveData<Boolean> stationsLoading = new MutableLiveData<>(false);
    // 오류가 없으면 null
    private final MutableLiveData<StationError> stationsError = new MutableLiveData<>();
    // 선택한 주유소 ID. 없으면 null
    private final MutableLiveData<String> selectedStationId = new MutableLiveData<>();

    // 지도 탭을 처음 열 때 한 번만 권한을 묻기 위한 표시
    private boolean permissionRequested = false;

    // 다시 시도할 때 쓸 마지막 조회 위치
    private UserLocation searchLocation;
    // 조회가 겹치면 마지막 요청의 결과만 반영한다.
    private int stationRequestId = 0;

    public MapViewModel(@NonNull Application application) {
        super(application);
        repository = new LocationRepository(application);
        stationRepository = new StationRepository(application);
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

    public FuelType getFuelType() {
        return DEFAULT_FUEL_TYPE;
    }

    public int getRadiusMeters() {
        return DEFAULT_RADIUS_METERS;
    }

    public SortOrder getSortOrder() {
        return DEFAULT_SORT_ORDER;
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

    /** 마커나 목록 항목을 눌렀을 때 */
    public void selectStation(String stationId) {
        selectedStationId.setValue(stationId);
    }

    private void loadStations(UserLocation center) {
        searchLocation = center;
        int requestId = ++stationRequestId;
        stationsLoading.setValue(true);
        stationsError.setValue(null);
        selectedStationId.setValue(null);

        StationQuery query = new StationQuery(center.getLatitude(), center.getLongitude(),
                getFuelType(), getRadiusMeters(), getSortOrder());
        stationRepository.fetchAroundStations(query, new StationRepository.Callback() {
            @Override
            public void onSuccess(List<GasStation> result) {
                if (requestId != stationRequestId) {
                    return;
                }
                stationsLoading.setValue(false);
                stations.setValue(Collections.unmodifiableList(result));
            }

            @Override
            public void onError(StationError error) {
                if (requestId != stationRequestId) {
                    return;
                }
                stationsLoading.setValue(false);
                stationsError.setValue(error);
                stations.setValue(Collections.emptyList());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stationRepository.shutdown();
    }
}
