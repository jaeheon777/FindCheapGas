package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.findcheapgas.model.CarInfo;

import org.junit.Test;

public class FormatUtilTest {

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
}
