package com.example.findcheapgas.ui.map;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.FindCheapGasApp;
import com.example.findcheapgas.R;
import com.example.findcheapgas.data.repository.LocationRepository;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.LocationPermissionState;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.ui.detail.StationDetailActivity;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.StationListBuilder;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapAuthException;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraAnimation;
import com.kakao.vectormap.camera.CameraUpdate;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelManager;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";

    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    // 주변 1~3km가 한 화면에 보이는 정도
    private static final int DEFAULT_ZOOM_LEVEL = 15;
    private static final int CAMERA_ANIMATION_MS = 500;
    // 기본 레이어에서 다른 라벨보다 위에 그려지도록 높게 둔다.
    private static final long MY_LOCATION_RANK = 10_000;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    // 상세 화면은 닫힐 때 주유소 ID를 돌려준다. 그 주유소를 선택한 채로 지도를 그 위치로 옮긴다.
    private final ActivityResultLauncher<Intent> detailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onDetailResult);

    // 카카오맵 콜백이 다른 스레드에서 올 수 있어서 화면 작업은 메인 스레드로 넘긴다.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MapViewModel viewModel;

    private MapView mapView;
    private KakaoMap kakaoMap;
    private LabelStyles myLocationStyles;
    private Label myLocationLabel;
    private StationMarkers stationMarkers;
    private boolean mapRunning = false;
    // 두 번 빨리 눌러서 상세 화면이 두 개 열리지 않게 한다. 상세 화면이 닫히면 풀린다.
    private boolean detailOpening = false;

    private TextView mapMessage;
    private View progress;
    private View banner;
    private TextView bannerText;
    private MaterialButton bannerButton;
    private FloatingActionButton myLocationButton;
    private View mapLegend;

    private Chip fuelTypeChip;
    private Chip radiusChip;
    private Chip brandChip;
    private MaterialButton sortButton;
    private TextView conditionText;
    private TextView basisText;

    private BottomSheetBehavior<View> sheetBehavior;
    private TextView sheetTitle;
    private RecyclerView stationList;
    private LinearLayoutManager stationLayoutManager;
    private StationAdapter stationAdapter;
    private View stationStatus;
    private View stationStatusProgress;
    private TextView stationStatusText;
    private View stationRetryButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MapViewModel.class);

        mapMessage = view.findViewById(R.id.map_message);
        progress = view.findViewById(R.id.location_progress);
        banner = view.findViewById(R.id.location_banner);
        bannerText = view.findViewById(R.id.location_banner_text);
        bannerButton = view.findViewById(R.id.location_banner_button);
        myLocationButton = view.findViewById(R.id.btn_my_location);
        mapLegend = view.findViewById(R.id.map_legend);

        bannerButton.setOnClickListener(v -> onBannerButtonClick());
        myLocationButton.setOnClickListener(v -> onMyLocationClick());

        setUpStationSheet(view);
        setUpFilters(view);

        if (FindCheapGasApp.isMapAvailable()) {
            startMap(view.findViewById(R.id.map_container));
        } else {
            showMapMessage(R.string.map_unavailable);
        }

        viewModel.getLocation().observe(getViewLifecycleOwner(), location -> showLocationOnMap(location, true));
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> renderProgress());
        viewModel.getPermissionState().observe(getViewLifecycleOwner(), state -> renderBanner());
        viewModel.getLocationEnabled().observe(getViewLifecycleOwner(), enabled -> renderBanner());

        viewModel.getFuelType().observe(getViewLifecycleOwner(), fuelType -> renderFilters());
        viewModel.getRadius().observe(getViewLifecycleOwner(), radius -> renderFilters());
        viewModel.getSortOrder().observe(getViewLifecycleOwner(), sortOrder -> renderFilters());
        // 칩 글자는 저장된 필터를 지금 브랜드 설정에 맞게 정리한 값으로 쓰므로 둘 다 본다.
        viewModel.getBrandFilter().observe(getViewLifecycleOwner(), filter -> renderBrandChip());
        viewModel.getBrandSettings().observe(getViewLifecycleOwner(), settings -> renderBrandChip());
        // 내 차 정보 수정 화면에서 저장하고 돌아오면 바로 바뀐다.
        viewModel.getCarInfo().observe(getViewLifecycleOwner(), this::renderCalculationBasis);

        viewModel.getStations().observe(getViewLifecycleOwner(), this::showStations);
        viewModel.getStationsLoading().observe(getViewLifecycleOwner(), loading -> {
            renderProgress();
            renderStationStatus();
        });
        viewModel.getStationsError().observe(getViewLifecycleOwner(), error -> renderStationStatus());
        viewModel.getSelectedStationId().observe(getViewLifecycleOwner(), id -> {
            stationAdapter.setSelectedId(id);
            if (stationMarkers != null) {
                stationMarkers.setSelected(id);
            }
        });

        // 지도 탭이 처음 열릴 때 한 번만 권한을 묻는다.
        if (!viewModel.isPermissionRequested()) {
            viewModel.markPermissionRequested();
            if (viewModel.hasPermission()) {
                viewModel.onPermissionGranted();
            } else {
                permissionLauncher.launch(LOCATION_PERMISSIONS);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateMapLifecycle(true);

        if (viewModel.isPermissionRequested()) {
            if (viewModel.getPermissionState().getValue() != LocationPermissionState.GRANTED) {
                // 앱 설정 화면에서 권한을 켜고 돌아온 경우
                if (viewModel.hasPermission()) {
                    viewModel.onPermissionGranted();
                }
            } else {
                // 위치 설정 화면에서 기기 위치를 켜고 돌아온 경우
                viewModel.checkLocationEnabled();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        updateMapLifecycle(false);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // 다른 탭으로 바꾸면 Fragment는 그대로 두고 지도 렌더링만 멈춘다.
        updateMapLifecycle(isResumed());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
        mapView = null;
        kakaoMap = null;
        myLocationStyles = null;
        myLocationLabel = null;
        stationMarkers = null;
        mapRunning = false;
        sheetBehavior = null;
        stationList = null;
        stationLayoutManager = null;
        stationAdapter = null;
    }

    // ---------- 권한 ----------

    private void onPermissionResult(Map<String, Boolean> result) {
        if (viewModel.hasPermission()) {
            // 대략적 위치만 허용한 경우도 여기로 온다.
            viewModel.onPermissionGranted();
            return;
        }
        // 거부했는데 시스템이 더 이상 설명을 보여주지 않는다면 "다시 묻지 않음" 상태다.
        // 결과가 비어 있으면 요청이 중간에 취소된 것이므로 다시 요청할 수 있게 둔다.
        boolean permanently = !result.isEmpty()
                && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        viewModel.onPermissionDenied(permanently);
    }

    private void renderBanner() {
        LocationPermissionState state = viewModel.getPermissionState().getValue();
        if (state == LocationPermissionState.DENIED || state == LocationPermissionState.PERMANENTLY_DENIED) {
            bannerText.setText(R.string.permission_rationale);
            bannerButton.setText(state == LocationPermissionState.PERMANENTLY_DENIED
                    ? R.string.permission_open_settings
                    : R.string.permission_request);
            banner.setVisibility(View.VISIBLE);
        } else if (state == LocationPermissionState.GRANTED
                && Boolean.FALSE.equals(viewModel.getLocationEnabled().getValue())) {
            bannerText.setText(R.string.location_disabled);
            bannerButton.setText(R.string.location_open_settings);
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void onBannerButtonClick() {
        LocationPermissionState state = viewModel.getPermissionState().getValue();
        if (state == LocationPermissionState.PERMANENTLY_DENIED) {
            openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null)));
        } else if (state == LocationPermissionState.GRANTED) {
            openSettings(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            permissionLauncher.launch(LOCATION_PERMISSIONS);
        }
    }

    private void onMyLocationClick() {
        if (viewModel.hasPermission()) {
            viewModel.refreshLocation();
        } else {
            // 권한이 없으면 내 위치를 알 수 없으므로 권한부터 받는다.
            onBannerButtonClick();
        }
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "설정 화면을 열 수 없습니다: " + intent.getAction(), e);
        }
    }

    // ---------- 지도 ----------

    private void startMap(ViewGroup container) {
        mapView = new MapView(requireContext());
        container.addView(mapView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapDestroy() {
                Log.d(TAG, "지도 종료");
            }

            @Override
            public void onMapError(Exception error) {
                mainHandler.post(() -> onMapFailed(error));
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull KakaoMap map) {
                mainHandler.post(() -> {
                    if (getView() == null) {
                        return;
                    }
                    kakaoMap = map;
                    // 아래 목록 시트에 가리는 만큼 지도 중심을 위로 올린다.
                    map.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.station_sheet_peek_height));
                    map.setOnLabelClickListener((clickedMap, layer, label) -> {
                        String stationId = stationMarkers != null ? stationMarkers.stationIdOf(layer, label) : null;
                        if (stationId == null) {
                            return false;
                        }
                        mainHandler.post(() -> onMarkerClick(stationId));
                        return true;
                    });
                    stationMarkers = StationMarkers.create(requireContext(), map);

                    UserLocation location = viewModel.getLocation().getValue();
                    if (location != null) {
                        showLocationOnMap(location, false);
                    }
                    List<StationSaving> stations = viewModel.getStations().getValue();
                    if (stations != null) {
                        showStationMarkers(stations);
                    }
                });
            }

            // 지도가 처음 열릴 위치. 위치를 아직 못 받았으면 기본 위치에서 시작한다.
            @NonNull
            @Override
            public LatLng getPosition() {
                UserLocation location = viewModel.getLocation().getValue();
                if (location != null) {
                    return LatLng.from(location.getLatitude(), location.getLongitude());
                }
                return LatLng.from(LocationRepository.DEFAULT_LATITUDE, LocationRepository.DEFAULT_LONGITUDE);
            }

            @Override
            public int getZoomLevel() {
                return DEFAULT_ZOOM_LEVEL;
            }
        });
    }

    private void onMapFailed(Exception error) {
        if (error instanceof MapAuthException) {
            Log.e(TAG, "카카오맵 인증 실패 (errorCode=" + ((MapAuthException) error).getErrorCode()
                    + "). 네이티브 앱 키와 키 해시 등록을 확인하세요.", error);
        } else {
            Log.e(TAG, "카카오맵 오류", error);
        }
        if (getView() == null) {
            return;
        }
        showMapMessage(error instanceof MapAuthException ? R.string.map_auth_failed : R.string.map_error);
    }

    private void showMapMessage(@StringRes int message) {
        mapMessage.setText(message);
        mapMessage.setVisibility(View.VISIBLE);
        myLocationButton.setVisibility(View.GONE);
        mapLegend.setVisibility(View.GONE);
    }

    /** 탭이 보이고 화면이 켜져 있을 때만 지도를 그린다. */
    private void updateMapLifecycle(boolean resumed) {
        if (mapView == null) {
            return;
        }
        boolean shouldRun = resumed && !isHidden();
        if (shouldRun == mapRunning) {
            return;
        }
        mapRunning = shouldRun;
        if (shouldRun) {
            mapView.resume();
        } else {
            mapView.pause();
        }
    }

    private void showLocationOnMap(UserLocation location, boolean animate) {
        if (kakaoMap == null || location == null) {
            return; // 지도가 준비되면 onMapReady에서 다시 호출된다.
        }
        LatLng position = LatLng.from(location.getLatitude(), location.getLongitude());
        CameraUpdate update = CameraUpdateFactory.newCenterPosition(position, DEFAULT_ZOOM_LEVEL);
        if (animate) {
            kakaoMap.moveCamera(update, CameraAnimation.from(CAMERA_ANIMATION_MS));
        } else {
            kakaoMap.moveCamera(update);
        }
        updateMyLocationLabel(location, position);
    }

    /** 카카오맵 SDK v2에는 내 위치 표시 기능이 없어서 파란 점 Label을 직접 올린다. */
    private void updateMyLocationLabel(UserLocation location, LatLng position) {
        LabelManager labelManager = kakaoMap.getLabelManager();
        if (labelManager == null) {
            return;
        }
        LabelLayer layer = labelManager.getLayer();
        if (layer == null) {
            return;
        }

        if (!location.isUserLocation()) {
            // 기본 위치는 내 위치가 아니므로 점을 지운다.
            if (myLocationLabel != null) {
                layer.remove(myLocationLabel);
                myLocationLabel = null;
            }
            return;
        }

        if (myLocationLabel != null) {
            myLocationLabel.moveTo(position);
            return;
        }
        if (myLocationStyles == null) {
            myLocationStyles = labelManager.addLabelStyles(LabelStyles.from(
                    LabelStyle.from(createMyLocationBitmap())
                            .setAnchorPoint(0.5f, 0.5f)    // 점의 가운데가 좌표에 오도록
                            .setApplyDpScale(false)));     // 비트맵을 이미 dp 기준 크기로 그렸다.
        }
        myLocationLabel = layer.addLabel(LabelOptions.from(position)
                .setStyles(myLocationStyles)
                .setRank(MY_LOCATION_RANK));
    }

    /** 흰 테두리가 있는 파란 원 */
    private Bitmap createMyLocationBitmap() {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(22 * density);
        float radius = size / 2f;
        float shadow = density;
        float border = 3 * density;

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 밝은 지도 위에서도 테두리가 보이도록 옅은 그림자
        paint.setColor(0x33000000);
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setColor(ContextCompat.getColor(requireContext(), R.color.my_location_border));
        canvas.drawCircle(radius, radius, radius - shadow, paint);
        paint.setColor(ContextCompat.getColor(requireContext(), R.color.my_location_dot));
        canvas.drawCircle(radius, radius, radius - shadow - border, paint);
        return bitmap;
    }

    // ---------- 주변 주유소 (마커, 아래 목록) ----------

    private void setUpStationSheet(View view) {
        sheetBehavior = BottomSheetBehavior.from(view.findViewById(R.id.station_sheet));
        sheetTitle = view.findViewById(R.id.station_sheet_title);
        stationList = view.findViewById(R.id.station_list);
        stationStatus = view.findViewById(R.id.station_status);
        stationStatusProgress = view.findViewById(R.id.station_status_progress);
        stationStatusText = view.findViewById(R.id.station_status_text);
        stationRetryButton = view.findViewById(R.id.station_retry_button);

        stationAdapter = new StationAdapter(this::onStationItemClick);
        stationLayoutManager = new LinearLayoutManager(requireContext());
        stationList.setLayoutManager(stationLayoutManager);
        stationList.setAdapter(stationAdapter);
        stationRetryButton.setOnClickListener(v -> viewModel.retryStations());

        // 시트는 화면 높이만큼 크고 아래로 내려가 있으므로, 화면 밖으로 나간 만큼 목록 아래 여백을 늘려
        // 어느 상태에서든 마지막 항목까지 스크롤하면 화면 안에 보이게 한다.
        View sheet = view.findViewById(R.id.station_sheet);
        int baseBottomPadding = stationList.getPaddingBottom();
        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState != BottomSheetBehavior.STATE_DRAGGING
                        && newState != BottomSheetBehavior.STATE_SETTLING) {
                    fitListToVisibleSheet(sheet, baseBottomPadding);
                }
                updateLegendVisibility();
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateLegendVisibility();
            }
        });
        // 처음 배치, 화면 회전 등으로 크기가 바뀐 뒤에도 맞춘다. 배치 중에는 여백을 바꾸지 않도록 post한다.
        sheet.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                v.post(() -> {
                    fitListToVisibleSheet(sheet, baseBottomPadding);
                    updateLegendVisibility();
                }));
    }

    /**
     * 범례는 시트 위쪽에 붙어 움직이므로, 시트를 절반보다 높이 올리면 필터 줄과 겹치지 않게 숨긴다.
     * 그때는 지도가 거의 가려져서 범례가 필요 없다.
     */
    private void updateLegendVisibility() {
        if (sheetBehavior == null || mapLegend == null || mapMessage.getVisibility() == View.VISIBLE) {
            return;
        }
        View sheet = getView() != null ? getView().findViewById(R.id.station_sheet) : null;
        if (sheet == null || !(sheet.getParent() instanceof View)) {
            return;
        }
        int parentHeight = ((View) sheet.getParent()).getHeight();
        boolean aboveHalf = parentHeight > 0 && sheet.getTop() < parentHeight / 2;
        mapLegend.setVisibility(aboveHalf ? View.INVISIBLE : View.VISIBLE);
    }

    /** 시트에서 부모 아래로 잘려 나간 높이만큼 목록 아래 여백을 준다. */
    private void fitListToVisibleSheet(View sheet, int baseBottomPadding) {
        if (stationList == null || !(sheet.getParent() instanceof View)) {
            return;
        }
        int parentHeight = ((View) sheet.getParent()).getHeight();
        int hidden = Math.max(0, sheet.getBottom() - parentHeight);
        int padding = baseBottomPadding + hidden;
        if (stationList.getPaddingBottom() != padding) {
            stationList.setPadding(stationList.getPaddingLeft(), stationList.getPaddingTop(),
                    stationList.getPaddingRight(), padding);
        }
    }

    private void showStations(List<StationSaving> stations) {
        List<StationSaving> list = stations != null ? stations : Collections.emptyList();
        stationAdapter.submit(list, cheapestPrice(list));
        stationAdapter.setSelectedId(viewModel.getSelectedStationId().getValue());
        stationLayoutManager.scrollToPosition(0);
        if (stations != null && !stations.isEmpty()) {
            sheetTitle.setText(getString(R.string.station_sheet_title_count, stations.size()));
        } else {
            sheetTitle.setText(R.string.station_sheet_title);
        }
        showStationMarkers(list);
        renderStationStatus();
    }

    private void showStationMarkers(List<StationSaving> stations) {
        if (stationMarkers == null) {
            return; // 지도가 준비되면 onMapReady에서 다시 호출된다.
        }
        stationMarkers.show(stations, viewModel.getSelectedStationId().getValue());
    }

    /** 위치 조회 중이거나 주유소 조회 중이면 위쪽 진행 막대를 보여준다. */
    private void renderProgress() {
        boolean busy = Boolean.TRUE.equals(viewModel.getLoading().getValue())
                || Boolean.TRUE.equals(viewModel.getStationsLoading().getValue());
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    /**
     * 목록 대신 보여줄 안내: 불러오는 중 → 오류 → 주유소 없음 순서로 확인한다.
     * 주유소가 없으면 반경 때문인지, 숨긴 브랜드나 빠른 필터 때문인지 나눠서 알려준다.
     */
    private void renderStationStatus() {
        List<StationSaving> stations = viewModel.getStations().getValue();
        StationError error = viewModel.getStationsError().getValue();
        boolean loading = Boolean.TRUE.equals(viewModel.getStationsLoading().getValue());

        String message = null;
        boolean showProgress = false;
        boolean showRetry = false;
        if (loading) {
            message = getString(R.string.station_loading);
            showProgress = true;
        } else if (error != null) {
            message = getString(error == StationError.NETWORK
                    ? R.string.station_error_network
                    : R.string.station_error_opinet);
            showRetry = true;
        } else if (stations == null) {
            // 아직 위치를 받기 전
            message = getString(R.string.station_locating);
            showProgress = true;
        } else if (stations.isEmpty()) {
            StationListBuilder.EmptyReason reason = viewModel.getEmptyReason().getValue();
            if (reason == StationListBuilder.EmptyReason.BRAND_FILTER) {
                message = getString(R.string.station_empty_brand_filter);
            } else if (reason == StationListBuilder.EmptyReason.HIDDEN_BRANDS) {
                message = getString(R.string.station_empty_hidden_brands);
            } else {
                message = getString(R.string.station_empty, viewModel.getRadius().getValue().getLabel());
            }
        }

        if (message == null) {
            stationStatus.setVisibility(View.GONE);
            stationList.setVisibility(View.VISIBLE);
            return;
        }
        stationStatusText.setText(message);
        stationStatusProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        stationRetryButton.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        stationStatus.setVisibility(View.VISIBLE);
        stationList.setVisibility(View.GONE);
    }

    /**
     * 마커를 처음 누르면 목록에서 그 주유소를 강조하고 맨 위로 스크롤한다.
     * 이미 선택된 마커를 한 번 더 누르면 상세 화면을 연다.
     */
    private void onMarkerClick(String stationId) {
        if (getView() == null) {
            return;
        }
        if (stationId.equals(viewModel.getSelectedStationId().getValue())) {
            StationSaving saving = viewModel.findStation(stationId);
            if (saving != null) {
                openDetail(saving);
                return;
            }
        }
        viewModel.selectStation(stationId);
        scrollListTo(stationId);
    }

    /**
     * 목록 항목을 누르면 그 주유소를 선택하고 상세 화면을 연다.
     * 돌아왔을 때 지도에 그 주유소가 보이도록 시트를 접고 지도를 미리 옮겨 둔다.
     */
    private void onStationItemClick(StationSaving saving) {
        viewModel.selectStation(saving.getStation().getId());
        focusStationOnMap(saving.getStation());
        openDetail(saving);
    }

    /** 지도 탭에서 계산한 예상 이득 결과를 그대로 넘긴다. 상세 화면은 이 값을 다시 계산하지 않는다. */
    private void openDetail(StationSaving saving) {
        if (detailOpening) {
            return;
        }
        detailOpening = true;
        detailLauncher.launch(StationDetailActivity.createIntent(
                requireContext(), saving, viewModel.getFuelType().getValue()));
    }

    /** 상세 화면에서 돌아오면 그 주유소를 선택한 채로 지도를 그 위치로 옮긴다. */
    private void onDetailResult(ActivityResult result) {
        detailOpening = false;
        String stationId = StationDetailActivity.stationIdFrom(result.getData());
        StationSaving saving = viewModel.findStation(stationId);
        if (saving == null || getView() == null) {
            return; // 그사이 목록이 바뀌어 주유소가 없으면 그대로 둔다.
        }
        viewModel.selectStation(stationId);
        focusStationOnMap(saving.getStation());
        scrollListTo(stationId);
    }

    /**
     * 지도가 보이도록 시트를 접고 지도 중심을 주유소로 옮긴다.
     * 곧 상세 화면이 열리거나 막 닫힌 때라서 애니메이션 없이 바로 옮긴다.
     */
    private void focusStationOnMap(GasStation station) {
        if (sheetBehavior != null) {
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        if (kakaoMap != null) {
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(
                    LatLng.from(station.getLatitude(), station.getLongitude())));
        }
    }

    private void scrollListTo(String stationId) {
        int position = stationAdapter.positionOf(stationId);
        if (position != RecyclerView.NO_POSITION) {
            stationLayoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private static int cheapestPrice(List<StationSaving> stations) {
        int cheapest = Integer.MAX_VALUE;
        for (StationSaving saving : stations) {
            cheapest = Math.min(cheapest, saving.getStation().getPrice());
        }
        return cheapest;
    }

    // ---------- 유종, 반경, 브랜드, 정렬 선택 ----------

    private void setUpFilters(View view) {
        fuelTypeChip = view.findViewById(R.id.chip_fuel_type);
        radiusChip = view.findViewById(R.id.chip_radius);
        brandChip = view.findViewById(R.id.chip_brand);
        sortButton = view.findViewById(R.id.btn_sort);
        conditionText = view.findViewById(R.id.station_sheet_condition);
        basisText = view.findViewById(R.id.station_sheet_basis);

        // 칩의 ▼ 아이콘을 눌러도 칩을 누른 것과 같게 한다.
        fuelTypeChip.setOnClickListener(this::showFuelTypeMenu);
        fuelTypeChip.setOnCloseIconClickListener(v -> showFuelTypeMenu(fuelTypeChip));
        radiusChip.setOnClickListener(this::showRadiusMenu);
        radiusChip.setOnCloseIconClickListener(v -> showRadiusMenu(radiusChip));
        brandChip.setOnClickListener(v -> showBrandFilter());
        brandChip.setOnCloseIconClickListener(v -> showBrandFilter());
        sortButton.setOnClickListener(this::showSortMenu);
        basisText.setOnClickListener(v ->
                startActivity(OnboardingActivity.createEditIntent(requireContext())));
    }

    private void showFuelTypeMenu(View anchor) {
        showChoiceMenu(anchor, FuelType.values(), viewModel.getFuelType().getValue(),
                FuelType::getLabel, viewModel::setFuelType);
    }

    private void showRadiusMenu(View anchor) {
        showChoiceMenu(anchor, SearchRadius.values(), viewModel.getRadius().getValue(),
                SearchRadius::getLabel, viewModel::setRadius);
    }

    /** 여러 개를 고를 수 있어서 팝업 메뉴 대신 시트를 연다. 이미 열려 있으면 다시 열지 않는다. */
    private void showBrandFilter() {
        if (getChildFragmentManager().findFragmentByTag(BrandFilterSheet.TAG) == null) {
            new BrandFilterSheet().show(getChildFragmentManager(), BrandFilterSheet.TAG);
        }
    }

    /** 정렬 메뉴는 SortOrder 상수 순서대로 만든다 (예상 이득순, 가격순, 거리순). */
    private void showSortMenu(View anchor) {
        showChoiceMenu(anchor, SortOrder.values(), viewModel.getSortOrder().getValue(),
                SortOrder::getLabel, viewModel::setSortOrder);
    }

    /** 하나만 고를 수 있는 팝업 메뉴. 지금 선택한 항목에 체크 표시를 한다. */
    private <T> void showChoiceMenu(View anchor, T[] options, T current,
                                    Function<T, String> label, Consumer<T> onChoice) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();
        for (int i = 0; i < options.length; i++) {
            menu.add(Menu.NONE, i, i, label.apply(options[i])).setChecked(options[i] == current);
        }
        menu.setGroupCheckable(Menu.NONE, true, true);
        popup.setOnMenuItemClickListener(item -> {
            onChoice.accept(options[item.getItemId()]);
            return true;
        });
        popup.show();
    }

    /** 필터 칩, 정렬 버튼, 시트의 조건 문구를 지금 선택에 맞춘다. */
    private void renderFilters() {
        FuelType fuelType = viewModel.getFuelType().getValue();
        SearchRadius radius = viewModel.getRadius().getValue();
        SortOrder sortOrder = viewModel.getSortOrder().getValue();
        if (fuelType == null || radius == null || sortOrder == null) {
            return;
        }
        fuelTypeChip.setText(fuelType.getLabel());
        fuelTypeChip.setContentDescription(getString(R.string.filter_fuel_type_description, fuelType.getLabel()));
        radiusChip.setText(getString(R.string.filter_radius, radius.getLabel()));
        radiusChip.setContentDescription(getString(R.string.filter_radius_description, radius.getLabel()));
        sortButton.setText(sortOrder.getLabel());
        sortButton.setContentDescription(getString(R.string.station_sort_description, sortOrder.getLabel()));
        conditionText.setText(getString(R.string.station_sheet_condition,
                fuelType.getLabel(), radius.getLabel(), sortOrder.getLabel()));
    }

    /** 브랜드 칩 글자: "브랜드: 전체", "할인 브랜드", "SK 외 2" 등 */
    private void renderBrandChip() {
        BrandFilter filter = viewModel.getBrandFilter().getValue();
        BrandSettings settings = viewModel.getBrandSettings().getValue();
        if (filter == null || settings == null) {
            return;
        }
        String label = filter.effective(settings).label();
        brandChip.setText(label);
        brandChip.setContentDescription(getString(R.string.filter_brand_description, label));
    }

    /** 시트 제목 아래 계산 기준 문구. 기본값이면 "(기본값)"이 붙는다. */
    private void renderCalculationBasis(CarInfo carInfo) {
        String basis = FormatUtil.formatCalculationBasis(carInfo);
        basisText.setText(basis);
        basisText.setContentDescription(getString(R.string.station_basis_description, basis));
    }
}
