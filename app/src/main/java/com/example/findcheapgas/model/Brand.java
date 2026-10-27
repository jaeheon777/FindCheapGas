package com.example.findcheapgas.model;

import java.util.HashMap;
import java.util.Map;

/** 오피넷 브랜드 코드(POLL_DIV_CD)를 한글 이름으로 바꾼다. */
public final class Brand {

    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        NAMES.put("SKE", "SK에너지");
        NAMES.put("GSC", "GS칼텍스");
        NAMES.put("HDO", "현대오일뱅크");
        NAMES.put("SOL", "S-OIL");
        NAMES.put("RTE", "자영알뜰");
        NAMES.put("RTX", "고속도로알뜰");
        NAMES.put("NHO", "농협알뜰");
        NAMES.put("E1G", "E1");
        NAMES.put("SKG", "SK가스");
        NAMES.put("ETC", "자가상표");
    }

    private Brand() {
    }

    /** 표에 없는 코드는 코드를 그대로 보여준다. */
    public static String nameOf(String code) {
        if (code == null) {
            return NAMES.get("ETC");
        }
        String name = NAMES.get(code);
        return name != null ? name : code;
    }
}
