package com.example.findcheapgas.model;

/**
 * 주유소마다 어떤 할인 조건을 적용할지 정한다.
 * 이번 단계에서는 NONE(모든 브랜드 할인 없음)만 쓰고, 내 브랜드 설정을 만들 때 브랜드별 값을 돌려주게 한다.
 */
public interface DiscountPolicy {

    DiscountPolicy NONE = station -> DiscountRule.NONE;

    DiscountRule ruleFor(GasStation station);
}
