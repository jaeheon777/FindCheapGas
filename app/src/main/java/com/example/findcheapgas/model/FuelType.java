package com.example.findcheapgas.model;

/** 오피넷 유종 코드 */
public enum FuelType {
    GASOLINE("B027", "휘발유"),
    DIESEL("D047", "경유"),
    PREMIUM_GASOLINE("B034", "고급휘발유"),
    LPG("K015", "LPG");

    private final String code;
    private final String label;

    FuelType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
