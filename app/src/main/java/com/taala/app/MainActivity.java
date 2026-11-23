package com.taala.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.BlurMaskFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int RQ_SAVE = 1, RQ_PICK = 2, RQ_IMPORT = 3;
    private static final String ALNUM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final String[] STEP_TEXT = {"Generating RSA key pair", "Signing certificate", "Packing PKCS12", "Building JKS and verifying"};

    private static final String WORKFLOW = String.join("\n",
            "name: Build signed APK", "on:", "  workflow_dispatch:", "  push:", "    branches: [main]",
            "jobs:", "  build:", "    runs-on: ubuntu-latest", "    steps:",
            "      - uses: actions/checkout@v4",
            "      - uses: actions/setup-java@v4", "        with:", "          distribution: temurin", "          java-version: 17",
            "      - name: Decode keystore",
            "        run: echo \"${{ secrets.KEYSTORE_BASE64 }}\" | base64 -d > release.jks",
            "      - name: Build release APK", "        run: ./gradlew assembleRelease", "        env:",
            "          KEYSTORE_PATH: ${{ github.workspace }}/release.jks",
            "          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}",
            "          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}",
            "          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}",
            "      - uses: actions/upload-artifact@v4", "        with:", "          name: signed-apk",
            "          path: app/build/outputs/apk/release/*.apk");

    private static final String GRADLE = String.join("\n",
            "android {", "    signingConfigs {", "        release {",
            "            storeFile file(System.getenv(\"KEYSTORE_PATH\") ?: \"release.jks\")",
            "            storePassword System.getenv(\"KEYSTORE_PASSWORD\")",
            "            keyAlias System.getenv(\"KEY_ALIAS\")",
            "            keyPassword System.getenv(\"KEY_PASSWORD\")",
            "        }", "    }", "    buildTypes {", "        release { signingConfig signingConfigs.release }", "    }", "}");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom rnd = new SecureRandom();
    private SharedPreferences prefs;
    private Db db;
    private boolean dark;
    private List<Project> all = new ArrayList<>();
    private Project cur;
    private LinkedHashMap<String, byte[]> curFiles;
    private String filter = "all", view = "home";
    private int sort = 0, bits = 2048;
    private boolean aliasTouched, settingAlias;
    private byte[] pendingSave;
    private Uri inspUri;
    private Dialog dlg;
    private ObjectAnimator spin;
    private final TextView[] steps = new TextView[4];
    private Runnable hideToast;
    private View svHome, svNew, svRes, svInsp, fab;
    private LinearLayout list;
    private MeterView meter;
    private TextView toastView;
    private TextView[] tabs;
    private LinearLayout[] panes;
    private final String[] tabIds = {"sec", "fp", "fl", "wf", "gr", "nt"};

    private <T extends View> T f(int id) { return findViewById(id); }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        prefs = getSharedPreferences("taala", MODE_PRIVATE);
        boolean sysDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dark = prefs.contains("theme") ? "dark".equals(prefs.getString("theme", "light")) : sysDark;
        setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        Ui.initFonts(this);
        Ui.fonts(f(android.R.id.content));
        db = new Db(this);
        bind();
        initForm();
        all = db.all();
        render();
        go("home");
    }

    @Override
    protected void onDestroy() {
        if (dlg != null && dlg.isShowing()) dlg.dismiss();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!view.equals("home")) { render(); go("home"); } else super.onBackPressed();
    }

    // ------------------------------------------------------------------ binding

    private void bind() {
        svHome = f(R.id.sv_home); svNew = f(R.id.sv_new); svRes = f(R.id.sv_res); svInsp = f(R.id.sv_insp);
        fab = f(R.id.fab); list = f(R.id.list); meter = f(R.id.meter); toastView = f(R.id.toast);
        tabs = new TextView[]{f(R.id.tab_sec), f(R.id.tab_fp), f(R.id.tab_fl), f(R.id.tab_wf), f(R.id.tab_gr), f(R.id.tab_nt)};
        panes = new LinearLayout[]{f(R.id.p_sec), f(R.id.p_fp), f(R.id.p_fl), f(R.id.p_wf), f(R.id.p_gr), f(R.id.p_nt)};
        for (int i = 0; i < tabs.length; i++) { final String id = tabIds[i]; tabs[i].setOnClickListener(v -> tab(id)); }

        f(R.id.btn_theme).setOnClickListener(v -> {
            prefs.edit().putString("theme", dark ? "light" : "dark").apply();
            recreate();
        });
        f(R.id.nav_home).setOnClickListener(v -> { render(); go("home"); });
        f(R.id.nav_insp).setOnClickListener(v -> go("insp"));
        fab.setOnClickListener(v -> { resetForm(); go("new"); });

        ((EditText) f(R.id.et_search)).addTextChangedListener(watcher(this::render));
        f(R.id.btn_sort).setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, 0, 0, "Newest");
            m.getMenu().add(0, 1, 1, "Name");
            m.getMenu().add(0, 2, 2, "Expiry");
            m.setOnMenuItemClickListener(it -> {
                sort = it.getItemId();
                ((TextView) f(R.id.btn_sort)).setText(it.getTitle() + " ▾");
                render();
                return true;
            });
            m.show();
        });
        f(R.id.chip_all).setOnClickListener(v -> { filter = "all"; render(); });
        f(R.id.chip_pin).setOnClickListener(v -> { filter = "pin"; render(); });
        f(R.id.chip_exp).setOnClickListener(v -> { filter = "exp"; render(); });
        f(R.id.btn_backup).setOnClickListener(v -> backup());
        f(R.id.btn_import).setOnClickListener(v -> pick(RQ_IMPORT));

        f(R.id.btn_cancel).setOnClickListener(v -> { render(); go("home"); });
        f(R.id.btn_gen).setOnClickListener(v -> { ((EditText) f(R.id.f_sp)).setText(randomPassword()); });
        f(R.id.btn_bits).setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, 2048, 0, "RSA 2048");
            m.getMenu().add(0, 4096, 1, "RSA 4096 (slower)");
            m.setOnMenuItemClickListener(it -> {
                bits = it.getItemId();
                ((TextView) f(R.id.btn_bits)).setText("RSA " + bits + " ▾");
                return true;
            });
            m.show();
        });
        f(R.id.btn_create).setOnClickListener(v -> generate());

        f(R.id.btn_zip).setOnClickListener(v -> saveZip(cur));
        f(R.id.btn_share).setOnClickListener(v -> shareZip(cur));
        f(R.id.btn_back).setOnClickListener(v -> { render(); go("home"); });

        f(R.id.btn_choose).setOnClickListener(v -> pick(RQ_PICK));
        f(R.id.btn_read).setOnClickListener(v -> readInspect());
    }

    private TextWatcher watcher(Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { r.run(); }
        };
    }

    private String txt(int id) { return ((EditText) f(id)).getText().toString().trim(); }
    private void set(int id, String s) { ((EditText) f(id)).setText(s); }

    private View inflate(int layout, ViewGroup parent) {
        View v = getLayoutInflater().inflate(layout, parent, false);
        Ui.fonts(v);
        return v;
    }

    // ------------------------------------------------------------------ navigation

    private void go(String n) {
        view = n;
        View[] vs = {svHome, svNew, svRes, svInsp};
        String[] names = {"home", "new", "res", "insp"};
        for (int i = 0; i < 4; i++) {
            boolean on = names[i].equals(n);
            vs[i].setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) {
                vs[i].setAlpha(0f);
                vs[i].setTranslationY(Ui.dp(this, 14));
                vs[i].animate().alpha(1f).translationY(0f).setDuration(350).start();
                vs[i].scrollTo(0, 0);
            }
        }
        fab.setVisibility(n.equals("home") ? View.VISIBLE : View.GONE);
        navStyle((TextView) f(R.id.nav_home), !n.equals("insp"));
        navStyle((TextView) f(R.id.nav_insp), n.equals("insp"));
    }

    private void navStyle(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.bg_nav_sel : 0);
        t.setTextColor(Ui.attr(this, on ? R.attr.cOn : R.attr.cMute));
    }

    private void tabStyle(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.bg_tab_on : R.drawable.bg_tab);
        t.setTextColor(Ui.attr(this, on ? R.attr.cOn : R.attr.cMute));
    }

    private void tab(String n) {
        for (int i = 0; i < tabIds.length; i++) {
            boolean on = tabIds[i].equals(n);
            tabStyle(tabs[i], on);
            panes[i].setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) {
                panes[i].setAlpha(0f);
                panes[i].setTranslationY(Ui.dp(this, 10));
                panes[i].animate().alpha(1f).translationY(0f).setDuration(300).start();
            }
        }
    }

    // ------------------------------------------------------------------ home

    private long daysLeft(Project p) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(p.expires);
            return Math.round((d.getTime() - System.currentTimeMillis()) / 86400000.0);
        } catch (Exception e) {
            return 0;
        }
    }

    private void render() {
        String q = txt(R.id.et_search).toLowerCase(Locale.ROOT);
        List<Project> l = new ArrayList<>();
        for (Project p : all) {
            String h = (p.project + p.app + p.pkg + p.alias).toLowerCase(Locale.ROOT);
            if (!h.contains(q)) continue;
            if (filter.equals("pin") && !p.pin) continue;
            if (filter.equals("exp") && daysLeft(p) >= 730) continue;
            l.add(p);
        }
        Collections.sort(l, (a, b) -> {
            int c = Boolean.compare(b.pin, a.pin);
            if (c != 0) return c;
            if (sort == 1) return a.project.compareToIgnoreCase(b.project);
            if (sort == 2) return a.expires.compareTo(b.expires);
            return Long.compare(b.created, a.created);
        });

        long first = Long.MAX_VALUE;
        int verified = 0;
        for (Project p : all) { first = Math.min(first, daysLeft(p)); if (p.ok) verified++; }
        ((TextView) f(R.id.stat_count)).setText(String.valueOf(all.size()));
        ((TextView) f(R.id.stat_exp)).setText(all.isEmpty() ? "–" : String.format(Locale.US, "%.1f y", first / 365.0));
        ((TextView) f(R.id.stat_ver)).setText(String.valueOf(verified));
        tabStyle((TextView) f(R.id.chip_all), filter.equals("all"));
        tabStyle((TextView) f(R.id.chip_pin), filter.equals("pin"));
        tabStyle((TextView) f(R.id.chip_exp), filter.equals("exp"));

        list.removeAllViews();
        if (l.isEmpty()) {
            TextView e = (TextView) inflate(R.layout.row_empty, list);
            e.setText(all.isEmpty() ? "No projects yet.\nTap New project to create your first keystore."
                    : "No project matches this view.");
            list.addView(e);
            return;
        }
        for (int i = 0; i < l.size(); i++) {
            final Project p = l.get(i);
            View v = inflate(R.layout.item_project, list);
            ((TextView) v.findViewById(R.id.av)).setText(new String(Character.toChars(p.project.codePointAt(0))).toUpperCase(Locale.ROOT));
            ((TextView) v.findViewById(R.id.name)).setText(p.project);
            ((TextView) v.findViewById(R.id.meta)).setText(p.app + (p.pkg.isEmpty() ? "" : " · " + p.pkg));
            TextView star = v.findViewById(R.id.star);
            star.setText(p.pin ? "★" : "☆");
            star.setTextColor(p.pin ? Color.parseColor("#D9A21F") : Ui.attr(this, R.attr.cMute));
            star.setOnClickListener(x -> { p.pin = !p.pin; db.put(p); render(); });
            ViewGroup chips = v.findViewById(R.id.chips);
            long dl = daysLeft(p);
            chips.addView(chip(p.alias, false));
            chips.addView(chip("RSA " + p.bits, false));
            chips.addView(chip(dl < 0 ? "Expired" : String.format(Locale.US, "%.1f y left", dl / 365.0), dl < 365));
            if (!p.note.isEmpty()) chips.addView(chip("Note", false));
            v.findViewById(R.id.btn_open).setOnClickListener(x -> showResult(p));
            v.findViewById(R.id.btn_zip1).setOnClickListener(x -> saveZip(p));
            v.findViewById(R.id.btn_clone).setOnClickListener(x -> {
                fillForm(p);
                go("new");
                toast("Settings copied. A new key will be generated.");
            });
            v.findViewById(R.id.btn_del).setOnClickListener(x -> new AlertDialog.Builder(this)
                    .setMessage("Delete this keystore from the app? Make sure you downloaded it.")
                    .setPositiveButton("Delete", (d, w) -> { db.delete(p.id); all.remove(p); render(); })
                    .setNegativeButton("Cancel", null).show());
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 10));
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 60L).setDuration(450).start();
            list.addView(v);
        }
    }

    private TextView chip(String s, boolean warn) {
        TextView t = (TextView) inflate(R.layout.row_chip, null);
        t.setText(s);
        t.setTextColor(Ui.attr(this, warn ? R.attr.cBad : R.attr.cMute));
        return t;
    }

    // ------------------------------------------------------------------ result

    private void showResult(Project p) {
        cur = p;
        try { curFiles = KeystoreFactory.files(p); } catch (Exception e) { toast("Could not build files: " + e.getMessage()); return; }
        ((TextView) f(R.id.r_title)).setText(p.project + " is ready.");
        ((TextView) f(R.id.r_sub)).setText(p.app + " · RSA " + p.bits + " · valid until " + p.expires);
        TextView badge = f(R.id.r_badge);
        badge.setText(p.ok ? "✓ Verified" : "Not verified");
        badge.setBackgroundResource(p.ok ? R.drawable.bg_badge : R.drawable.bg_badge_warn);
        for (LinearLayout pn : panes) pn.removeAllViews();

        LinearLayout sec = panes[0];
        kvRow(sec, "KEYSTORE_BASE64", p.jks, true, 0);
        kvRow(sec, "KEYSTORE_PASSWORD", p.sp, true, 1);
        kvRow(sec, "KEY_ALIAS", p.alias, false, 2);
        kvRow(sec, "KEY_PASSWORD", p.kp, true, 3);
        final String env = "KEYSTORE_BASE64=" + p.jks + "\nKEYSTORE_PASSWORD=" + p.sp + "\nKEY_ALIAS=" + p.alias + "\nKEY_PASSWORD=" + p.kp;
        TextView envBtn = (TextView) inflate(R.layout.row_btn, sec);
        envBtn.setText("Copy all as .env");
        envBtn.setOnClickListener(x -> copy(env, envBtn));
        sec.addView(envBtn);

        kvRow(panes[1], "SHA-1", p.sha1, false, 0);
        kvRow(panes[1], "SHA-256", p.sha256, false, 1);
        kvRow(panes[1], "Valid until", p.expires, false, 2);

        int i = 0;
        for (Map.Entry<String, byte[]> e : curFiles.entrySet()) {
            final String name = e.getKey();
            final byte[] data = e.getValue();
            View r = inflate(R.layout.row_file, panes[2]);
            ((TextView) r.findViewById(R.id.fname)).setText(name);
            ((TextView) r.findViewById(R.id.fdesc)).setText(describe(name) + " · " + String.format(Locale.US, "%.1f KB", data.length / 1024.0));
            r.findViewById(R.id.fdl).setOnClickListener(x -> saveAs(name, mime(name), data));
            stagger(r, i++);
            panes[2].addView(r);
        }
        TextView note = (TextView) inflate(R.layout.row_text, panes[2]);
        note.setText("Android has no JKS support, so an app that asks for JKS shows “JKS not found”. In Android apps choose the BKS file (or P12). Use JKS only on a computer.");
        panes[2].addView(note);

        codeRow(panes[3], WORKFLOW, "Copy workflow");
        codeRow(panes[4], GRADLE, "Copy Gradle block");

        View nv = inflate(R.layout.pane_note, panes[5]);
        final EditText ne = nv.findViewById(R.id.note);
        ne.setText(p.note);
        nv.findViewById(R.id.save).setOnClickListener(x -> {
            p.note = ne.getText().toString().trim();
            db.put(p);
            toast("Note saved");
        });
        panes[5].addView(nv);

        tab("sec");
        go("res");
    }

    private void kvRow(LinearLayout pane, String k, String v, boolean masked, int idx) {
        View r = inflate(R.layout.row_kv, pane);
        r.findViewById(R.id.divider).setVisibility(idx == 0 ? View.GONE : View.VISIBLE);
        ((TextView) r.findViewById(R.id.k)).setText(k);
        final TextView code = r.findViewById(R.id.code);
        code.setText(v);
        final TextView rev = r.findViewById(R.id.reveal);
        final TextView cp = r.findViewById(R.id.copy);
        if (masked) {
            final boolean[] hidden = {true};
            mask(code, true);
            rev.setOnClickListener(x -> {
                hidden[0] = !hidden[0];
                mask(code, hidden[0]);
                rev.setText(hidden[0] ? "Reveal" : "Hide");
            });
        } else {
            rev.setVisibility(View.GONE);
        }
        cp.setOnClickListener(x -> copy(v, cp));
        stagger(r, idx);
        pane.addView(r);
    }

    private void codeRow(LinearLayout pane, String text, String label) {
        View r = inflate(R.layout.row_code, pane);
        ((TextView) r.findViewById(R.id.code)).setText(text);
        final TextView cp = r.findViewById(R.id.copy);
        cp.setText(label);
        cp.setOnClickListener(x -> copy(text, cp));
        pane.addView(r);
    }

    private void stagger(View v, int i) {
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(this, 10));
        v.animate().alpha(1f).translationY(0f).setStartDelay(i * 60L).setDuration(450).start();
    }

    private void mask(TextView t, boolean on) {
        t.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        t.getPaint().setMaskFilter(on ? new BlurMaskFilter(Ui.dp(this, 5), BlurMaskFilter.Blur.NORMAL) : null);
        t.invalidate();
    }

    private void copy(String text, TextView btn) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Taala", text));
        final CharSequence old = btn.getText();
        btn.setText("Copied ✓");
        ui.postDelayed(() -> btn.setText(old), 1200);
        toast("Copied to clipboard");
    }

    private String describe(String n) {
        switch (n) {
            case "keystore.jks": return "Java keystore for Gradle, Android Studio, apksigner. Android apps cannot open it";
            case "keystore.bks": return "BKS, the native Android type. Use this inside Android apps";
            case "keystore.p12": return "PKCS12, key password equals keystore password";
            case "certificate.pem": return "Public certificate";
            case "private-key.pem": return "Private key (PKCS8)";
            case "keystore_base64.txt": return "Base64 of the JKS";
            default: return "All secrets and project details";
        }
    }

    private String mime(String n) {
        return n.endsWith(".pem") || n.endsWith(".txt") ? "text/plain" : "application/octet-stream";
    }

    // ------------------------------------------------------------------ create form

    private void initForm() {
        EditText sp = f(R.id.f_sp), kp = f(R.id.f_kp);
        CheckBox same = f(R.id.cb_same);
        sp.addTextChangedListener(watcher(() -> { syncPasswords(); updateMeter(); }));
        same.setOnCheckedChangeListener((b, c) -> syncPasswords());
        ((EditText) f(R.id.f_project)).addTextChangedListener(watcher(() -> {
            if (aliasTouched) return;
            settingAlias = true;
            set(R.id.f_alias, slug(txt(R.id.f_project)).replace("-", ""));
            settingAlias = false;
        }));
        ((EditText) f(R.id.f_alias)).addTextChangedListener(watcher(() -> { if (!settingAlias) aliasTouched = true; }));
        for (String k : new String[]{"cn", "ou", "o", "l", "st", "c"}) {
            int id = getResources().getIdentifier("f_" + k, "id", getPackageName());
            ((EditText) f(id)).setText(prefs.getString("id_" + k, ""));
        }
        resetForm();
    }

    private void syncPasswords() {
        EditText kp = f(R.id.f_kp);
        boolean same = ((CheckBox) f(R.id.cb_same)).isChecked();
        kp.setEnabled(!same);
        kp.setAlpha(same ? 0.55f : 1f);
        if (same) kp.setText(txt(R.id.f_sp));
    }

    private void updateMeter() {
        String x = txt(R.id.f_sp);
        int n = 0;
        if (x.length() >= 8) n++;
        if (x.length() >= 12) n++;
        if (x.matches(".*[a-z].*") && x.matches(".*[A-Z].*")) n++;
        if (x.matches(".*\\d.*")) n++;
        if (x.matches(".*[^A-Za-z0-9].*") || x.length() >= 18) n++;
        meter.setLevel(n);
    }

    private void resetForm() {
        for (int id : new int[]{R.id.f_project, R.id.f_app, R.id.f_pkg, R.id.f_alias}) ((EditText) f(id)).setText("");
        aliasTouched = false;
        ((CheckBox) f(R.id.cb_same)).setChecked(true);
        set(R.id.f_sp, randomPassword());
        f(R.id.tv_err).setVisibility(View.GONE);
    }

    private void fillForm(Project p) {
        resetForm();
        set(R.id.f_project, p.project + " (copy)");
        set(R.id.f_app, p.app);
        set(R.id.f_pkg, p.pkg);
        set(R.id.f_alias, p.alias);
        aliasTouched = true;
        set(R.id.f_years, String.valueOf(p.years));
        bits = p.bits;
        ((TextView) f(R.id.btn_bits)).setText("RSA " + bits + " ▾");
        set(R.id.f_cn, p.cn); set(R.id.f_ou, p.ou); set(R.id.f_o, p.o);
        set(R.id.f_l, p.l); set(R.id.f_st, p.st); set(R.id.f_c, p.c);
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) sb.append(ALNUM.charAt(rnd.nextInt(ALNUM.length())));
        return sb.toString();
    }

    private static String slug(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private void generate() {
        final TextView err = f(R.id.tv_err);
        err.setVisibility(View.GONE);
        final Project p = new Project();
        p.project = txt(R.id.f_project);
        p.app = txt(R.id.f_app);
        p.pkg = txt(R.id.f_pkg);
        p.alias = txt(R.id.f_alias);
        p.sp = ((EditText) f(R.id.f_sp)).getText().toString();
        boolean same = ((CheckBox) f(R.id.cb_same)).isChecked();
        p.kp = same ? p.sp : ((EditText) f(R.id.f_kp)).getText().toString();
        try { p.years = Math.max(1, Math.min(100, Integer.parseInt(txt(R.id.f_years)))); } catch (Exception e) { p.years = 25; }
        p.bits = bits;
        p.cn = txt(R.id.f_cn); p.ou = txt(R.id.f_ou); p.o = txt(R.id.f_o);
        p.l = txt(R.id.f_l); p.st = txt(R.id.f_st); p.c = txt(R.id.f_c).toUpperCase(Locale.ROOT);

        String m = "";
        if (p.project.isEmpty() || p.app.isEmpty() || p.alias.isEmpty()) m = "Project name, app name and key alias are required.";
        else if (p.sp.length() < 6 || p.kp.length() < 6) m = "Passwords need at least 6 characters. Tap Generate for a strong one.";
        else if (!p.c.isEmpty() && p.c.length() != 2) m = "Country code must be 2 letters, for example BD.";
        if (!m.isEmpty()) { err.setText(m); err.setVisibility(View.VISIBLE); return; }

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(svNew.getWindowToken(), 0);
        showProgress();
        new Thread(() -> {
            try {
                KeystoreFactory.create(p, n -> ui.post(() -> setStep(n)));
                db.put(p);
                ui.post(() -> {
                    setStep(4);
                    ui.postDelayed(() -> {
                        hideProgress();
                        if (((CheckBox) f(R.id.cb_rem)).isChecked()) {
                            prefs.edit().putString("id_cn", p.cn).putString("id_ou", p.ou).putString("id_o", p.o)
                                    .putString("id_l", p.l).putString("id_st", p.st).putString("id_c", p.c).apply();
                        }
                        all = db.all();
                        showResult(p);
                    }, 500);
                });
            } catch (Throwable t) {
                ui.post(() -> {
                    hideProgress();
                    err.setText("Could not generate the keystore: " + t.getMessage());
                    err.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ progress overlay

    private void showProgress() {
        dlg = new Dialog(this, R.style.ProgressDialog);
        dlg.setCancelable(false);
        dlg.setContentView(R.layout.dialog_progress);
        Ui.fonts(dlg.findViewById(android.R.id.content));
        Window w = dlg.getWindow();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        w.setBackgroundDrawable(new ColorDrawable((Ui.attr(this, R.attr.cBg) & 0x00FFFFFF) | 0x99000000));
        if (Build.VERSION.SDK_INT >= 31) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.setBlurBehindRadius(60);
            w.setAttributes(lp);
        }
        int[] ids = {R.id.s0, R.id.s1, R.id.s2, R.id.s3};
        for (int i = 0; i < 4; i++) steps[i] = dlg.findViewById(ids[i]);
        ImageView logo = dlg.findViewById(R.id.spin);
        spin = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f);
        spin.setDuration(2200);
        spin.setRepeatCount(ObjectAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.start();
        setStep(0);
        dlg.show();
    }

    private void setStep(int n) {
        for (int i = 0; i < 4; i++) {
            if (steps[i] == null) continue;
            String sym = i < n ? "✓  " : i == n ? "◐  " : "○  ";
            steps[i].setText(sym + STEP_TEXT[i]);
            steps[i].setTextColor(Ui.attr(this, i < n ? R.attr.cAcD : i == n ? R.attr.cInk : R.attr.cMute));
        }
    }

    private void hideProgress() {
        if (spin != null) spin.cancel();
        if (dlg != null && dlg.isShowing()) dlg.dismiss();
    }

    // ------------------------------------------------------------------ files, share, backup, inspect

    private String zipName(Project p) {
        String s = slug(p.project);
        return (s.isEmpty() ? "keystore" : s) + "-keystore.zip";
    }

    private void saveZip(Project p) {
        try { saveAs(zipName(p), "application/zip", KeystoreFactory.zip(p)); }
        catch (Exception e) { toast("Could not build zip: " + e.getMessage()); }
    }

    private void saveAs(String name, String mime, byte[] data) {
        pendingSave = data;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, RQ_SAVE);
    }

    private void pick(int rq) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, rq);
    }

    private void shareZip(Project p) {
        try {
            File dir = new File(getCacheDir(), "share");
            dir.mkdirs();
            String name = zipName(p);
            try (FileOutputStream fo = new FileOutputStream(new File(dir, name))) { fo.write(KeystoreFactory.zip(p)); }
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("application/zip");
            s.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://com.taala.app.share/" + Uri.encode(name)));
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(s, "Share keystore zip"));
        } catch (Exception e) {
            toast("Could not share: " + e.getMessage());
        }
    }

    private void backup() {
        try {
            JSONArray a = new JSONArray();
            for (Project p : all) a.put(p.toJson());
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            saveAs("taala-backup-" + day + ".json", "application/json", a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            toast("Could not back up: " + e.getMessage());
        }
    }

    private byte[] readAll(Uri u) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(u); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        }
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        return "Selected file";
    }

    @Override
    protected void onActivityResult(int rq, int rc, Intent d) {
        super.onActivityResult(rq, rc, d);
        if (rc != RESULT_OK || d == null || d.getData() == null) return;
        Uri u = d.getData();
        if (rq == RQ_SAVE) {
            try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(pendingSave); toast("Saved"); }
            catch (Exception e) { toast("Could not save: " + e.getMessage()); }
            pendingSave = null;
        } else if (rq == RQ_PICK) {
            inspUri = u;
            ((TextView) f(R.id.tv_fname)).setText(displayName(u));
        } else if (rq == RQ_IMPORT) {
            try {
                JSONArray a = new JSONArray(new String(readAll(u), StandardCharsets.UTF_8));
                int n = 0;
                for (int i = 0; i < a.length(); i++) {
                    try { db.put(Project.fromJson(a.getJSONObject(i))); n++; } catch (Exception ignored) { }
                }
                all = db.all();
                render();
                toast("Imported " + n + " keystores");
            } catch (Exception e) {
                toast("Not a valid Taala backup");
            }
        }
    }

    private void readInspect() {
        final TextView err = f(R.id.i_err);
        final LinearLayout out = f(R.id.i_out);
        err.setVisibility(View.GONE);
        out.setVisibility(View.GONE);
        if (inspUri == null) { err.setText("Choose a .p12 or .pfx file first."); err.setVisibility(View.VISIBLE); return; }
        try {
            List<String[]> rows = KeystoreFactory.inspect(readAll(inspUri), ((EditText) f(R.id.i_pw)).getText().toString());
            out.removeAllViews();
            for (int i = 0; i < rows.size(); i++) kvRow(out, rows.get(i)[0], rows.get(i)[1], false, i);
            out.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            err.setText("Could not read the file. Check the password. (" + e.getMessage() + ")");
            err.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------ toast

    private void toast(String s) {
        toastView.setText(s);
        toastView.setVisibility(View.VISIBLE);
        toastView.setAlpha(0f);
        toastView.setTranslationY(Ui.dp(this, 20));
        toastView.animate().alpha(1f).translationY(0f).setDuration(250).start();
        if (hideToast != null) ui.removeCallbacks(hideToast);
        hideToast = () -> toastView.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> toastView.setVisibility(View.GONE)).start();
        ui.postDelayed(hideToast, 1800);
    }
}
