package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.repository.StationDetailRepository;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;

/**
 * 주유소 상세 화면의 상태.
 * 목록에서 넘겨받은 기본 정보(상호, 브랜드, 가격, 거리)는 화면이 직접 들고 있고, 여기서는 상세 조회 결과만 가진다.
 * 상세 조회는 화면을 처음 열 때 한 번만 한다. 화면을 회전하면 ViewModel이 그대로 남아 있어서 다시 부르지 않는다.
 */
public class DetailViewModel extends AndroidViewModel {

    private final StationDetailRepository repository;

    // 조회 전이거나 실패했으면 null
    private final MutableLiveData<StationDetail> detail = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    // 오류가 없으면 null
    private final MutableLiveData<StationError> error = new MutableLiveData<>();

    private String stationId;

    public DetailViewModel(@NonNull Application application) {
        super(application);
        repository = StationDetailRepository.getInstance(application);
    }

    public LiveData<StationDetail> getDetail() {
        return detail;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<StationError> getError() {
        return error;
    }

    /** 화면을 만들 때마다 부르지만, 처음 한 번만 조회한다. */
    public void start(String id) {
        if (stationId != null) {
            return;
        }
        stationId = id;
        load();
    }

    /** 오류 안내의 다시 시도 버튼 */
    public void retry() {
        if (stationId != null && !Boolean.TRUE.equals(loading.getValue())) {
            load();
        }
    }

    private void load() {
        loading.setValue(true);
        error.setValue(null);
        repository.fetchDetail(stationId, new StationDetailRepository.Callback() {
            @Override
            public void onSuccess(StationDetail result) {
                loading.setValue(false);
                detail.setValue(result);
            }

            @Override
            public void onError(StationError stationError) {
                loading.setValue(false);
                error.setValue(stationError);
            }
        });
    }
}
