package com.example.findcheapgas.util;

import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.StationSaving;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 가격, 계산 금액, 예상 이득, 할인·내 브랜드 요약, 거리, 연비·주유량, 계산 기준, 가격 기준 일시 표시 형식 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /** 1659 → "1,659원" */
    public static String formatPrice(int price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "원";
    }

    /** 계산한 금액(실수)을 원 단위로 반올림해서 표시한다. 68099.17 → "68,099원" */
    public static String formatWon(double amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(Math.round(amount)) + "원";
    }

    /**
     * 목록과 상세 화면의 예상 이득 문구. 추정치이므로 "약"을 붙인다.
     * 기준 주유소: "기준 (가장 가까운 곳)", 이득: "약 1,925원 이득", 손해: "약 300원 손해",
     * 원 단위로 차이가 없으면 "기준과 거의 같음"
     */
    public static String formatSaving(StationSaving saving) {
        if (saving.isBaseline()) {
            return "기준 (가장 가까운 곳)";
        }
        long won = saving.getSavingWon();
        if (won == 0) {
            return "기준과 거의 같음";
        }
        String amount = formatWon(Math.abs(won));
        return won > 0 ? "약 " + amount + " 이득" : "약 " + amount + " 손해";
    }

    /**
     * 내 브랜드 목록 한 줄의 할인 요약.
     * "리터당 100원 할인 · 1회 한도 3,000원", 한도를 비워 두면 "리터당 100원 할인 · 한도 미적용", 할인이 없으면 "할인 없음"
     */
    public static String formatDiscountSummary(DiscountRule rule) {
        if (!rule.hasDiscount()) {
            return "할인 없음";
        }
        String perLiter = "리터당 " + formatPrice(rule.getPerLiter()) + " 할인";
        Integer cap = rule.getCap();
        return cap != null ? perLiter + " · 1회 한도 " + formatPrice(cap) : perLiter + " · 한도 미적용";
    }

    /** 설정 탭 "내 브랜드" 요약. 0곳인 항목은 뺀다. "할인 2곳 · 선호 1곳 · 숨김 1곳", 아무것도 없으면 "설정한 브랜드 없음" */
    public static String formatBrandSummary(BrandSettings settings) {
        List<String> parts = new ArrayList<>();
        if (settings.discountCount() > 0) {
            parts.add("할인 " + settings.discountCount() + "곳");
        }
        if (settings.preferredCount() > 0) {
            parts.add("선호 " + settings.preferredCount() + "곳");
        }
        if (settings.hiddenCount() > 0) {
            parts.add("숨김 " + settings.hiddenCount() + "곳");
        }
        if (parts.isEmpty()) {
            return "설정한 브랜드 없음";
        }
        // String.join은 API 26부터라서 직접 잇는다.
        StringBuilder summary = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            summary.append(" · ").append(parts.get(i));
        }
        return summary.toString();
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
