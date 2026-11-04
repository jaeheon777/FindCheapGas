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
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;
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

    /** 목록의 주유소 정보와 지금 고른 유종으로 상세 화면을 여는 Intent */
    public static Intent createIntent(Context context, GasStation station, FuelType fuelType) {
        return new Intent(context, StationDetailActivity.class)
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
