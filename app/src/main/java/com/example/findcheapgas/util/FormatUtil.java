package com.example.findcheapgas.util;

import java.text.NumberFormat;
import java.util.Locale;

/** 가격, 거리, 가격 기준 일시 표시 형식 */
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

    /**
     * 오피넷 가격 기준 일시. ("20261104", "153000") → "11월 4일 15:30"
     * 시각을 읽을 수 없으면 날짜만, 날짜를 읽을 수 없으면 null을 돌려준다.
     */
    public static String formatTradeTime(String date, String time) {
        if (date == null || !date.matches("\\d{8}")) {
            return null;
        }
        int month = Integer.parseInt(date.substring(4, 6));
        int day = Integer.parseInt(date.substring(6, 8));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        String result = month + "월 " + day + "일";
        if (time != null && time.matches("\\d{4,6}")) {
            int hour = Integer.parseInt(time.substring(0, 2));
            int minute = Integer.parseInt(time.substring(2, 4));
            if (hour < 24 && minute < 60) {
                result += String.format(Locale.KOREA, " %02d:%02d", hour, minute);
            }
        }
        return result;
    }
}
