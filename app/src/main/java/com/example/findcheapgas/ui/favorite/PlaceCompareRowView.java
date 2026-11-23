package com.example.findcheapgas.ui.favorite;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 즐겨찾기 탭 집·회사 기준 비교 카드 안의 한 줄 (item_place_compare_row.xml)을 그린다.
 * - READY: 왼쪽에 위치 이름, 이득 1위 상호, 보조 글자 한 줄(브랜드 · 거리 · 가격 · 가져온 시각),
 *   오른쪽에 예상 총비용과 "가장 저렴" 배지 / "약 1,200원 더 들어요"(빨강) / "비슷해요"(회색, 1,000원 미만 차이)
 * - 등록 안 함: 줄 전체를 "집 위치 등록하기" 버튼 하나로
 * - 그 밖: 위치 이름과 짧은 이유 (가져오는 중, 주유소 없음, 한도 도달, 오류, 가짜 데이터 범위 밖)
 */
final class PlaceCompareRowView {

    interface Listener {
        /** 줄을 눌렀을 때 (등록하지 않은 위치는 등록 버튼만 누를 수 있다) */
        void onRowClick(PlaceCompareCard card);

        /** 등록하지 않은 위치의 "집 위치 등록하기" 버튼 */
        void onRegisterClick(PlaceCompareCard card);
    }

    private final View row;
    private final TextView label;
    private final TextView station;
    private final TextView detail;
    private final TextView message;
    private final View end;
    private final TextView total;
    private final TextView cheapest;
    private final TextView extra;
    private final TextView register;

    PlaceCompareRowView(View root) {
        row = root.findViewById(R.id.compare_row);
        label = root.findViewById(R.id.compare_row_label);
        station = root.findViewById(R.id.compare_row_station);
        detail = root.findViewById(R.id.compare_row_detail);
        message = root.findViewById(R.id.compare_row_message);
        end = root.findViewById(R.id.compare_row_end);
        total = root.findViewById(R.id.compare_row_total);
        cheapest = root.findViewById(R.id.compare_row_cheapest);
        extra = root.findViewById(R.id.compare_row_extra);
        register = root.findViewById(R.id.compare_row_register);
    }

    /**
     * @param labelText   예: "집 근처 · 선릉역"
     * @param kindLabel   등록 버튼에 넣을 이름 ("집", "회사"). 지금 조회 위치 줄이면 쓰지 않는다.
     * @param radiusLabel 지금 반경 (예: "3km")
     */
    void bind(PlaceCompareCard compareCard, String labelText, String kindLabel, String radiusLabel, long now,
              Listener listener) {
        Context context = row.getContext();
        boolean notRegistered = compareCard.getStatus() == PlaceCompareCard.Status.NOT_REGISTERED;
        row.setVisibility(notRegistered ? View.GONE : View.VISIBLE);
        register.setVisibility(notRegistered ? View.VISIBLE : View.GONE);
        if (notRegistered) {
            register.setText(context.getString(R.string.compare_register, kindLabel));
            register.setOnClickListener(v -> listener.onRegisterClick(compareCard));
            return;
        }

        label.setText(labelText);
        StationSaving best = compareCard.getBest();
        boolean ready = compareCard.getStatus() == PlaceCompareCard.Status.READY && best != null;
        station.setVisibility(ready ? View.VISIBLE : View.GONE);
        detail.setVisibility(ready ? View.VISIBLE : View.GONE);
        end.setVisibility(ready ? View.VISIBLE : View.GONE);
        message.setVisibility(ready ? View.GONE : View.VISIBLE);
        cheapest.setVisibility(ready && compareCard.isCheapest() ? View.VISIBLE : View.GONE);
        extra.setVisibility(ready && compareCard.getExtraCostWon() > 0 ? View.VISIBLE : View.GONE);

        String summary;
        if (ready) {
            GasStation gasStation = best.getStation();
            station.setText(gasStation.getName());
            List<String> parts = new ArrayList<>();
            parts.add(gasStation.getBrandName());
            parts.add(FormatUtil.formatDistance(gasStation.getDistance()));
            parts.add(FormatUtil.formatPrice(gasStation.getPrice()));
            if (compareCard.getFetchedAt() > 0) {
                parts.add(FormatUtil.formatPriceAge(compareCard.getFetchedAt(), now));
            }
            detail.setText(join(parts));
            total.setText(FormatUtil.formatApproxWon(best.getTotalCost()));
            // 가장 저렴한 곳과 1,000원 미만 차이면 지도 탭의 "비슷"과 같이 회색 "비슷해요", 그보다 크면 빨강
            boolean similar = compareCard.isSimilarToCheapest();
            extra.setText(similar ? context.getString(R.string.compare_similar)
                    : FormatUtil.formatExtraCost(compareCard.getExtraCostWon()));
            extra.setTextColor(ContextCompat.getColor(context,
                    SavingColors.textColorOf(similar ? SavingLevel.SIMILAR : SavingLevel.LOSS)));
            summary = gasStation.getName() + ", " + detail.getText() + ", "
                    + context.getString(R.string.compare_row_total, total.getText())
                    + (compareCard.isCheapest() ? ", " + cheapest.getText() : "")
                    + (compareCard.getExtraCostWon() > 0
                    ? ", " + (similar
                    ? context.getString(R.string.compare_similar_description,
                    FormatUtil.formatWon(compareCard.getExtraCostWon()))
                    : extra.getText())
                    : "");
        } else {
            message.setText(messageFor(context, compareCard, radiusLabel));
            summary = message.getText().toString();
        }

        row.setOnClickListener(v -> listener.onRowClick(compareCard));
        row.setContentDescription(context.getString(R.string.compare_row_description, labelText, summary));
    }

    private static String join(List<String> parts) {
        // String.join은 API 26부터라서 직접 잇는다.
        StringBuilder text = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            text.append(" · ").append(parts.get(i));
        }
        return text.toString();
    }

    private static String messageFor(Context context, PlaceCompareCard card, String radiusLabel) {
        switch (card.getStatus()) {
            case WAITING:
                return context.getString(R.string.compare_waiting);
            case OUTSIDE_MOCK_AREA:
                return context.getString(R.string.compare_outside_mock);
            case EMPTY:
                switch (card.getEmptyReason()) {
                    case HIDDEN_BRANDS:
                        return context.getString(R.string.compare_empty_hidden);
                    case BRAND_FILTER:
                        return context.getString(R.string.compare_empty_filter);
                    default:
                        return context.getString(R.string.compare_empty_radius, radiusLabel);
                }
            case ERROR:
                return context.getString(errorMessage(card.getError()));
            default:
                return context.getString(R.string.compare_loading);
        }
    }

    @StringRes
    private static int errorMessage(StationError error) {
        if (error == StationError.DAILY_LIMIT) {
            return R.string.compare_error_daily_limit;
        }
        if (error == StationError.NETWORK) {
            return R.string.compare_error_network;
        }
        if (error == StationError.API_KEY) {
            return R.string.compare_key_missing;
        }
        return R.string.compare_error_opinet;
    }
}
