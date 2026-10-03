package com.lumen.browser;

import static com.lumen.browser.Scripts.*;

import android.util.Log;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import android.app.PictureInPictureParams;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.speech.RecognizerIntent;
import android.widget.HorizontalScrollView;
import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final String TAG = "Lasur";
    // Feature areas split out of this class; each keeps a reference back to the activity.
    final HomePage home = new HomePage(this);
    final TabSwitcher tabSwitcher = new TabSwitcher(this);
    final MenuUi menu = new MenuUi(this);
    final Lists lists = new Lists(this);
    final SettingsUi settingsUi = new SettingsUi(this);
    final VideoUi videoUi = new VideoUi(this);
    final Downloads downloads = new Downloads(this);
    final PasswordsUi passwordsUi = new PasswordsUi(this);
    final SitePermissions perms = new SitePermissions(this);
    final Onboarding onboarding = new Onboarding(this);
    final PipController pip = new PipController(this);
    /** One shared pool for background work instead of ad-hoc threads. */
    static final java.util.concurrent.ExecutorService BG = java.util.concurrent.Executors.newFixedThreadPool(3);
    /** Ordered disk writes (tab state, history). */
    static final java.util.concurrent.ExecutorService IO = Store.IO;
    // Injected page scripts live in Scripts.java

    static final String[] VIDEO_EXT = {".mp4", ".webm", ".m3u8", ".mkv", ".mov", ".flv", ".3gp", ".mpd", ".m4v", ".avi", ".wmv"};
    static final int REQ_FILE = 11, REQ_STORAGE = 12, REQ_NOTIF = 13;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    Store store;
    final ArrayList<Tab> tabs = new ArrayList<>();
    Tab current;
    final Handler ui = new Handler(Looper.getMainLooper());

    FrameLayout root, content, webContainer, ntpHolder, switcher, fullscreen, tabBtn, videoFab;
    LinearLayout mainCol, toolbar, findBar, omniPill, suggestBox;
    ScrollView suggestScroll;
    EditText omni, findInput;
    TextView tabCount, findCount, videoBadge;
    ImageView homeBtn, backBtn, menuBtn, lockIcon, clearBtn;
    View divider;
    ProgressBar progress;

    View customView; WebChromeClient.CustomViewCallback customCb;
    Tab fullscreenWebTab;
    ValueCallback<Uri[]> fileCb;
    Runnable pendingPerm;
    String mobileUA, desktopUA;
    boolean switcherIncognito;


    // ------------------------------------------------------------------ lifecycle
    @Override protected void onCreate(Bundle b) {
        store = new Store(this);
        passwords = new Passwords(store.p);
        int tm = store.p.getInt("themeMode", 0), ac = store.p.getInt("accent", 0);
        if (ac < 0 || ac > 6) ac = 0;
        boolean dk = Ui.resolveDark(this, tm);
        int[] L = {R.style.AppTheme_L0, R.style.AppTheme_L1, R.style.AppTheme_L2, R.style.AppTheme_L3, R.style.AppTheme_L4, R.style.AppTheme_L5, R.style.AppTheme_L6};
        int[] D = {R.style.AppTheme_D0, R.style.AppTheme_D1, R.style.AppTheme_D2, R.style.AppTheme_D3, R.style.AppTheme_D4, R.style.AppTheme_D5, R.style.AppTheme_D6};
        setTheme(dk ? D[ac] : L[ac]);
        super.onCreate(b);
        installCrashLog();
        Ui.init(this, tm, ac);
        AdBlocker.init(this);
        wipeIncognito();
        AdBlocker.enabled = store.adblock();
        AdBlocker.whitelist = store.whitelist();
        AdBlocker.totalBlocked.set(store.p.getLong("blockedTotal", 0));
        String base = WebSettings.getDefaultUserAgent(this);
        mobileUA = base.replace("; wv", "").replaceAll("Version/\\d+(\\.\\d+)* ", "");
        Matcher m = Pattern.compile("Chrome/([\\d.]+)").matcher(base);
        String ver = m.find() ? m.group(1) : "124.0.0.0";
        desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + ver + " Safari/537.36";
        buildUi();
        setContentView(root);
        setupEdgeToEdge();
        String act = getIntent() != null ? getIntent().getAction() : null;
        boolean viaLink = Intent.ACTION_VIEW.equals(act) || Intent.ACTION_SEND.equals(act) || Intent.ACTION_WEB_SEARCH.equals(act);
        if (b == null && !viaLink) onboarding.showSplash(); else if (!store.bool("onboarded", false)) onboarding.showWelcome();
        root.requestFocus();
        if (b == null) ui.postDelayed(this::offerCrashReport, 1400);
        if (store.restoreTabs()) restoreTabs();
        boolean handled = handleIntent(getIntent());
        if (!handled) {
            if (tabs.isEmpty()) newTab(null, false, true, null);
            else selectTab(tabs.get(Math.max(0, Math.min(store.p.getInt("tabIndex", 0), tabs.size() - 1))));
        }
        downloads.registerDlReceiver();
        pip.registerPipReceiver();
        registerBack();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i); }

    @Override protected void onPause() {
        if (pip.pipEligible() && customView != null) pip.rememberPipFullscreen();
        super.onPause();
        pip.activityVisible = false;
        if (current != null && (pip.pipEntering || isInPictureInPictureMode()))
            current.web.evaluateJavascript(Scripts.R("window.__lasurPipBackground=true;"), null);
        saveTabs();
        store.flush();
        store.p.edit().putLong("blockedTotal", AdBlocker.totalBlocked.get()).apply();
        CookieManager.getInstance().flush();
    }

    @Override protected void onDestroy() {
        downloads.abortBlobs(null);
        if (dlReceiver != null) { try { unregisterReceiver(dlReceiver); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } }
        if (pip.pipReceiver != null) { try { unregisterReceiver(pip.pipReceiver); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } }
        for (Tab t : tabs) { try { t.web.destroy(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } }
        super.onDestroy();
    }

    boolean handleIntent(Intent i) {
        if (i == null) return false;
        String a = i.getAction();
        String url = null;
        if (Intent.ACTION_VIEW.equals(a) && i.getData() != null) url = i.getData().toString();
        else if (Intent.ACTION_SEND.equals(a)) {
            String txt = i.getStringExtra(Intent.EXTRA_TEXT);
            if (txt != null) {
                Matcher m = Patterns.WEB_URL.matcher(txt);
                url = m.find() ? toUrl(m.group()) : toUrl(txt);
            }
        } else if (Intent.ACTION_WEB_SEARCH.equals(a)) {
            String q = i.getStringExtra("query");
            if (q != null) url = toUrl(q);
        }
        if (url == null) return false;
        i.setAction(null);
        newTab(url, false, true, null);
        return true;
    }

    void saveTabs() {
        JSONArray arr = new JSONArray();
        int idx = 0, n = 0;
        final ArrayList<byte[]> states = new ArrayList<>();
        try {
            for (Tab t : tabs) {
                if (t.incognito) continue;
                String u = t.pendingUrl != null ? t.pendingUrl : (t.ntp ? "" : t.web.getUrl());
                JSONObject o = new JSONObject();
                o.put("u", u == null ? "" : u);
                o.put("t", t.title);
                arr.put(o);
                // back/forward history, so "Back" still works after the app was killed
                Bundle b = t.pendingState;
                if (b == null && !t.ntp && t.web.getUrl() != null) {
                    try { Bundle x = new Bundle(); if (t.web.saveState(x) != null) b = x; } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                }
                states.add(b == null ? null : bundleBytes(b));
                if (t == current) idx = n;
                n++;
            }
        } catch (Exception e) { Log.w(TAG, "saveTabs", e); }
        store.p.edit().putString("tabs", arr.toString()).putInt("tabIndex", idx).apply();
        final File dir = new File(getFilesDir(), "tabstate");
        IO.execute(() -> {
            dir.mkdirs();
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            for (int i = 0; i < states.size(); i++) {
                byte[] d = states.get(i);
                if (d == null) continue;
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, i + ".bin"))) { o.write(d); } catch (Exception e) { Log.w(TAG, "tab state", e); }
            }
        });
    }

    static byte[] bundleBytes(Bundle b) {
        android.os.Parcel p = android.os.Parcel.obtain();
        try { b.writeToParcel(p, 0); return p.marshall(); } catch (Exception e) { return null; } finally { p.recycle(); }
    }

    Bundle bundleFrom(File f) {
        if (!f.exists() || f.length() > 4 * 1024 * 1024) return null;
        android.os.Parcel p = android.os.Parcel.obtain();
        try {
            byte[] d = java.nio.file.Files.readAllBytes(f.toPath());
            p.unmarshall(d, 0, d.length);
            p.setDataPosition(0);
            return p.readBundle(getClassLoader());
        } catch (Exception e) { return null; } finally { p.recycle(); }
    }

    void restoreTabs() {
        try {
            JSONArray arr = new JSONArray(store.p.getString("tabs", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Tab t = createTab(false, null);
                String u = o.optString("u");
                t.title = o.optString("t");
                if (!u.isEmpty()) {
                    t.pendingUrl = u; t.url = u; t.ntp = false;
                    t.pendingState = bundleFrom(new File(new File(getFilesDir(), "tabstate"), i + ".bin"));
                }
            }
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
    }

    // ------------------------------------------------------------------ UI construction
    int dp(float v) { return Ui.dp(v); }

    /** Real current window size (resources of the locale-wrapped context don't follow rotation). */
    android.graphics.Rect winBounds() {
        try {
            if (Build.VERSION.SDK_INT >= 30) return getWindowManager().getCurrentWindowMetrics().getBounds();
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(m);
            return new android.graphics.Rect(0, 0, m.widthPixels, m.heightPixels);
        } catch (Exception e) {
            android.util.DisplayMetrics m = super.getResources().getDisplayMetrics();
            return new android.graphics.Rect(0, 0, m.widthPixels, m.heightPixels);
        }
    }
    int scrWpx() { return winBounds().width(); }
    int scrHpx() { return winBounds().height(); }

    void buildUi() {
        root = new FrameLayout(this);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setBackgroundColor(Ui.BG);
        mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainCol, new FrameLayout.LayoutParams(MATCH, MATCH));

        stripScroll = new HorizontalScrollView(this);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setFillViewport(true);
        stripRow = new LinearLayout(this);
        stripRow.setGravity(Gravity.BOTTOM);
        stripRow.setPaddingRelative(dp(6), dp(6), dp(6), 0);
        stripScroll.addView(stripRow, new FrameLayout.LayoutParams(WRAP, MATCH));
        stripScroll.setVisibility(View.GONE);
        mainCol.addView(stripScroll, new LinearLayout.LayoutParams(MATCH, dp(44)));

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPaddingRelative(dp(4), 0, dp(2), 0);
        mainCol.addView(toolbar, new LinearLayout.LayoutParams(MATCH, dp(56)));

        homeBtn = Ui.iconBtn(this, R.drawable.ic_home, Ui.TEXT2);
        homeBtn.setOnClickListener(v -> goHome());
        toolbar.addView(homeBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        backBtn = Ui.iconBtn(this, R.drawable.ic_back, Ui.TEXT2);
        backBtn.setOnClickListener(v -> { if (current != null && !current.ntp && current.web.canGoBack()) current.web.goBack(); else handleBack(); });
        backBtn.setVisibility(View.GONE);
        toolbar.addView(backBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));

        omniPill = new LinearLayout(this);
        omniPill.setGravity(Gravity.CENTER_VERTICAL);
        omniPill.setPaddingRelative(dp(3), 0, dp(2), 0);
        lockIcon = Ui.iconBtn(this, R.drawable.ic_search, Ui.TEXT2);
        lockIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lockIcon.setPaddingRelative(dp(15), dp(15), dp(15), dp(15));
        lockIcon.setOnClickListener(v -> menu.showSiteInfo());
        omniPill.addView(lockIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        omni = new EditText(this);
        omni.setBackground(null);
        omni.setSingleLine(true);
        omni.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        omni.setHint(L.t("Введите запрос или URL"));
        omni.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        omni.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        omni.setSelectAllOnFocus(true);
        omni.setPaddingRelative(dp(4), 0, dp(4), 0);
        omniPill.addView(omni, new LinearLayout.LayoutParams(0, MATCH, 1));
        clearBtn = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> omni.setText(""));
        omniPill.addView(clearBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        micBtn = Ui.iconBtn(this, R.drawable.ic_mic, Ui.TEXT2);
        micBtn.setOnClickListener(v -> voiceSearch());
        omniPill.addView(micBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(48), 1);
        plp.setMargins(dp(4), 0, dp(4), 0);
        toolbar.addView(omniPill, plp);
        View.OnTouchListener tabSwipe = tabSwipeListener();
        omni.setOnTouchListener(tabSwipe);
        omniPill.setOnTouchListener(tabSwipe);

        tabBtn = new FrameLayout(this);
        tabBtn.setBackground(Ui.ripple(this, true));
        tabCount = new TextView(this);
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tabCount.setTypeface(Typeface.DEFAULT_BOLD);
        tabBtn.addView(tabCount, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        tabBtn.setOnClickListener(v -> tabSwitcher.showSwitcher());
        tabBtn.setOnLongClickListener(v -> { newTab(null, current != null && current.incognito, true, null); return true; });
        toolbar.addView(tabBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));

        menuBtn = Ui.iconBtn(this, R.drawable.ic_more, Ui.TEXT2);
        menuBtn.setOnClickListener(v -> menu.showMenu());
        toolbar.addView(menuBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));

        buildFindBar();

        divider = new View(this);
        divider.setBackgroundColor(Ui.DIVIDER);
        mainCol.addView(divider, new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(0.7f))));

        content = new FrameLayout(this);
        mainCol.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1));
        webContainer = new FrameLayout(this);
        content.addView(webContainer, new FrameLayout.LayoutParams(MATCH, MATCH));
        ntpHolder = new FrameLayout(this);
        content.addView(ntpHolder, new FrameLayout.LayoutParams(MATCH, MATCH));
        suggestScroll = new ScrollView(this);
        suggestScroll.setBackgroundColor(Ui.BG);
        suggestScroll.setVisibility(View.GONE);
        suggestBox = new LinearLayout(this);
        suggestBox.setOrientation(LinearLayout.VERTICAL);
        suggestScroll.addView(suggestBox, new FrameLayout.LayoutParams(MATCH, WRAP));
        content.addView(suggestScroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        progress.setVisibility(View.GONE);
        content.addView(progress, new FrameLayout.LayoutParams(MATCH, dp(3), Gravity.TOP));
        if (store.bottomBar()) layoutBars();
        toolbar.addOnLayoutChangeListener((v, l, t2, r, b2, ol, ot, or, ob) -> { if (b2 - t2 != ob - ot) placeFloating(); });

        videoFab = new FrameLayout(this);
        videoFab.setBackground(Ui.round(Ui.ACCENT, 26));
        videoFab.setElevation(dp(6));
        LinearLayout fr = new LinearLayout(this);
        fr.setGravity(Gravity.CENTER_VERTICAL);
        fr.setPaddingRelative(dp(16), 0, dp(20), 0);
        int fabFg = Ui.dark ? 0xFF202124 : Color.WHITE;
        fr.addView(Ui.icon(this, R.drawable.ic_download, fabFg), new LinearLayout.LayoutParams(dp(24), dp(24)));
        videoLabel = Ui.medium(Ui.text(this, L.t("Видео"), 15, fabFg));
        videoLabel.setPaddingRelative(dp(8), 0, 0, 0);
        fr.addView(videoLabel);
        videoFab.addView(fr, new FrameLayout.LayoutParams(WRAP, MATCH));
        videoFab.setForeground(Ui.ripple(this, false));
        videoFab.setOnClickListener(v -> videoUi.showVideos());
        videoFab.setOnLongClickListener(v -> { if (current != null && current.video != null) settingsUi.openExternal(current.video, current); return true; });
        videoFab.setVisibility(View.GONE);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(WRAP, dp(52), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(18), dp(28));
        root.addView(videoFab, flp);

        buildPtr();
        switcher = new FrameLayout(this);
        switcher.setVisibility(View.GONE);
        switcher.setClickable(true);
        root.addView(switcher, new FrameLayout.LayoutParams(MATCH, MATCH));
        fullscreen = new FrameLayout(this);
        fullscreen.setBackgroundColor(Color.BLACK);
        fullscreen.setVisibility(View.GONE);
        root.addView(fullscreen, new FrameLayout.LayoutParams(MATCH, MATCH));

        omni.setOnFocusChangeListener((v, has) -> {
            if (has) {
                String u = current != null && !current.ntp ? current.web.getUrl() : "";
                omni.setText(u == null ? "" : u);
                omni.selectAll();
                updateOmniButtons();
                updateSuggestions("");
                suggestScroll.setVisibility(View.VISIBLE);
                videoFab.setVisibility(View.GONE);
            } else {
                updateOmniButtons();
                suggestScroll.setVisibility(View.GONE);
                updateOmniDisplay();
                videoUi.updateVideoFab();
            }
        });
        omni.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { if (omni.hasFocus()) updateSuggestions(s.toString()); updateOmniButtons(); }
        });
        omni.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                String s = omni.getText().toString();
                if (!s.trim().isEmpty()) navigate(s);
                return true;
            }
            return false;
        });
    }

    void buildFindBar() {
        findBar = new LinearLayout(this);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setPaddingRelative(dp(12), 0, dp(4), 0);
        findBar.setVisibility(View.GONE);
        findInput = new EditText(this);
        findInput.setBackground(null);
        findInput.setSingleLine(true);
        findInput.setHint(L.t("Найти на странице"));
        findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        findBar.addView(findInput, new LinearLayout.LayoutParams(0, MATCH, 1));
        findCount = Ui.text(this, "", 13, Ui.TEXT2);
        findBar.addView(findCount, new LinearLayout.LayoutParams(WRAP, WRAP));
        ImageView up = Ui.iconBtn(this, R.drawable.ic_up, Ui.TEXT2);
        ImageView down = Ui.iconBtn(this, R.drawable.ic_down, Ui.TEXT2);
        ImageView close = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
        up.setOnClickListener(v -> { if (current != null) current.web.findNext(false); });
        down.setOnClickListener(v -> { if (current != null) current.web.findNext(true); });
        close.setOnClickListener(v -> hideFind());
        findBar.addView(up, new LinearLayout.LayoutParams(dp(48), dp(48)));
        findBar.addView(down, new LinearLayout.LayoutParams(dp(48), dp(48)));
        findBar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        findInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                if (current == null) return;
                if (s.length() == 0) { current.web.clearMatches(); findCount.setText(""); }
                else current.web.findAllAsync(s.toString());
            }
        });
        findInput.setOnEditorActionListener((v, id, ev) -> { if (current != null) current.web.findNext(true); return true; });
        mainCol.addView(findBar, new LinearLayout.LayoutParams(MATCH, dp(56)));
    }

    // ------------------------------------------------------------------ tabs
    Tab newTab(String url, boolean inc, boolean select, Tab parent) {
        Tab t = createTab(inc, parent);
        if (url != null) { t.pendingUrl = url; t.url = url; t.ntp = false; }
        if (select) selectTab(t); else updateTabCount();
        return t;
    }

    Tab createTab(boolean inc, Tab parent) {
        Tab t = new Tab();
        t.incognito = inc;
        t.parent = parent;
        t.desktop = store.desktopDefault();
        t.web = newWebView(inc);
        setupWeb(t);
        int pos = parent != null && tabs.contains(parent) ? tabs.indexOf(parent) + 1 : tabs.size();
        tabs.add(pos, t);
        return t;
    }

    void selectTab(Tab t) {
        if (toolbar != null) resetBars(false);
        if (current != null && current != t) {
            captureThumb(current);
            try { current.web.evaluateJavascript(PAUSE_JS, null); current.web.onPause(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        }
        passwordsUi.hidePwBar();
        if (current != null && customView != null) hideCustomView();
        current = t;
        webContainer.removeAllViews();
        if (t.web.getParent() != null) ((ViewGroup) t.web.getParent()).removeView(t.web);
        webContainer.addView(t.web, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.web.onResume();
        t.lastUsed = System.currentTimeMillis();
        if (t.pendingState != null) {
            Bundle st = t.pendingState;
            t.pendingState = null;
            boolean ok = false;
            try { android.webkit.WebBackForwardList l = t.web.restoreState(st); ok = l != null && l.getSize() > 0; } catch (Exception e) { Log.w(TAG, "restoreState", e); }
            if (ok) {
                // some WebView versions restore the history but leave the page empty: load it then
                final String fallback = t.pendingUrl;
                final WebView w = t.web;
                t.pendingUrl = null;
                ui.postDelayed(() -> {
                    String cu = w.getUrl();
                    if (fallback != null && t.web == w && (cu == null || cu.equals("about:blank")) && w.getProgress() >= 100) w.loadUrl(fallback);
                }, 1200);
            }
        }
        if (t.pendingUrl != null) { String u = t.pendingUrl; t.pendingUrl = null; t.web.loadUrl(u); }
        trimLiveTabs();
        if (findBar.getVisibility() == View.VISIBLE) hideFind();
        refreshChrome();
    }

    void closeTab(Tab t) {
        int i = tabs.indexOf(t);
        if (i < 0) return;
        downloads.abortBlobs(t);
        tabs.remove(i);
        for (Tab o : tabs) if (o.parent == t) o.parent = null;
        if (t.web.getParent() != null) ((ViewGroup) t.web.getParent()).removeView(t.web);
        try { t.web.stopLoading(); t.web.destroy(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        if (t.incognito) {
            boolean anyInc = false;
            for (Tab o : tabs) if (o.incognito) { anyInc = true; break; }
            if (!anyInc) { wipeIncognito(); for (ArrayList<Closed> g : new ArrayList<>(closedStack)) for (Closed c : g) if (c.inc) { closedStack.remove(g); break; } }
        }
        if (t == current) {
            current = null;
            Tab next = null;
            if (t.parent != null && tabs.contains(t.parent)) next = t.parent;
            else {
                for (int k = Math.min(i, tabs.size() - 1); k >= 0; k--) if (tabs.get(k).incognito == t.incognito) { next = tabs.get(k); break; }
                if (next == null) for (Tab o : tabs) if (o.incognito == t.incognito) { next = o; break; }
                if (next == null && !tabs.isEmpty()) next = tabs.get(Math.min(i, tabs.size() - 1));
            }
            if (next == null) { next = newTab(null, false, false, null); autoTab = next; }
            selectTab(next);
        }
        updateTabCount();
        if (switcher.getVisibility() == View.VISIBLE) tabSwitcher.buildSwitcher();
    }

    // ---------------------------------------------------------------- tab memory: unload / recreate web views
    static final int MAX_LIVE_TABS = 6;

    /** Replaces the tab's WebView with a fresh, empty one (after a renderer crash or to free memory). */
    void replaceWeb(Tab t) {
        WebView old = t.web;
        if (old.getParent() != null) ((ViewGroup) old.getParent()).removeView(old);
        try { old.stopLoading(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        try { old.destroy(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        t.web = newWebView(t.incognito);
        setupWeb(t);
        t.loading = false; t.progress = 0; t.video = null; t.mediaPlaying = false; t.mediaPipEligible = false; t.injectedFor = null;
        if (t == current) {
            webContainer.removeAllViews();
            webContainer.addView(t.web, new FrameLayout.LayoutParams(MATCH, MATCH));
            t.web.onResume();
        }
    }

    boolean isLive(Tab t) { return t.web != null && t.web.getUrl() != null && t.pendingState == null && t.pendingUrl == null; }

    /** Unloads a background tab: keeps its history in memory, frees the page. */
    void discard(Tab t) {
        if (t == current || !isLive(t) || t.mediaPlaying) return;
        String u = t.web.getUrl();
        Bundle b = new Bundle();
        try { if (t.web.saveState(b) != null) t.pendingState = b; } catch (Exception e) { Log.w(TAG, "saveState", e); }
        replaceWeb(t);
        t.pendingUrl = u;
        t.url = u;
    }

    /** Keeps at most MAX_LIVE_TABS pages loaded; the least recently used ones are unloaded. */
    void trimLiveTabs() { trimLiveTabs(MAX_LIVE_TABS); }

    void trimLiveTabs(int keep) {
        ArrayList<Tab> live = new ArrayList<>();
        for (Tab o : tabs) if (o != current && isLive(o) && !o.mediaPlaying) live.add(o);
        int allowed = Math.max(0, keep - 1);
        if (live.size() <= allowed) return;
        java.util.Collections.sort(live, (a, b) -> Long.compare(a.lastUsed, b.lastUsed));
        for (int i = 0; i < live.size() - allowed; i++) discard(live.get(i));
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // UI_HIDDEN (20) and BACKGROUND (40) only mean the app left the screen: unloading tabs there made
        // every tab reload when the user came back.
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) trimLiveTabs(1);
        else if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_MODERATE) trimLiveTabs(3);
    }

    @Override public void onLowMemory() { super.onLowMemory(); trimLiveTabs(1); }

    void captureThumb(Tab t) {
        try {
            if (t.ntp || t.web.getWidth() == 0 || t.web.getParent() == null) { t.thumb = null; return; }
            int w = t.web.getWidth(), h = t.web.getHeight();
            float scale = 0.4f;
            int bh = (int) (Math.min(h, w * 1.6f) * scale);
            Bitmap bmp = Bitmap.createBitmap((int) (w * scale), Math.max(1, bh), Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bmp);
            c.scale(scale, scale);
            c.translate(-t.web.getScrollX(), -t.web.getScrollY());
            t.web.draw(c);
            t.thumb = bmp;
        } catch (Throwable e) { t.thumb = null; }
    }

    // ---------------------------------------------------------------- incognito profile
    static final String INC_PROFILE = "lasur_incognito";

    /** WebView ≥ 123 can keep incognito cookies / storage in a separate profile. */
    static boolean incProfile() {
        try { return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE); } catch (Throwable e) { return false; }
    }

    LWebView newWebView(boolean inc) {
        LWebView w = new LWebView(this);
        w.setOnScrollChangeListener((v, x, y, ox, oy) -> onWebScroll(v, y, oy));
        if (inc && incProfile()) {
            try { WebViewCompat.setProfile(w, INC_PROFILE); w.privateProfile = true; } catch (Throwable e) { Log.w(TAG, "incognito profile", e); }
        }
        return w;
    }

    final ScrollBarGesture barGesture = new ScrollBarGesture();
    boolean barsHidden;
    android.animation.ValueAnimator barAnimator;
    int barGeneration;
    long barScrollBlockedUntil;
    static final int BAR_ANIM_MS = 200;

    void onWebScroll(View v, int y, int oy) {
        if (current == null || current.web != v || !store.hideOnScroll() || customView != null
                || isInPictureInPictureMode() || findBar.getVisibility() == View.VISIBLE) return;
        if (omni.hasFocus()) { setBarsHidden(false); return; }
        long now = System.currentTimeMillis();
        Boolean hide = barGesture.scroll(y, oy, barsHidden, lastTouch != 0 && now - lastTouch < 1500,
                barAnimator != null || now < barScrollBlockedUntil, dp(8), dp(48), dp(32));
        if (hide != null) setBarsHidden(hide);
    }

    /** Translate during motion; resize the WebView once, with layout-triggered scroll events ignored. */
    void setBarsHidden(boolean hide) {
        if (barsHidden == hide) return;
        boolean wasGone = toolbar.getVisibility() == View.GONE;
        int generation = ++barGeneration;
        if (barAnimator != null) { barAnimator.cancel(); barAnimator = null; }
        barsHidden = hide;
        barGesture.reset();
        boolean bottom = store.bottomBar();
        int h = dp(56) + Math.max(1, dp(0.7f)); // getHeight() is zero after GONE
        float off = bottom ? h : -h;
        if (wasGone) {
            toolbar.setVisibility(View.VISIBLE);
            divider.setVisibility(View.VISIBLE);
            toolbar.setTranslationY(off);
            divider.setTranslationY(off);
            if (!bottom) content.setTranslationY(-h);
        }
        float startBar = toolbar.getTranslationY(), startContent = content.getTranslationY();
        float endBar = hide ? off : 0, endContent = hide && !bottom ? -h : 0;
        barScrollBlockedUntil = System.currentTimeMillis() + BAR_ANIM_MS + 150;
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0, 1);
        barAnimator = anim;
        anim.setDuration(BAR_ANIM_MS);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        anim.addUpdateListener(value -> {
            if (generation != barGeneration) return;
            float f = (float) value.getAnimatedValue();
            float translation = startBar + (endBar - startBar) * f;
            toolbar.setTranslationY(translation); divider.setTranslationY(translation);
            content.setTranslationY(startContent + (endContent - startContent) * f);
            placeFloating();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (generation != barGeneration) return;
                barAnimator = null;
                finishBars(hide);
            }
        });
        anim.start();
        placeFloating();
    }

    void finishBars(boolean hidden) {
        barsHidden = hidden;
        toolbar.setVisibility(hidden ? View.GONE : View.VISIBLE);
        divider.setVisibility(hidden ? View.GONE : View.VISIBLE);
        toolbar.setTranslationY(0); divider.setTranslationY(0); content.setTranslationY(0);
        barGesture.reset();
        barScrollBlockedUntil = System.currentTimeMillis() + 150;
        placeFloating();
    }

    /** Cancels pending callbacks as well as transforms, e.g. when switching tabs or entering PiP. */
    void resetBars(boolean hidden) {
        ++barGeneration;
        if (barAnimator != null) { barAnimator.cancel(); barAnimator = null; }
        finishBars(hidden);
    }

    /** Height of the address bar when it sits at the bottom (floating buttons and messages go above it). */
    int bottomChrome() {
        if (!store.bottomBar() || toolbar.getVisibility() != View.VISIBLE) return 0;
        return Math.max(0, (toolbar.getHeight() > 0 ? toolbar.getHeight() : dp(56)) + Math.max(1, divider.getHeight()) - Math.round(toolbar.getTranslationY()));
    }

    void placeFloating() {
        if (videoFab == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoFab.getLayoutParams();
        int want = dp(28) + bottomChrome();
        if (lp.bottomMargin != want) { lp.bottomMargin = want; videoFab.setLayoutParams(lp); }
        if (snackView != null) {
            FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) snackView.getLayoutParams();
            int margin = dp(videoFab.getVisibility() == View.VISIBLE ? 92 : pwBar != null && pwBar.getVisibility() == View.VISIBLE ? 70 : 20) + bottomChrome();
            if (sp.bottomMargin != margin) { sp.bottomMargin = margin; snackView.setLayoutParams(sp); }
        }
    }

    /** Puts the address bar above or below the page according to the setting. */
    void layoutBars() {
        boolean bottom = store.bottomBar();
        mainCol.removeView(toolbar); mainCol.removeView(findBar); mainCol.removeView(divider); mainCol.removeView(content);
        if (bottom) {
            mainCol.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1));
            mainCol.addView(divider, new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(0.7f))));
            mainCol.addView(findBar, new LinearLayout.LayoutParams(MATCH, dp(56)));
            mainCol.addView(toolbar, new LinearLayout.LayoutParams(MATCH, dp(56)));
        } else {
            mainCol.addView(toolbar, new LinearLayout.LayoutParams(MATCH, dp(56)));
            mainCol.addView(findBar, new LinearLayout.LayoutParams(MATCH, dp(56)));
            mainCol.addView(divider, new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(0.7f))));
            mainCol.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1));
        }
        progress.setLayoutParams(new FrameLayout.LayoutParams(MATCH, dp(3), bottom ? Gravity.BOTTOM : Gravity.TOP));
        resetBars(false);
    }

    CookieManager cookies(boolean inc) {
        if (inc && incProfile()) {
            try { return ProfileStore.getInstance().getOrCreateProfile(INC_PROFILE).getCookieManager(); } catch (Throwable ignored) { }
        }
        return CookieManager.getInstance();
    }

    /** Forgets everything incognito tabs stored (called on start and when the last incognito tab closes). */
    void wipeIncognito() {
        if (!incProfile()) return;
        try {
            if (ProfileStore.getInstance().deleteProfile(INC_PROFILE)) return;
            Profile p = ProfileStore.getInstance().getOrCreateProfile(INC_PROFILE);
            p.getCookieManager().removeAllCookies(null);
            p.getWebStorage().deleteAllData();
            p.getGeolocationPermissions().clearAll();
        } catch (Throwable e) { Log.w(TAG, "wipe incognito", e); }
    }

    void setupWeb(final Tab t) {
        final WebView w = t.web;
        WebSettings s = w.getSettings();
        boolean isolated = !t.incognito || ((LWebView) w).privateProfile;
        s.setBlockNetworkLoads(!isolated);
        s.setJavaScriptEnabled(isolated && store.js());
        s.setDomStorageEnabled(isolated);
        s.setDatabaseEnabled(isolated);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setGeolocationEnabled(true);
        s.setUserAgentString(t.desktop ? desktopUA : mobileUA);
        t.ua = s.getUserAgentString();
        applySiteSettings(s);
        if (t.incognito) { s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setSaveFormData(false); }
        if (isolated) {
            CookieManager cm = cookies(t.incognito);
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(w, true);
        }
        w.setBackgroundColor(Color.WHITE);
        w.addJavascriptInterface(new Bridge(t), "LumenBridge");
        // Run ahead of site visibility listeners; progress-time injection is too late on YouTube.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(w,
                    "if(window===window.top){" + Scripts.R("window.__lasurKeep=" + store.bool("pip", true) + ";") + MEDIA_JS + "}",
                    java.util.Collections.singleton("*"));
        }
        w.setWebViewClient(new Client(t));
        w.setWebChromeClient(new Chrome(t));
        w.setDownloadListener((url, ua, cd, mime, len) -> downloads.onDownload(t, url, ua, cd, mime, len));
        w.setFindListener((active, total, done) -> {
            if (t == current) findCount.setText(total == 0 ? "0/0" : (active + 1) + "/" + total);
        });
        w.setOnLongClickListener(v -> onLongPress(t));
        w.setOnTouchListener((v, e) -> handlePull(t, e));
    }

    class Bridge {
        final Tab t;
        Bridge(Tab t) { this.t = t; }
        @JavascriptInterface public void onVideo(String url, String title) {
            if (url != null && url.startsWith("http")) ui.post(() -> videoUi.addVideo(t, url, t.pageUrl, title));
        }
        @JavascriptInterface public String css(String host) { return AdBlocker.cssFor(host); }
        @JavascriptInterface public void tap(float x, float y) {
            ui.post(() -> {
                if (t.pageHost == null || !t.pageHost.endsWith("youtube.com") || t.web == null) return;
                long n = android.os.SystemClock.uptimeMillis();
                MotionEvent d = MotionEvent.obtain(n, n, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent u = MotionEvent.obtain(n, n + 30, MotionEvent.ACTION_UP, x, y, 0);
                d.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                u.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                try { t.web.dispatchTouchEvent(d); t.web.dispatchTouchEvent(u); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                d.recycle(); u.recycle();
            });
        }
        @JavascriptInterface public void media(int playing, int w, int h, int eligible) {
            ui.post(() -> { if (playing == 1 || t.mediaPlaying) t.mediaPlayAt = android.os.SystemClock.uptimeMillis(); t.mediaPlaying = playing == 1; t.mediaPipEligible = eligible == 1; if (w > 0 && h > 0) { t.mediaW = w; t.mediaH = h; } if (t == current) pip.updatePipParams(); });
        }
        @JavascriptInterface public void ptr(int v) {
            t.ptrJs = v;
            if (v == 0) ui.post(() -> { if (t == current && ptrEngaged) { ptrEngaged = false; hidePtr(); } });
        }
        @JavascriptInterface public void pwPending(String href, String u, String p) {
            ui.post(() -> {
                // The href argument comes from the page and is ignored: only the browser-known URL counts.
                String site = Passwords.site(t.web != null && t.web.getUrl() != null ? t.web.getUrl() : t.pageUrl);
                if (site == null) return;
                t.pwSite = site; t.pwUser = u == null ? "" : u.trim(); t.pwPass = p;
            });
        }
        @JavascriptInterface public void pwSubmit() { ui.post(() -> { if (t.pwPass != null) t.pwSubmitAt = System.currentTimeMillis(); }); }
        @JavascriptInterface public void pwDone() { ui.post(() -> passwordsUi.maybeOfferSave(t)); }
        @JavascriptInterface public void pwFocus(int isPass) { ui.post(() -> passwordsUi.showPwBar(t)); }
        @JavascriptInterface public void pwBlur() { ui.post(() -> { ui.removeCallbacks(pwBlurR); ui.postDelayed(pwBlurR, 300); }); }
        // Tokens are issued only after confirmation and belong to this tab and document.
        @JavascriptInterface public boolean saveBegin(String token, String mime, long size) {
            BlobSave b = downloads.ownedBlob(token, t);
            if (b == null) return false;
            synchronized (b) {
                try {
                    b.quota.begin(size);
                    b.saver = Saver.create(MainActivity.this, b.name, Downloads.mimeFor(b.name, b.mime));
                    b.touched = System.currentTimeMillis();
                    return true;
                } catch (Exception e) { downloads.abortBlob(token); return false; }
            }
        }
        @JavascriptInterface public boolean saveChunk(String token, String b64) {
            BlobSave b = downloads.ownedBlob(token, t);
            if (b == null) return false;
            synchronized (b) {
                try {
                    if (b.saver == null || b64 == null || b64.length() > 1048576) throw new java.io.IOException("Invalid chunk");
                    byte[] data = Base64.decode(b64, Base64.NO_WRAP);
                    b.quota.add(data.length);
                    b.saver.out.write(data);
                    b.touched = System.currentTimeMillis();
                    return true;
                } catch (Exception e) { downloads.abortBlob(token); return false; }
            }
        }
        @JavascriptInterface public void saveEnd(String token, int ok) {
            BlobSave b = downloads.ownedBlob(token, t);
            if (b == null) return;
            synchronized (b) {
                if (!downloads.blobSaves.remove(token, b)) return;
                try {
                    if (ok != 1 || b.saver == null || !b.quota.complete()) throw new java.io.IOException(L.t("Ошибка чтения файла"));
                    b.saver.finish();
                    ui.post(() -> toast(L.t("Сохранено в Загрузки/Lasur: ") + b.saver.name));
                } catch (Exception e) {
                    if (b.saver != null) b.saver.abort();
                    ui.post(() -> toast(L.t("Не удалось сохранить: ") + e.getMessage()));
                }
            }
        }
    }

    static boolean isVideoUrl(String url) {
        try {
            String p = Uri.parse(url).getPath();
            if (p == null) return false;
            p = p.toLowerCase();
            for (String e : VIDEO_EXT) if (p.endsWith(e)) return true;
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        return false;
    }


    class Client extends WebViewClient {
        final Tab t;
        Client(Tab t) { this.t = t; }

        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (t.incognito && !((LWebView) t.web).privateProfile) return true;
            Uri u = r.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
            String url = u.toString();
            if (scheme.equals("http") || scheme.equals("https")) {
                if (r.isForMainFrame() && AdBlocker.enabled && !r.hasGesture() && t.pageHost != null
                        && AdBlocker.shouldBlock(url, u.getHost(), t.pageHost)) {
                    final WebView vv = v;
                    snack(L.t("Рекламный переход заблокирован"), L.t("Всё равно открыть"), () -> vv.loadUrl(url));
                    return true;
                }
                return false;
            }
            if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) return false;
            final boolean gesture = r.hasGesture();
            Runnable open = () -> {
                try {
                    if (scheme.equals("intent")) {
                        Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        i.addCategory(Intent.CATEGORY_BROWSABLE);
                        i.setComponent(null);
                        i.setSelector(null);
                        try { startActivity(i); }
                        catch (ActivityNotFoundException e) {
                            String fb = i.getStringExtra("browser_fallback_url");
                            // Only web pages are acceptable as a fallback (never javascript:, file:, content:…)
                            if (fb != null && (fb.startsWith("https://") || fb.startsWith("http://"))) v.loadUrl(fb);
                            else if (i.getPackage() != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + i.getPackage())));
                        }
                    } else {
                        Intent i = new Intent(Intent.ACTION_VIEW, u);
                        i.addCategory(Intent.CATEGORY_BROWSABLE);
                        startActivity(i);
                    }
                } catch (Exception e) { toast(L.t("Нет приложения для открытия ссылки")); }
            };
            // Without a user tap a page may not throw the user into another app: ask first.
            if (gesture) open.run();
            else if (t == current) snack(L.t("Сайт хочет открыть приложение"), L.t("Открыть"), open);
            return true;
        }

        @Override public void onPageStarted(WebView v, String url, Bitmap fav) {
            if (fullscreenWebTab == t) hideCustomView();
            if (pip.pipFullscreenTab == t) { pip.pipHadFullscreen = false; pip.pipReturnFullscreen = false; pip.pipFullscreenTab = null; }
            downloads.abortBlobs(t);
            if (t == current) resetBars(false);
            postStrip();
            if (t.pwPass != null) passwordsUi.maybeOfferSave(t);
            if (t == current) passwordsUi.hidePwBar();
            t.ptrJs = -1;
            t.mediaPlaying = false; t.mediaPipEligible = false;
            t.url = url;
            t.pageUrl = url;
            try { t.pageHost = Uri.parse(url).getHost(); } catch (Exception e) { t.pageHost = null; }
            if (t.pageHost != null && t.pageHost.endsWith("youtube.com") && AdBlocker.enabled && !AdBlocker.siteAllowed(t.pageHost))
                v.evaluateJavascript(YT_JS, null);
            t.video = null;
            t.injectedFor = null;
            t.mixed = false;
            t.blocked.set(0);
            t.loading = true;
            t.favicon = null;
            if (t.popup && AdBlocker.enabled && AdBlocker.isAd(t.pageHost)) {
                t.popup = false;
                ui.post(() -> { if (t.held) discardHeld(t); else closeTab(t); toast(L.t("Рекламное окно закрыто")); });
                return;
            }
            if (t.held && !t.asked && url != null && !url.equals("about:blank")) ui.post(() -> askPopup(t, url));
            if (t == current) refreshChrome();
        }

        @Override public void onPageCommitVisible(WebView v, String url) {
            if (AdBlocker.enabled && !AdBlocker.siteAllowed(t.pageHost)) v.evaluateJavascript(COSMETIC_JS, null);
        }

        @Override public void onPageFinished(WebView v, String url) {
            t.loading = false;
            t.popup = false;
            injectScripts(t);
            passwordsUi.autoFill(t);
            if (t == current && ptrSpinning) ui.postDelayed(() -> { if (ptrSpinning) hidePtr(); }, 200);
            if (!t.incognito) store.addHistory(v.getTitle(), url);
            if (t == current) refreshChrome();
        }

        @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) {
            if (t.pwPass != null && t.pwSubmitAt > 0 && t.pageUrl != null && url != null && !url.equals(t.pageUrl)) passwordsUi.maybeOfferSave(t);
            String old = t.pageUrl;
            if (old != null && url != null && !samePath(old, url) && t.video != null) { t.video = null; if (t == current) videoUi.updateVideoFab(); }
            t.url = url;
            t.pageUrl = url;
            if (t == current) { updateOmniDisplay(); updateLock(); updateBackBtn(); }
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String host = u.getHost();
            String url = u.toString();
            if (r.isForMainFrame()) {
                // Sub-resources of the new page may arrive before onPageStarted runs on the UI thread:
                // switch the page host here so they are judged against the right site.
                t.pageHost = host;
                t.mixed = false;
                if (isVideoUrl(url)) { String page = t.pageUrl; ui.post(() -> videoUi.addVideo(t, url, page, null)); }
                return null;
            }
            if (AdBlocker.shouldBlock(url, host, t.pageHost, AdBlocker.typeOf(r))) {
                t.blocked.incrementAndGet();
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
            }
            String pu = t.pageUrl;
            if (pu != null && pu.startsWith("https:") && "http".equals(u.getScheme()) && !t.mixed) {
                t.mixed = true;
                ui.post(() -> { if (t == current) updateLock(); });
            }
            if (isVideoUrl(url)) {
                String page = t.pageUrl;
                ui.post(() -> videoUi.addVideo(t, url, page, null));
            }
            return null;
        }

        @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
            if (t != current) { h.cancel(); return; }
            dialog()
                    .setTitle(L.t("Подключение не защищено"))
                    .setMessage(L.t("Сертификат сайта недействителен. Злоумышленники могут попытаться похитить ваши данные.\n\n") + e.getUrl())
                    .setPositiveButton(L.t("Назад к безопасности"), (d, w) -> h.cancel())
                    .setNegativeButton(L.t("Всё равно перейти"), (d, w) -> {
                        try { String hh = Uri.parse(e.getUrl()).getHost(); if (hh != null) t.sslHosts.add(hh.toLowerCase(Locale.ROOT)); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                        h.proceed();
                        if (t == current) updateLock();
                    })
                    .setOnCancelListener(d -> h.cancel())
                    .show();
        }

        @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            // The renderer is usually shared by all tabs, so every tab gets this call. Keep each tab and
            // only recreate its WebView; background tabs reload when they are opened again.
            if (v != t.web) return true;
            String u = t.pageUrl != null ? t.pageUrl : t.url;
            if (customView != null && t == current) hideCustomView();
            replaceWeb(t);
            if (u != null && u.startsWith("http")) { t.pendingUrl = u; t.url = u; }
            if (t == current) {
                if (t.pendingUrl != null) { String x = t.pendingUrl; t.pendingUrl = null; t.web.loadUrl(x); }
                snack(L.t("Страница перезагружена после сбоя"), null, null);
                refreshChrome();
            }
            return true;
        }
    }

    void injectScripts(Tab t) { injectScripts(t, true); }

    /** @param force false = skip if the scripts were already injected for this URL (progress ticks). */
    void injectScripts(Tab t, boolean force) {
        WebView v = t.web;
        String cur = v.getUrl();
        String key = cur + "#" + (t.progress >= 100 ? 2 : t.progress >= 70 ? 1 : 0);
        if (!force && key.equals(t.injectedFor)) return;
        t.injectedFor = key;
        boolean ab = AdBlocker.enabled && (t.pageHost == null || !AdBlocker.siteAllowed(t.pageHost));
        if (ab) v.evaluateJavascript(COSMETIC_JS, null);
        v.evaluateJavascript(VIDEO_JS, null);
        v.evaluateJavascript(PTR_JS, null);
        v.evaluateJavascript(Scripts.R("window.__lasurKeep=" + store.bool("pip", true) + ";") + MEDIA_JS, null);
        if (store.js() && (store.bool("pwSave", true) || store.bool("pwFill", true))) v.evaluateJavascript(PW_JS, null);
        if (ab && t.pageHost != null && t.pageHost.endsWith("youtube.com")) v.evaluateJavascript(YT_JS, null);
        if (t.desktop) v.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(m)m.setAttribute('content','width=1100');})();", null);
    }

    class Chrome extends WebChromeClient {
        final Tab t;
        Chrome(Tab t) { this.t = t; }

        @Override public void onProgressChanged(WebView v, int p) {
            t.progress = p;
            if (p >= 30) injectScripts(t, false);
            if (p >= 100 && t == current && ptrSpinning) ui.postDelayed(() -> { if (ptrSpinning) hidePtr(); }, 250);
            if (t == current) {
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }
        }

        @Override public void onReceivedTitle(WebView v, String title) {
            t.title = title;
            if (!t.incognito) store.updateHistoryTitle(v.getUrl(), title);
            postStrip();
        }

        @Override public void onReceivedIcon(WebView v, Bitmap icon) { t.favicon = icon; if (!t.incognito) IconCache.offer(MainActivity.this, v.getUrl(), icon); postStrip(); }

        @Override public void onShowCustomView(View view, CustomViewCallback cb) {
            if (fullscreenWebTab != null) hideCustomView();
            if (customView != null) { cb.onCustomViewHidden(); return; }
            customView = view;
            customCb = cb;
            rememberScroll(t);
            passwordsUi.hidePwBar();
            fullscreen.addView(view, new FrameLayout.LayoutParams(MATCH, MATCH));
            fullscreen.setVisibility(View.VISIBLE);
            videoFab.setVisibility(View.GONE);
            setFullscreenBars(true);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            boolean portrait = t.mediaW > 0 && t.mediaH > t.mediaW;
            setRequestedOrientation(portrait ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            notice(R.drawable.ic_fullscreen_exit, L.t("Полноэкранный режим · «Назад» — выход"));
        }

        @Override public void onHideCustomView() {
            if (fullscreenWebTab != null) return; // this fullscreen host belongs to the browser
            if (pip.pipEntering || isInPictureInPictureMode()) pip.rememberPipFullscreen();
            hideCustomView();
        }

        @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) {
            if (msg == null || !(msg.obj instanceof WebView.WebViewTransport)) return false;
            final Tab nt = createTab(t.incognito, t);
            tabs.remove(nt);
            nt.ntp = false;
            nt.popup = true;
            nt.held = true;
            nt.opener = t;
            nt.popupGesture = gesture;
            ((WebView.WebViewTransport) msg.obj).setWebView(nt.web);
            msg.sendToTarget();
            ui.postDelayed(() -> { if (nt.held && !nt.asked) askPopup(nt, nt.web.getUrl()); }, 1500);
            return true;
        }

        @Override public void onPermissionRequest(android.webkit.PermissionRequest r) { ui.post(() -> perms.handlePermission(t, r)); }

        @Override public void onPermissionRequestCanceled(android.webkit.PermissionRequest r) {
            ui.post(() -> { if (perms.permReq == r && perms.permDialog != null) { perms.permReq = null; perms.permDialog.dismiss(); } });
        }

        @Override public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback cb) {
            ui.post(() -> perms.handleGeo(t, origin, cb));
        }

        @Override public void onCloseWindow(WebView v) { if (t.held) discardHeld(t); else closeTab(t); }

        @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = cb;
            try {
                Intent i = p.createIntent();
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                startActivityForResult(i, REQ_FILE);
            } catch (Exception e) { fileCb = null; return false; }
            return true;
        }

        @Override public Bitmap getDefaultVideoPoster() { return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); }
    }

    void hideCustomView() {
        if (customView == null) return;
        if (fullscreenWebTab != null) {
            Tab t = fullscreenWebTab;
            fullscreenWebTab = null;
            if (t.web.getParent() != null) ((android.view.ViewGroup) t.web.getParent()).removeView(t.web);
            if (t == current) webContainer.addView(t.web, new FrameLayout.LayoutParams(MATCH, MATCH));
            t.web.evaluateJavascript(FULLSCREEN_OFF_JS, null);
        }
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setFullscreenBars(false);
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
        refreshChrome();
        if (!pip.pipEntering && !isInPictureInPictureMode()) restoreScroll();
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) pip.preparePip();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_AUTH) {
            Runnable r = pendingAuth;
            pendingAuth = null;
            if (res == RESULT_OK) { authUntil = System.currentTimeMillis() + 120000; if (r != null) r.run(); }
            return;
        }
        if (req == REQ_VOICE) {
            if (res == RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) navigate(r.get(0));
            }
            return;
        }
        if (req == REQ_WALL) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                Uri u = data.getData();
                BG.execute(() -> {
                    boolean ok = Wallpaper.saveCustom(this, u);
                    ui.post(() -> {
                        if (ok) { store.p.edit().putInt("wp", Wallpaper.CUSTOM).apply(); refreshChrome(); toast(L.t("Обои установлены")); }
                        else toast(L.t("Не удалось открыть изображение"));
                    });
                });
            }
            return;
        }
        if (req == Lists.REQ_BM_IMPORT || req == Lists.REQ_BM_EXPORT) {
            if (res == RESULT_OK && data != null) lists.onBookmarkFile(req, data.getData());
            return;
        }
        if (req == REQ_FILE) {
            if (fileCb != null) fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    // ------------------------------------------------------------------ navigation & chrome
    String toUrl(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        String lo = s.toLowerCase(Locale.ROOT);
        // Pasted "javascript:" / "data:" links are a classic self-XSS trick: search for them instead of running them.
        if (lo.startsWith("javascript:") || lo.startsWith("data:")) return store.searchUrl() + Uri.encode(s);
        if (s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") || lo.startsWith("about:")) return s;
        if (!s.contains(" ") && isLocalAddress(lo)) return "http://" + s;
        if (!s.contains(" ") && (s.contains(".") && Patterns.WEB_URL.matcher(s).matches())) return "https://" + s;
        return store.searchUrl() + Uri.encode(s);
    }

    /** localhost, LAN IPs and .local/.lan names usually have no HTTPS: open them over http. */
    static boolean isLocalAddress(String s) {
        String h = s;
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(']') < colon) h = h.substring(0, colon);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true;
        if (!h.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return false;
        String[] p = h.split("\\.");
        int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
        return a == 127 || a == 10 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31) || (a == 169 && b == 254);
    }

    void navigate(String input) {
        String u = toUrl(input);
        if (u == null) return;
        if (current == null) newTab(null, false, true, null);
        if (current.ntp) current.fromNtp = true;
        current.ntp = false;
        current.web.loadUrl(u);
        unfocusOmni();
        refreshChrome();
    }

    void goHome() {
        if (current == null) return;
        current.ntp = true;
        settingsUi.silence(current);
        unfocusOmni();
        refreshChrome();
    }

    void unfocusOmni() {
        hideKb(omni);
        root.requestFocus();
        omni.clearFocus();
        root.requestFocus();
    }

    void hideKb(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    void showKb(View v) {
        v.requestFocus();
        v.postDelayed(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v, InputMethodManager.SHOW_IMPLICIT), 120);
    }

    static String displayUrl(String u) {
        if (u == null) return "";
        try {
            Uri p = Uri.parse(u);
            String h = p.getHost();
            if (h != null && ("http".equals(p.getScheme()) || "https".equals(p.getScheme()))) {
                String path = p.getPath() == null ? "" : p.getPath(), q = null;
                try {
                    if ((h.contains("google.") || h.contains("bing.com")) && path.equals("/search")) q = p.getQueryParameter("q");
                    else if ((h.contains("yandex.") || h.equals("ya.ru")) && path.startsWith("/search")) q = p.getQueryParameter("text");
                    else if (h.contains("duckduckgo.com") && (path.isEmpty() || path.equals("/"))) q = p.getQueryParameter("q");
                } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                if (q != null && !q.trim().isEmpty()) return q;
                return h.startsWith("www.") ? h.substring(4) : h;
            }
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        return u;
    }

    void refreshChrome() {
        Tab t = current;
        if (t == null) return;
        boolean inc = t.incognito;
        int tb = inc ? Ui.INC_TOOLBAR : Ui.TOOLBAR, pill = inc ? Ui.INC_PILL : Ui.PILL;
        int fg = inc ? Ui.INC_TEXT : Ui.TEXT, fg2 = inc ? Ui.INC_TEXT2 : Ui.TEXT2;
        toolbar.setBackgroundColor(tb);
        findBar.setBackgroundColor(tb);
        divider.setBackgroundColor(inc ? Ui.INC_DIVIDER : Ui.DIVIDER);
        omniPill.setBackground(Ui.round(pill, 22));
        omni.setTextColor(fg);
        omni.setHintTextColor(fg2);
        findInput.setTextColor(fg);
        findInput.setHintTextColor(fg2);
        Ui.tint(homeBtn, fg2); Ui.tint(backBtn, fg2); Ui.tint(menuBtn, fg2); Ui.tint(lockIcon, fg2); Ui.tint(clearBtn, fg2); Ui.tint(micBtn, fg2);
        tabCount.setTextColor(fg);
        tabCount.setBackground(Ui.stroke(Color.TRANSPARENT, fg, 2, 4));
        setSecure(inc || (switcher.getVisibility() == View.VISIBLE && switcherIncognito));
        int pageBg = inc ? Ui.INC_BG : Ui.BG;
        boolean bottom = store.bottomBar();
        setBarColors(stripScroll != null && stripScroll.getVisibility() == View.VISIBLE ? barsBg.top : bottom ? pageBg : tb, bottom ? tb : pageBg);
        if (customView == null) setLightBars(!inc && !Ui.dark);
        if (!t.ntp && t.silenced) { t.silenced = false; try { t.web.onResume(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } }
        if (t.ntp) {
            // rebuilding the home page (wallpaper decode, icons, shortcuts) on every chrome refresh was expensive
            String key = home.ntpKey(inc);
            if (!key.equals(home.ntpCacheKey) || ntpHolder.getChildCount() == 0) {
                ntpHolder.removeAllViews();
                ntpHolder.addView(home.buildNtp(inc));
                home.ntpCacheKey = key;
            } else if (home.ntpBlockedText != null && AdBlocker.enabled) {
                home.ntpBlockedText.setText(L.t("Заблокировано рекламы и трекеров: ") + AdBlocker.totalBlocked.get());
            }
            ntpHolder.setVisibility(View.VISIBLE);
        } else ntpHolder.setVisibility(View.GONE);
        updateOmniDisplay();
        updateLock();
        progress.setProgress(t.progress);
        progress.setVisibility(t.loading && !t.ntp && t.progress < 100 ? View.VISIBLE : View.GONE);
        updateTabCount();
        videoUi.updateVideoFab();
        updateOmniButtons();
        pip.updatePipParams();
        if (isInPictureInPictureMode()) pip.preparePip();
    }

    // ---------------------------------------------------------------- system bars (edge-to-edge, Android 15+ ready)
    WindowInsets lastInsets;
    boolean lightBars;
    final BarsDrawable barsBg = new BarsDrawable();

    /** Root background: one color behind the status bar, another behind the navigation bar / below. */
    static final class BarsDrawable extends android.graphics.drawable.Drawable {
        int top = Color.WHITE, bottom = Color.WHITE, topH;
        final android.graphics.Paint p = new android.graphics.Paint();
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            p.setColor(bottom); c.drawRect(b, p);
            if (topH > 0) { p.setColor(top); c.drawRect(b.left, b.top, b.right, b.top + topH, p); }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    void setBarColors(int top, int bottom) {
        if (barsBg.top == top && barsBg.bottom == bottom) return;
        barsBg.top = top; barsBg.bottom = bottom;
        barsBg.invalidateSelf();
    }

    static final int LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

    /** Draws behind the system bars on every Android version; the root view is padded by the insets instead. */
    void setupEdgeToEdge() {
        Window w = getWindow();
        edgeToEdgeWindow(w);
        root.setBackground(barsBg);
        root.setOnApplyWindowInsetsListener((v, ins) -> { lastInsets = ins; applyInsets(); return ins; });
        root.requestApplyInsets();
    }

    @SuppressWarnings("deprecation")
    static void edgeToEdgeWindow(Window w) {
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        else w.getDecorView().setSystemUiVisibility(LAYOUT_FLAGS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) { w.setNavigationBarContrastEnforced(false); w.setStatusBarContrastEnforced(false); }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams a = w.getAttributes();
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(a);
        }
    }

    /** Full-screen dialogs get the same treatment: content padded by the bars, bars tinted by the background. */
    void edgeToEdge(Window w, View content, int bg, boolean light) {
        edgeToEdgeWindow(w);
        w.getDecorView().setBackgroundColor(bg);
        setLightBars(w, light);
        content.setOnApplyWindowInsetsListener((v, ins) -> {
            int[] p = insetsOf(ins);
            v.setPadding(p[0], p[1], p[2], p[3]);
            return ins;
        });
    }

    /** {left, top, right, bottom} of system bars + cutout, bottom including the keyboard. */
    @SuppressWarnings("deprecation")
    static int[] insetsOf(WindowInsets ins) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = ins.getInsets(WindowInsets.Type.ime());
            return new int[]{s.left, s.top, s.right, Math.max(s.bottom, ime.bottom)};
        }
        return new int[]{ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(), ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom()};
    }

    void applyInsets() {
        if (lastInsets == null || root == null) return;
        int[] p = insetsOf(lastInsets);
        boolean full = customView != null || (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode());
        if (full) root.setPaddingRelative(0, 0, 0, 0);
        else root.setPadding(p[0], p[1], p[2], p[3]);
        int th = full ? 0 : p[1];
        if (barsBg.topH != th) { barsBg.topH = th; barsBg.invalidateSelf(); }
    }

    void setLightBars(boolean light) { lightBars = light; setLightBars(getWindow(), light); }

    @SuppressWarnings("deprecation")
    static void setLightBars(Window w, boolean light) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            int m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(light ? m : 0, m);
        } else {
            w.getDecorView().setSystemUiVisibility(LAYOUT_FLAGS | (light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        }
    }

    /** Hides the system bars for full-screen video (swipe shows them temporarily). */
    @SuppressWarnings("deprecation")
    void setFullscreenBars(boolean on) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                if (on) { c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); c.hide(WindowInsets.Type.systemBars()); }
                else c.show(WindowInsets.Type.systemBars());
            }
        } else if (on) {
            w.getDecorView().setSystemUiVisibility(LAYOUT_FLAGS | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else setLightBars(w, lightBars);
        applyInsets();
    }

    /** Incognito content must not appear in screenshots or in the recent-apps preview. */
    void setSecure(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    void updateOmniDisplay() {
        if (omni.hasFocus() || current == null) return;
        if (current.ntp) { omni.setText(""); return; }
        String u = current.web.getUrl();
        if (u == null) u = current.url;
        String d = displayUrl(u);
        String h = hostOf(u);
        if (h != null && h.startsWith("www.")) h = h.substring(4);
        if (h != null && d.equals(h)) {
            // Phishing hosts like "bank.com.login.evil.ru" must not hide the real site: the registrable domain
            // is always shown in full and highlighted; extra subdomains are dimmed and cut from the left.
            String reg = Psl.registrable(h);
            if (reg == null) reg = AdBlocker.base(h);
            String sub = h.length() > reg.length() ? h.substring(0, h.length() - reg.length()) : "";
            if (sub.length() > 18) sub = "…" + sub.substring(sub.length() - 17);
            android.text.SpannableString ss = new android.text.SpannableString(sub + reg);
            int dim = current.incognito ? Ui.INC_TEXT2 : Ui.TEXT2;
            if (!sub.isEmpty()) ss.setSpan(new android.text.style.ForegroundColorSpan(dim), 0, sub.length(), 0);
            omni.setText(ss);
        } else omni.setText(d);
        omni.setContentDescription(L.t("Адрес: ") + (h != null ? h : d));
    }

    /** 0 = no page, 1 = secure, 2 = insecure (http, accepted bad certificate or mixed content). */
    int security(Tab t) {
        if (t == null || t.ntp) return 0;
        String u = t.web.getUrl() != null ? t.web.getUrl() : t.url;
        if (u == null || u.isEmpty()) return 0;
        if (!u.startsWith("https://")) return u.startsWith("http://") ? 2 : 0;
        String h = hostOf(u);
        if (h != null && t.sslHosts.contains(h.toLowerCase(Locale.ROOT))) return 2;
        return t.mixed ? 2 : 1;
    }

    void updateLock() {
        if (current == null) return;
        int sec = security(current);
        String u = current.ntp ? null : current.web.getUrl();
        boolean badCert = sec == 2 && u != null && u.startsWith("https://") && !current.mixed;
        int fg2 = current.incognito ? Ui.INC_TEXT2 : Ui.TEXT2;
        if (sec == 0) { lockIcon.setImageResource(R.drawable.ic_search); Ui.tint(lockIcon, fg2); lockIcon.setContentDescription(L.t("Поиск")); }
        else if (sec == 1) { lockIcon.setImageResource(R.drawable.ic_lock); Ui.tint(lockIcon, fg2); lockIcon.setContentDescription(L.t("Подключение защищено")); }
        else {
            lockIcon.setImageResource(R.drawable.ic_info);
            Ui.tint(lockIcon, badCert ? (Ui.dark || current.incognito ? 0xFFF28B82 : 0xFFD93025) : fg2);
            lockIcon.setContentDescription(L.t("Подключение не защищено"));
        }
    }

    void updateTabCount() {
        if (current == null) return;
        int n = 0;
        for (Tab t : tabs) if (t.incognito == current.incognito) n++;
        tabCount.setText(n > 99 ? "99+" : String.valueOf(n));
        tabCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, n > 99 ? 9 : 12);
        tabBtn.setContentDescription(L.t("Вкладки: ") + n);
        refreshStrip();
    }

    // ------------------------------------------------------------------ tab strip (large screens)
    HorizontalScrollView stripScroll;
    LinearLayout stripRow;
    boolean stripHidden;
    final Runnable stripTask = this::refreshStrip;

    boolean stripWanted() {
        return Math.round(scrWpx() / Ui.density) >= 600 && store.bool("tabStrip", true) && !stripHidden;
    }

    void postStrip() { ui.removeCallbacks(stripTask); ui.postDelayed(stripTask, 120); }

    void refreshStrip() {
        if (stripScroll == null || current == null) return;
        if (!stripWanted()) { stripScroll.setVisibility(View.GONE); return; }
        stripScroll.setVisibility(View.VISIBLE);
        final boolean inc = current.incognito;
        int tb = inc ? Ui.INC_TOOLBAR : Ui.TOOLBAR;
        int stripBg = inc ? 0xFF111214 : Ui.dark ? (Ui.amoled ? 0xFF161616 : 0xFF131416) : 0xFFDEE1E6;
        int fg = inc ? Ui.INC_TEXT : Ui.TEXT, fg2 = inc ? Ui.INC_TEXT2 : Ui.TEXT2;
        stripScroll.setBackgroundColor(stripBg);
        setBarColors(stripBg, barsBg.bottom);
        stripRow.removeAllViews();
        ArrayList<Tab> list = new ArrayList<>();
        for (Tab t : tabs) if (t.incognito == inc) list.add(t);
        int avail = scrWpx() - dp(12 + 44);
        int w = list.isEmpty() ? dp(240) : Math.max(dp(116), Math.min(dp(240), avail / list.size()));
        View sel = null;
        for (int i = 0; i < list.size(); i++) {
            final Tab t = list.get(i);
            boolean on = t == current;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPaddingRelative(dp(12), 0, dp(2), 0);
            if (on) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(tb);
                float r = dp(12);
                g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                item.setBackground(g);
            } else item.setBackground(Ui.ripple(this, false));
            View ic;
            if (t.ntp) {
                ImageView li = inc ? Ui.icon(this, R.drawable.ic_incognito, fg2) : new ImageView(this);
                if (!inc) li.setImageResource(R.drawable.ic_logo_drop);
                li.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ic = li;
            } else if (t.favicon != null) {
                ImageView fi = new ImageView(this);
                fi.setScaleType(ImageView.ScaleType.FIT_CENTER);
                fi.setImageBitmap(t.favicon);
                ic = fi;
            } else {
                String u = t.pendingUrl != null ? t.pendingUrl : t.web.getUrl() != null ? t.web.getUrl() : t.url;
                ic = home.tileIcon(t.title != null && !t.title.isEmpty() ? t.title : String.valueOf(u), u, false, 0, 18, !t.incognito);
            }
            item.addView(ic, new LinearLayout.LayoutParams(dp(18), dp(18)));
            String title = t.ntp ? L.t("Новая вкладка") : (t.title != null && !t.title.isEmpty() ? t.title : displayUrl(t.web.getUrl() != null ? t.web.getUrl() : t.url));
            TextView tv = Ui.single(this, title, 13, on ? fg : fg2);
            tv.setPaddingRelative(dp(10), 0, dp(4), 0);
            tv.setHorizontalFadingEdgeEnabled(true);
            tv.setFadingEdgeLength(dp(16));
            tv.setEllipsize(null);
            item.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
            ImageView x = Ui.iconBtn(this, R.drawable.ic_close, fg2);
            x.setScaleType(ImageView.ScaleType.FIT_CENTER);
            x.setPaddingRelative(dp(8), dp(8), dp(8), dp(8));
            x.setOnClickListener(v -> closeTabs(one(t)));
            item.addView(x, new LinearLayout.LayoutParams(dp(32), dp(32)));
            item.setOnClickListener(v -> { if (t != current) { if (switcher.getVisibility() == View.VISIBLE) tabSwitcher.hideSwitcher(); selectTab(t); } });
            item.setOnLongClickListener(v -> { tabMenu(t); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, MATCH);
            stripRow.addView(item, lp);
            if (!on && i + 1 < list.size() && list.get(i + 1) != current) {
                View sep = new View(this);
                sep.setBackgroundColor(inc ? 0xFF3C4043 : Ui.dark ? 0xFF3C4043 : 0xFFB0B4BA);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(18));
                sl.gravity = Gravity.CENTER_VERTICAL;
                sl.topMargin = -dp(3);
                stripRow.addView(sep, sl);
            }
            if (on) sel = item;
        }
        ImageView plus = Ui.iconBtn(this, R.drawable.ic_add, fg2);
        plus.setScaleType(ImageView.ScaleType.FIT_CENTER);
        plus.setPaddingRelative(dp(9), dp(9), dp(9), dp(9));
        plus.setOnClickListener(v -> { if (switcher.getVisibility() == View.VISIBLE) tabSwitcher.hideSwitcher(); newTab(null, inc, true, null); });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(38), dp(38));
        pl.gravity = Gravity.CENTER_VERTICAL;
        pl.setMarginStart(dp(4));
        pl.bottomMargin = dp(2);
        stripRow.addView(plus, pl);
        final View fsel = sel;
        if (fsel != null) stripScroll.post(() -> {
            int l = fsel.getLeft(), r = fsel.getRight(), sx = stripScroll.getScrollX(), vw = stripScroll.getWidth();
            if (l < sx) stripScroll.smoothScrollTo(l - dp(6), 0);
            else if (r > sx + vw) stripScroll.smoothScrollTo(r - vw + dp(44), 0);
        });
    }


    // ------------------------------------------------------------------ new tab page

    // ------------------------------------------------------------------ suggestions


    // ------------------------------------------------------------------ tab switcher

    // ------------------------------------------------------------------ menu

    // ------------------------------------------------------------------ full-screen lists

    /** One list row: a day/folder header, a folder entry, or an item. */
    static final class ListRow {
        final String header, folder; final Store.Item it;
        ListRow(String header, String folder, Store.Item it) { this.header = header; this.folder = folder; this.it = it; }
    }


    // ------------------------------------------------------------------ settings


    interface BoolCb { void set(boolean v); }


    // ------------------------------------------------------------------ videos

    // ------------------------------------------------------------------ downloads

    static final class BlobSave {
        final Tab owner; final String origin, name, mime;
        final BlobQuota quota = new BlobQuota();
        Saver saver; volatile long touched = System.currentTimeMillis();
        BlobSave(Tab owner, String origin, String name, String mime) {
            this.owner = owner; this.origin = origin; this.name = name; this.mime = mime;
        }
    }


    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == SitePermissions.REQ_SITE_PERM) { Runnable a = this.perms.sitePermAfter; this.perms.sitePermAfter = null; if (a != null) a.run(); return; }
        Runnable r = pendingPerm;
        pendingPerm = null;
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_STORAGE && !ok) { toast(L.t("Нужен доступ к памяти для сохранения файлов")); return; }
        if (r != null) r.run();
    }

    // ------------------------------------------------------------------ long press
    boolean onLongPress(Tab t) {
        WebView.HitTestResult r = t.web.getHitTestResult();
        int type = r.getType();
        String extra = r.getExtra();
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) { linkMenu(extra, null, null); return true; }
        if (type == WebView.HitTestResult.IMAGE_TYPE) { linkMenu(null, null, extra); return true; }
        if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            Handler h = new Handler(Looper.getMainLooper(), msg -> {
                String href = msg.getData().getString("url");
                linkMenu(href, null, extra);
                return true;
            });
            t.web.requestFocusNodeHref(h.obtainMessage());
            return true;
        }
        return false;
    }


    // ------------------------------------------------------------------ find & back
    void showFind() {
        resetBars(false);
        toolbar.setVisibility(View.GONE);
        findBar.setVisibility(View.VISIBLE);
        findInput.setText("");
        findCount.setText("");
        showKb(findInput);
    }

    void hideFind() {
        if (current != null) current.web.clearMatches();
        findBar.setVisibility(View.GONE);
        resetBars(false);
        hideKb(findInput);
        root.requestFocus();
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { if (!handleBack()) leaveApp(); }

    /** Android 13+ (and required for predictive back with targetSdk 36): back goes through this callback. */
    void registerBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (!handleBack()) leaveApp(); });
        }
    }

    /** @return true if "Back" was handled inside the browser. */
    /** Back on the last page: the app goes away completely, without sliding into picture-in-picture. */
    void leaveApp() {
        if (Build.VERSION.SDK_INT >= 31) {
            try { setPictureInPictureParams(new PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()); } catch (Exception ex) { android.util.Log.d("Lasur", "pip", ex); }
        }
        leavingByBack = true;
        if (current != null && current.web != null) { try { current.web.evaluateJavascript(PAUSE_JS, null); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } current.mediaPlaying = false; current.mediaPipEligible = false; }
        moveTaskToBack(true);
    }

    boolean leavingByBack;

    boolean handleBack() {
        if (customView != null) { hideCustomView(); return true; }
        if (switcher.getVisibility() == View.VISIBLE) { tabSwitcher.hideSwitcher(); return true; }
        if (findBar.getVisibility() == View.VISIBLE) { hideFind(); return true; }
        if (omni.hasFocus()) { unfocusOmni(); return true; }
        Tab t = current;
        if (t != null) {
            String u = t.web.getUrl();
            if (t.ntp && u != null && !u.equals("about:blank") && t.pendingUrl == null) { t.ntp = false; refreshChrome(); return true; }
            if (!t.ntp && t.web.canGoBack()) { t.web.goBack(); return true; }
            if (!t.ntp && t.fromNtp) { t.ntp = true; t.fromNtp = false; settingsUi.silence(t); refreshChrome(); return true; }
            if (t.parent != null && tabs.contains(t.parent)) { closeTab(t); return true; }
        }
        return false;
    }

    // ================================================================== v1.2 additions
    TextView videoLabel;
    ImageView micBtn;
    FrameLayout ptr;
    ImageView ptrIcon;
    float ptrY = -1;
    LinearLayout remoteBox;
    int suggestSeq;
    boolean ntpOnWall;
    Dialog settingsDialog;
    static final int REQ_VOICE = 14, REQ_WALL = 15;

    // ---------------------------------------------------------------- video: only the latest one

    // ---------------------------------------------------------------- bottom sheets
    Dialog sheet(View content) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        SheetLayout wrap = new SheetLayout(this);
        wrap.onDismiss = () -> { Window ww = d.getWindow(); if (ww != null) ww.setWindowAnimations(0); d.dismiss(); };
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.dark ? Ui.SURFACE : Color.WHITE);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        wrap.setPaddingRelative(0, dp(10), 0, dp(14));
        View handle = new View(this);
        handle.setBackground(Ui.round(Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 2));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(36), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(10);
        wrap.addView(handle, hl);
        ScrollView sv = new ScrollView(this);
        sv.addView(content);
        wrap.scroll = sv;
        wrap.addView(sv, new LinearLayout.LayoutParams(MATCH, WRAP));
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.getDecorView().setPaddingRelative(0, 0, 0, 0);
            w.setLayout(MATCH, WRAP);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setDimAmount(0.45f);
            w.setNavigationBarColor(Ui.dark ? Ui.SURFACE : Color.WHITE);
        }
        return d;
    }

    void sheetMenu(String header, ArrayList<Object[]> items) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        if (header != null) {
            TextView h = Ui.text(this, header, 13, Ui.TEXT2);
            h.setMaxLines(2);
            h.setEllipsize(android.text.TextUtils.TruncateAt.END);
            h.setPaddingRelative(dp(24), 0, dp(24), dp(10));
            box.addView(h);
            View dv = new View(this);
            dv.setBackgroundColor(Ui.DIVIDER);
            box.addView(dv, new LinearLayout.LayoutParams(MATCH, 1));
        }
        final Dialog[] d = new Dialog[1];
        for (Object[] it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(24), 0, dp(20), 0);
            row.setBackground(Ui.ripple(this, false));
            row.addView(Ui.icon(this, (Integer) it[0], Ui.TEXT2), new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView tv = Ui.single(this, (String) it[1], 15, Ui.TEXT);
            tv.setPaddingRelative(dp(20), 0, 0, 0);
            row.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
            row.setOnClickListener(v -> { d[0].dismiss(); ((Runnable) it[2]).run(); });
            box.addView(row, new LinearLayout.LayoutParams(MATCH, dp(52)));
        }
        d[0] = sheet(box);
    }

    void linkMenu(String link, String title, String img) {
        ArrayList<Object[]> it = new ArrayList<>();
        final boolean inc = current != null && current.incognito;
        final String page = current != null ? current.web.getUrl() : null;
        if (link != null && !link.startsWith("javascript:")) {
            it.add(new Object[]{R.drawable.ic_add, L.t("Открыть в новой вкладке"), (Runnable) () -> newTab(link, inc, true, current)});
            it.add(new Object[]{R.drawable.ic_add, L.t("Открыть в фоновой вкладке"), (Runnable) () -> { Tab nt = newTab(link, inc, false, current); snack(L.t("Вкладка открыта в фоне"), L.t("Перейти"), () -> { if (tabs.contains(nt)) selectTab(nt); }); }});
            if (!inc) it.add(new Object[]{R.drawable.ic_incognito, L.t("Открыть в режиме инкогнито"), (Runnable) () -> newTab(link, true, true, null)});
            if (isVideoUrl(link)) it.add(new Object[]{R.drawable.ic_play, L.t("Смотреть во внешнем плеере"), (Runnable) () -> settingsUi.openExternal(new Tab.Video(link, page, title), current)});
            it.add(new Object[]{R.drawable.ic_copy, L.t("Копировать адрес ссылки"), (Runnable) () -> menu.copy(link)});
            it.add(new Object[]{R.drawable.ic_download, L.t("Скачать по ссылке"), (Runnable) () -> downloads.confirmDownload(link, Downloads.fileName(link, null, null), null, page, videoUi.uaOf(current), -1)});
            it.add(new Object[]{R.drawable.ic_share, L.t("Поделиться ссылкой"), (Runnable) () -> menu.share(link, title)});
        }
        if (img != null && !img.startsWith("data:")) {
            it.add(new Object[]{R.drawable.ic_wallpaper, L.t("Открыть изображение"), (Runnable) () -> newTab(img, inc, true, current)});
            it.add(new Object[]{R.drawable.ic_download, L.t("Скачать изображение"), (Runnable) () -> downloads.startDownload(img, Saver.clean(Downloads.fileName(img, null, "image/jpeg")), null, page, videoUi.uaOf(current))});
            it.add(new Object[]{R.drawable.ic_copy, L.t("Копировать адрес изображения"), (Runnable) () -> menu.copy(img)});
        } else if (img != null) {
            it.add(new Object[]{R.drawable.ic_download, L.t("Скачать изображение"), (Runnable) () -> downloads.withStorage(() -> BG.execute(() -> downloads.saveDataUrl(img, "image_" + System.currentTimeMillis() + ".png", null)))});
        }
        if (it.isEmpty()) return;
        sheetMenu(link != null ? link : img, it);
    }

    // ---------------------------------------------------------------- omnibox: suggestions & voice
    void updateOmniButtons() {
        boolean focus = omni.hasFocus();
        boolean has = focus && omni.getText().length() > 0;
        clearBtn.setVisibility(has ? View.VISIBLE : View.GONE);
        // on a web page the microphone gives its room to the address; it is back while typing / on the home page
        micBtn.setVisibility(!has && (focus || current == null || current.ntp) ? View.VISIBLE : View.GONE);
        updateBackBtn();
    }

    void updateBackBtn() {
        if (backBtn == null) return;
        boolean show = current != null && !current.ntp && !omni.hasFocus() && current.web.canGoBack();
        backBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        homeBtn.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    void updateSuggestions(String q) {
        suggestBox.removeAllViews();
        q = q.trim();
        boolean inc = current != null && current.incognito;
        suggestScroll.setBackgroundColor(inc ? Ui.INC_BG : Ui.BG);
        if (!q.isEmpty()) {
            boolean isUrl = !q.contains(" ") && q.contains(".") && Patterns.WEB_URL.matcher(q).matches();
            addSuggest(suggestBox, isUrl ? R.drawable.ic_globe : R.drawable.ic_search, q, isUrl ? L.t("Перейти") : L.t("Поиск в ") + L.t(Store.ENGINES[store.engine()]), q, false);
        } else if (current != null && !current.ntp && current.web.getUrl() != null) {
            addSuggest(suggestBox, R.drawable.ic_copy, current.title == null || current.title.isEmpty() ? L.t("Текущая страница") : current.title, L.t("Нажмите, чтобы скопировать ссылку"), null, false);
        }
        remoteBox = new LinearLayout(this);
        remoteBox.setOrientation(LinearLayout.VERTICAL);
        suggestBox.addView(remoteBox);
        String lq = q.toLowerCase();
        HashSet<String> seen = new HashSet<>();
        int n = 0;
        ArrayList<Store.Item> all = new ArrayList<>(store.bookmarks);
        all.addAll(store.history);
        for (int i = 0; i < all.size() && n < 8; i++) {
            Store.Item it = all.get(i);
            if (seen.contains(it.u)) continue;
            if (lq.isEmpty() || it.t.toLowerCase().contains(lq) || it.u.toLowerCase().contains(lq)) {
                seen.add(it.u);
                addSuggest(suggestBox, i < store.bookmarks.size() ? R.drawable.ic_star : R.drawable.ic_history, it.t, it.u, it.u, false);
                n++;
            }
        }
        final int seq = ++suggestSeq;
        if (!q.isEmpty() && store.bool("suggest", true) && !inc) fetchSuggest(q, seq);
    }

    void fetchSuggest(String q, int seq) {
        int e = store.engine();
        String base = e == 2 ? "https://suggest.yandex.ru/suggest-ff.cgi?part="
                : e == 1 ? "https://duckduckgo.com/ac/?type=list&q="
                : "https://suggestqueries.google.com/complete/search?client=firefox&ie=utf-8&oe=utf-8&hl=" + L.lang + "&q=";
        ui.postDelayed(() -> {
            if (seq != suggestSeq) return;
            BG.execute(() -> {
                try {
                    JSONArray a = new JSONArray(VideoUi.httpText(base + Uri.encode(q), mobileUA, null, 64 * 1024, null)).getJSONArray(1);
                    ArrayList<String> out = new ArrayList<>();
                    for (int i = 0; i < a.length() && out.size() < 5; i++) {
                        String s = a.getString(i);
                        if (!s.equalsIgnoreCase(q)) out.add(s);
                    }
                    ui.post(() -> {
                        if (seq != suggestSeq || !omni.hasFocus() || remoteBox == null) return;
                        remoteBox.removeAllViews();
                        for (String s : out) addSuggest(remoteBox, R.drawable.ic_search, s, null, s, true);
                    });
                } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            });
        }, 160);
    }

    void addSuggest(LinearLayout parent, int icon, String title, String sub, String target, boolean insert) {
        boolean inc = current != null && current.incognito;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPaddingRelative(dp(18), dp(sub == null ? 14 : 10), dp(6), dp(sub == null ? 14 : 10));
        r.setBackground(Ui.ripple(this, false));
        if (icon == R.drawable.ic_history || icon == R.drawable.ic_star) {
            if (sub != null && sub.startsWith("http")) r.addView(home.tileIcon(title, sub, false, 0, 26), new LinearLayout.LayoutParams(dp(26), dp(26)));
            else r.addView(Ui.icon(this, icon, inc ? Ui.INC_TEXT2 : Ui.TEXT2), new LinearLayout.LayoutParams(dp(26), dp(26)));
        } else r.addView(Ui.icon(this, icon, inc ? Ui.INC_TEXT2 : Ui.TEXT2), new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPaddingRelative(dp(16), 0, 0, 0);
        tx.addView(Ui.single(this, title, 15, inc ? Ui.INC_TEXT : Ui.TEXT));
        if (sub != null) tx.addView(Ui.single(this, sub.startsWith("http") ? displayUrl(sub) : sub, 13, Ui.ACCENT));
        r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (insert) {
            ImageView ins = Ui.iconBtn(this, R.drawable.ic_up, inc ? Ui.INC_TEXT2 : Ui.TEXT2);
            ins.setRotation(-45);
            ins.setOnClickListener(v -> { omni.setText(title + " "); omni.setSelection(omni.getText().length()); });
            r.addView(ins, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        if (target == null) r.setOnClickListener(v -> { menu.copy(current.web.getUrl()); unfocusOmni(); });
        else r.setOnClickListener(v -> navigate(target));
        parent.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    void voiceSearch() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, L.t("Говорите…"));
            startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) { toast(L.t("Голосовой ввод недоступен на этом устройстве")); }
    }

    // ---------------------------------------------------------------- downloads screen

    // ---------------------------------------------------------------- appearance: themes & wallpapers

    // ---------------------------------------------------------------- misc
    void translate(Tab t) {
        String u = t.web.getUrl();
        if (u == null || !u.startsWith("http")) return;
        String lang = Locale.getDefault().getLanguage();
        snack(L.t("Перевод страницы…"), L.t("Отменить"), () -> { if (t.web.canGoBack()) t.web.goBack(); });
        t.web.loadUrl("https://translate.google.com/translate?sl=auto&tl=" + (lang.isEmpty() ? "ru" : lang) + "&u=" + Uri.encode(u));
    }

    void applySiteSettings(WebSettings s) {
        s.setTextZoom(store.p.getInt("zoom", 100));
        boolean dk = store.bool("darkSites", false) && Ui.dark;
        try {
            if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(dk);
            else if (Build.VERSION.SDK_INT >= 29) s.setForceDark(dk ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        } catch (Throwable ignored) { }
    }

    static boolean samePath(String a, String b) {
        try {
            Uri x = Uri.parse(a), y = Uri.parse(b);
            return String.valueOf(x.getHost()).equals(String.valueOf(y.getHost())) && String.valueOf(x.getPath()).equals(String.valueOf(y.getPath()))
                    && String.valueOf(x.getQueryParameter("v")).equals(String.valueOf(y.getQueryParameter("v")));
        } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- settings

    // ================================================================== v1.3 additions
    Passwords passwords;
    LinearLayout snackView, noticeView, pwBar;
    final Runnable snackHide = this::hideSnack;
    final Runnable pwBlurR = passwordsUi::hidePwBar;
    static final class Closed { String url, title; boolean inc, ntp; int index; Bundle state; Bitmap thumb; }
    final ArrayList<ArrayList<Closed>> closedStack = new ArrayList<>();
    Tab autoTab;
    boolean switcherAnim;
    static final int REQ_AUTH = 16;
    Runnable pendingAuth;
    long authUntil, lastTouch;
    android.content.BroadcastReceiver dlReceiver;
    int fsScrollY = -1, fsScrollGeneration; String fsJsScroll; Tab fsTab; long fsExitAt;


    /** Tells the browser whether a downward drag that starts here may become pull-to-refresh. */

    /** Detects login forms: reports typed credentials, focus on login fields and provides a fill function. */

    // ---------------------------------------------------------------- snackbar & notices
    /** AlertDialog matching the browser palette (surface, corner radius, accent buttons). */
    AlertDialog.Builder dialog() { return new StyledBuilder(this); }

    static final class StyledBuilder extends AlertDialog.Builder {
        StyledBuilder(Context c) { super(c); }
        @Override public AlertDialog show() {
            AlertDialog d = super.show();
            android.view.Window w = d.getWindow();
            if (w != null) w.setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(Ui.round(Ui.MENU_SURFACE, 24), Ui.dp(16)));
            for (int b : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
                android.widget.Button bt = d.getButton(b);
                if (bt != null) bt.setTextColor(Ui.ACCENT);
            }
            return d;
        }
    }

    void toast(String s) { snack(s, null, null); }
    /** System toast: stays visible above full-screen dialogs. */
    void longToast(String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    void snack(String msg, String action, Runnable r) {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(() -> snack(msg, action, r)); return; }
        if (root == null || msg == null || isInPictureInPictureMode()) return;
        if (snackView != null) { final View old = snackView; snackView = null; old.animate().cancel(); old.setVisibility(View.GONE); ui.post(() -> root.removeView(old)); }
        ui.removeCallbacks(snackHide);
        LinearLayout s = new LinearLayout(this);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setMinimumHeight(dp(50));
        s.setPaddingRelative(dp(18), dp(6), dp(action != null ? 6 : 18), dp(6));
        s.setBackground(Ui.round(Ui.SNACK, 14));
        s.setElevation(dp(10));
        TextView tv = Ui.text(this, msg, 14, Ui.dark ? 0xFF202124 : 0xFFF1F3F4);
        tv.setMaxLines(3);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (action != null) {
            TextView a = Ui.medium(Ui.text(this, action, 14, Ui.dark ? Ui.ACCENT_LIGHT[Ui.accent] : Ui.ACCENT_DARK[Ui.accent]));
            a.setPaddingRelative(dp(14), dp(12), dp(14), dp(12));
            a.setBackground(Ui.ripple(this, true));
            a.setOnClickListener(v -> { hideSnack(); if (r != null) ui.post(r); });
            s.addView(a);
        }
        int sw = scrWpx();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(sw - dp(24), dp(560)), WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        boolean fab = videoFab != null && videoFab.getVisibility() == View.VISIBLE;
        boolean bar = pwBar != null && pwBar.getVisibility() == View.VISIBLE;
        lp.bottomMargin = dp(fab ? 92 : bar ? 70 : 20) + bottomChrome();
        root.addView(s, lp);
        snackView = s;
        s.setAlpha(0f);
        s.setTranslationY(dp(40));
        s.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        s.setOnTouchListener(new View.OnTouchListener() {   // swipe sideways to dismiss
            float x0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: x0 = e.getRawX(); ui.removeCallbacks(snackHide); return true;
                    case MotionEvent.ACTION_MOVE:
                        v.setTranslationX(e.getRawX() - x0);
                        v.setAlpha(Math.max(0.2f, 1f - Math.abs(v.getTranslationX()) / Math.max(1, v.getWidth())));
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        if (Math.abs(v.getTranslationX()) > v.getWidth() / 3f) {
                            if (snackView == v) snackView = null;
                            v.setOnTouchListener(null);
                            v.animate().translationX(Math.signum(v.getTranslationX()) * v.getWidth()).alpha(0f).setDuration(120)
                                    .withEndAction(() -> ui.post(() -> root.removeView(v))).start();
                        }
                        else { v.animate().translationX(0).alpha(1f).setDuration(150).start(); ui.postDelayed(snackHide, 2500); }
                        return true;
                }
                return false;
            }
        });
        ui.postDelayed(snackHide, action != null ? 5000 : 2800);
    }

    void hideSnack() {
        final View s = snackView;
        if (s == null) return;
        snackView = null;
        s.setOnTouchListener(null);
        s.animate().cancel();
        s.animate().alpha(0f).translationY(dp(30)).setDuration(180).withEndAction(() -> ui.post(() -> root.removeView(s))).start();
    }

    /** Small pill at the top of the screen, e.g. «Полноэкранный режим». */
    void notice(int icon, String msg) {
        if (isInPictureInPictureMode()) return;
        if (noticeView != null) root.removeView(noticeView);
        LinearLayout n = new LinearLayout(this);
        n.setGravity(Gravity.CENTER_VERTICAL);
        n.setPaddingRelative(dp(14), dp(10), dp(18), dp(10));
        n.setBackground(Ui.round(0xE6202124, 22));
        n.setElevation(dp(8));
        n.addView(Ui.icon(this, icon, Color.WHITE), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView tv = Ui.text(this, msg, 14, Color.WHITE);
        tv.setPaddingRelative(dp(10), 0, 0, 0);
        n.addView(tv);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(28);
        root.addView(n, lp);
        noticeView = n;
        n.setAlpha(0f);
        n.setTranslationY(-dp(20));
        n.animate().alpha(1f).translationY(0).setDuration(220).start();
        n.postDelayed(() -> n.animate().alpha(0f).translationY(-dp(20)).setDuration(250)
                .withEndAction(() -> { root.removeView(n); if (noticeView == n) noticeView = null; }).start(), 2800);
    }

    // ---------------------------------------------------------------- closing tabs with undo
    ArrayList<Tab> one(Tab t) { ArrayList<Tab> a = new ArrayList<>(); a.add(t); return a; }

    Closed snapshot(Tab t) {
        Closed c = new Closed();
        c.inc = t.incognito;
        c.ntp = t.ntp && t.pendingUrl == null;
        c.title = t.title;
        c.index = tabs.indexOf(t);
        c.thumb = t.thumb;
        c.url = t.pendingUrl != null ? t.pendingUrl : t.web.getUrl();
        if (c.url == null) c.url = t.url;
        if (t.pendingState != null) c.state = t.pendingState;
        else if (t.pendingUrl == null && t.web.getUrl() != null) {
            try { Bundle b = new Bundle(); if (t.web.saveState(b) != null) c.state = b; } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        }
        return c;
    }

    void closeTabs(ArrayList<Tab> list) {
        if (list.isEmpty()) return;
        ArrayList<Closed> group = new ArrayList<>();
        for (Tab t : list) group.add(snapshot(t));
        java.util.Collections.sort(group, (a, b) -> Integer.compare(a.index, b.index));
        for (Tab t : new ArrayList<>(list)) closeTab(t);
        closedStack.add(group);
        if (closedStack.size() > 15) closedStack.remove(0);
        String msg = list.size() == 1 ? (list.get(0).incognito ? L.t("Вкладка инкогнито закрыта") : L.t("Вкладка закрыта")) : L.t("Закрыто вкладок: ") + list.size();
        snack(msg, L.t("Вернуть"), () -> reopen(group));
    }

    void reopen(ArrayList<Closed> group) {
        if (!closedStack.remove(group) || group.isEmpty()) return;
        Tab last = null;
        for (Closed c : group) {
            Tab t = createTab(c.inc, null);
            tabs.remove(t);
            tabs.add(Math.max(0, Math.min(c.index, tabs.size())), t);
            t.title = c.title == null ? "" : c.title;
            t.thumb = c.thumb;
            if (!c.ntp && c.url != null && !c.url.isEmpty() && !c.url.equals("about:blank")) {
                t.ntp = false;
                t.url = c.url;
                boolean ok = false;
                if (c.state != null) { try { ok = t.web.restoreState(c.state) != null; } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } }
                if (!ok) t.pendingUrl = c.url;
            }
            last = t;
        }
        Tab auto = autoTab;
        autoTab = null;
        boolean sw = switcher.getVisibility() == View.VISIBLE;
        if (!sw) selectTab(last);
        if (auto != null && auto != last && tabs.contains(auto) && auto.ntp && !auto.web.canGoBack() && tabs.size() > 1) {
            if (auto == current) selectTab(last);
            tabs.remove(auto);
            try { auto.web.destroy(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        }
        if (sw) { switcherIncognito = last.incognito; tabSwitcher.buildSwitcher(); }
        updateTabCount();
        snack(group.size() == 1 ? L.t("Вкладка восстановлена") : L.t("Восстановлено вкладок: ") + group.size(), null, null);
    }

    void tabMenu(Tab t) {
        ArrayList<Object[]> m = new ArrayList<>();
        final String u = t.ntp ? null : (t.pendingUrl != null ? t.pendingUrl : t.web.getUrl() != null ? t.web.getUrl() : t.url);
        m.add(new Object[]{R.drawable.ic_close, L.t("Закрыть вкладку"), (Runnable) () -> closeTabs(one(t))});
        ArrayList<Tab> others = new ArrayList<>();
        for (Tab o : tabs) if (o != t && o.incognito == t.incognito) others.add(o);
        if (!others.isEmpty()) m.add(new Object[]{R.drawable.ic_close, L.t("Закрыть другие вкладки"), (Runnable) () -> closeTabs(others)});
        if (u != null && !u.isEmpty()) {
            m.add(new Object[]{R.drawable.ic_add, L.t("Дублировать вкладку"), (Runnable) () -> {
                Tab n = newTab(u, t.incognito, false, t);
                n.title = t.title; n.thumb = t.thumb;
                if (switcher.getVisibility() == View.VISIBLE) tabSwitcher.buildSwitcher();
            }});
            m.add(new Object[]{R.drawable.ic_copy, L.t("Копировать ссылку"), (Runnable) () -> menu.copy(u)});
            m.add(new Object[]{R.drawable.ic_share, L.t("Поделиться"), (Runnable) () -> menu.share(u, t.title)});
        }
        sheetMenu(t.ntp ? L.t("Новая вкладка") : (t.title == null || t.title.isEmpty() ? displayUrl(u) : t.title), m);
    }

    static String hostOf(String u) {
        try { String h = Uri.parse(u).getHost(); if (h != null) return h.startsWith("www.") ? h.substring(4) : h; } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        return u == null ? "" : u;
    }

    // ---------------------------------------------------------------- pull to refresh
    boolean ptrArmed, ptrEngaged, ptrReady, ptrSpinning;
    float ptrX0, ptrY0, ptrBase;
    android.animation.ObjectAnimator ptrSpin;

    void buildPtr() {
        ptr = new FrameLayout(this);
        ptr.setBackground(Ui.oval(Ui.PTR));
        ptr.setElevation(dp(6));
        ptrIcon = Ui.icon(this, R.drawable.ic_refresh, Ui.ACCENT);
        ptrIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ptrIcon.setPaddingRelative(dp(9), dp(9), dp(9), dp(9));
        ptr.addView(ptrIcon, new FrameLayout.LayoutParams(MATCH, MATCH));
        ptr.setVisibility(View.GONE);
        ptr.setTranslationY(-dp(60));
        content.addView(ptr, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }

    boolean handlePull(Tab t, MotionEvent e) {
        lastTouch = System.currentTimeMillis();
        if (t != current || ptr == null) return false;
        WebView w = t.web;
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ptrEngaged = false;
                t.ptrJs = -1;
                ptrArmed = store.bool("ptr", true) && !ptrSpinning && customView == null && !t.ntp && !SettingsUi.isReelsUrl(t.pageUrl)
                        && e.getPointerCount() == 1 && w.getScrollY() <= 0;
                ptrX0 = e.getRawX();
                ptrY0 = e.getRawY();
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                ptrArmed = false;
                if (ptrEngaged) { ptrEngaged = false; hidePtr(); return true; }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (ptrEngaged) { updatePull(e.getRawY() - ptrBase); return true; }
                if (!ptrArmed) return false;
                float dx = e.getRawX() - ptrX0, dy = e.getRawY() - ptrY0;
                if (e.getPointerCount() > 1 || w.getScrollY() > 0 || t.ptrJs == 0) { ptrArmed = false; return false; }
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 0.8f) { ptrArmed = false; return false; }
                if (dy < -slop / 2f) { ptrArmed = false; return false; }
                float need = t.ptrJs == 1 ? slop * 1.5f : dp(36);
                if (dy > need) {
                    ptrArmed = false;
                    ptrEngaged = true;
                    ptrReady = false;
                    ptrBase = e.getRawY();
                    MotionEvent c = MotionEvent.obtain(e);
                    c.setAction(MotionEvent.ACTION_CANCEL);
                    w.onTouchEvent(c);
                    c.recycle();
                    if (ptrSpin != null) ptrSpin.cancel();
                    ptr.animate().cancel();
                    ptr.setVisibility(View.VISIBLE);
                    ptr.bringToFront();
                    ptr.setBackground(Ui.oval(Ui.PTR));
                    Ui.tint(ptrIcon, Ui.ACCENT);
                    updatePull(0);
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
                ptrArmed = false;
                if (ptrEngaged) {
                    ptrEngaged = false;
                    if (ptrReady && t.ptrJs != 0) startRefresh(t); else hidePtr();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                ptrArmed = false;
                if (ptrEngaged) { ptrEngaged = false; hidePtr(); return true; }
                return false;
        }
        return ptrEngaged;
    }

    void updatePull(float dy) {
        float max = dp(150), trig = dp(76);
        float d = dy <= 0 ? 0 : max * (1f - (float) Math.exp(-dy / max * 1.2f));
        ptr.setTranslationY(d - dp(42));
        float f = Math.min(1f, d / trig);
        ptr.setAlpha(Math.min(1f, f * 1.5f));
        ptr.setScaleX(0.55f + 0.45f * f);
        ptr.setScaleY(0.55f + 0.45f * f);
        ptr.setRotation(d * 2.4f);
        boolean ready = d >= trig;
        if (ready != ptrReady) {
            ptrReady = ready;
            if (ready) ptr.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            ptr.setBackground(Ui.oval(ready ? Ui.ACCENT : Ui.PTR));
            Ui.tint(ptrIcon, ready ? (Ui.dark ? 0xFF202124 : Color.WHITE) : Ui.ACCENT);
        }
    }

    void startRefresh(Tab t) {
        ptrSpinning = true;
        ptr.setBackground(Ui.oval(Ui.PTR));
        Ui.tint(ptrIcon, Ui.ACCENT);
        ptr.animate().translationY(dp(20)).scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
        ptrSpin = android.animation.ObjectAnimator.ofFloat(ptr, "rotation", ptr.getRotation(), ptr.getRotation() + 360f);
        ptrSpin.setDuration(750);
        ptrSpin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        ptrSpin.setInterpolator(new android.view.animation.LinearInterpolator());
        ptrSpin.start();
        t.web.reload();
        ui.postDelayed(() -> { if (ptrSpinning) hidePtr(); }, 12000);
    }

    void hidePtr() {
        ptrSpinning = false;
        if (ptrSpin != null) { ptrSpin.cancel(); ptrSpin = null; }
        ptr.animate().cancel();
        ptr.animate().translationY(-dp(60)).alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(180)
                .withEndAction(() -> { if (!ptrEngaged && !ptrSpinning) ptr.setVisibility(View.GONE); }).start();
    }

    // ---------------------------------------------------------------- fullscreen: keep scroll position
    void rememberScroll(Tab t) {
        final int generation = ++fsScrollGeneration;
        fsTab = t;
        fsScrollY = t.web.getScrollY();
        fsJsScroll = null;
        try { t.web.evaluateJavascript(SCROLL_JS, v -> { if (generation == fsScrollGeneration && v != null && v.matches("\"[\\d.]+,[\\d.]+\"")) fsJsScroll = v.replace("\"", ""); }); }
        catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
    }

    void restoreScroll() {
        final Tab t = fsTab;
        if (t == null || fsScrollY < 0) return;
        final int generation = ++fsScrollGeneration;
        final int y = fsScrollY;
        final String js = fsJsScroll;
        fsExitAt = System.currentTimeMillis();
        for (int delay : new int[]{50, 250, 600, 1100, 1800}) {
            ui.postDelayed(() -> {
                if (generation != fsScrollGeneration || t != current || customView != null || lastTouch > fsExitAt) return;
                if (Math.abs(t.web.getScrollY() - y) <= dp(6)) return;
                if (js != null) t.web.evaluateJavascript("window.scrollTo(" + js + ")", null);
                else t.web.scrollTo(t.web.getScrollX(), y);
            }, delay);
        }
        ui.postDelayed(() -> { if (generation == fsScrollGeneration) { fsTab = null; fsScrollY = -1; } }, 2000);
    }

    // ---------------------------------------------------------------- passwords

    // ---------------------------------------------------------------- download completion notice

    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        ui.postDelayed(() -> {
            refreshStrip();
            if (switcher.getVisibility() == View.VISIBLE) tabSwitcher.buildSwitcher();
            else if (current != null && current.ntp && customView == null) refreshChrome();
        }, 150);
    }

    // ================================================================== v1.4 additions
    void savePage(Tab t) {
        if (t == null || t.ntp) { toast(L.t("Откройте страницу, чтобы скачать её")); return; }
        String u = t.web.getUrl();
        if (u == null || !(u.startsWith("http://") || u.startsWith("https://"))) { toast(L.t("Эту страницу нельзя скачать")); return; }
        String path = Uri.parse(u).getPath();
        String fn = Downloads.fileName(u, null, null);
        boolean file = path != null && path.matches(".*\\.(pdf|zip|rar|7z|apk|docx?|xlsx?|pptx?|mp3|mp4|webm|mkv|jpg|jpeg|png|gif|webp|txt|epub)$");
        if (file) { downloads.confirmDownload(u, fn, null, u, videoUi.uaOf(t), -1); return; }
        String title = t.title == null || t.title.trim().isEmpty() ? hostOf(u) : t.title.trim();
        final String name = Saver.clean(title.length() > 80 ? title.substring(0, 80) : title) + ".mht";
        final java.io.File tmp = new java.io.File(getCacheDir(), "page_" + System.currentTimeMillis() + ".mht");
        snack(L.t("Сохранение страницы…"), null, null);
        try {
            t.web.saveWebArchive(tmp.getAbsolutePath(), false, res -> {
                if (res == null || !tmp.exists()) { toast(L.t("Не удалось сохранить страницу")); return; }
                downloads.withStorage(() -> BG.execute(() -> {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(tmp)) {
                        Saver s = Saver.create(this, name, "multipart/related");
                        byte[] b = new byte[65536];
                        int n;
                        while ((n = in.read(b)) > 0) s.out.write(b, 0, n);
                        s.finish();
                        snack(L.t("Страница сохранена: ") + s.name, L.t("Загрузки"), downloads::showDownloads);
                    } catch (Exception e) { toast(L.t("Не удалось сохранить страницу: ") + e.getMessage()); }
                    finally { tmp.delete(); }
                }));
            });
        } catch (Exception e) { toast(L.t("Не удалось сохранить страницу")); }
    }

    // ---------------------------------------------------------------- ad blocker panel

    // ================================================================== v1.5 additions
    // ---------------------------------------------------------------- file names for downloads

    // ---------------------------------------------------------------- popups / new tabs: ask first
    void askPopup(Tab nt, String url) {
        if (!nt.held || nt.asked) return;
        if (url != null && (url.equals("about:blank") || url.isEmpty())) url = null;
        nt.asked = true;
        final String target = url;
        final Tab op = nt.opener;
        final String oh = op != null ? op.pageHost : null;
        if (oh != null && store.p.getStringSet("popupAllow", new HashSet<>()).contains(oh)) { admitPopup(nt); return; }
        if (!nt.popupGesture && store.blockPopups()) {
            snack(L.t("Заблокировано всплывающее окно") + (target != null ? ": " + hostOf(target) : ""), L.t("Открыть"), () -> admitPopup(nt));
            ui.postDelayed(() -> { if (nt.held) discardHeld(nt); }, 6000);
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(dp(22), dp(4), dp(22), dp(8));
        box.addView(sheetHead(R.drawable.ic_add, L.t("Открыть новую вкладку?"),
                (oh != null ? oh : L.t("Сайт")) + L.t(" хочет открыть ") + (target != null ? hostOf(target) : L.t("новую вкладку"))));
        if (target != null) {
            TextView ut = Ui.text(this, target, 12, Ui.TEXT2);
            ut.setMaxLines(2);
            ut.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ut.setPaddingRelative(0, dp(10), 0, 0);
            box.addView(ut);
        }
        android.widget.CheckBox always = new android.widget.CheckBox(this);
        always.setText(L.t("Всегда разрешать для ") + oh);
        always.setTextColor(Ui.TEXT);
        always.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
        if (oh != null && !nt.incognito) {
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(WRAP, WRAP);
            al.topMargin = dp(8);
            box.addView(always, al);
        }
        final boolean[] answered = {false};
        TextView[] b = sheetButtons(box, L.t("Не открывать"), L.t("Открыть"));
        final Dialog d = sheet(box);
        d.setOnDismissListener(x -> { if (!answered[0]) discardHeld(nt); });
        b[0].setOnClickListener(v -> { answered[0] = true; d.dismiss(); discardHeld(nt); });
        b[1].setOnClickListener(v -> {
            answered[0] = true;
            d.dismiss();
            if (always.isChecked() && oh != null) {
                java.util.Set<String> s = new HashSet<>(store.p.getStringSet("popupAllow", new HashSet<>()));
                s.add(oh);
                store.p.edit().putStringSet("popupAllow", s).apply();
            }
            admitPopup(nt);
        });
    }

    void admitPopup(Tab nt) {
        if (!nt.held) return;
        nt.held = false;
        int pos = nt.opener != null && tabs.contains(nt.opener) ? tabs.indexOf(nt.opener) + 1 : tabs.size();
        tabs.add(pos, nt);
        if (switcher.getVisibility() == View.VISIBLE) { switcher.setVisibility(View.GONE); switcher.removeAllViews(); }
        selectTab(nt);
    }

    void discardHeld(Tab nt) {
        if (!nt.held) return;
        nt.held = false;
        try { nt.web.stopLoading(); nt.web.destroy(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
    }

    // ---------------------------------------------------------------- shared sheet pieces
    View sheetHead(int icon, String title, String sub) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView bi = Ui.icon(this, icon, Ui.ON_TONAL);
        bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bi.setPaddingRelative(dp(12), dp(12), dp(12), dp(12));
        badge.addView(bi, new FrameLayout.LayoutParams(MATCH, MATCH));
        head.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPaddingRelative(dp(14), 0, 0, 0);
        tx.addView(Ui.medium(Ui.text(this, title, 18, Ui.TEXT)));
        TextView st = Ui.text(this, sub, 13, Ui.TEXT2);
        st.setMaxLines(2);
        tx.addView(st);
        head.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        return head;
    }

    TextView[] sheetButtons(LinearLayout box, String no, String yes) {
        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPaddingRelative(0, dp(16), 0, 0);
        TextView n = Ui.medium(Ui.text(this, no, 15, Ui.ACCENT));
        n.setGravity(Gravity.CENTER);
        n.setPaddingRelative(dp(16), 0, dp(16), 0);
        n.setBackground(Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 24));
        TextView y = Ui.medium(Ui.text(this, yes, 15, Ui.dark ? 0xFF202124 : Color.WHITE));
        y.setGravity(Gravity.CENTER);
        y.setBackground(Ui.round(Ui.ACCENT, 24));
        y.setForeground(Ui.ripple(this, false));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, dp(48), 1);
        nl.setMarginEnd(dp(10));
        btns.addView(n, nl);
        btns.addView(y, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(btns);
        return new TextView[]{n, y};
    }

    // ---------------------------------------------------------------- site permissions (camera, microphone, location)
    interface PermCb { void done(boolean allow); }

    // ================================================================== v1.6 additions
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(L.wrap(base)); }

    // ---------------------------------------------------------------- launch animation

    // ---------------------------------------------------------------- welcome screen (first launch)

    // ---------------------------------------------------------------- language

    // ---------------------------------------------------------------- picture-in-picture

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        pip.onUserLeaveHint();
    }

    @Override public void onPictureInPictureModeChanged(boolean in, android.content.res.Configuration c) {
        super.onPictureInPictureModeChanged(in, c);
        pip.onModeChanged(in);
    }

    @Override protected void onResume() {
        super.onResume(); pip.activityVisible = true; pip.pipExited = false; pip.pipClosed = false;
        if (current != null) current.web.evaluateJavascript(Scripts.R("window.__lasurPipBackground=false;"), null);
        leavingByBack = false;
        if (!isInPictureInPictureMode()) pip.restorePip();
        pip.updatePipParams();
        if (current != null) { Tab st = current; for (int d : new int[]{300, 1200}) ui.postDelayed(() -> { if (st == current && pip.activityVisible && st.web != null) st.web.evaluateJavascript(PIP_SYNC_JS, null); }, d); }
    }


    @Override protected void onStop() {
        super.onStop();
        if (isInPictureInPictureMode()) return;
        pip.stopBackgroundMedia();
        pip.pipExited = false;
        if (current != null && current.web != null) {
            try { current.web.onPause(); current.web.pauseTimers(); pip.timersPaused = true; } catch (Exception e) { Log.d(TAG, "pause timers", e); }
        }
    }


    @Override protected void onStart() {
        super.onStart();
        if (pip.timersPaused && current != null) {
            pip.timersPaused = false;
            try { current.web.resumeTimers(); if (!current.ntp) current.web.onResume(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        }
    }

    // ---------------------------------------------------------------- print / PDF
    void printPage(Tab t) {
        if (t == null || t.web == null || t.ntp) return;
        try {
            String name = t.title == null || t.title.trim().isEmpty() ? "Lasur" : t.title.trim();
            android.print.PrintManager pm = (android.print.PrintManager) getSystemService(Context.PRINT_SERVICE);
            pm.print(name, t.web.createPrintDocumentAdapter(name), new android.print.PrintAttributes.Builder().build());
        } catch (Exception e) { Log.d(TAG, "print", e); toast(L.t("Не удалось сохранить страницу")); }
    }

    // ---------------------------------------------------------------- swipe the address bar to switch tabs
    View.OnTouchListener tabSwipeListener() {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] start = new float[2];
        final boolean[] swiping = {false};
        return (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX(); start[1] = e.getRawY(); swiping[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (omni.hasFocus()) return false;
                    float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
                    if (!swiping[0] && Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        swiping[0] = true;
                        MotionEvent c = MotionEvent.obtain(e);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(c);
                        c.recycle();
                    }
                    if (swiping[0]) omniPill.setTranslationX(Math.max(-dp(40), Math.min(dp(40), dx / 3f)));
                    return swiping[0];
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!swiping[0]) return false;
                    swiping[0] = false;
                    omniPill.animate().translationX(0).setDuration(150).start();
                    float dx = e.getRawX() - start[0];
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dx) > dp(56)) {
                        boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                        switchAdjacentTab((dx < 0) != rtl ? 1 : -1);
                    }
                    return true;
                }
            }
            return swiping[0];
        };
    }

    /** Selects the neighbouring tab of the same kind (regular or incognito); dir is +1 for next, -1 for previous. */
    boolean switchAdjacentTab(int dir) {
        if (current == null) return false;
        ArrayList<Tab> same = new ArrayList<>();
        for (Tab o : tabs) if (o.incognito == current.incognito) same.add(o);
        int i = same.indexOf(current) + dir;
        if (i < 0 || i >= same.size()) {
            omniPill.animate().translationX(-dir * dp(10)).setDuration(80)
                    .withEndAction(() -> omniPill.animate().translationX(0).setDuration(120).start()).start();
            return false;
        }
        selectTab(same.get(i));
        webContainer.setTranslationX(dir * dp(36));
        webContainer.setAlpha(0.6f);
        webContainer.animate().translationX(0).alpha(1f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        return true;
    }

    // ---------------------------------------------------------------- crash log
    static final String CRASH_FILE = "crash.txt";

    void installCrashLog() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev instanceof CrashLogger) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashLogger(getApplicationContext(), prev));
    }

    static final class CrashLogger implements Thread.UncaughtExceptionHandler {
        final Context app; final Thread.UncaughtExceptionHandler prev;
        CrashLogger(Context app, Thread.UncaughtExceptionHandler prev) { this.app = app; this.prev = prev; }
        @Override public void uncaughtException(Thread th, Throwable ex) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                String trace = sw.toString();
                if (trace.length() > 12000) trace = trace.substring(0, 12000);
                String text = "Lasur " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                        + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                        + "WebView " + webViewVersion(app) + "\n"
                        + "Thread: " + th.getName() + "\n"
                        + "Time: " + new java.util.Date() + "\n\n" + trace;
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(app.getFilesDir(), CRASH_FILE))) {
                    o.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(th, ex);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
        }
    }

    static String webViewVersion(Context c) {
        try {
            android.content.pm.PackageInfo p = WebView.getCurrentWebViewPackage();
            return p != null ? p.packageName + " " + p.versionName : "?";
        } catch (Throwable t) { return "?"; }
    }

    void offerCrashReport() {
        java.io.File f = new java.io.File(getFilesDir(), CRASH_FILE);
        if (!f.exists() || isFinishing()) return;
        String text;
        try { text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { f.delete(); return; }
        f.delete();
        dialog().setTitle(L.t("Lasur неожиданно закрылся"))
                .setMessage(L.t("Отправить отчёт об ошибке? В нём только версии приложения, Android и WebView, модель устройства и техническое описание ошибки — без истории и адресов страниц."))
                .setPositiveButton(L.t("Отправить"), (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, L.t("Отчёт об ошибке Lasur"))
                            .putExtra(Intent.EXTRA_TEXT, text);
                    try { startActivity(Intent.createChooser(i, L.t("Отчёт об ошибке Lasur"))); } catch (Exception ex) { Log.d(TAG, "share crash", ex); }
                })
                .setNegativeButton(L.t("Не отправлять"), null)
                .show();
    }
}
