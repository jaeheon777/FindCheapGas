package com.example.findcheapgas.util;

import java.text.NumberFormat;
import java.util.Locale;

/** 가격, 거리 표시 형식 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /** 1659 → "1,659원" */
    public static String formatPrice(int price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "원";
    }

    /** 1234.5(m) → "1.2km" */
    public static String formatDistance(double meters) {
        return String.format(Locale.KOREA, "%.1fkm", meters / 1000.0);
    }
}
