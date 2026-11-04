package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
