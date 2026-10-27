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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.LocationPermissionState;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
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
import java.util.Locale;
import java.util.Map;

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

    // 카카오맵 콜백이 다른 스레드에서 올 수 있어서 화면 작업은 메인 스레드로 넘긴다.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MapViewModel viewModel;

    private MapView mapView;
    private KakaoMap kakaoMap;
    private LabelStyles myLocationStyles;
    private Label myLocationLabel;
    private StationMarkers stationMarkers;
    private boolean mapRunning = false;

    private TextView mapMessage;
    private View progress;
    private View banner;
    private TextView bannerText;
    private MaterialButton bannerButton;
    private FloatingActionButton myLocationButton;

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

        bannerButton.setOnClickListener(v -> onBannerButtonClick());
        myLocationButton.setOnClickListener(v -> onMyLocationClick());

        setUpStationSheet(view);

        if (FindCheapGasApp.isMapAvailable()) {
            startMap(view.findViewById(R.id.map_container));
        } else {
            showMapMessage(R.string.map_unavailable);
        }

        viewModel.getLocation().observe(getViewLifecycleOwner(), location -> showLocationOnMap(location, true));
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> renderProgress());
        viewModel.getPermissionState().observe(getViewLifecycleOwner(), state -> renderBanner());
        viewModel.getLocationEnabled().observe(getViewLifecycleOwner(), enabled -> renderBanner());

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
                    List<GasStation> stations = viewModel.getStations().getValue();
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

        TextView condition = view.findViewById(R.id.station_sheet_condition);
        condition.setText(getString(R.string.station_sheet_condition,
                viewModel.getFuelType().getLabel(),
                radiusKm(),
                viewModel.getSortOrder().getLabel()));

        stationAdapter = new StationAdapter(this::onStationItemClick);
        stationLayoutManager = new LinearLayoutManager(requireContext());
        stationList.setLayoutManager(stationLayoutManager);
        stationList.setAdapter(stationAdapter);
        stationRetryButton.setOnClickListener(v -> viewModel.retryStations());
    }

    private void showStations(List<GasStation> stations) {
        List<GasStation> list = stations != null ? stations : Collections.emptyList();
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

    private void showStationMarkers(List<GasStation> stations) {
        if (stationMarkers == null) {
            return; // 지도가 준비되면 onMapReady에서 다시 호출된다.
        }
        stationMarkers.show(stations, cheapestPrice(stations), viewModel.getSelectedStationId().getValue());
    }

    /** 위치 조회 중이거나 주유소 조회 중이면 위쪽 진행 막대를 보여준다. */
    private void renderProgress() {
        boolean busy = Boolean.TRUE.equals(viewModel.getLoading().getValue())
                || Boolean.TRUE.equals(viewModel.getStationsLoading().getValue());
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    /** 목록 대신 보여줄 안내: 불러오는 중 → 오류 → 주유소 없음 순서로 확인한다. */
    private void renderStationStatus() {
        List<GasStation> stations = viewModel.getStations().getValue();
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
            message = getString(R.string.station_empty, radiusKm());
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

    /** 지도에서 마커를 누르면 목록에서 그 주유소를 강조하고 맨 위로 스크롤한다. */
    private void onMarkerClick(String stationId) {
        if (getView() == null) {
            return;
        }
        viewModel.selectStation(stationId);
        int position = stationAdapter.positionOf(stationId);
        if (position != RecyclerView.NO_POSITION) {
            stationLayoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    /** 목록 항목을 누르면 지도를 그 주유소로 옮긴다. 지도가 보이도록 시트는 접는다. */
    private void onStationItemClick(GasStation station) {
        viewModel.selectStation(station.getId());
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        if (kakaoMap != null) {
            kakaoMap.moveCamera(
                    CameraUpdateFactory.newCenterPosition(LatLng.from(station.getLatitude(), station.getLongitude())),
                    CameraAnimation.from(CAMERA_ANIMATION_MS));
        }
    }

    private static int cheapestPrice(List<GasStation> stations) {
        int cheapest = Integer.MAX_VALUE;
        for (GasStation station : stations) {
            cheapest = Math.min(cheapest, station.getPrice());
        }
        return cheapest;
    }

    /** 3000 → "3", 1500 → "1.5" */
    private String radiusKm() {
        int meters = viewModel.getRadiusMeters();
        return meters % 1000 == 0
                ? String.valueOf(meters / 1000)
                : String.format(Locale.KOREA, "%.1f", meters / 1000.0);
    }
}
