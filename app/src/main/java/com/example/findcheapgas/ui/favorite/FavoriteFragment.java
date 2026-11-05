package com.example.findcheapgas.ui.favorite;

import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.findcheapgas.MainActivity;
import com.example.findcheapgas.R;
import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.ui.OriginLabels;
import com.example.findcheapgas.ui.detail.StationDetailActivity;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.ui.place.PlaceSettingsActivity;
import com.example.findcheapgas.util.FavoritePriceResolver;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.FavoriteViewModel;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.example.findcheapgas.viewmodel.PlaceCompareViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * 즐겨찾기 탭.
 * - 위쪽: 집·회사 기준 비교 (지금 조회 위치 / 집 근처 / 회사 근처의 이득 1위 주유소와 예상 총비용, PlaceCompareViewModel).
 *   카드를 누르면 지도 탭으로 가서 그 위치 기준으로 이득 1위 주유소를 선택한다. 등록하지 않았으면 등록 버튼을 보여준다.
 * - 저장한 주유소를 최근에 저장한 순서로 보여준다: 상호, 브랜드, 주소, 지금 고른 유종 가격, 조회 위치에서 거리, 예상 이득
 * - 조회 위치, 유종, 기준 주유소는 지도 탭(MapViewModel, Activity 범위)의 마지막 주변 조회 결과를 쓴다.
 * - 가격은 탭이 보일 때 가져온다 (주변 목록 → 상세 캐시 → 조회). 당겨서 새로고침하면 30분이 지난 가격을 다시 가져온다.
 * - 왼쪽으로 밀거나 메뉴로 삭제하고, 삭제 직후 "실행 취소"를 보여준다.
 * - 항목을 누르면 상세 화면이 열리고, 상세 화면의 "지도에서 보기"를 누르면 지도 탭으로 가서 그 주유소를 보여준다.
 */
public class FavoriteFragment extends Fragment {

    private FavoriteViewModel viewModel;
    private PlaceCompareViewModel compareViewModel;
    private MapViewModel mapViewModel;
    private FavoriteAdapter adapter;

    private TextView compareCondition;
    private PlaceCompareCardView compareCurrent;
    private PlaceCompareCardView compareHome;
    private PlaceCompareCardView compareWork;

    private TextView countText;
    private TextView conditionText;
    private TextView basisText;
    private TextView noticeText;
    private SwipeRefreshLayout refreshLayout;
    private RecyclerView list;
    private View emptyView;

