package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;

/** 설정 탭의 상태. 지금은 내 차 정보와 내 브랜드 요약이 있다. */
public class SettingsViewModel extends AndroidViewModel {

    private final CarInfoRepository carInfoRepository;
    private final BrandSettingsRepository brandSettingsRepository;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
        brandSettingsRepository = BrandSettingsRepository.getInstance(application);
    }

    public LiveData<CarInfo> getCarInfo() {
        return carInfoRepository.getCarInfo();
    }

    public LiveData<BrandSettings> getBrandSettings() {
        return brandSettingsRepository.getBrandSettings();
    }

    public void resetCarInfoToDefault() {
        carInfoRepository.resetToDefault();
    }
}
