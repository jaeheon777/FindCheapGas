package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationSaving;

import org.junit.Test;

public class FormatUtilTest {

    private static StationSaving saving(String id, double totalCost, double baselineTotalCost) {
        GasStation baseline = new GasStation("base", "SKE", "base", 1700, 500, 0, 0, 0, 0);
        GasStation station = id.equals("base") ? baseline : new GasStation(id, "SKE", id, 1650, 2000, 0, 0, 0, 0);
        return new StationSaving(station, baseline, CarInfo.DEFAULT, DiscountRule.NONE,
                0, 0, 0, 0, totalCost, baselineTotalCost, false);
    }

    @Test
    public void won_roundsToWon() {
        assertEquals("99원", FormatUtil.formatWon(99.1667));
        assertEquals("68,099원", FormatUtil.formatWon(68_099.1667));
        assertEquals("1,926원", FormatUtil.formatWon(1_925.5));
    }

    @Test
    public void saving_texts() {
        assertEquals("기준 (가장 가까운 곳)", FormatUtil.formatSaving(saving("base", 68_099, 68_099)));
        assertEquals("약 1,925원 이득", FormatUtil.formatSaving(saving("x", 66_174, 68_099.17)));
        assertEquals("약 300원 손해", FormatUtil.formatSaving(saving("x", 68_399.2, 68_099.17)));
        assertEquals("기준과 거의 같음", FormatUtil.formatSaving(saving("x", 68_099.3, 68_099.17)));
    }

    @Test
    public void tradeTime_dateAndTime() {
        assertEquals("11월 4일 15:30", FormatUtil.formatTradeTime("20261104", "153000"));
        assertEquals("1월 15일 08:05", FormatUtil.formatTradeTime("20270115", "0805"));
    }

    @Test
    public void tradeTime_invalidTime_showsDateOnly() {
        assertEquals("11월 4일", FormatUtil.formatTradeTime("20261104", ""));
        assertEquals("11월 4일", FormatUtil.formatTradeTime("20261104", null));
        assertEquals("11월 4일", FormatUtil.formatTradeTime("20261104", "2561"));
    }

    @Test
    public void tradeTime_invalidDate_returnsNull() {
        assertNull(FormatUtil.formatTradeTime("", "153000"));
        assertNull(FormatUtil.formatTradeTime(null, null));
        assertNull(FormatUtil.formatTradeTime("2026-11-04", "153000"));
        assertNull(FormatUtil.formatTradeTime("20261304", "153000"));
    }

    @Test
    public void fuelEfficiency_integerWithoutDecimal() {
        assertEquals("12km/L", FormatUtil.formatFuelEfficiency(120));
        assertEquals("3km/L", FormatUtil.formatFuelEfficiency(30));
        assertEquals("40km/L", FormatUtil.formatFuelEfficiency(400));
    }

    @Test
    public void fuelEfficiency_decimalOneDigit() {
        assertEquals("12.5km/L", FormatUtil.formatFuelEfficiency(125));
        assertEquals("3.1km/L", FormatUtil.formatFuelEfficiency(31));
        assertEquals("12.5", FormatUtil.formatFuelEfficiencyNumber(125));
        assertEquals("12", FormatUtil.formatFuelEfficiencyNumber(120));
    }

    @Test
    public void calculationBasis_default() {
        assertEquals("연비 12km/L · 40L 기준 (기본값)", FormatUtil.formatCalculationBasis(CarInfo.DEFAULT));
    }

    @Test
    public void calculationBasis_userEntered() {
        assertEquals("내 차 기준 · 연비 15km/L · 50L",
                FormatUtil.formatCalculationBasis(CarInfo.userEntered(150, 50)));
        assertEquals("내 차 기준 · 연비 12.5km/L · 45L",
                FormatUtil.formatCalculationBasis(CarInfo.userEntered(125, 45)));
        // 기본값과 같은 숫자를 직접 입력해도 "내 차 기준"
        assertEquals("내 차 기준 · 연비 12km/L · 40L",
                FormatUtil.formatCalculationBasis(CarInfo.userEntered(120, 40)));
    }

    @Test
    public void discountSummary() {
        assertEquals("리터당 100원 할인 · 1회 한도 3,000원",
                FormatUtil.formatDiscountSummary(new DiscountRule(100, 3_000)));
        assertEquals("리터당 100원 할인 · 한도 미적용", FormatUtil.formatDiscountSummary(new DiscountRule(100, null)));
        assertEquals("할인 없음", FormatUtil.formatDiscountSummary(DiscountRule.NONE));
    }

    @Test
    public void brandSummary() {
        assertEquals("설정한 브랜드 없음", FormatUtil.formatBrandSummary(BrandSettings.EMPTY));
        BrandSettings settings = BrandSettings.EMPTY
                .with("SKE", new BrandSetting(100, 3_000, true, false))
                .with("GSC", new BrandSetting(50, null, false, false))
                .with("SOL", new BrandSetting(0, null, false, true));
        assertEquals("할인 2곳 · 선호 1곳 · 숨김 1곳", FormatUtil.formatBrandSummary(settings));
        assertEquals("선호 1곳", FormatUtil.formatBrandSummary(
                BrandSettings.EMPTY.with("SKE", new BrandSetting(0, null, true, false))));
    }

    @Test
    public void compareTotalAndExtraCost() {
        assertEquals("예상 총비용 약 66,174원", FormatUtil.formatTotalCost(66174.3));
        assertEquals("약 1,200원 더 듦", FormatUtil.formatExtraCost(1200));
    }

    @Test
    public void priceAge() {
        long now = 1_800_000_000_000L;
        long minute = 60_000L;
        assertEquals("방금 가져온 가격", FormatUtil.formatPriceAge(now - 30_000L, now));
        assertEquals("약 1분 전 가격", FormatUtil.formatPriceAge(now - minute, now));
        assertEquals("약 25분 전 가격", FormatUtil.formatPriceAge(now - 25 * minute, now));
        assertEquals("약 1시간 전 가격", FormatUtil.formatPriceAge(now - 60 * minute, now));
        assertEquals("약 1시간 전 가격", FormatUtil.formatPriceAge(now - 89 * minute, now));
        assertEquals("약 2시간 전 가격", FormatUtil.formatPriceAge(now - 90 * minute, now));
        assertEquals("약 3시간 전 가격", FormatUtil.formatPriceAge(now - 180 * minute, now));
        // 기기 시계가 거꾸로 가 있어도 음수로 보이지 않는다.
        assertEquals("방금 가져온 가격", FormatUtil.formatPriceAge(now + 5 * minute, now));
    }
}
