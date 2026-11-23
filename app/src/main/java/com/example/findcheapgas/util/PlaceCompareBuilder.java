package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 집·회사 기준 비교 카드를 만든다. API는 부르지 않고, 안드로이드에 의존하지 않는다.
 *
 * 카드마다 그 위치에서 출발한다고 보고 지도 탭과 같은 순서로 계산한다:
 *   5km 안 전체 목록 → 반경 → 숨긴 브랜드 → 브랜드 빠른 필터 → 예상 이득 계산(내 차 정보, 브랜드 할인) → 이득 1위
 * (StationListBuilder를 그대로 쓴다)
 *
 * 위치끼리는 예상 이득이 아니라 이득 1위 주유소의 예상 총비용으로 비교한다.
 * 예상 이득은 그 위치에서 가장 가까운 주유소를 기준으로 한 값이라 위치마다 기준이 달라서 서로 비교할 수 없다.
 * 예상 총비용(기름값 + 그 위치에서 가는 이동 비용)은 같은 주유량을 넣는 데 드는 돈이라 위치끼리 비교할 수 있다.
 * READY 카드가 두 장 이상일 때만 원 단위 예상 총비용이 가장 낮은 카드에 "가장 저렴"을 표시하고, 나머지는 차이를 계산한다.
 * 즐겨찾기 탭 비교 카드 맨 위 결론(conclude)은 가장 저렴한 곳과 두 번째로 저렴한 곳의 차이로 정한다.
 */
public final class PlaceCompareBuilder {

    /** 카드 한 장의 입력: 위치 상태와, 받아 둔 5km 안 전체 목록 */
    public static final class Source {
        final PlaceCompareCard.Slot slot;
        final PlaceCompareCard.Status status;
        @Nullable
        final StationError error;
        @Nullable
        final List<GasStation> stations;
        final long fetchedAt;

        private Source(PlaceCompareCard.Slot slot, PlaceCompareCard.Status status, @Nullable StationError error,
                       @Nullable List<GasStation> stations, long fetchedAt) {
            this.slot = slot;
            this.status = status;
            this.error = error;
            this.stations = stations;
            this.fetchedAt = fetchedAt;
        }

        public static Source of(PlaceCompareCard.Slot slot, PlaceCompareCard.Status status) {
            return new Source(slot, status, null, null, 0);
        }

        public static Source error(PlaceCompareCard.Slot slot, StationError error) {
            return new Source(slot, PlaceCompareCard.Status.ERROR, error, null, 0);
        }

        /** 받아 둔 5km 안 전체 목록. 거리는 이 위치 기준이어야 한다. fetchedAt을 모르면 0 */
        public static Source loaded(PlaceCompareCard.Slot slot, List<GasStation> stations, long fetchedAt) {
            return new Source(slot, PlaceCompareCard.Status.READY, null, stations, fetchedAt);
        }
    }

    private PlaceCompareBuilder() {
    }

    /** 입력과 같은 순서로 카드를 돌려준다. */
    public static List<PlaceCompareCard> build(List<Source> sources, SearchRadius radius, CarInfo carInfo,
                                               BrandSettings brandSettings, BrandFilter brandFilter) {
        List<PlaceCompareCard> cards = new ArrayList<>(sources.size());
        for (Source source : sources) {
            cards.add(firstPass(source, radius, carInfo, brandSettings, brandFilter));
        }
        return Collections.unmodifiableList(compareTotals(cards));
    }

    /**
     * 비교한 카드(READY 두 장 이상)가 모두 가장 저렴한 카드와 "비슷" 구간(SavingLevel, ±1,000원) 안인지.
     * 즐겨찾기 탭 맨 위 결론을 "어디서 넣어도 비슷해요"로 바꿀 때 쓴다. "가장 저렴" 판정은 그대로다.
     */
    public static boolean allSimilarToCheapest(@Nullable List<PlaceCompareCard> cards) {
        if (cards == null) {
            return false;
        }
        int ready = 0;
        for (PlaceCompareCard card : cards) {
            if (card.getStatus() != PlaceCompareCard.Status.READY) {
                continue;
            }
            ready++;
            if (!card.isCheapest() && !card.isSimilarToCheapest()) {
                return false;
            }
        }
        return ready >= 2;
    }

    /**
     * 비교 카드 맨 위 결론. conclude()가 만든다.
     * - UNAVAILABLE: 비교한 카드(READY)가 두 장보다 적다. 큰 숫자 없이 짧은 안내만
     * - CHEAPER: 가장 저렴한 곳이 두 번째로 저렴한 곳보다 1,000원 이상 싸다 (SavingLevel.GAIN). 차이를 큰 숫자로
     * - SIMILAR: 모든 카드가 가장 저렴한 카드와 1,000원 미만 차이 (allSimilarToCheapest). 큰 숫자 없음
     * - CLOSE: 두 번째로 저렴한 곳과는 1,000원 미만이지만 1,000원 이상 더 드는 곳도 있다. 큰 숫자 없이 결론만
     */
    public static final class Conclusion {

        public enum Kind {
            UNAVAILABLE,
            CHEAPER,
            SIMILAR,
            CLOSE
        }

        private final Kind kind;
        @Nullable
        private final PlaceCompareCard cheapest;
        @Nullable
        private final PlaceCompareCard runnerUp;

