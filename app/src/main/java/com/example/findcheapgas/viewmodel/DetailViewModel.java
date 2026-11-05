package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.findcheapgas.data.repository.FavoriteRepository;
import com.example.findcheapgas.data.repository.StationDetailRepository;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;

/**
 * 주유소 상세 화면의 상태.
 * 목록에서 넘겨받은 기본 정보(상호, 브랜드, 가격, 거리)는 화면이 직접 들고 있고, 여기서는 상세 조회 결과만 가진다.
 * 상세 조회는 화면을 처음 열 때 한 번만 한다. 화면을 회전하면 ViewModel이 그대로 남아 있어서 다시 부르지 않는다.
 * 즐겨찾기 버튼: 이 주유소가 즐겨찾기인지 LiveData로 보여주고, 누르면 추가 / 삭제를 바꾼다.
 */
public class DetailViewModel extends AndroidViewModel {

    /** 즐겨찾기 버튼을 눌렀을 때 결과 */
    public enum FavoriteMessage {
        ADDED,
        REMOVED,
        FULL
    }

    private final StationDetailRepository repository;
    private final FavoriteRepository favoriteRepository;
    // 이 주유소가 즐겨찾기인지. start 전에는 false
    private final MediatorLiveData<Boolean> favorite = new MediatorLiveData<>(false);
    // 즐겨찾기 버튼 결과 안내 (한 번 보여주면 null)
    private final MutableLiveData<FavoriteMessage> favoriteMessage = new MutableLiveData<>();
    // 추가나 삭제를 처리하는 중이면 버튼을 다시 눌러도 무시한다.
    private boolean favoriteBusy = false;

    // 조회 전이거나 실패했으면 null
    private final MutableLiveData<StationDetail> detail = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    // 오류가 없으면 null
    private final MutableLiveData<StationError> error = new MutableLiveData<>();

    private String stationId;

    public DetailViewModel(@NonNull Application application) {
        super(application);
        repository = StationDetailRepository.getInstance(application);
        favoriteRepository = FavoriteRepository.getInstance(application);
    }

    public LiveData<Boolean> isFavorite() {
        return favorite;
    }

    public LiveData<FavoriteMessage> getFavoriteMessage() {
        return favoriteMessage;
    }

    public void onFavoriteMessageShown() {
        favoriteMessage.setValue(null);
    }

    /**
     * 즐겨찾기 버튼. 즐겨찾기면 지우고, 아니면 저장한다 (20곳이 차 있으면 저장하지 않음).
     * 주소는 상세 조회가 끝났으면 그 주소를 함께 저장한다.
     */
    public void toggleFavorite(FavoriteStation candidate) {
        if (favoriteBusy) {
            return;
        }
        favoriteBusy = true;
        if (Boolean.TRUE.equals(favorite.getValue())) {
            favoriteRepository.remove(candidate.getId(), removed -> {
                favoriteBusy = false;
                favoriteMessage.setValue(FavoriteMessage.REMOVED);
            });
            return;
        }
        favoriteRepository.add(candidate, result -> {
            favoriteBusy = false;
            favoriteMessage.setValue(result == FavoriteStation.AddResult.FULL
                    ? FavoriteMessage.FULL : FavoriteMessage.ADDED);
        });
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
        favorite.addSource(Transformations.map(favoriteRepository.getFavoriteIds(), ids -> ids.contains(id)),
                favorite::setValue);
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
                // 주소를 모르고 저장한 즐겨찾기면 주소를 채운다.
                favoriteRepository.fillAddress(stationId, result.getAddress());
            }

            @Override
            public void onError(StationError stationError) {
                loading.setValue(false);
                error.setValue(stationError);
            }
        });
    }
}
