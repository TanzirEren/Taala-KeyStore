package com.taala.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Password strength bar (0 to 5), animated like the web meter. */
public class MeterView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int bad, good;
    private float shown = 0f;
    private ValueAnimator anim;

    public MeterView(Context c, AttributeSet a) {
        super(c, a);
        track.setColor(Ui.attr(c, R.attr.cLine));
        bad = Ui.attr(c, R.attr.cBad);
        good = Ui.attr(c, R.attr.cAc);
        fill.setColor(bad);
    }

    public void setLevel(int n) {
        float target = Math.max(0, Math.min(5, n)) / 5f;
        fill.setColor(n < 3 ? bad : good);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, target);
        anim.setDuration(300);
        anim.addUpdateListener(v -> { shown = (float) v.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth(), r = h / 2f;
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, track);
        if (shown > 0) {
            rect.set(0, 0, Math.max(h, w * shown), h);
            c.drawRoundRect(rect, r, r, fill);
        }
    }
}
