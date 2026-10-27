package com.example.findcheapgas.model;

/** 주유소 목록 정렬 기준. code는 오피넷 sort 파라미터 값이다. */
public enum SortOrder {
    PRICE(1, "가격순"),
    DISTANCE(2, "거리순");

    private final int code;
    private final String label;

    SortOrder(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
