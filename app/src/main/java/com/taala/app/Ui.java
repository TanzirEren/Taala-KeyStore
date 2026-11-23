package com.taala.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class Ui {
    static Typeface body, bold, disp, mono;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Resolves a theme colour attribute such as R.attr.cInk. */
    static int attr(Context c, int attrId) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    private static Typeface variable(AssetManager am, String path, String axes, Typeface fallback) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Typeface t = new Typeface.Builder(am, path).setFontVariationSettings(axes).build();
                if (t != null) return t;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    static void initFonts(Context c) {
        if (body != null) return;
        AssetManager am = c.getAssets();
        body = variable(am, "fonts/bricolage.ttf", "'wght' 400, 'opsz' 14, 'wdth' 100", Typeface.SANS_SERIF);
        bold = variable(am, "fonts/bricolage.ttf", "'wght' 600, 'opsz' 14, 'wdth' 100",
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        disp = variable(am, "fonts/fraunces_italic.ttf", "'wght' 700, 'opsz' 72, 'SOFT' 0, 'WONK' 1",
                Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        mono = variable(am, "fonts/jetbrains_mono.ttf", "'wght' 400", Typeface.MONOSPACE);
    }

    /** Tag "disp", "mono" or "bold" on a TextView picks the font; everything else gets the body font. */
    static void fonts(View v) {
        if (v instanceof TextView) {
            Object tag = v.getTag();
            Typeface f = body;
            if ("disp".equals(tag)) f = disp;
            else if ("mono".equals(tag)) f = mono;
            else if ("bold".equals(tag)) f = bold;
            ((TextView) v).setTypeface(f);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) fonts(g.getChildAt(i));
        }
    }
}
