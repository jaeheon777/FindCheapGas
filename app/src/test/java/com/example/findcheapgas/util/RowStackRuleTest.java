package com.example.findcheapgas.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 목록 한 줄을 세로로 쌓을지: 오른쪽 금액 묶음을 놓고 왼쪽에 남는 폭이 기준 비율보다 작을 때만 쌓는다. */
public class RowStackRuleTest {

    private static final float RATIO = 0.4f;

    @Test
    public void enoughRoom_keepsSideBySide() {
        // 글꼴 1.0 정도: 줄 1,000 중 금액 묶음 350 → 왼쪽 650 (65%)
        assertFalse(RowStackRule.shouldStack(1000, 350, RATIO));
    }

    @Test
    public void narrowStart_stacks() {
        // 글꼴 2.0 정도: 금액 묶음 700 → 왼쪽 300 (30%)
        assertTrue(RowStackRule.shouldStack(1000, 700, RATIO));
    }

    @Test
    public void boundary_exactlyRatio_keepsSideBySide() {
        assertFalse(RowStackRule.shouldStack(1000, 600, RATIO));
        assertTrue(RowStackRule.shouldStack(1000, 601, RATIO));
    }

    @Test
    public void endWiderThanRow_stacks() {
        assertTrue(RowStackRule.shouldStack(1000, 1200, RATIO));
    }

    @Test
    public void unknownWidth_doesNotStack() {
        assertFalse(RowStackRule.shouldStack(0, 300, RATIO));
        assertFalse(RowStackRule.shouldStack(-1, 300, RATIO));
    }
}
