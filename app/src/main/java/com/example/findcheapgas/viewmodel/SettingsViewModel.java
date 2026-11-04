package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.model.CarInfo;

/** 설정 탭의 상태. 지금은 내 차 정보만 있다. */
public class SettingsViewModel extends AndroidViewModel {

    private final CarInfoRepository carInfoRepository;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
    }

    public LiveData<CarInfo> getCarInfo() {
        return carInfoRepository.getCarInfo();
    }

    public void resetCarInfoToDefault() {
        carInfoRepository.resetToDefault();
    }
}
