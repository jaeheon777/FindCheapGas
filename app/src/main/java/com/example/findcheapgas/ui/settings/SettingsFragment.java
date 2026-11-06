package com.example.findcheapgas.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.ApiUsage;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.ThemeMode;
import com.example.findcheapgas.ui.brand.BrandSettingsActivity;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.ui.place.PlaceSettingsActivity;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.example.findcheapgas.viewmodel.SettingsViewModel;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 설정 탭. 내 차 정보, 내 브랜드, 집·회사 위치, 기본 유종·반경, 화면 테마, 오늘 오피넷 사용량.
 * 내 차 정보를 누르면 수정 화면(OnboardingActivity 수정 모드)이, 내 브랜드를 누르면 내 브랜드 화면이,
 * 집·회사 위치를 누르면 집·회사 위치 화면(PlaceSettingsActivity)이 열린다.
 * 값은 각 Repository의 LiveData를 보고 그리므로 수정 화면에서 저장하고 돌아오면 바로 바뀐다.
 * 기본 유종·반경은 지도 탭이 기억하는 값 그대로이고, Activity 범위의 MapViewModel로 바꿔서 지도 탭에도 바로 반영된다.
 * 오늘 오피넷 사용량은 탭이 보일 때마다 다시 읽는다.
 */
public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;
    private MapViewModel mapViewModel;

    private View carInfoCard;
    private TextView fuelEfficiencyText;
    private TextView fuelAmountText;
    private TextView carInfoStatus;
    private View resetButton;
    private View brandCard;
    private TextView brandSummary;
    private View placesCard;
    private TextView placesHome;
    private TextView placesWork;
    private View fuelTypeRow;
    private TextView fuelTypeText;
    private View radiusRow;
    private TextView radiusText;
    private View themeRow;
    private TextView themeText;
    private View apiUsageCard;
    private TextView apiUsageCount;
    private LinearProgressIndicator apiUsageProgress;
    private TextView apiUsageStatus;
    private TextView apiUsageMock;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        carInfoCard = view.findViewById(R.id.settings_car_info_card);
        fuelEfficiencyText = view.findViewById(R.id.settings_fuel_efficiency);
        fuelAmountText = view.findViewById(R.id.settings_fuel_amount);
        carInfoStatus = view.findViewById(R.id.settings_car_info_status);
        resetButton = view.findViewById(R.id.settings_car_info_reset);
        brandCard = view.findViewById(R.id.settings_brand_card);
        brandSummary = view.findViewById(R.id.settings_brand_summary);

        carInfoCard.setOnClickListener(v ->
                startActivity(OnboardingActivity.createEditIntent(requireContext())));
        resetButton.setOnClickListener(v -> {
            viewModel.resetCarInfoToDefault();
            Snackbar.make(view, R.string.settings_car_info_reset_done, Snackbar.LENGTH_SHORT)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_nav))
                    .show();
        });

        brandCard.setOnClickListener(v ->
                startActivity(BrandSettingsActivity.createIntent(requireContext())));

        placesCard = view.findViewById(R.id.settings_places_card);
        placesHome = view.findViewById(R.id.settings_places_home);
        placesWork = view.findViewById(R.id.settings_places_work);
        placesCard.setOnClickListener(v ->
                startActivity(PlaceSettingsActivity.createIntent(requireContext())));

        // 지도 탭과 같은 MapViewModel (MainActivity 범위)
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        fuelTypeRow = view.findViewById(R.id.settings_fuel_type_row);
        fuelTypeText = view.findViewById(R.id.settings_fuel_type);
        radiusRow = view.findViewById(R.id.settings_radius_row);
        radiusText = view.findViewById(R.id.settings_radius);
        fuelTypeRow.setOnClickListener(v -> showChoiceDialog(R.string.settings_fuel_type,
                FuelType.values(), mapViewModel.getFuelType().getValue(), FuelType::getLabel,
                mapViewModel::setFuelType));
        radiusRow.setOnClickListener(v -> showChoiceDialog(R.string.settings_radius,
                SearchRadius.values(), mapViewModel.getRadius().getValue(), SearchRadius::getLabel,
                mapViewModel::setRadius));

        themeRow = view.findViewById(R.id.settings_theme_row);
        themeText = view.findViewById(R.id.settings_theme);
        themeRow.setOnClickListener(v -> showChoiceDialog(R.string.settings_theme,
                ThemeMode.values(), viewModel.getThemeMode().getValue(), ThemeMode::getLabel,
                viewModel::setThemeMode));

        apiUsageCard = view.findViewById(R.id.settings_api_usage_card);
        apiUsageCount = view.findViewById(R.id.settings_api_usage_count);
        apiUsageProgress = view.findViewById(R.id.settings_api_usage_progress);
        apiUsageStatus = view.findViewById(R.id.settings_api_usage_status);
        apiUsageMock = view.findViewById(R.id.settings_api_usage_mock);

        viewModel.getCarInfo().observe(getViewLifecycleOwner(), this::showCarInfo);
        viewModel.getBrandSettings().observe(getViewLifecycleOwner(), this::showBrandSummary);
        viewModel.getPlaces().observe(getViewLifecycleOwner(), this::showPlaces);
        mapViewModel.getFuelType().observe(getViewLifecycleOwner(), fuelType ->
                showRowValue(fuelTypeRow, fuelTypeText, R.string.settings_fuel_type, fuelType.getLabel()));
        mapViewModel.getRadius().observe(getViewLifecycleOwner(), radius ->
                showRowValue(radiusRow, radiusText, R.string.settings_radius, radius.getLabel()));
        viewModel.getThemeMode().observe(getViewLifecycleOwner(), mode ->
                showRowValue(themeRow, themeText, R.string.settings_theme, mode.getLabel()));
        viewModel.getApiUsage().observe(getViewLifecycleOwner(), this::showApiUsage);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 앱으로 돌아왔을 때. 탭이 숨겨져 있으면 보일 때(onHiddenChanged) 읽는다.
        if (!isHidden()) {
            viewModel.refreshApiUsage();
        }
    }

    /** 하단 탭을 바꿀 때 show/hide로 불린다. 다른 탭에서 늘어난 호출 횟수를 바로 반영한다. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && viewModel != null) {
            viewModel.refreshApiUsage();
        }
    }

    private void showRowValue(View row, TextView valueText, @StringRes int title, String value) {
        valueText.setText(value);
        row.setContentDescription(getString(R.string.settings_row_description, getString(title), value));
    }

    /** "오늘 12회 / 300회", 진행 막대, 250회부터 경고 색과 문구, 가짜 데이터 모드 안내 */
    private void showApiUsage(ApiUsage usage) {
        String count = FormatUtil.formatApiUsageCount(usage);
        String status = FormatUtil.formatApiUsageStatus(usage);
        apiUsageCount.setText(count);
        apiUsageProgress.setProgressCompat(usage.getProgressPercent(), false);
        boolean warning = usage.getLevel() != ApiUsage.Level.NORMAL;
        apiUsageProgress.setIndicatorColor(MaterialColors.getColor(apiUsageProgress, warning
                ? androidx.appcompat.R.attr.colorError : androidx.appcompat.R.attr.colorPrimary));
        apiUsageStatus.setText(status);
        apiUsageStatus.setVisibility(status != null ? View.VISIBLE : View.GONE);
        apiUsageMock.setVisibility(usage.isMockData() ? View.VISIBLE : View.GONE);

        StringBuilder notes = new StringBuilder();
        if (status != null) {
            notes.append(status).append(". ");
        }
        notes.append(getString(R.string.settings_api_usage_note));
        if (usage.isMockData()) {
            notes.append(' ').append(getString(R.string.settings_api_usage_mock));
        }
        apiUsageCard.setContentDescription(getString(R.string.settings_api_usage_description,
                count, usage.getLimit(), notes));
    }

    /** 하나를 고르는 창. 고르면 바로 적용하고 닫힌다. */
    private <T> void showChoiceDialog(@StringRes int title, T[] options, @Nullable T current,
                                      Function<T, String> label, Consumer<T> onSelected) {
        String[] labels = new String[options.length];
        int checked = -1;
        for (int i = 0; i < options.length; i++) {
            labels[i] = label.apply(options[i]);
            if (options[i] == current) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    onSelected.accept(options[which]);
                })
                .setNegativeButton(R.string.settings_choice_cancel, null)
                .show();
    }

    /** "집 · 선릉역", 등록하지 않았으면 "집 · 등록 안 함" */
    private void showPlaces(@Nullable SavedPlaces places) {
        String home = placeLine(R.string.place_kind_home, places != null ? places.get(SavedPlace.Kind.HOME) : null);
        String work = placeLine(R.string.place_kind_work, places != null ? places.get(SavedPlace.Kind.WORK) : null);
        placesHome.setText(home);
        placesWork.setText(work);
        placesCard.setContentDescription(getString(R.string.settings_places_description, home, work));
    }

    private String placeLine(@StringRes int kindLabel, @Nullable SavedPlace place) {
        return getString(R.string.settings_place_line, getString(kindLabel),
                place != null ? place.getName() : getString(R.string.place_not_registered));
    }

    /** "할인 2곳 · 선호 1곳 · 숨김 1곳", 아무것도 없으면 "설정한 브랜드 없음" */
    private void showBrandSummary(BrandSettings settings) {
        String summary = FormatUtil.formatBrandSummary(settings);
        brandSummary.setText(summary);
        brandCard.setContentDescription(getString(R.string.settings_brand_description, summary));
    }

    private void showCarInfo(CarInfo carInfo) {
        String efficiency = FormatUtil.formatFuelEfficiency(carInfo.getFuelEfficiencyTenths());
        String amount = FormatUtil.formatFuelAmount(carInfo.getFuelAmount());
        @StringRes int status = carInfo.isDefault()
                ? R.string.settings_car_info_default
                : R.string.settings_car_info_user_entered;
        fuelEfficiencyText.setText(efficiency);
        fuelAmountText.setText(amount);
        carInfoStatus.setText(status);
        carInfoCard.setContentDescription(getString(R.string.settings_car_info_description,
                efficiency, amount, getString(status)));
        // 이미 기본값이면 되돌릴 것이 없다.
        resetButton.setVisibility(carInfo.isDefault() ? View.GONE : View.VISIBLE);
    }
}
