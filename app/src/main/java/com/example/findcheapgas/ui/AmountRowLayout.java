package com.example.findcheapgas.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.findcheapgas.R;
import com.example.findcheapgas.util.RowStackRule;

/**
 * 목록 한 줄의 틀: 첫 번째 자식은 왼쪽 묶음(상호·보조 정보), 마지막 자식은 오른쪽 금액 묶음(금액·설명).
 *
 * 보통은 가로로 놓는다: 오른쪽 금액 묶음이 줄바꿈 없이 먼저 폭을 차지하고, 남는 폭을 왼쪽이 쓰며 길면 말줄임된다.
 * 큰 글꼴처럼 금액 묶음이 넓어져 왼쪽에 남는 폭이 너무 좁으면(RowStackRule) 그 줄만 세로로 쌓는다:
 * 왼쪽 묶음이 줄 전체 폭을 쓰고, 금액 묶음은 그 아래에 왼쪽 정렬로 놓는다. 금액과 설명의 순서와 한 줄 규칙은 그대로다.
 * 판단은 측정할 때마다 실제 폭으로 다시 하므로 목록에서 줄을 다시 써도 맞게 바뀐다.
 * 자식의 순서는 바꾸지 않으므로 누르기, 바탕, 접근성 읽는 순서는 그대로다.
 *
 * 지도 탭 주유소 목록·저장한 주유소(view_list_row_amount), 집·회사 비교 카드의 줄(item_place_compare_row),
 * 상세 화면 유종별 가격 줄(item_fuel_price)이 쓴다.
 */
public class AmountRowLayout extends LinearLayout {

    private final float minStartRatio;
    private final int stackGap;
    private boolean stacked;
    // 가로 배치일 때 오른쪽 묶음의 왼쪽 간격 (레이아웃에 적은 값). 처음 측정할 때 기억한다.
    private int endMarginStart = -1;

    public AmountRowLayout(Context context) {
        this(context, null);
    }

    public AmountRowLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AmountRowLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedValue value = new TypedValue();
        getResources().getValue(R.dimen.list_row_stack_min_start_ratio, value, true);
        minStartRatio = value.getFloat();
        stackGap = getResources().getDimensionPixelSize(R.dimen.list_row_stack_gap);
    }

    /** 지금 세로로 쌓았는지 */
    public boolean isStacked() {
        return stacked;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() >= 2 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            View end = getChildAt(getChildCount() - 1);
            LayoutParams endParams = (LayoutParams) end.getLayoutParams();
            if (endMarginStart < 0) {
                endMarginStart = endParams.getMarginStart();
            }
            int contentWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
            boolean shouldStack = false;
            if (end.getVisibility() != GONE) {
                // 금액 묶음이 줄바꿈 없이 차지하는 폭
                end.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                shouldStack = RowStackRule.shouldStack(contentWidth, end.getMeasuredWidth() + endMarginStart,
                        minStartRatio);
            }
            applyStacked(shouldStack, end, endParams);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** 측정 중이라 requestLayout 없이 배치 값만 바꾼다 (바로 이어서 super.onMeasure가 새 값으로 잰다). */
    private void applyStacked(boolean stack, View end, LayoutParams endParams) {
        if (stack == stacked) {
            return;
        }
        stacked = stack;
        View start = getChildAt(0);
        LayoutParams startParams = (LayoutParams) start.getLayoutParams();
        setOrientation(stack ? VERTICAL : HORIZONTAL);
        startParams.width = stack ? LayoutParams.MATCH_PARENT : 0;
        startParams.weight = stack ? 0 : 1;
        endParams.setMarginStart(stack ? 0 : endMarginStart);
        // 이미 방향을 정한 뒤라 시작 여백을 왼쪽·오른쪽 여백으로 다시 옮겨 둔다.
        endParams.resolveLayoutDirection(getLayoutDirection());
        endParams.topMargin = stack ? stackGap : 0;
        endParams.gravity = stack ? Gravity.START : Gravity.NO_GRAVITY;
        if (end instanceof LinearLayout) {
            ((LinearLayout) end).setGravity(stack ? Gravity.START : Gravity.END);
        }
    }
}
