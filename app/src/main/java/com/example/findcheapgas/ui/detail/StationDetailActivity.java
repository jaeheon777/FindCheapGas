package com.example.findcheapgas.ui.detail;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.DetailViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * 주유소 상세 화면. 지도 탭에서 목록 항목을 누르거나, 선택된 마커를 한 번 더 누르면 열린다.
 * 목록에서 이미 아는 정보(상호, 브랜드, 선택한 유종 가격, 거리)는 Intent로 받아 바로 보여주고,
 * 상세 조회(DetailViewModel)가 끝나면 주소, 전화, 유종별 가격, 부가시설을 채운다.
 * 상세 조회가 실패해도 기본 정보는 그대로 둔다.
 * 예상 이득 계산 내역은 지도 탭이 계산한 결과(StationSaving)를 Intent로 받아 그대로 보여준다.
 * 여기서는 다시 조회하거나 계산하지 않고, 계산 기준(내 차 정보)도 바꿀 수 없다.
 * 닫힐 때 주유소 ID를 결과로 돌려줘서 지도 탭이 그 주유소를 선택한 채로 보여주게 한다.
 */
public class StationDetailActivity extends AppCompatActivity {

    private static final String TAG = "StationDetailActivity";

    public static final String EXTRA_STATION_ID = "station_id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_BRAND_CODE = "brand_code";
    private static final String EXTRA_PRICE = "price";
    private static final String EXTRA_DISTANCE = "distance";
    private static final String EXTRA_FUEL_TYPE = "fuel_type";
    private static final String EXTRA_SAVING = "saving";

    // 1회 할인 한도를 비워 둔 채 할인액이 이 금액 이상이면 "한도가 있다면..." 주의 문구를 보여준다.
    private static final int NO_CAP_WARNING_DISCOUNT = 5_000;

    private DetailViewModel viewModel;
    private FuelType selectedFuelType;

    private View status;
    private View statusProgress;
    private TextView statusText;
    private View retryButton;
    private View content;
    private TextView addressText;
    private View phoneSection;
    private MaterialButton phoneButton;
    private LinearLayout priceRows;
    private View pricesEmpty;
    private ChipGroup facilityGroup;
    private View facilitiesEmpty;
    private View selfNote;

    /** 목록의 주유소 정보, 예상 이득 계산 결과, 지금 고른 유종으로 상세 화면을 여는 Intent */
    public static Intent createIntent(Context context, StationSaving saving, FuelType fuelType) {
        GasStation station = saving.getStation();
        return new Intent(context, StationDetailActivity.class)
                .putExtra(EXTRA_SAVING, saving)
                .putExtra(EXTRA_STATION_ID, station.getId())
                .putExtra(EXTRA_NAME, station.getName())
                .putExtra(EXTRA_BRAND_CODE, station.getBrandCode())
                .putExtra(EXTRA_PRICE, station.getPrice())
                .putExtra(EXTRA_DISTANCE, station.getDistance())
                .putExtra(EXTRA_FUEL_TYPE, fuelType.getCode());
    }