    // 상세 화면에서 "지도에서 보기"를 누르고 돌아오면 지도 탭으로 간다.
    private final ActivityResultLauncher<android.content.Intent> detailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onDetailResult);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FavoriteViewModel.class);
        compareViewModel = new ViewModelProvider(this).get(PlaceCompareViewModel.class);
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        compareCondition = view.findViewById(R.id.compare_condition);
        compareCurrent = new PlaceCompareCardView(view.findViewById(R.id.compare_current));
        compareHome = new PlaceCompareCardView(view.findViewById(R.id.compare_home));
        compareWork = new PlaceCompareCardView(view.findViewById(R.id.compare_work));

        countText = view.findViewById(R.id.favorite_count);
        conditionText = view.findViewById(R.id.favorite_condition);
        basisText = view.findViewById(R.id.favorite_basis);
        noticeText = view.findViewById(R.id.favorite_notice);
        refreshLayout = view.findViewById(R.id.favorite_refresh);
        list = view.findViewById(R.id.favorite_list);
        emptyView = view.findViewById(R.id.favorite_empty);

        adapter = new FavoriteAdapter(new FavoriteAdapter.Listener() {
            @Override
            public void onItemClick(FavoriteItem item) {
                openDetail(item);
            }

            @Override
            public void onMenuClick(View anchor, FavoriteItem item) {
                showItemMenu(anchor, item);
            }

            @Override
            public void onDeleteClick(FavoriteItem item) {
                viewModel.remove(item.getFavorite().getId());
            }
        });
        list.setAdapter(adapter);
        attachSwipeToDelete();

        // 당겨서 새로고침: 즐겨찾기 가격(30분이 지난 것)과 집·회사 비교(20분이 지난 것)를 함께 다시 가져온다.
        refreshLayout.setOnRefreshListener(() -> {
            viewModel.refresh();
            compareViewModel.refresh();
        });
        basisText.setOnClickListener(v -> startActivity(OnboardingActivity.createEditIntent(requireContext())));

        mapViewModel.getAroundStations().observe(getViewLifecycleOwner(), this::onAroundChanged);
        mapViewModel.getFuelType().observe(getViewLifecycleOwner(),
                fuelType -> onAroundChanged(mapViewModel.getAroundStations().getValue()));
        // 집·회사 비교는 지도 탭의 반경, 빠른 필터, 조회 상태도 따른다.
        mapViewModel.getRadius().observe(getViewLifecycleOwner(), radius -> updateCompareMapState());
        mapViewModel.getBrandFilter().observe(getViewLifecycleOwner(), filter -> updateCompareMapState());
        mapViewModel.getStationsLoading().observe(getViewLifecycleOwner(), loading -> updateCompareMapState());
        mapViewModel.getStationsError().observe(getViewLifecycleOwner(), error -> updateCompareMapState());
        mapViewModel.getOrigin().observe(getViewLifecycleOwner(), origin -> renderCompare());
        viewModel.getItems().observe(getViewLifecycleOwner(), this::showItems);
        viewModel.getCarInfo().observe(getViewLifecycleOwner(), this::renderBasis);
        viewModel.getRefreshing().observe(getViewLifecycleOwner(), refreshing -> renderRefreshing());
        compareViewModel.getRefreshing().observe(getViewLifecycleOwner(), refreshing -> renderRefreshing());
        compareViewModel.getCards().observe(getViewLifecycleOwner(), cards -> renderCompare());
        compareViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> renderCompare());
        viewModel.getLastRemoved().observe(getViewLifecycleOwner(), this::showUndo);
        viewModel.getRestoreFailed().observe(getViewLifecycleOwner(), failed -> {
            if (Boolean.TRUE.equals(failed)) {
                Snackbar.make(view, getString(R.string.favorite_restore_failed, FavoriteStation.MAX_COUNT),
                        Snackbar.LENGTH_LONG).show();
                viewModel.onRestoreFailedShown();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.setVisible(!isHidden());
        compareViewModel.setVisible(!isHidden());
        // "약 1시간 전 가격"을 지금 시각 기준으로 다시 쓴다.
        renderCompare();
    }

    @Override
    public void onPause() {
        super.onPause();
        viewModel.setVisible(false);
        compareViewModel.setVisible(false);
    }

    /** 하단 탭을 바꿀 때 show/hide로 불린다. 탭이 보이면 가격을 가져온다. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (viewModel != null && isResumed()) {
            viewModel.setVisible(!hidden);
            compareViewModel.setVisible(!hidden);
            if (!hidden) {
                renderCompare();
            }
        }
    }

    private void onAroundChanged(@Nullable AroundStations around) {
        FuelType mapFuelType = mapViewModel.getFuelType().getValue();
        viewModel.setAround(around, mapFuelType != null ? mapFuelType : FuelType.GASOLINE);
        renderCondition();
        updateCompareMapState();
    }

    private void updateCompareMapState() {
        FuelType fuelType = mapViewModel.getFuelType().getValue();
        SearchRadius radius = mapViewModel.getRadius().getValue();
        BrandFilter filter = mapViewModel.getBrandFilter().getValue();
        compareViewModel.setMapState(mapViewModel.getAroundStations().getValue(),
                mapViewModel.getStationsError().getValue(),
                Boolean.TRUE.equals(mapViewModel.getStationsLoading().getValue()),
                fuelType != null ? fuelType : FuelType.GASOLINE,
                radius != null ? radius : SearchRadius.KM_3,
                filter != null ? filter : BrandFilter.ALL);
        renderCompare();
    }

    private void renderRefreshing() {
        refreshLayout.setRefreshing(Boolean.TRUE.equals(viewModel.getRefreshing().getValue())
                || Boolean.TRUE.equals(compareViewModel.getRefreshing().getValue()));
    }

    /** 집·회사 기준 비교: 조건 문구와 카드 세 장 */
    private void renderCompare() {
        if (getView() == null) {
            return;
        }
        SearchRadius radius = mapViewModel.getRadius().getValue();
        String radiusLabel = (radius != null ? radius : SearchRadius.KM_3).getLabel();
        compareCondition.setText(getString(R.string.compare_condition, viewModel.getFuelType().getLabel(),
                radiusLabel));
        List<PlaceCompareCard> cards = compareViewModel.getCards().getValue();
        if (cards == null) {
            return;
        }
        SavedPlaces places = compareViewModel.getPlaces().getValue();
        long now = System.currentTimeMillis();
        for (PlaceCompareCard card : cards) {
            switch (card.getSlot()) {
                case CURRENT:
                    compareCurrent.bind(card, getString(R.string.compare_slot_current, originLabel()), "",
                            radiusLabel, now, compareListener);
                    break;
                case HOME:
                    compareHome.bind(card, placeTitle(places, SavedPlace.Kind.HOME),
                            getString(R.string.place_kind_home), radiusLabel, now, compareListener);
                    break;
                default:
                    compareWork.bind(card, placeTitle(places, SavedPlace.Kind.WORK),
                            getString(R.string.place_kind_work), radiusLabel, now, compareListener);
                    break;
            }
        }
    }

    /** "집 근처 · 선릉역", 등록하지 않았으면 "집 근처" */
    private String placeTitle(@Nullable SavedPlaces places, SavedPlace.Kind kind) {
        SavedPlace place = places != null ? places.get(kind) : null;
        boolean home = kind == SavedPlace.Kind.HOME;
        if (place == null) {
            return getString(home ? R.string.compare_slot_home_empty : R.string.compare_slot_work_empty);
        }
        return getString(home ? R.string.compare_slot_home : R.string.compare_slot_work, place.getName());
    }

    private final PlaceCompareCardView.Listener compareListener = new PlaceCompareCardView.Listener() {
        @Override
        public void onCardClick(PlaceCompareCard card) {
            openCompareOnMap(card);
        }

        @Override
        public void onRegisterClick(PlaceCompareCard card) {
            startActivity(PlaceSettingsActivity.createRegisterIntent(requireContext(),
                    card.getSlot() == PlaceCompareCard.Slot.HOME ? SavedPlace.Kind.HOME : SavedPlace.Kind.WORK));
        }
    };

    /**
     * 비교 카드를 누르면 지도 탭으로 간다.
     * 지금 조회 위치는 다시 조회하지 않고 이득 1위를 선택하고, 집·회사는 그 위치를 조회 위치("집 기준")로 조회한 뒤
     * 이득 1위를 선택한다 (같은 위치·유종을 20분 안에 조회했으면 캐시).
     */
    private void openCompareOnMap(PlaceCompareCard card) {
        if (card.getSlot() == PlaceCompareCard.Slot.CURRENT) {
            mapViewModel.selectBestStation();
        } else {
            boolean home = card.getSlot() == PlaceCompareCard.Slot.HOME;
            SavedPlaces places = compareViewModel.getPlaces().getValue();
            SavedPlace place = places != null ? places.get(home ? SavedPlace.Kind.HOME : SavedPlace.Kind.WORK) : null;
            if (place == null) {
                return;
            }
            mapViewModel.showSavedPlaceOnMap(getString(home ? R.string.place_kind_home : R.string.place_kind_work),
                    place.getLatitude(), place.getLongitude());
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showMapTab();
        }
    }

    private void showItems(@Nullable List<FavoriteItem> items) {
        if (items == null) {
            return;
        }
        adapter.submit(items, originLabel());
        countText.setText(getString(R.string.favorite_count, items.size(), FavoriteStation.MAX_COUNT));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        renderNotice(items);
        renderCondition();
        renderCompare();
    }

    /** 예: "휘발유 가격 · 강남역 기준 거리와 예상 이득" */
    private void renderCondition() {
        conditionText.setText(getString(R.string.favorite_condition,
                viewModel.getFuelType().getLabel(), originLabel()));
    }

    private void renderBasis(CarInfo carInfo) {
        String basis = FormatUtil.formatCalculationBasis(carInfo);
        basisText.setText(basis);
        basisText.setContentDescription(getString(R.string.station_basis_description, basis));
    }

    /** 오늘 한도에 도달했으면 한도 안내, 아니면 30분 넘은 가격이 있을 때 새로고침 안내 */
    private void renderNotice(List<FavoriteItem> items) {
        boolean limit = false;
        boolean stale = false;
        long now = System.currentTimeMillis();
        for (FavoriteItem item : items) {
            FavoritePrice price = item.getPrice();
            if (price == null) {
                continue;
            }
            if (price.getStatus() == FavoritePrice.Status.DAILY_LIMIT) {
                limit = true;
            } else if (price.getSource() == FavoritePrice.Source.CACHE
                    && now - price.getFetchedAt() >= FavoritePriceResolver.FRESH_CACHE_MS) {
                stale = true;
            }
        }
        if (limit) {
            noticeText.setText(R.string.favorite_limit_notice);
        } else if (stale) {
            noticeText.setText(R.string.favorite_stale_notice);
        }
        noticeText.setVisibility(limit || stale ? View.VISIBLE : View.GONE);
    }

    private String originLabel() {
        AroundStations around = mapViewModel.getAroundStations().getValue();
        SearchOrigin origin = around != null ? around.getOrigin() : mapViewModel.getOrigin().getValue();
        return OriginLabels.of(requireContext(), origin);
    }

    private void openDetail(FavoriteItem item) {
        detailLauncher.launch(StationDetailActivity.createFavoriteIntent(requireContext(), item,
                viewModel.getFuelType(), originLabel()));
    }

    /** "지도에서 보기": 그 주유소 위치를 조회 위치로 주변을 조회하고 지도 탭으로 간다. */
    private void onDetailResult(ActivityResult result) {
        StationDetailActivity.ShowOnMapRequest request = StationDetailActivity.showOnMapFrom(result.getData());
        if (request == null) {
            return;
        }
        mapViewModel.showStationOnMap(request.stationId, request.name, request.latitude, request.longitude);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showMapTab();
        }
    }

    private void showItemMenu(View anchor, FavoriteItem item) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(R.string.favorite_menu_delete);
        menu.setOnMenuItemClickListener(menuItem -> {
            viewModel.remove(item.getFavorite().getId());
            return true;
        });
        menu.show();
    }

    private void showUndo(@Nullable FavoriteStation removed) {
        View view = getView();
        if (removed == null || view == null) {
            return;
        }
        Snackbar.make(view, getString(R.string.favorite_removed, removed.getName()), Snackbar.LENGTH_LONG)
                .setAction(R.string.favorite_undo, v -> viewModel.restore(removed))
                .show();
        viewModel.onRemovedShown();
    }

    /** 왼쪽으로 밀면 삭제. 미는 동안 뒤에 빨간 배경과 삭제 아이콘을 그린다. */
    private void attachSwipeToDelete() {
        ColorDrawable background = new ColorDrawable(
                ContextCompat.getColor(requireContext(), R.color.swipe_delete_background));
        Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete);
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                FavoriteItem item = adapter.getItem(viewHolder.getAdapterPosition());
                if (item != null) {
                    viewModel.remove(item.getFavorite().getId());
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                View item = viewHolder.itemView;
                if (dX < 0) {
                    background.setBounds(item.getRight() + (int) dX, item.getTop(), item.getRight(), item.getBottom());
                    background.draw(canvas);
                    if (icon != null) {
                        int margin = (item.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int right = item.getRight() - margin;
                        icon.setBounds(right - icon.getIntrinsicWidth(), item.getTop() + margin, right,
                                item.getTop() + margin + icon.getIntrinsicHeight());
                        icon.draw(canvas);
                    }
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(list);
    }
}
