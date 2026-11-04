package com.example.findcheapgas.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.ui.brand.BrandSettingsActivity;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.SettingsViewModel;
import com.google.android.material.snackbar.Snackbar;

/**
 * 설정 탭. 지금은 내 차 정보와 내 브랜드가 있고, 나머지 항목은 "준비 중"으로 보여준다.
 * 내 차 정보를 누르면 수정 화면(OnboardingActivity 수정 모드)이, 내 브랜드를 누르면 내 브랜드 화면이 열린다.
 * 값은 각 Repository의 LiveData를 보고 그리므로 수정 화면에서 저장하고 돌아오면 바로 바뀐다.
 */
public class SettingsFragment extends Fragment {

    // 아직 만들지 않은 설정 항목 (README 화면 구성 순서)
    private static final int[] COMING_SOON_ITEMS = {
            R.string.settings_places,
            R.string.settings_default_filter,
            R.string.settings_dark_mode,
            R.string.settings_api_usage,
    };

    private SettingsViewModel viewModel;

    private View carInfoCard;
    private TextView fuelEfficiencyText;
    private TextView fuelAmountText;
    private TextView carInfoStatus;
    private View resetButton;
    private View brandCard;
    private TextView brandSummary;

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

        addComingSoonItems(view.findViewById(R.id.settings_coming_soon_list));

        viewModel.getCarInfo().observe(getViewLifecycleOwner(), this::showCarInfo);
        viewModel.getBrandSettings().observe(getViewLifecycleOwner(), this::showBrandSummary);
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

    private void addComingSoonItems(ViewGroup list) {
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        for (int title : COMING_SOON_ITEMS) {
            View item = inflater.inflate(R.layout.item_settings_coming_soon, list, false);
            ((TextView) item.findViewById(R.id.settings_item_title)).setText(title);
            list.addView(item);
        }
    }
}
