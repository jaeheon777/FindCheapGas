package com.example.findcheapgas.ui.favorite;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.FormatUtil;

/**
 * 즐겨찾기 탭의 집·회사 기준 비교 카드 한 장 (item_place_compare.xml)을 그린다.
 * READY면 이득 1위 주유소의 상호 · 브랜드, 가격 · 거리, 예상 총비용, 가격을 가져온 시각과
 * "가장 저렴" 또는 "약 1,200원 더 듦"을 보여주고, 그 밖에는 이유(등록 안 함, 주유소 없음, 한도 도달 등)를 보여준다.
 */
final class PlaceCompareCardView {

    interface Listener {
        /** 카드를 눌렀을 때 (등록하지 않은 카드는 누를 수 없다) */
        void onCardClick(PlaceCompareCard card);

        /** 등록하지 않은 카드의 "등록" 버튼 */
        void onRegisterClick(PlaceCompareCard card);
    }

    private final View root;
    private final TextView title;
    private final TextView cheapest;
    private final TextView extra;
    private final TextView station;
    private final TextView price;
    private final TextView total;
    private final TextView age;
    private final TextView message;
    private final View register;

    PlaceCompareCardView(View root) {
        this.root = root;
        title = root.findViewById(R.id.compare_title);
        cheapest = root.findViewById(R.id.compare_cheapest);
        extra = root.findViewById(R.id.compare_extra);
        station = root.findViewById(R.id.compare_station);
        price = root.findViewById(R.id.compare_price);
        total = root.findViewById(R.id.compare_total);
        age = root.findViewById(R.id.compare_age);
        message = root.findViewById(R.id.compare_message);
        register = root.findViewById(R.id.compare_register);
    }

    /**
     * @param titleText   예: "집 근처 · 선릉역"
     * @param kindLabel   등록 안내에 넣을 이름 ("집", "회사"). 지금 조회 위치 카드면 쓰지 않는다.
     * @param radiusLabel 지금 반경 (예: "3km")
     */
    void bind(PlaceCompareCard card, String titleText, String kindLabel, String radiusLabel, long now,
              Listener listener) {
        Context context = root.getContext();
        title.setText(titleText);
        StationSaving best = card.getBest();
        boolean ready = card.getStatus() == PlaceCompareCard.Status.READY && best != null;

        station.setVisibility(ready ? View.VISIBLE : View.GONE);
        price.setVisibility(ready ? View.VISIBLE : View.GONE);
        total.setVisibility(ready ? View.VISIBLE : View.GONE);
        cheapest.setVisibility(ready && card.isCheapest() ? View.VISIBLE : View.GONE);
        extra.setVisibility(ready && card.getExtraCostWon() > 0 ? View.VISIBLE : View.GONE);
        age.setVisibility(ready && card.getFetchedAt() > 0 ? View.VISIBLE : View.GONE);
        message.setVisibility(ready ? View.GONE : View.VISIBLE);
        register.setVisibility(card.getStatus() == PlaceCompareCard.Status.NOT_REGISTERED
                ? View.VISIBLE : View.GONE);

        String summary;
        if (ready) {
            GasStation gasStation = best.getStation();
            station.setText(context.getString(R.string.compare_station_line,
                    gasStation.getName(), gasStation.getBrandName()));
            price.setText(context.getString(R.string.compare_price_line,
                    FormatUtil.formatPrice(gasStation.getPrice()), FormatUtil.formatDistance(gasStation.getDistance())));
            total.setText(FormatUtil.formatTotalCost(best.getTotalCost()));
            extra.setText(FormatUtil.formatExtraCost(card.getExtraCostWon()));
            age.setText(FormatUtil.formatPriceAge(card.getFetchedAt(), now));
            summary = station.getText() + ", " + price.getText() + ", " + total.getText()
                    + (card.isCheapest() ? ", " + cheapest.getText() : "")
                    + (card.getExtraCostWon() > 0 ? ", " + extra.getText() : "");
        } else {
            message.setText(messageFor(context, card, kindLabel, radiusLabel));
            summary = message.getText().toString();
        }

        boolean clickable = card.getStatus() != PlaceCompareCard.Status.NOT_REGISTERED;
        root.setClickable(clickable);
        root.setFocusable(clickable);
        root.setOnClickListener(clickable ? v -> listener.onCardClick(card) : null);
        root.setContentDescription(clickable
                ? context.getString(R.string.compare_card_description, titleText, summary)
                : titleText + ". " + summary);
        register.setContentDescription(context.getString(R.string.compare_register_description, kindLabel));
        register.setOnClickListener(v -> listener.onRegisterClick(card));
    }

    private static String messageFor(Context context, PlaceCompareCard card, String kindLabel, String radiusLabel) {
        switch (card.getStatus()) {
            case NOT_REGISTERED:
                return context.getString(R.string.compare_not_registered, kindLabel);
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
        return R.string.compare_error_opinet;
    }
}
