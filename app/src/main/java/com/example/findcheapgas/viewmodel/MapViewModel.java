package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.repository.LocationRepository;
import com.example.findcheapgas.model.LocationPermissionState;
import com.example.findcheapgas.model.UserLocation;

/**
 * 지도 탭의 화면 상태(현재 좌표, 권한 상태, 로딩 여부, 기기 위치 켜짐 여부)를 가진다.
 */
public class MapViewModel extends AndroidViewModel {

    private final LocationRepository repository;

    private final MutableLiveData<UserLocation> location = new MutableLiveData<>();
    private final MutableLiveData<LocationPermissionState> permissionState =
            new MutableLiveData<>(LocationPermissionState.UNKNOWN);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> locationEnabled = new MutableLiveData<>(true);

    // 지도 탭을 처음 열 때 한 번만 권한을 묻기 위한 표시
    private boolean permissionRequested = false;

    public MapViewModel(@NonNull Application application) {
        super(application);
        repository = new LocationRepository(application);
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
            location.setValue(repository.getDefaultLocation());
        }
    }

    /** 위치를 다시 가져온다. 결과는 매번 새 값으로 넣어서 같은 좌표여도 카메라가 다시 이동한다. */
    public void refreshLocation() {
        if (Boolean.TRUE.equals(loading.getValue())) {
            return;
        }
        locationEnabled.setValue(repository.isLocationEnabled());
        loading.setValue(true);
        repository.fetchCurrentLocation(result -> {
            loading.setValue(false);
            location.setValue(result);
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
}