    /** 상세 화면이 돌려준 결과에서 주유소 ID를 꺼낸다. 없으면 null */
    @Nullable
    public static String stationIdFrom(@Nullable Intent result) {
        return result != null ? result.getStringExtra(EXTRA_STATION_ID) : null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String stationId = intent.getStringExtra(EXTRA_STATION_ID);
        if (stationId == null) {
            Log.e(TAG, "주유소 ID 없이 열렸습니다.");
            finish();
            return;
        }
        setContentView(R.layout.activity_station_detail);
        // 뒤로 가기, 위쪽 화살표 어느 쪽으로 닫혀도 지도 탭이 이 주유소를 선택하도록 미리 결과를 넣어 둔다.
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_STATION_ID, stationId));

        selectedFuelType = FuelType.fromCode(intent.getStringExtra(EXTRA_FUEL_TYPE), FuelType.GASOLINE);
        bindViews();
        showSummary(intent);
        showSaving(savingFrom(intent));

        viewModel = new ViewModelProvider(this).get(DetailViewModel.class);
        viewModel.getDetail().observe(this, detail -> {
            renderStatus();
            if (detail != null) {
                showDetail(detail);
            }
        });
        viewModel.getLoading().observe(this, loading -> renderStatus());
        viewModel.getError().observe(this, error -> renderStatus());
        // 화면을 회전해도 ViewModel이 남아 있으므로 다시 조회하지 않는다.
        viewModel.start(stationId);
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.detail_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        status = findViewById(R.id.detail_status);
        statusProgress = findViewById(R.id.detail_status_progress);
        statusText = findViewById(R.id.detail_status_text);
        retryButton = findViewById(R.id.detail_retry_button);
        content = findViewById(R.id.detail_content);
        addressText = findViewById(R.id.detail_address);
        phoneSection = findViewById(R.id.detail_phone_section);
        phoneButton = findViewById(R.id.detail_phone);
        priceRows = findViewById(R.id.detail_price_rows);
        pricesEmpty = findViewById(R.id.detail_prices_empty);
        facilityGroup = findViewById(R.id.detail_facilities);
        facilitiesEmpty = findViewById(R.id.detail_facilities_empty);
        selfNote = findViewById(R.id.detail_self_note);

        retryButton.setOnClickListener(v -> viewModel.retry());
    }

    /** 목록에서 넘겨받은 기본 정보 */
    private void showSummary(Intent intent) {
        String name = intent.getStringExtra(EXTRA_NAME);
        ((TextView) findViewById(R.id.detail_name)).setText(name);
        ((TextView) findViewById(R.id.detail_info)).setText(getString(R.string.detail_info,
                Brand.nameOf(intent.getStringExtra(EXTRA_BRAND_CODE)),
                FormatUtil.formatDistance(intent.getDoubleExtra(EXTRA_DISTANCE, 0))));
        ((TextView) findViewById(R.id.detail_selected_fuel)).setText(selectedFuelType.getLabel());
        ((TextView) findViewById(R.id.detail_selected_price)).setText(
                FormatUtil.formatPrice(intent.getIntExtra(EXTRA_PRICE, 0)));
    }

    @Nullable
    @SuppressWarnings("deprecation") // getSerializableExtra(String, Class)는 API 33부터라 minSdk 24에서는 이 방법을 쓴다.
    private static StationSaving savingFrom(Intent intent) {
        Object extra = intent.getSerializableExtra(EXTRA_SAVING);
        return extra instanceof StationSaving ? (StationSaving) extra : null;
    }

    /**
     * 예상 이득 계산 내역. 계산 결과가 없으면 구역을 숨긴다.
     * 금액은 계산할 때 실수로 들고 있고, 여기서 보여줄 때만 원 단위로 반올림한다.
     */
    private void showSaving(@Nullable StationSaving saving) {
        View section = findViewById(R.id.detail_saving_section);
        if (saving == null) {
            section.setVisibility(View.GONE);
            return;
        }
        GasStation station = saving.getStation();
        CarInfo carInfo = saving.getCarInfo();
        String price = FormatUtil.formatPrice(station.getPrice());

        ((TextView) findViewById(R.id.detail_saving_basis)).setText(FormatUtil.formatCalculationBasis(carInfo));
        findViewById(R.id.detail_saving_is_baseline).setVisibility(saving.isBaseline() ? View.VISIBLE : View.GONE);

        LinearLayout rows = findViewById(R.id.detail_saving_rows);
        rows.removeAllViews();

        if (!saving.isBaseline()) {
            GasStation baseline = saving.getBaseline();
            addSavingRow(rows, R.string.detail_saving_baseline, baseline.getName(),
                    getString(R.string.detail_saving_baseline_note,
                            FormatUtil.formatDistance(baseline.getDistance()),
                            FormatUtil.formatPrice(baseline.getPrice()),
                            FormatUtil.formatWon(saving.getBaselineTotalCost())), null);
        }

        String amount = FormatUtil.formatFuelAmount(carInfo.getFuelAmount());
        String fuelNote = saving.getDiscountAmount() > 0
                ? getString(R.string.detail_saving_fuel_cost_note_discount, price, amount,
                        FormatUtil.formatWon(saving.getDiscountAmount()))
                : getString(R.string.detail_saving_fuel_cost_note, price, amount);
        addSavingRow(rows, R.string.detail_saving_fuel_cost, FormatUtil.formatWon(saving.getFuelCost()),
                fuelNote, null);

        showDiscountRow(rows, saving);

        String roadDistance = FormatUtil.formatDistance(saving.getRoadDistance());
        addSavingRow(rows, R.string.detail_saving_road_distance,
                getString(R.string.detail_saving_approx, roadDistance),
                getString(R.string.detail_saving_road_distance_note,
                        FormatUtil.formatDistance(saving.getStraightDistance())), null);
        addSavingRow(rows, R.string.detail_saving_travel_cost,
                getString(R.string.detail_saving_approx, FormatUtil.formatWon(saving.getTravelCost())),
                getString(R.string.detail_saving_travel_cost_note, roadDistance,
                        FormatUtil.formatFuelEfficiency(carInfo.getFuelEfficiencyTenths()), price), null);
        TextView total = addSavingRow(rows, R.string.detail_saving_total,
                getString(R.string.detail_saving_approx, FormatUtil.formatWon(saving.getTotalCost())),
                getString(R.string.detail_saving_total_note), null);
        total.setTypeface(null, Typeface.BOLD);

        if (saving.isBaseline()) {
            addSavingRow(rows, R.string.detail_saving_difference,
                    getString(R.string.detail_saving_difference_baseline), null, null);
        } else {
            TextView difference = addSavingRow(rows, R.string.detail_saving_difference,
                    FormatUtil.formatSaving(saving),
                    getString(R.string.detail_saving_difference_note,
                            FormatUtil.formatWon(saving.getBaselineTotalCost()),
                            FormatUtil.formatWon(saving.getTotalCost())), null);
            difference.setTypeface(null, Typeface.BOLD);
            if (saving.getLevel() == SavingLevel.GAIN) {
                difference.setTextColor(ContextCompat.getColor(this, R.color.saving_gain_text));
            } else if (saving.getLevel() == SavingLevel.LOSS) {
                difference.setTextColor(ContextCompat.getColor(this, R.color.saving_loss_text));
            }
        }
        section.setVisibility(View.VISIBLE);
    }

    /**
     * 할인액 줄. 내 브랜드 설정의 할인을 보여주고, 할인이 없으면 "할인 없음".
     * 예: "리터당 100원 · 할인 3,000원 (1회 한도 3,000원 적용)", "리터당 100원 · 할인 4,000원 (한도 미적용)"
     * 1회 할인 한도를 비워 뒀으면 "한도 미적용"을 쓰고, 그 상태에서 할인액이 5,000원 이상이면 주의 문구를 붙인다.
     */
    private void showDiscountRow(LinearLayout rows, StationSaving saving) {
        DiscountRule rule = saving.getDiscountRule();
        if (!rule.hasDiscount()) {
            addSavingRow(rows, R.string.detail_saving_discount,
                    getString(R.string.detail_saving_discount_none), null, null);
            return;
        }
        String perLiter = FormatUtil.formatPrice(rule.getPerLiter());
        String discount = FormatUtil.formatWon(saving.getDiscountAmount());
        String note;
        String warning = null;
        Integer cap = rule.getCap();
        if (cap == null) {
            note = getString(R.string.detail_saving_discount_no_cap, perLiter, discount);
            if (saving.getDiscountAmount() >= NO_CAP_WARNING_DISCOUNT) {
                warning = getString(R.string.detail_saving_discount_no_cap_warning);
            }
        } else if (saving.isDiscountCapApplied()) {
            note = getString(R.string.detail_saving_discount_cap_applied, perLiter, discount,
                    FormatUtil.formatPrice(cap));
        } else {
            note = getString(R.string.detail_saving_discount_cap_not_reached, perLiter, discount,
                    FormatUtil.formatPrice(cap));
        }
        addSavingRow(rows, R.string.detail_saving_discount,
                getString(R.string.detail_saving_discount_value, FormatUtil.formatWon(saving.getDiscountAmount())),
                note, warning);
    }

    /** 계산 내역 한 줄을 넣고 금액 TextView를 돌려준다. note, warning이 없으면 그 줄을 숨긴다. */
    private TextView addSavingRow(LinearLayout rows, @StringRes int label, String value,
                                  @Nullable String note, @Nullable String warning) {
        View row = getLayoutInflater().inflate(R.layout.item_saving_row, rows, false);
        ((TextView) row.findViewById(R.id.saving_row_label)).setText(label);
        TextView valueText = row.findViewById(R.id.saving_row_value);
        valueText.setText(value);
        setOptionalText(row.findViewById(R.id.saving_row_note), note);
        setOptionalText(row.findViewById(R.id.saving_row_warning), warning);
        rows.addView(row);
        return valueText;
    }

    private static void setOptionalText(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    /** 불러오는 중 → 오류 순서로 확인한다. 상세 정보가 있으면 안내를 숨긴다. */
    private void renderStatus() {
        boolean loading = Boolean.TRUE.equals(viewModel.getLoading().getValue());
        StationError error = viewModel.getError().getValue();
        boolean hasDetail = viewModel.getDetail().getValue() != null;

        @StringRes int message = 0;
        if (loading) {
            message = R.string.detail_loading;
        } else if (error != null && !hasDetail) {
            message = error == StationError.NETWORK ? R.string.detail_error_network : R.string.detail_error_opinet;
        }
        if (message == 0) {
            status.setVisibility(View.GONE);
            return;
        }
        statusText.setText(message);
        statusProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(loading ? View.GONE : View.VISIBLE);
        status.setVisibility(View.VISIBLE);
    }

    private void showDetail(StationDetail detail) {
        String address = detail.getAddress();
        addressText.setText(address != null ? address : getString(R.string.detail_address_empty));

        String phone = detail.getPhone();
        if (phone != null) {
            phoneButton.setText(phone);
            phoneButton.setContentDescription(getString(R.string.detail_phone_description, phone));
            phoneButton.setOnClickListener(v -> openDialer(phone));
            phoneSection.setVisibility(View.VISIBLE);
        } else {
            phoneSection.setVisibility(View.GONE);
        }

        showPrices(detail.getPrices());
        showFacilities(detail);
        content.setVisibility(View.VISIBLE);
    }

    /** 파는 유종만 한 줄씩. 지도 탭에서 고른 유종은 강조한다. */
    private void showPrices(List<FuelPrice> prices) {
        priceRows.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (FuelPrice price : prices) {
            View row = inflater.inflate(R.layout.item_fuel_price, priceRows, false);
            boolean selected = price.getFuelType() == selectedFuelType;
            row.setActivated(selected);
            ((TextView) row.findViewById(R.id.fuel_label)).setText(price.getFuelType().getLabel());
            row.findViewById(R.id.fuel_selected).setVisibility(selected ? View.VISIBLE : View.GONE);
            TextView priceText = row.findViewById(R.id.fuel_price);
            priceText.setText(FormatUtil.formatPrice(price.getPrice()));
            priceText.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);

            TextView tradeTime = row.findViewById(R.id.fuel_trade_time);
            String time = FormatUtil.formatTradeTime(price.getTradeDate(), price.getTradeTime());
            if (time != null) {
                tradeTime.setText(getString(R.string.detail_trade_time, time));
                tradeTime.setVisibility(View.VISIBLE);
            } else {
                tradeTime.setVisibility(View.GONE);
            }
            priceRows.addView(row);
        }
        pricesEmpty.setVisibility(prices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 있는 부가시설만 칩으로. 셀프 여부를 상호로 추정했으면 "셀프 (추정)"과 안내 문구를 함께 보여준다. */
    private void showFacilities(StationDetail detail) {
        facilityGroup.removeAllViews();
        boolean selfEstimated = detail.isSelfService() && detail.isSelfServiceEstimated();
        if (detail.isSelfService()) {
            addFacilityChip(selfEstimated ? R.string.facility_self_estimated : R.string.facility_self);
        }
        if (detail.hasCarWash()) {
            addFacilityChip(R.string.facility_car_wash);
        }
        if (detail.hasConvenienceStore()) {
            addFacilityChip(R.string.facility_convenience_store);
        }
        if (detail.hasMaintenance()) {
            addFacilityChip(R.string.facility_maintenance);
        }
        if (detail.isQualityCertified()) {
            addFacilityChip(R.string.facility_quality_certified);
        }
        boolean empty = facilityGroup.getChildCount() == 0;
        facilityGroup.setVisibility(empty ? View.GONE : View.VISIBLE);
        facilitiesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        selfNote.setVisibility(selfEstimated ? View.VISIBLE : View.GONE);
    }

    /** 누를 수 없는 표시용 칩 */
    private void addFacilityChip(@StringRes int label) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setFocusable(false);
        chip.setEnsureMinTouchTargetSize(false);
        facilityGroup.addView(chip);
    }

    /** 전화 앱의 다이얼 화면만 연다 (전화 권한이 필요 없다). */
    private void openDialer(String phone) {
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null));
        try {
            startActivity(dial);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "다이얼 화면을 열 수 없습니다.", e);
            Toast.makeText(this, R.string.detail_dial_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