        Conclusion(Kind kind, @Nullable PlaceCompareCard cheapest, @Nullable PlaceCompareCard runnerUp) {
            this.kind = kind;
            this.cheapest = cheapest;
            this.runnerUp = runnerUp;
        }

        public Kind getKind() {
            return kind;
        }

        /** 가장 저렴한 카드 (원 단위로 같으면 입력 순서상 앞의 카드). UNAVAILABLE이면 null */
        @Nullable
        public PlaceCompareCard getCheapest() {
            return cheapest;
        }

        /** 두 번째로 저렴한 카드. UNAVAILABLE이면 null */
        @Nullable
        public PlaceCompareCard getRunnerUp() {
            return runnerUp;
        }

        /** 가장 저렴한 곳이 두 번째로 저렴한 곳보다 싼 금액(원). UNAVAILABLE이면 0 */
        public long getSavingWon() {
            return runnerUp != null ? runnerUp.getExtraCostWon() : 0;
        }
    }

    /**
     * 비교 카드 맨 위 결론을 정한다. 예상 총비용과 "가장 저렴" 판정은 build()의 결과를 그대로 쓰고,
     * 차이는 두 번째로 저렴한 카드의 getExtraCostWon(가장 저렴한 카드보다 더 드는 금액)이다.
     */
    public static Conclusion conclude(@Nullable List<PlaceCompareCard> cards) {
        if (cards == null) {
            return new Conclusion(Conclusion.Kind.UNAVAILABLE, null, null);
        }
        PlaceCompareCard cheapest = null;
        for (PlaceCompareCard card : cards) {
            if (card.getStatus() == PlaceCompareCard.Status.READY && card.isCheapest()) {
                cheapest = card;
                break;
            }
        }
        if (cheapest == null) {
            // "가장 저렴"은 READY 카드가 두 장 이상일 때만 정해진다.
            return new Conclusion(Conclusion.Kind.UNAVAILABLE, null, null);
        }
        PlaceCompareCard runnerUp = null;
        for (PlaceCompareCard card : cards) {
            if (card == cheapest || card.getStatus() != PlaceCompareCard.Status.READY) {
                continue;
            }
            if (runnerUp == null || card.getExtraCostWon() < runnerUp.getExtraCostWon()) {
                runnerUp = card;
            }
        }
        if (runnerUp == null) {
            return new Conclusion(Conclusion.Kind.UNAVAILABLE, null, null);
        }
        Conclusion.Kind kind;
        if (SavingLevel.of(runnerUp.getExtraCostWon()) == SavingLevel.GAIN) {
            kind = Conclusion.Kind.CHEAPER;
        } else if (allSimilarToCheapest(cards)) {
            kind = Conclusion.Kind.SIMILAR;
        } else {
            kind = Conclusion.Kind.CLOSE;
        }
        return new Conclusion(kind, cheapest, runnerUp);
    }

    /** 화면 목록에서 이득 1위 주유소. 없으면 null */
    @Nullable
    public static StationSaving findBest(@Nullable List<StationSaving> displayed) {
        if (displayed == null) {
            return null;
        }
        for (StationSaving saving : displayed) {
            if (saving.isBest()) {
                return saving;
            }
        }
        return null;
    }

    private static PlaceCompareCard firstPass(Source source, SearchRadius radius, CarInfo carInfo,
                                              BrandSettings brandSettings, BrandFilter brandFilter) {
        if (source.status != PlaceCompareCard.Status.READY || source.stations == null) {
            return new PlaceCompareCard(source.slot, source.status, source.error,
                    StationListBuilder.EmptyReason.NONE, null, source.fetchedAt, false, 0);
        }
        // 정렬은 결과에 영향이 없다 (이득 1위 판정은 정렬과 상관없이 실제 예상 이득으로 한다).
        List<StationSaving> displayed = StationListBuilder.build(source.stations, radius, SortOrder.SAVING,
                carInfo, brandSettings, brandFilter);
        StationSaving best = findBest(displayed);
        if (best == null) {
            return new PlaceCompareCard(source.slot, PlaceCompareCard.Status.EMPTY, null,
                    StationListBuilder.emptyReason(source.stations, radius, brandSettings, brandFilter),
                    null, source.fetchedAt, false, 0);
        }
        return new PlaceCompareCard(source.slot, PlaceCompareCard.Status.READY, null,
                StationListBuilder.EmptyReason.NONE, best, source.fetchedAt, false, 0);
    }

    /** READY 카드끼리 원 단위 예상 총비용을 비교한다. 두 장 미만이면 비교하지 않는다. */
    private static List<PlaceCompareCard> compareTotals(List<PlaceCompareCard> cards) {
        long min = Long.MAX_VALUE;
        int ready = 0;
        for (PlaceCompareCard card : cards) {
            if (card.getStatus() == PlaceCompareCard.Status.READY) {
                ready++;
                min = Math.min(min, card.getTotalCostWon());
            }
        }
        if (ready < 2) {
            return cards;
        }
        List<PlaceCompareCard> result = new ArrayList<>(cards.size());
        for (PlaceCompareCard card : cards) {
            if (card.getStatus() != PlaceCompareCard.Status.READY) {
                result.add(card);
                continue;
            }
            long extra = card.getTotalCostWon() - min;
            result.add(new PlaceCompareCard(card.getSlot(), card.getStatus(), null, card.getEmptyReason(),
                    card.getBest(), card.getFetchedAt(), extra == 0, extra));
        }
        return result;
    }
}
