package com.example.findcheapgas.util;

import com.example.findcheapgas.model.CarInfo;

import java.text.NumberFormat;
import java.util.Locale;

/** 가격, 거리, 연비·주유량, 계산 기준, 가격 기준 일시 표시 형식 */
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

    /** 0.1km/L 단위 연비 → 정수면 소수점 없이, 아니면 한 자리까지. 120 → "12", 125 → "12.5" */
    public static String formatFuelEfficiencyNumber(int tenths) {
        if (tenths % 10 == 0) {
            return String.valueOf(tenths / 10);
        }
        return (tenths / 10) + "." + (tenths % 10);
    }

    /** 120 → "12km/L", 125 → "12.5km/L" */
    public static String formatFuelEfficiency(int tenths) {
        return formatFuelEfficiencyNumber(tenths) + "km/L";
    }

    /** 40 → "40L" */
    public static String formatFuelAmount(int liters) {
        return liters + "L";
    }

    /**
     * 계산 기준 문구. 목록 위쪽, 상세 계산 내역에 쓴다.
     * 기본값: "연비 12km/L · 40L 기준 (기본값)", 직접 입력: "내 차 기준 · 연비 15km/L · 50L"
     */
    public static String formatCalculationBasis(CarInfo carInfo) {
        String efficiency = formatFuelEfficiency(carInfo.getFuelEfficiencyTenths());
        String amount = formatFuelAmount(carInfo.getFuelAmount());
        if (carInfo.isDefault()) {
            return "연비 " + efficiency + " · " + amount + " 기준 (기본값)";
        }
        return "내 차 기준 · 연비 " + efficiency + " · " + amount;
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
