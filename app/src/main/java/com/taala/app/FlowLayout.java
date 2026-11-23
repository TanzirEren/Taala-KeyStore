package com.taala.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Wraps children onto new rows, like a row of chips or buttons on the web. */
public class FlowLayout extends ViewGroup {
    private final int gap;

    public FlowLayout(Context c, AttributeSet a) {
        super(c, a);
        gap = Math.round(8 * c.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0, used = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0; }
            x += w + gap;
            used = Math.max(used, x - gap);
            rowH = Math.max(rowH, h);
        }
        setMeasuredDimension(
                resolveSize(used + getPaddingLeft() + getPaddingRight(), wSpec),
                resolveSize(y + rowH + getPaddingTop() + getPaddingBottom(), hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0; }
            int left = getPaddingLeft() + x, top = getPaddingTop() + y;
            v.layout(left, top, left + w, top + h);
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
    }
}
