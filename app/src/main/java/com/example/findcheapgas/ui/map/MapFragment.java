package com.example.findcheapgas.ui.map;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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
import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.ui.OriginLabels;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.ui.detail.RouteAppLauncher;
import com.example.findcheapgas.ui.detail.StationDetailActivity;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.PlaceCompareBuilder;
import com.example.findcheapgas.util.StationListBuilder;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.example.findcheapgas.viewmodel.SearchViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
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
    // 검색창 상태. 검색 결과 시트도 같은 ViewModel을 쓴다.
    private SearchViewModel searchViewModel;

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
    private FloatingActionButton myLocationButton;
    private View researchButton;

    private MaterialButtonToggleGroup searchModeGroup;
    private EditText searchInput;

    private Chip originChip;
    private Chip fuelTypeChip;
    private Chip radiusChip;
    private Chip brandChip;
    private MaterialButton sortButton;

    private View stationSheet;
    private BottomSheetBehavior<View> sheetBehavior;
    private TextView sheetTitle;
    private RecyclerView stationList;
    private LinearLayoutManager stationLayoutManager;
    private StationAdapter stationAdapter;
    // 지도 아래 여백으로 쓴 시트 peek 높이. 이득 1위 카드 높이가 바뀌면 둘 다 다시 맞춘다.
    private int appliedPeekHeight;

    // 시트를 내린 상태의 이득 1위 카드
    private View bestCard;
    private View bestCardContent;
    private TextView bestCardLabel;
    private View bestCardShowBest;
    private TextView bestCardHeadline;
    private TextView bestCardName;
    private TextView bestCardInfo;
    private View bestCardRoute;
    private TextView basisText;
    private View bestCardStatus;
    private View bestCardStatusProgress;
    private TextView bestCardStatusText;
    private MaterialButton bestCardStatusButton;
    // 카드가 지금 보여주는 주유소 (상세 화면, 길찾기에 넘긴다). 안내를 보여주는 중이면 null
    @Nullable
    private StationSaving cardStation;
    // 안내 버튼을 눌렀을 때 할 일 (다시 시도, 권한 요청, 설정 열기)
    @Nullable
    private Runnable cardStatusAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 즐겨찾기 탭과 함께 쓰므로 Activity 범위로 만든다.
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        mapMessage = view.findViewById(R.id.map_message);
        progress = view.findViewById(R.id.location_progress);
        myLocationButton = view.findViewById(R.id.btn_my_location);

        myLocationButton.setOnClickListener(v -> onMyLocationClick());
        researchButton = view.findViewById(R.id.btn_research);
        researchButton.setOnClickListener(v -> viewModel.researchMapCenter());

        setUpStationSheet(view);
        setUpBestCard(view);
        setUpSearch(view);
        setUpFilters(view);

        if (FindCheapGasApp.isMapAvailable()) {
            startMap(view.findViewById(R.id.map_container));
        } else {
            showMapMessage(R.string.map_unavailable);
        }

        // 내 위치는 파란 점만 옮기고, 지도는 조회 위치(내 위치 또는 검색한 장소)로 옮긴다.
        viewModel.getLocation().observe(getViewLifecycleOwner(), this::updateMyLocationLabel);
        // 지도 중심 기준("이 지역에서 다시 검색")이면 지도가 이미 그 자리에 있으므로 옮기지 않는다 (확대 수준도 그대로).
        // 동 이름이 나중에 붙을 때도 여기로 오는데, 그때도 지도를 옮기지 않는다.
        viewModel.getOrigin().observe(getViewLifecycleOwner(), origin -> {
            if (origin != null && !origin.isMapCenter()) {
                moveCameraToOrigin(origin, true);
            }
            renderOriginChip(origin);
            renderFilters();
            renderSheetTitle();
            renderBestCard();
        });
        viewModel.getMockAreaNotice().observe(getViewLifecycleOwner(), show -> {
            if (Boolean.TRUE.equals(show)) {
                Snackbar.make(view, R.string.mock_area_notice, Snackbar.LENGTH_LONG).show();
                viewModel.onMockAreaNoticeShown();
            }
        });
        viewModel.getSearchedStationMissing().observe(getViewLifecycleOwner(), name -> {
            if (name != null) {
                Snackbar.make(view, getString(R.string.searched_station_missing, name), Snackbar.LENGTH_LONG).show();
                viewModel.onSearchedStationMissingShown();
            }
        });
        viewModel.getResearchAvailable().observe(getViewLifecycleOwner(), show ->
                researchButton.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE));
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> renderProgress());
        // 위치 권한·기기 위치 안내는 이득 1위 카드 자리에서 보여준다.
        viewModel.getPermissionState().observe(getViewLifecycleOwner(), state -> renderBestCard());
        viewModel.getLocationEnabled().observe(getViewLifecycleOwner(), enabled -> renderBestCard());

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
            renderBestCard();
        });
        viewModel.getStationsError().observe(getViewLifecycleOwner(), error -> renderBestCard());
        // 마커를 누르면 카드가 그 주유소로 바뀐다.
        viewModel.getSelectedStationId().observe(getViewLifecycleOwner(), id -> {
            stationAdapter.setSelectedId(id);
            if (stationMarkers != null) {
                stationMarkers.setSelected(id);
            }
            renderBestCard();
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
        searchInput = null;
        searchModeGroup = null;
        researchButton = null;
        mapView = null;
        kakaoMap = null;
        myLocationStyles = null;
        myLocationLabel = null;
        stationMarkers = null;
        mapRunning = false;
        stationSheet = null;
        sheetBehavior = null;
        stationList = null;
        stationLayoutManager = null;
        stationAdapter = null;
        cardStation = null;
        cardStatusAction = null;
        appliedPeekHeight = 0;
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

    /**
     * 이득 1위 카드 자리에 위치 권한 / 기기 위치 안내를 보여줄지.
     * 내 위치를 따라가는 중인데 실제 내 위치를 얻지 못해 기본 위치(서울시청)로 조회한 경우에만 보여준다.
     * 검색한 장소 기준이거나 마지막 위치라도 얻었으면 그 결과가 쓸모 있으므로 결과를 그대로 보여준다.
     * @return 안내 문구와 버튼 문구, 보여주지 않으면 null
     */
    @Nullable
    private int[] locationNotice() {
        SearchOrigin origin = viewModel.getOrigin().getValue();
        UserLocation location = viewModel.getLocation().getValue();
        if ((origin != null && origin.isFixed()) || (location != null && location.isUserLocation())) {
            return null;
        }
        LocationPermissionState state = viewModel.getPermissionState().getValue();
        if (state == LocationPermissionState.DENIED || state == LocationPermissionState.PERMANENTLY_DENIED) {
            return new int[]{R.string.permission_rationale, state == LocationPermissionState.PERMANENTLY_DENIED
                    ? R.string.permission_open_settings : R.string.permission_request};
        }
        if (state == LocationPermissionState.GRANTED
                && Boolean.FALSE.equals(viewModel.getLocationEnabled().getValue())) {
            return new int[]{R.string.location_disabled, R.string.location_open_settings};
        }
        return null;
    }

    /** 권한 요청, 앱 설정 열기(다시 묻지 않음), 위치 설정 열기(기기 위치 꺼짐) 중 지금 상태에 맞는 것 */
    private void requestLocationAccess() {
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

    /** 장소 기준이어도 내 위치 기준으로 돌아간다. */
    private void onMyLocationClick() {
        viewModel.returnToMyLocation();
        if (!viewModel.hasPermission()) {
            // 권한이 없으면 내 위치를 알 수 없으므로 권한도 받는다 (그동안은 기본 위치 기준).
            requestLocationAccess();
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
                    // 아래 시트(손잡이 + 이득 1위 카드)에 가리는 만큼 지도 중심을 위로 올린다.
                    map.setPadding(0, 0, 0, appliedPeekHeight > 0 ? appliedPeekHeight
                            : getResources().getDimensionPixelSize(R.dimen.station_sheet_peek_height));
                    map.setOnLabelClickListener((clickedMap, layer, label) -> {
                        String stationId = stationMarkers != null ? stationMarkers.stationIdOf(layer, label) : null;
                        if (stationId == null) {
                            return false;
                        }
                        mainHandler.post(() -> onMarkerClick(stationId));
                        return true;
                    });
                    stationMarkers = StationMarkers.create(requireContext(), map);
                    // 지도를 움직여도 조회하지 않는다. 이동이 끝나면 지도 중심만 알려서 다시 검색 버튼을 보여줄지 정한다.
                    map.setOnCameraMoveEndListener((movedMap, position, gestureType) -> {
                        LatLng center = position.getPosition();
                        mainHandler.post(() -> {
                            if (getView() != null) {
                                viewModel.onMapMoved(center.getLatitude(), center.getLongitude());
                            }
                        });
                    });

                    moveCameraToOrigin(viewModel.getOrigin().getValue(), false);
                    updateMyLocationLabel(viewModel.getLocation().getValue());
                    List<StationSaving> stations = viewModel.getStations().getValue();
                    if (stations != null) {
                        showStationMarkers(stations);
                    }
                });
            }

            // 지도가 처음 열릴 위치. 조회 위치를 아직 못 정했으면 기본 위치에서 시작한다.
            @NonNull
            @Override
            public LatLng getPosition() {
                SearchOrigin origin = viewModel.getOrigin().getValue();
                if (origin != null) {
                    return LatLng.from(origin.getLatitude(), origin.getLongitude());
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

    /** 지도 중심을 조회 위치로 옮긴다. */
    private void moveCameraToOrigin(SearchOrigin origin, boolean animate) {
        if (kakaoMap == null || origin == null) {
            return; // 지도가 준비되면 onMapReady에서 다시 호출된다.
        }
        LatLng position = LatLng.from(origin.getLatitude(), origin.getLongitude());
        CameraUpdate update = CameraUpdateFactory.newCenterPosition(position, DEFAULT_ZOOM_LEVEL);
        if (animate) {
            kakaoMap.moveCamera(update, CameraAnimation.from(CAMERA_ANIMATION_MS));
        } else {
            kakaoMap.moveCamera(update);
        }
    }

    /**
     * 카카오맵 SDK v2에는 내 위치 표시 기능이 없어서 파란 점 Label을 직접 올린다.
     * 조회 위치가 검색한 장소여도 파란 점은 내 위치에 둔다.
     */
    private void updateMyLocationLabel(UserLocation location) {
        if (kakaoMap == null || location == null) {
            return; // 지도가 준비되면 onMapReady에서 다시 호출된다.
        }
        LatLng position = LatLng.from(location.getLatitude(), location.getLongitude());
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
        Resources res = getResources();
        int size = res.getDimensionPixelSize(R.dimen.my_location_dot_size);
        float radius = size / 2f;
        float shadow = res.getDimension(R.dimen.my_location_shadow);
        float border = res.getDimension(R.dimen.my_location_border);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 밝은 지도 위에서도 테두리가 보이도록 옅은 그림자
        paint.setColor(ContextCompat.getColor(requireContext(), R.color.my_location_shadow));
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setColor(ContextCompat.getColor(requireContext(), R.color.my_location_border));
        canvas.drawCircle(radius, radius, radius - shadow, paint);
        paint.setColor(ContextCompat.getColor(requireContext(), R.color.my_location_dot));
        canvas.drawCircle(radius, radius, radius - shadow - border, paint);
        return bitmap;
    }

    // ---------- 주변 주유소 (마커, 아래 시트) ----------

    private void setUpStationSheet(View view) {
        stationSheet = view.findViewById(R.id.station_sheet);
        sheetBehavior = BottomSheetBehavior.from(stationSheet);
        sheetTitle = view.findViewById(R.id.station_sheet_title);
        stationList = view.findViewById(R.id.station_list);
        view.findViewById(R.id.btn_legend).setOnClickListener(v -> showLegend());

        stationAdapter = new StationAdapter(this::onStationItemClick);
        stationLayoutManager = new LinearLayoutManager(requireContext());
        stationList.setLayoutManager(stationLayoutManager);
        stationList.setAdapter(stationAdapter);

        // 시트는 화면 높이만큼 크고 아래로 내려가 있으므로, 화면 밖으로 나간 만큼 목록 아래 여백을 늘려
        // 어느 상태에서든 마지막 항목까지 스크롤하면 화면 안에 보이게 한다.
        View sheet = stationSheet;
        int baseBottomPadding = stationList.getPaddingBottom();
        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState != BottomSheetBehavior.STATE_DRAGGING
                        && newState != BottomSheetBehavior.STATE_SETTLING) {
                    fitListToVisibleSheet(sheet, baseBottomPadding);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
        // 처음 배치, 화면 회전, 카드 내용·글꼴 크기 변화로 크기가 바뀐 뒤에도 맞춘다. 배치 중에는 바꾸지 않도록 post한다.
        sheet.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                v.post(() -> {
                    fitListToVisibleSheet(sheet, baseBottomPadding);
                    fitPeekToBestCard();
                }));
    }

    /**
     * 시트를 내렸을 때 손잡이와 이득 1위 카드가 딱 보이도록 peek 높이를 카드 아래 끝에 맞추고,
     * 지도 아래 여백도 같은 값으로 바꿔서 지도 중심이 카드에 가리지 않게 한다.
     */
    private void fitPeekToBestCard() {
        if (sheetBehavior == null || bestCard == null || bestCard.getHeight() == 0) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) bestCard.getLayoutParams();
        int peek = bestCard.getBottom() + params.bottomMargin;
        if (peek == appliedPeekHeight) {
            return;
        }
        appliedPeekHeight = peek;
        sheetBehavior.setPeekHeight(peek, sheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED);
        if (kakaoMap != null) {
            kakaoMap.setPadding(0, 0, 0, peek);
        }
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

    /** 목록 머리줄의 (?) 버튼: 마커 색 안내 */
    private void showLegend() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.map_legend_title)
                .setView(R.layout.dialog_map_legend)
                .setPositiveButton(R.string.map_legend_confirm, null)
                .show();
    }

    private void showStations(List<StationSaving> stations) {
        List<StationSaving> list = stations != null ? stations : Collections.emptyList();
        stationAdapter.submit(list, cheapestPrice(list));
        String selectedId = viewModel.getSelectedStationId().getValue();
        stationAdapter.setSelectedId(selectedId);
        // 주유소 이름 검색에서 고른 주유소처럼 선택된 주유소가 있으면 그 줄을, 없으면 맨 위를 보여준다.
        int selectedPosition = selectedId != null ? stationAdapter.positionOf(selectedId) : RecyclerView.NO_POSITION;
        if (selectedPosition != RecyclerView.NO_POSITION) {
            stationLayoutManager.scrollToPositionWithOffset(selectedPosition, 0);
        } else {
            stationLayoutManager.scrollToPosition(0);
        }
        renderSheetTitle();
        showStationMarkers(list);
        renderBestCard();
    }

    /** 목록 머리줄: "주변 23곳 · 내 위치 기준". 유종·반경은 필터 칩에 있어서 쓰지 않는다. */
    private void renderSheetTitle() {
        List<StationSaving> stations = viewModel.getStations().getValue();
        String origin = originLabel(viewModel.getOrigin().getValue());
        if (stations != null && !stations.isEmpty()) {
            sheetTitle.setText(getString(R.string.station_sheet_title_count, stations.size(), origin));
        } else {
            sheetTitle.setText(getString(R.string.station_sheet_title, origin));
        }
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

    // ---------- 이득 1위 카드 ----------

    private void setUpBestCard(View view) {
        bestCard = view.findViewById(R.id.best_card);
        bestCardContent = view.findViewById(R.id.best_card_content);
        bestCardLabel = view.findViewById(R.id.best_card_label);
        bestCardShowBest = view.findViewById(R.id.best_card_show_best);
        bestCardHeadline = view.findViewById(R.id.best_card_headline);
        bestCardName = view.findViewById(R.id.best_card_name);
        bestCardInfo = view.findViewById(R.id.best_card_info);
        bestCardRoute = view.findViewById(R.id.best_card_route);
        basisText = view.findViewById(R.id.best_card_basis);
        bestCardStatus = view.findViewById(R.id.best_card_status);
        bestCardStatusProgress = view.findViewById(R.id.best_card_status_progress);
        bestCardStatusText = view.findViewById(R.id.best_card_status_text);
        bestCardStatusButton = view.findViewById(R.id.best_card_status_button);

        // 카드 본문을 누르면 지도 탭에서 계산한 결과와 함께 상세 화면을 연다.
        bestCardContent.setOnClickListener(v -> {
            if (cardStation != null) {
                openDetail(cardStation);
            }
        });
        bestCardRoute.setOnClickListener(v -> {
            if (cardStation != null) {
                GasStation station = cardStation.getStation();
                RouteAppLauncher.showChooser(requireActivity(), station.getName(),
                        station.getLatitude(), station.getLongitude());
            }
        });
        bestCardShowBest.setOnClickListener(v -> showBestStation());
        basisText.setOnClickListener(v ->
                startActivity(OnboardingActivity.createEditIntent(requireContext())));
        bestCardStatusButton.setOnClickListener(v -> {
            if (cardStatusAction != null) {
                cardStatusAction.run();
            }
        });
    }

    /**
     * 시트를 내린 상태의 카드. 안내가 필요하면 안내를 보여주고, 아니면 주유소 한 곳을 보여준다.
     * 안내 순서: 불러오는 중 → 오류 → 위치 권한·기기 위치 → 위치 확인 중 → 주유소 없음.
     * 주유소는 마커로 고른 주유소가 있으면 그곳, 없으면 실제 예상 이득 1위(StationSaving.isBest)다.
     * 선호 브랜드의 정렬용 +500원은 쓰지 않으므로 목록 1번 줄과 다를 수 있다.
     */
    private void renderBestCard() {
        if (bestCard == null) {
            return;
        }
        List<StationSaving> stations = viewModel.getStations().getValue();
        StationError error = viewModel.getStationsError().getValue();
        boolean loading = Boolean.TRUE.equals(viewModel.getStationsLoading().getValue());
        int[] locationNotice = locationNotice();

        if (loading) {
            showCardStatus(getString(R.string.station_loading), true, 0, null);
        } else if (error != null) {
            // 하루 한도에 도달했으면 다시 시도해도 오늘은 조회할 수 없다.
            showCardStatus(getString(errorMessage(error)), false,
                    error != StationError.DAILY_LIMIT ? R.string.station_retry : 0, viewModel::retryStations);
        } else if (locationNotice != null) {
            showCardStatus(getString(locationNotice[0]), false, locationNotice[1], this::requestLocationAccess);
        } else if (stations == null) {
            // 아직 위치를 받기 전
            showCardStatus(getString(R.string.station_locating), true, 0, null);
        } else if (stations.isEmpty()) {
            showCardStatus(emptyMessage(), false, 0, null);
        } else {
            StationSaving best = PlaceCompareBuilder.findBest(stations);
            StationSaving selected = viewModel.findStation(viewModel.getSelectedStationId().getValue());
            showCardStation(selected != null ? selected : best != null ? best : stations.get(0));
        }
    }

    /** 주유소가 없는 이유: 반경 때문인지, 숨긴 브랜드나 빠른 필터 때문인지 나눠서 알려준다. */
    private String emptyMessage() {
        StationListBuilder.EmptyReason reason = viewModel.getEmptyReason().getValue();
        if (reason == StationListBuilder.EmptyReason.RADIUS && viewModel.isOutsideMockArea()) {
            return getString(R.string.mock_area_notice);
        } else if (reason == StationListBuilder.EmptyReason.BRAND_FILTER) {
            return getString(R.string.station_empty_brand_filter);
        } else if (reason == StationListBuilder.EmptyReason.HIDDEN_BRANDS) {
            return getString(R.string.station_empty_hidden_brands);
        }
        return getString(R.string.station_empty, viewModel.getRadius().getValue().getLabel());
    }

    private void showCardStatus(String message, boolean showProgress, @StringRes int button,
                                @Nullable Runnable action) {
        cardStation = null;
        cardStatusAction = button != 0 ? action : null;
        bestCardStatusText.setText(message);
        bestCardStatusProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (button != 0) {
            bestCardStatusButton.setText(button);
        }
        bestCardStatusButton.setVisibility(button != 0 ? View.VISIBLE : View.GONE);
        bestCardStatus.setVisibility(View.VISIBLE);
        bestCardContent.setVisibility(View.GONE);
    }

    private void showCardStation(StationSaving saving) {
        cardStation = saving;
        cardStatusAction = null;
        GasStation station = saving.getStation();
        boolean best = saving.isBest();

        bestCardLabel.setText(best ? R.string.best_card_label : R.string.best_card_label_selected);
        bestCardShowBest.setVisibility(best ? View.GONE : View.VISIBLE);
        String headline = FormatUtil.formatSavingHeadline(saving);
        bestCardHeadline.setText(headline);
        bestCardHeadline.setTextColor(ContextCompat.getColor(requireContext(),
                SavingColors.heroTextColorOf(saving.getLevel())));
        bestCardName.setText(station.getName());
        String info = getString(R.string.best_card_info, station.getBrandName(),
                FormatUtil.formatDistance(station.getDistance()), FormatUtil.formatPrice(station.getPrice()));
        bestCardInfo.setText(info);
        bestCardContent.setContentDescription(getString(R.string.best_card_description,
                bestCardLabel.getText(), headline, station.getName() + ", " + info));

        bestCardStatus.setVisibility(View.GONE);
        bestCardContent.setVisibility(View.VISIBLE);
    }

    /** "이득 1위 보기": 이득 1위를 선택하고 지도를 그 주유소로 옮긴다. */
    private void showBestStation() {
        viewModel.selectBestStation();
        StationSaving best = PlaceCompareBuilder.findBest(viewModel.getStations().getValue());
        if (best == null) {
            return;
        }
        if (kakaoMap != null) {
            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(
                    best.getStation().getLatitude(), best.getStation().getLongitude())),
                    CameraAnimation.from(CAMERA_ANIMATION_MS));
        }
        scrollListTo(best.getStation().getId());
    }

    @StringRes
    private static int errorMessage(StationError error) {
        switch (error) {
            case NETWORK:
                return R.string.station_error_network;
            case DAILY_LIMIT:
                return R.string.station_error_daily_limit;
            default:
                return R.string.station_error_opinet;
        }
    }

    /**
     * 마커를 처음 누르면 그 주유소를 선택한다. 이득 1위 카드가 그 주유소로 바뀌고(상세는 열지 않음),
     * 목록에서도 그 줄을 강조하고 맨 위로 스크롤한다. 이미 선택된 마커를 한 번 더 누르면 상세 화면을 연다.
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
        detailLauncher.launch(StationDetailActivity.createIntent(requireContext(), saving,
                viewModel.getFuelType().getValue(), originLabel(viewModel.getOrigin().getValue())));
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

    // ---------- 검색창, 조회 위치 ----------

    private void setUpSearch(View view) {
        searchModeGroup = view.findViewById(R.id.search_mode_group);
        searchInput = view.findViewById(R.id.search_input);
        View searchButton = view.findViewById(R.id.search_button);

        SearchViewModel.Mode mode = searchViewModel.getMode().getValue();
        searchModeGroup.check(mode == SearchViewModel.Mode.STATION ? R.id.search_mode_station : R.id.search_mode_place);
        renderSearchHint(mode);
        searchModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            SearchViewModel.Mode newMode = checkedId == R.id.search_mode_station
                    ? SearchViewModel.Mode.STATION : SearchViewModel.Mode.PLACE;
            searchViewModel.setMode(newMode);
            renderSearchHint(newMode);
        });

        // 입력 중에는 검색하지 않는다. 검색 버튼이나 키보드의 검색 키를 눌렀을 때만 검색한다.
        searchButton.setOnClickListener(v -> submitSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });

        originChip = view.findViewById(R.id.chip_origin);
        originChip.setOnCloseIconClickListener(v -> viewModel.returnToMyLocation());
        originChip.setOnClickListener(v -> moveCameraToOrigin(viewModel.getOrigin().getValue(), true));
    }

    private void renderSearchHint(SearchViewModel.Mode mode) {
        searchInput.setHint(mode == SearchViewModel.Mode.STATION
                ? R.string.search_hint_station : R.string.search_hint_place);
    }

    private void submitSearch() {
        SearchViewModel.SubmitResult result = searchViewModel.submit(searchInput.getText().toString());
        if (result == SearchViewModel.SubmitResult.TOO_SHORT) {
            Toast.makeText(requireContext(), R.string.search_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (result == SearchViewModel.SubmitResult.BLANK) {
            Toast.makeText(requireContext(), R.string.search_blank, Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        if (getChildFragmentManager().findFragmentByTag(SearchResultSheet.TAG) == null) {
            new SearchResultSheet().show(getChildFragmentManager(), SearchResultSheet.TAG);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
    }

    /** 검색한 장소·주유소, 지도 중심 기준일 때만 "강남역 기준", "역삼1동 기준" 칩을 보여준다. */
    private void renderOriginChip(SearchOrigin origin) {
        if (origin == null || !origin.isFixed()) {
            originChip.setVisibility(View.GONE);
            return;
        }
        String name = originLabel(origin);
        originChip.setText(getString(R.string.origin_chip, name));
        originChip.setContentDescription(getString(R.string.origin_chip_description, name));
        originChip.setVisibility(View.VISIBLE);
    }

    /** 조회 위치 이름: 검색한 장소 이름, 지도 중심의 동 이름(모르면 "지도 중심"), 내 위치, 기본 위치(서울시청) */
    private String originLabel(SearchOrigin origin) {
        return OriginLabels.of(requireContext(), origin);
    }

    // ---------- 유종, 반경, 브랜드, 정렬 선택 ----------

    private void setUpFilters(View view) {
        fuelTypeChip = view.findViewById(R.id.chip_fuel_type);
        radiusChip = view.findViewById(R.id.chip_radius);
        brandChip = view.findViewById(R.id.chip_brand);
        sortButton = view.findViewById(R.id.btn_sort);

        // 칩의 ▼ 아이콘을 눌러도 칩을 누른 것과 같게 한다.
        fuelTypeChip.setOnClickListener(this::showFuelTypeMenu);
        fuelTypeChip.setOnCloseIconClickListener(v -> showFuelTypeMenu(fuelTypeChip));
        radiusChip.setOnClickListener(this::showRadiusMenu);
        radiusChip.setOnCloseIconClickListener(v -> showRadiusMenu(radiusChip));
        brandChip.setOnClickListener(v -> showBrandFilter());
        brandChip.setOnCloseIconClickListener(v -> showBrandFilter());
        sortButton.setOnClickListener(this::showSortMenu);
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

    /** 필터 칩("휘발유", "5km")과 정렬 버튼을 지금 선택에 맞춘다. */
    private void renderFilters() {
        FuelType fuelType = viewModel.getFuelType().getValue();
        SearchRadius radius = viewModel.getRadius().getValue();
        SortOrder sortOrder = viewModel.getSortOrder().getValue();
        if (fuelType == null || radius == null || sortOrder == null) {
            return;
        }
        fuelTypeChip.setText(fuelType.getLabel());
        fuelTypeChip.setContentDescription(getString(R.string.filter_fuel_type_description, fuelType.getLabel()));
        radiusChip.setText(radius.getLabel());
        radiusChip.setContentDescription(getString(R.string.filter_radius_description, radius.getLabel()));
        sortButton.setText(sortOrder.getLabel());
        sortButton.setContentDescription(getString(R.string.station_sort_description, sortOrder.getLabel()));
    }

    /** 브랜드 칩 글자: "브랜드", "할인 브랜드", "SK", "브랜드 2" 등. 접근성 설명에는 고른 내용("SK 외 1")을 쓴다. */
    private void renderBrandChip() {
        BrandFilter filter = viewModel.getBrandFilter().getValue();
        BrandSettings settings = viewModel.getBrandSettings().getValue();
        if (filter == null || settings == null) {
            return;
        }
        BrandFilter effective = filter.effective(settings);
        brandChip.setText(effective.chipLabel());
        brandChip.setContentDescription(getString(R.string.filter_brand_description, effective.label()));
    }

    /** 이득 1위 카드 맨 아래 계산 기준 문구. 기본값이면 "(기본값)"이 붙는다. */
    private void renderCalculationBasis(CarInfo carInfo) {
        String basis = FormatUtil.formatCalculationBasis(carInfo);
        basisText.setText(basis);
        basisText.setContentDescription(getString(R.string.station_basis_description, basis));
    }
}
