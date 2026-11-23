package com.taala.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Soft olive and cream light blobs drifting behind the glass cards. */
public class BlobView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] cols = new int[4];
    private final Shader[] shaders = new Shader[4];
    private final float[] fx = {.14f, .92f, .24f, .88f};
    private final float[] fy = {.08f, .26f, .96f, .92f};
    private final float[] fr = {.55f, .50f, .62f, .50f};
    private final int bg;
    private float t = 0f;
    private ValueAnimator anim;

    public BlobView(Context c, AttributeSet a) {
        super(c, a);
        bg = Ui.attr(c, R.attr.cBg);
        cols[0] = Ui.attr(c, R.attr.cB1);
        cols[1] = Ui.attr(c, R.attr.cB2);
        cols[2] = Ui.attr(c, R.attr.cB3);
        cols[3] = Ui.attr(c, R.attr.cB4);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float base = Math.max(w, h) * 0.6f;
        for (int i = 0; i < 4; i++) {
            shaders[i] = new RadialGradient(fx[i] * w, fy[i] * h, base * fr[i] * 1.2f,
                    cols[i], cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(26000);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(bg);
        c.save();
        c.translate(t * 0.03f * w, t * 0.02f * h);
        c.scale(1f + 0.06f * t, 1f + 0.06f * t, w / 2f, h / 2f);
        for (int i = 0; i < 4; i++) {
            if (shaders[i] == null) continue;
            paint.setShader(shaders[i]);
            c.drawRect(-w, -h, 2f * w, 2f * h, paint);
        }
        c.restore();
    }
}
