package com.example.findcheapgas.util;

/**
 * 목록 한 줄(왼쪽 상호·보조 정보 / 오른쪽 금액 묶음)을 세로로 쌓을지 정하는 규칙. 안드로이드에 의존하지 않는다.
 *
 * 오른쪽 금액 묶음은 줄바꿈하지 않고 먼저 폭을 차지하고, 남는 폭을 왼쪽이 쓴다.
 * 큰 글꼴처럼 금액 묶음이 넓어지면 왼쪽이 "지금 조…", "(주)남…"처럼 거의 다 잘려 읽을 수 없다.
 * 그래서 오른쪽 묶음을 한 줄로 놓았을 때 왼쪽에 남는 폭이 줄 안쪽 폭의 일정 비율보다 작으면
 * 그 줄만 금액 묶음을 왼쪽 아래로 내려 세로로 쌓는다. 글꼴 배율이 아니라 실제로 잰 폭으로 판단한다.
 * 비율은 dimens.xml의 list_row_stack_min_start_ratio 한 곳에 둔다.
 */
public final class RowStackRule {

    private RowStackRule() {
    }

    /**
     * @param contentWidth  줄 안쪽 폭 (좌우 여백 제외, px)
     * @param endWidth      오른쪽 금액 묶음이 줄바꿈 없이 차지하는 폭 (왼쪽과의 간격 포함, px)
     * @param minStartRatio 왼쪽에 남아야 하는 최소 폭 비율 (0~1)
     * @return 왼쪽에 남는 폭이 contentWidth × minStartRatio보다 작으면 true. 폭을 아직 모르면(0 이하) false
     */
    public static boolean shouldStack(int contentWidth, int endWidth, float minStartRatio) {
        if (contentWidth <= 0) {
            return false;
        }
        return contentWidth - endWidth < contentWidth * minStartRatio;
    }
}
