package com.lumen.browser;

import android.Manifest;
import android.app.PictureInPictureParams;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Rational;
import android.view.animation.OvershootInterpolator;
import android.widget.HorizontalScrollView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
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
    ImageView homeBtn, menuBtn, lockIcon, clearBtn;
    View divider;
    ProgressBar progress;

    View customView; WebChromeClient.CustomViewCallback customCb;
    ValueCallback<Uri[]> fileCb;
    Runnable pendingPerm;
    String mobileUA, desktopUA;
    boolean switcherIncognito;

    static final String COSMETIC_JS = "(function(){try{if(window.__lumenCss)return;window.__lumenCss=1;var c=LumenBridge.css(location.hostname);if(!c)return;"
            + "try{var s=new CSSStyleSheet();s.replaceSync(c);document.adoptedStyleSheets=document.adoptedStyleSheets.concat([s]);}"
            + "catch(e){var st=document.createElement('style');st.textContent=c;(document.head||document.documentElement).appendChild(st);}}catch(e){}})();";
    static final String VIDEO_JS = "(function(){try{if(window.__lumenScan)return;window.__lumenScan=1;"
            + "function sc(){try{var v=document.querySelectorAll('video,video source,audio source');for(var i=0;i<v.length;i++){"
            + "var s=v[i].currentSrc||v[i].src;if(s&&s.indexOf('http')==0)LumenBridge.onVideo(s,document.title);}}catch(e){}}"
            + "sc();setInterval(sc,2500);document.addEventListener('play',sc,true);document.addEventListener('loadedmetadata',sc,true);}catch(e){}})();";
    static final String YT_JS = "(function(){if(window.__lumenYt)return;window.__lumenYt=1;setInterval(function(){try{"
            + "var p=document.querySelector('.ad-showing video,.ad-interrupting video');if(p&&isFinite(p.duration)&&p.duration>0){p.muted=true;p.currentTime=p.duration;}"
            + "var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytm-skip-ad-button');if(b)b.click();"
            + "var o=document.querySelectorAll('ytd-ad-slot-renderer,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ad-slot-renderer');for(var i=0;i<o.length;i++)o[i].style.display='none';"
            + "}catch(e){}},400);})();";

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
        Ui.init(this, tm, ac);
        AdBlocker.init(this);
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
        if (b == null) showSplash(); else if (!store.bool("onboarded", false)) showWelcome();
        root.requestFocus();
        if (store.restoreTabs()) restoreTabs();
        boolean handled = handleIntent(getIntent());
        if (!handled) {
            if (tabs.isEmpty()) newTab(null, false, true, null);
            else selectTab(tabs.get(Math.max(0, Math.min(store.p.getInt("tabIndex", 0), tabs.size() - 1))));
        }
        registerDlReceiver();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i); }

    @Override protected void onPause() {
        super.onPause();
        saveTabs();
        store.p.edit().putLong("blockedTotal", AdBlocker.totalBlocked.get()).apply();
        CookieManager.getInstance().flush();
    }

    @Override protected void onDestroy() {
        if (dlReceiver != null) { try { unregisterReceiver(dlReceiver); } catch (Exception ignored) { } }
        for (Tab t : tabs) { try { t.web.destroy(); } catch (Exception ignored) { } }
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
        try {
            for (Tab t : tabs) {
                if (t.incognito) continue;
                String u = t.pendingUrl != null ? t.pendingUrl : (t.ntp ? "" : t.web.getUrl());
                JSONObject o = new JSONObject();
                o.put("u", u == null ? "" : u);
                o.put("t", t.title);
                arr.put(o);
                if (t == current) idx = n;
                n++;
            }
        } catch (Exception ignored) { }
        store.p.edit().putString("tabs", arr.toString()).putInt("tabIndex", idx).apply();
    }

    void restoreTabs() {
        try {
            JSONArray arr = new JSONArray(store.p.getString("tabs", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Tab t = createTab(false, null);
                String u = o.optString("u");
                t.title = o.optString("t");
                if (!u.isEmpty()) { t.pendingUrl = u; t.url = u; t.ntp = false; }
            }
        } catch (Exception ignored) { }
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
        stripRow.setPadding(dp(6), dp(6), dp(6), 0);
        stripScroll.addView(stripRow, new FrameLayout.LayoutParams(WRAP, MATCH));
        stripScroll.setVisibility(View.GONE);
        mainCol.addView(stripScroll, new LinearLayout.LayoutParams(MATCH, dp(44)));

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), 0, dp(2), 0);
        mainCol.addView(toolbar, new LinearLayout.LayoutParams(MATCH, dp(56)));

        homeBtn = Ui.iconBtn(this, R.drawable.ic_home, Ui.TEXT2);
        homeBtn.setOnClickListener(v -> goHome());
        toolbar.addView(homeBtn, new LinearLayout.LayoutParams(dp(44), dp(48)));

        omniPill = new LinearLayout(this);
        omniPill.setGravity(Gravity.CENTER_VERTICAL);
        omniPill.setPadding(dp(12), 0, dp(2), 0);
        lockIcon = Ui.iconBtn(this, R.drawable.ic_search, Ui.TEXT2);
        lockIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lockIcon.setOnClickListener(v -> showSiteInfo());
        omniPill.addView(lockIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        omni = new EditText(this);
        omni.setBackground(null);
        omni.setSingleLine(true);
        omni.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        omni.setHint(L.t("Введите запрос или URL"));
        omni.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        omni.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        omni.setSelectAllOnFocus(true);
        omni.setPadding(dp(10), 0, dp(4), 0);
        omniPill.addView(omni, new LinearLayout.LayoutParams(0, MATCH, 1));
        clearBtn = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> omni.setText(""));
        omniPill.addView(clearBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
        micBtn = Ui.iconBtn(this, R.drawable.ic_mic, Ui.TEXT2);
        micBtn.setOnClickListener(v -> voiceSearch());
        omniPill.addView(micBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(44), 1);
        plp.setMargins(dp(4), 0, dp(4), 0);
        toolbar.addView(omniPill, plp);

        tabBtn = new FrameLayout(this);
        tabBtn.setBackground(Ui.ripple(this, true));
        tabCount = new TextView(this);
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tabCount.setTypeface(Typeface.DEFAULT_BOLD);
        tabBtn.addView(tabCount, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        tabBtn.setOnClickListener(v -> showSwitcher());
        tabBtn.setOnLongClickListener(v -> { newTab(null, current != null && current.incognito, true, null); return true; });
        toolbar.addView(tabBtn, new LinearLayout.LayoutParams(dp(44), dp(48)));

        menuBtn = Ui.iconBtn(this, R.drawable.ic_more, Ui.TEXT2);
        menuBtn.setOnClickListener(v -> showMenu());
        toolbar.addView(menuBtn, new LinearLayout.LayoutParams(dp(40), dp(48)));

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

        videoFab = new FrameLayout(this);
        videoFab.setBackground(Ui.round(Ui.ACCENT, 26));
        videoFab.setElevation(dp(6));
        LinearLayout fr = new LinearLayout(this);
        fr.setGravity(Gravity.CENTER_VERTICAL);
        fr.setPadding(dp(16), 0, dp(20), 0);
        int fabFg = Ui.dark ? 0xFF202124 : Color.WHITE;
        fr.addView(Ui.icon(this, R.drawable.ic_download, fabFg), new LinearLayout.LayoutParams(dp(24), dp(24)));
        videoLabel = Ui.medium(Ui.text(this, L.t("Видео"), 15, fabFg));
        videoLabel.setPadding(dp(8), 0, 0, 0);
        fr.addView(videoLabel);
        videoFab.addView(fr, new FrameLayout.LayoutParams(WRAP, MATCH));
        videoFab.setForeground(Ui.ripple(this, false));
        videoFab.setOnClickListener(v -> showVideos());
        videoFab.setOnLongClickListener(v -> { if (current != null && current.video != null) openExternal(current.video, current); return true; });
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
                updateVideoFab();
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
        findBar.setPadding(dp(12), 0, dp(4), 0);
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
        findBar.addView(up, new LinearLayout.LayoutParams(dp(44), dp(48)));
        findBar.addView(down, new LinearLayout.LayoutParams(dp(44), dp(48)));
        findBar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(48)));
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
        t.web = new LWebView(this);
        setupWeb(t);
        int pos = parent != null && tabs.contains(parent) ? tabs.indexOf(parent) + 1 : tabs.size();
        tabs.add(pos, t);
        return t;
    }

    void selectTab(Tab t) {
        if (current != null && current != t) { captureThumb(current); try { current.web.evaluateJavascript(PAUSE_JS, null); } catch (Exception ignored) { } }
        hidePwBar();
        if (current != null && customView != null) hideCustomView();
        current = t;
        webContainer.removeAllViews();
        if (t.web.getParent() != null) ((ViewGroup) t.web.getParent()).removeView(t.web);
        webContainer.addView(t.web, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.web.onResume();
        if (t.pendingUrl != null) { String u = t.pendingUrl; t.pendingUrl = null; t.web.loadUrl(u); }
        if (findBar.getVisibility() == View.VISIBLE) hideFind();
        refreshChrome();
    }

    void closeTab(Tab t) {
        int i = tabs.indexOf(t);
        if (i < 0) return;
        tabs.remove(i);
        for (Tab o : tabs) if (o.parent == t) o.parent = null;
        if (t.web.getParent() != null) ((ViewGroup) t.web.getParent()).removeView(t.web);
        try { t.web.stopLoading(); t.web.destroy(); } catch (Exception ignored) { }
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
        if (switcher.getVisibility() == View.VISIBLE) buildSwitcher();
    }

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

    void setupWeb(final Tab t) {
        final WebView w = t.web;
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(store.js());
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
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
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(Color.WHITE);
        w.addJavascriptInterface(new Bridge(t), "LumenBridge");
        w.setWebViewClient(new Client(t));
        w.setWebChromeClient(new Chrome(t));
        w.setDownloadListener((url, ua, cd, mime, len) -> onDownload(t, url, ua, cd, mime, len));
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
            if (url != null && url.startsWith("http")) ui.post(() -> addVideo(t, url, t.pageUrl, title));
        }
        @JavascriptInterface public String css(String host) { return AdBlocker.cssFor(host); }
        @JavascriptInterface public void media(int playing, int w, int h) {
            ui.post(() -> { t.mediaPlaying = playing == 1; if (w > 0 && h > 0) { t.mediaW = w; t.mediaH = h; } if (t == current) updatePipParams(); });
        }
        @JavascriptInterface public void ptr(int v) {
            t.ptrJs = v;
            if (v == 0) ui.post(() -> { if (t == current && ptrEngaged) { ptrEngaged = false; hidePtr(); } });
        }
        @JavascriptInterface public void pwPending(String href, String u, String p) {
            ui.post(() -> {
                String site = Passwords.site(t.pageUrl);
                if (site == null || !site.equals(Passwords.site(href))) return;
                t.pwSite = site; t.pwUser = u == null ? "" : u.trim(); t.pwPass = p;
            });
        }
        @JavascriptInterface public void pwSubmit() { ui.post(() -> { if (t.pwPass != null) t.pwSubmitAt = System.currentTimeMillis(); }); }
        @JavascriptInterface public void pwDone() { ui.post(() -> maybeOfferSave(t)); }
        @JavascriptInterface public void pwFocus(int isPass) { ui.post(() -> showPwBar(t)); }
        @JavascriptInterface public void pwBlur() { ui.post(() -> { ui.removeCallbacks(pwBlurR); ui.postDelayed(pwBlurR, 300); }); }
        @JavascriptInterface public void saveBase64(String dataUrl, String name, String mime) {
            new Thread(() -> saveDataUrl(dataUrl, name, mime)).start();
        }
    }

    static boolean isVideoUrl(String url) {
        try {
            String p = Uri.parse(url).getPath();
            if (p == null) return false;
            p = p.toLowerCase();
            for (String e : VIDEO_EXT) if (p.endsWith(e)) return true;
        } catch (Exception ignored) { }
        return false;
    }


    class Client extends WebViewClient {
        final Tab t;
        Client(Tab t) { this.t = t; }

        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
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
            try {
                if (scheme.equals("intent")) {
                    Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    i.setComponent(null);
                    i.setSelector(null);
                    try { startActivity(i); }
                    catch (ActivityNotFoundException e) {
                        String fb = i.getStringExtra("browser_fallback_url");
                        if (fb != null) v.loadUrl(fb);
                        else if (i.getPackage() != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + i.getPackage())));
                    }
                } else {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                }
            } catch (Exception e) { toast(L.t("Нет приложения для открытия ссылки")); }
            return true;
        }

        @Override public void onPageStarted(WebView v, String url, Bitmap fav) {
            postStrip();
            if (t.pwPass != null) maybeOfferSave(t);
            if (t == current) hidePwBar();
            t.ptrJs = -1;
            t.mediaPlaying = false;
            t.url = url;
            t.pageUrl = url;
            try { t.pageHost = Uri.parse(url).getHost(); } catch (Exception e) { t.pageHost = null; }
            t.video = null;
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
            if (AdBlocker.enabled && !AdBlocker.whitelist.contains(t.pageHost)) v.evaluateJavascript(COSMETIC_JS, null);
        }

        @Override public void onPageFinished(WebView v, String url) {
            t.loading = false;
            t.popup = false;
            injectScripts(t);
            autoFill(t);
            if (t == current && ptrSpinning) ui.postDelayed(() -> { if (ptrSpinning) hidePtr(); }, 200);
            if (!t.incognito) store.addHistory(v.getTitle(), url);
            if (t == current) refreshChrome();
        }

        @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) {
            if (t.pwPass != null && t.pwSubmitAt > 0 && t.pageUrl != null && url != null && !url.equals(t.pageUrl)) maybeOfferSave(t);
            String old = t.pageUrl;
            if (old != null && url != null && !samePath(old, url) && t.video != null) { t.video = null; if (t == current) updateVideoFab(); }
            t.url = url;
            t.pageUrl = url;
            if (t == current) { updateOmniDisplay(); updateLock(); }
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String host = u.getHost();
            String url = u.toString();
            if (!r.isForMainFrame() && AdBlocker.shouldBlock(url, host, t.pageHost)) {
                t.blocked.incrementAndGet();
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
            }
            if (isVideoUrl(url)) {
                String page = t.pageUrl;
                ui.post(() -> addVideo(t, url, page, null));
            }
            return null;
        }

        @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
            if (t != current) { h.cancel(); return; }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(L.t("Подключение не защищено"))
                    .setMessage(L.t("Сертификат сайта недействителен. Злоумышленники могут попытаться похитить ваши данные.\n\n") + e.getUrl())
                    .setPositiveButton(L.t("Назад к безопасности"), (d, w) -> h.cancel())
                    .setNegativeButton(L.t("Всё равно перейти"), (d, w) -> h.proceed())
                    .setOnCancelListener(d -> h.cancel())
                    .show();
        }

        @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            String u = t.url;
            boolean wasCurrent = t == current;
            boolean inc = t.incognito;
            closeTab(t);
            if (wasCurrent && u != null && u.startsWith("http")) { newTab(u, inc, true, null); snack(L.t("Страница перезагружена после сбоя"), null, null); }
            return true;
        }
    }

    void injectScripts(Tab t) {
        WebView v = t.web;
        boolean ab = AdBlocker.enabled && (t.pageHost == null || !AdBlocker.whitelist.contains(t.pageHost));
        if (ab) v.evaluateJavascript(COSMETIC_JS, null);
        v.evaluateJavascript(VIDEO_JS, null);
        v.evaluateJavascript(PTR_JS, null);
        v.evaluateJavascript("window.__lasurKeep=" + store.bool("pip", true) + ";" + MEDIA_JS, null);
        if (store.js() && (store.bool("pwSave", true) || store.bool("pwFill", true))) v.evaluateJavascript(PW_JS, null);
        if (ab && t.pageHost != null && t.pageHost.endsWith("youtube.com")) v.evaluateJavascript(YT_JS, null);
        if (t.desktop) v.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(m)m.setAttribute('content','width=1100');})();", null);
    }

    class Chrome extends WebChromeClient {
        final Tab t;
        Chrome(Tab t) { this.t = t; }

        @Override public void onProgressChanged(WebView v, int p) {
            t.progress = p;
            if (p >= 30 && p < 100) injectScripts(t);
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
            if (customView != null) { cb.onCustomViewHidden(); return; }
            customView = view;
            customCb = cb;
            rememberScroll(t);
            hidePwBar();
            fullscreen.addView(view, new FrameLayout.LayoutParams(MATCH, MATCH));
            fullscreen.setVisibility(View.VISIBLE);
            videoFab.setVisibility(View.GONE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            notice(R.drawable.ic_fullscreen_exit, L.t("Полноэкранный режим · «Назад» — выход"));
        }

        @Override public void onHideCustomView() { hideCustomView(); }

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

        @Override public void onPermissionRequest(android.webkit.PermissionRequest r) { ui.post(() -> handlePermission(t, r)); }

        @Override public void onPermissionRequestCanceled(android.webkit.PermissionRequest r) {
            ui.post(() -> { if (permReq == r && permDialog != null) { permReq = null; permDialog.dismiss(); } });
        }

        @Override public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback cb) {
            ui.post(() -> handleGeo(t, origin, cb));
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
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
        refreshChrome();
        restoreScroll();
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) preparePip();
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
                new Thread(() -> {
                    boolean ok = Wallpaper.saveCustom(this, u);
                    ui.post(() -> {
                        if (ok) { store.p.edit().putInt("wp", Wallpaper.CUSTOM).apply(); refreshChrome(); toast(L.t("Обои установлены")); }
                        else toast(L.t("Не удалось открыть изображение"));
                    });
                }).start();
            }
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
        if (s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") || s.startsWith("about:") || s.startsWith("javascript:") || s.startsWith("data:")) return s;
        if (!s.contains(" ") && (s.startsWith("localhost") || (s.contains(".") && Patterns.WEB_URL.matcher(s).matches()))) return "https://" + s;
        return store.searchUrl() + Uri.encode(s);
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
        silence(current);
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
                } catch (Exception ignored) { }
                if (q != null && !q.trim().isEmpty()) return q;
                return h.startsWith("www.") ? h.substring(4) : h;
            }
        } catch (Exception ignored) { }
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
        divider.setBackgroundColor(inc ? 0xFF3C4043 : Ui.DIVIDER);
        omniPill.setBackground(Ui.round(pill, 22));
        omni.setTextColor(fg);
        omni.setHintTextColor(fg2);
        findInput.setTextColor(fg);
        findInput.setHintTextColor(fg2);
        Ui.tint(homeBtn, fg2); Ui.tint(menuBtn, fg2); Ui.tint(lockIcon, fg2); Ui.tint(clearBtn, fg2); Ui.tint(micBtn, fg2);
        tabCount.setTextColor(fg);
        tabCount.setBackground(Ui.stroke(Color.TRANSPARENT, fg, 2, 4));
        Window win = getWindow();
        win.setStatusBarColor(tb);
        win.setNavigationBarColor(inc ? Ui.INC_BG : Ui.BG);
        if (customView == null) {
            int flags = 0;
            if (!inc && !Ui.dark) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            win.getDecorView().setSystemUiVisibility(flags);
        }
        if (!t.ntp && t.silenced) { t.silenced = false; try { t.web.onResume(); } catch (Exception ignored) { } }
        ntpHolder.removeAllViews();
        if (t.ntp) { ntpHolder.addView(buildNtp(inc)); ntpHolder.setVisibility(View.VISIBLE); }
        else ntpHolder.setVisibility(View.GONE);
        updateOmniDisplay();
        updateLock();
        progress.setProgress(t.progress);
        progress.setVisibility(t.loading && !t.ntp && t.progress < 100 ? View.VISIBLE : View.GONE);
        updateTabCount();
        updateVideoFab();
    }

    void updateOmniDisplay() {
        if (omni.hasFocus() || current == null) return;
        if (current.ntp) omni.setText("");
        else { String u = current.web.getUrl(); omni.setText(displayUrl(u != null ? u : current.url)); }
    }

    void updateLock() {
        if (current == null) return;
        String u = current.ntp ? null : (current.web.getUrl() != null ? current.web.getUrl() : current.url);
        if (u == null || u.isEmpty()) lockIcon.setImageResource(R.drawable.ic_search);
        else if (u.startsWith("https://")) lockIcon.setImageResource(R.drawable.ic_lock);
        else lockIcon.setImageResource(R.drawable.ic_info);
    }

    void updateTabCount() {
        if (current == null) return;
        int n = 0;
        for (Tab t : tabs) if (t.incognito == current.incognito) n++;
        tabCount.setText(n > 99 ? ":)" : String.valueOf(n));
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
        getWindow().setStatusBarColor(stripBg);
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
            item.setPadding(dp(12), 0, dp(2), 0);
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
                if (!inc) li.setImageResource(R.drawable.logo);
                li.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ic = li;
            } else if (t.favicon != null) {
                ImageView fi = new ImageView(this);
                fi.setScaleType(ImageView.ScaleType.FIT_CENTER);
                fi.setImageBitmap(t.favicon);
                ic = fi;
            } else {
                String u = t.pendingUrl != null ? t.pendingUrl : t.web.getUrl() != null ? t.web.getUrl() : t.url;
                ic = tileIcon(t.title != null && !t.title.isEmpty() ? t.title : String.valueOf(u), u, false, 0, 18);
            }
            item.addView(ic, new LinearLayout.LayoutParams(dp(18), dp(18)));
            String title = t.ntp ? L.t("Новая вкладка") : (t.title != null && !t.title.isEmpty() ? t.title : displayUrl(t.web.getUrl() != null ? t.web.getUrl() : t.url));
            TextView tv = Ui.single(this, title, 13, on ? fg : fg2);
            tv.setPadding(dp(10), 0, dp(4), 0);
            tv.setHorizontalFadingEdgeEnabled(true);
            tv.setFadingEdgeLength(dp(16));
            tv.setEllipsize(null);
            item.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
            ImageView x = Ui.iconBtn(this, R.drawable.ic_close, fg2);
            x.setScaleType(ImageView.ScaleType.FIT_CENTER);
            x.setPadding(dp(8), dp(8), dp(8), dp(8));
            x.setOnClickListener(v -> closeTabs(one(t)));
            item.addView(x, new LinearLayout.LayoutParams(dp(32), dp(32)));
            item.setOnClickListener(v -> { if (t != current) { if (switcher.getVisibility() == View.VISIBLE) hideSwitcher(); selectTab(t); } });
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
        plus.setPadding(dp(9), dp(9), dp(9), dp(9));
        plus.setOnClickListener(v -> { if (switcher.getVisibility() == View.VISIBLE) hideSwitcher(); newTab(null, inc, true, null); });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(38), dp(38));
        pl.gravity = Gravity.CENTER_VERTICAL;
        pl.leftMargin = dp(4);
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
    boolean ntpEdit = false;
    static final int MAX_SHORTCUTS = 20;
    static final int[] TILE_COLORS = {0, 0xFF1A73E8, 0xFFD93025, 0xFF188038, 0xFFF9AB00, 0xFF9334E6, 0xFF007B83, 0xFFE8710A, 0xFF5F6368};

    /** Round shortcut icon: site favicon on a soft circle, or a colored letter. */
    FrameLayout tileIcon(String title, String url, boolean letter, int color, int size) {
        FrameLayout f = new FrameLayout(this);
        int bg = color != 0 ? color : (Ui.dark ? 0xFF3C4043 : 0xFFF1F3F4);
        f.setBackground(Ui.oval(bg));
        TextView lt = new TextView(this);
        String l = title == null || title.trim().isEmpty() ? "?" : title.trim().substring(0, 1).toUpperCase();
        lt.setText(l);
        lt.setGravity(Gravity.CENTER);
        lt.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.40f);
        lt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        lt.setTextColor(color != 0 ? Color.WHITE : Ui.letterColor(title == null ? "" : title));
        f.addView(lt, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (!letter && url != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int is = dp(size * 0.52f);
            f.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
            Bitmap b = IconCache.get(this, url, bm -> { iv.setImageBitmap(bm); lt.setVisibility(View.GONE); });
            if (b != null) { iv.setImageBitmap(b); lt.setVisibility(View.GONE); }
        }
        return f;
    }

    View buildNtp(boolean inc) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(inc ? Ui.INC_BG : Ui.BG);
        int scrW = scrWpx(), scrH = scrHpx();
        Bitmap wp = inc ? null : Wallpaper.get(this, store.p.getInt("wp", Wallpaper.NONE), scrW / 2, scrH / 2);
        ntpOnWall = wp != null;
        if (ntpOnWall) sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Math.max(dp(16), (scrW - dp(680)) / 2);
        col.setPadding(side, dp(40), side, dp(32));
        sv.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        if (inc) {
            FrameLayout ring = new FrameLayout(this);
            ring.setBackground(Ui.oval(0xFF3C4043));
            ImageView ic = Ui.icon(this, R.drawable.ic_incognito, Ui.INC_TEXT);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ring.addView(ic, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(96), dp(96));
            rl.topMargin = dp(24);
            col.addView(ring, rl);
            TextView h = Ui.medium(Ui.text(this, L.t("Вы в режиме инкогнито"), 24, Ui.INC_TEXT));
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, dp(24), 0, dp(16));
            col.addView(h);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(20), dp(16), dp(20), dp(16));
            card.setBackground(Ui.round(0xFF2D2E31, 16));
            String[][] pts = {{L.t("Не сохраняется"), L.t("история просмотров, кэш, данные форм")},
                    {L.t("Сохраняется"), L.t("скачанные файлы и закладки")},
                    {L.t("Закрытие"), L.t("все вкладки инкогнито закрываются кнопкой в переключателе вкладок")}};
            for (String[] p : pts) {
                TextView a = Ui.medium(Ui.text(this, p[0], 14, Ui.INC_TEXT));
                TextView b = Ui.text(this, p[1], 14, Ui.INC_TEXT2);
                b.setPadding(0, dp(2), 0, dp(12));
                card.addView(a); card.addView(b);
            }
            col.addView(card, new LinearLayout.LayoutParams(MATCH, WRAP));
            return sv;
        }

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.END);
        ImageView pal = Ui.iconBtn(this, R.drawable.ic_palette, ntpOnWall ? Color.WHITE : Ui.TEXT2);
        pal.setOnClickListener(v -> showAppearance());
        topRow.addView(pal, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams trl = new LinearLayout.LayoutParams(MATCH, WRAP);
        trl.topMargin = -dp(32);
        col.addView(topRow, trl);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, dp(28)));

        // search box
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(18), 0, dp(10), 0);
        pill.setBackground(Ui.round(Ui.dark ? (Ui.amoled ? 0xFF1F1F1F : 0xFF303134) : Color.WHITE, 28));
        pill.setElevation(dp(Ui.dark ? 0 : 3));
        pill.addView(Ui.icon(this, R.drawable.ic_search, Ui.TEXT2), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView hint = Ui.single(this, L.t("Введите запрос или URL"), 16, Ui.TEXT2);
        hint.setPadding(dp(14), 0, 0, 0);
        pill.addView(hint, new LinearLayout.LayoutParams(0, WRAP, 1));
        ImageView mic = Ui.iconBtn(this, R.drawable.ic_mic, Ui.TEXT2);
        mic.setOnClickListener(v -> voiceSearch());
        pill.addView(mic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        ImageView paste = Ui.iconBtn(this, R.drawable.ic_copy, Ui.TEXT2);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (s != null && s.toString().trim().length() > 0) { navigate(s.toString()); return; }
            }
            toast(L.t("Буфер обмена пуст"));
        });
        pill.addView(paste, new LinearLayout.LayoutParams(dp(40), dp(40)));
        pill.setOnClickListener(v -> showKb(omni));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(MATCH, dp(56));
        pl.setMargins(dp(8), dp(28), dp(8), dp(20));
        col.addView(pill, pl);

        // shortcuts card
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(4), dp(8), dp(4), dp(8));
        if (ntpEdit) grid.setBackground(Ui.stroke(ntpCard(), Ui.ACCENT, 1.5f, 20));
        if (ntpEdit) {
            LinearLayout hdr = new LinearLayout(this);
            hdr.setGravity(Gravity.CENTER_VERTICAL);
            hdr.setPadding(dp(14), 0, dp(4), dp(4));
            TextView ht = Ui.text(this, L.t("Нажмите, чтобы изменить. Удерживайте и перетащите, чтобы переместить."), 12, Ui.TEXT2);
            hdr.addView(ht, new LinearLayout.LayoutParams(0, WRAP, 1));
            TextView done = Ui.medium(Ui.text(this, L.t("Готово"), 14, Ui.ACCENT));
            done.setPadding(dp(12), dp(10), dp(12), dp(10));
            done.setBackground(Ui.ripple(this, true));
            done.setOnClickListener(v -> { ntpEdit = false; refreshChrome(); });
            hdr.addView(done);
            grid.addView(hdr);
        }
        ArrayList<Store.Item> sc = store.shortcuts;
        boolean showAdd = sc.size() < MAX_SHORTCUTS;
        int total = sc.size() + (showAdd ? 1 : 0);
        LinearLayout row = null;
        for (int i = 0; i < total; i++) {
            if (i % 4 == 0) { row = new LinearLayout(this); grid.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP)); }
            row.addView(i < sc.size() ? shortcutTile(sc.get(i), i) : addTile(), new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        if (row != null && total % 4 != 0) for (int k = total % 4; k < 4; k++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        col.addView(grid, new LinearLayout.LayoutParams(MATCH, WRAP));

        // ad-block stats
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), dp(8), dp(16), dp(8));
        chip.setBackground(Ui.round(Ui.dark ? 0xFF1E3A2B : 0xFFE6F4EA, 18));
        chip.addView(Ui.icon(this, R.drawable.ic_shield, Ui.dark ? 0xFF81C995 : 0xFF188038), new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView ct = Ui.text(this, AdBlocker.enabled ? L.t("Заблокировано рекламы и трекеров: ") + AdBlocker.totalBlocked.get() : L.t("Блокировка рекламы выключена"), 13,
                Ui.dark ? 0xFF81C995 : 0xFF137333);
        ct.setPadding(dp(8), 0, 0, 0);
        chip.addView(ct);
        chip.setOnClickListener(v -> showAdblock());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
        cl.topMargin = dp(20);
        col.addView(chip, cl);

        // recent pages
        ArrayList<Store.Item> recent = new ArrayList<>();
        for (Store.Item it : store.history) { if (recent.size() >= 4) break; recent.add(it); }
        if (!recent.isEmpty() && !ntpEdit) {
            LinearLayout rc = new LinearLayout(this);
            rc.setOrientation(LinearLayout.VERTICAL);
            rc.setPadding(0, dp(6), 0, dp(6));
            rc.setBackground(Ui.round(ntpCard(), 20));
            LinearLayout rh = new LinearLayout(this);
            rh.setGravity(Gravity.CENTER_VERTICAL);
            rh.setPadding(dp(18), dp(6), dp(4), 0);
            rh.addView(Ui.medium(Ui.text(this, L.t("Недавние"), 15, Ui.TEXT)), new LinearLayout.LayoutParams(0, WRAP, 1));
            TextView all = Ui.medium(Ui.text(this, L.t("История"), 13, Ui.ACCENT));
            all.setPadding(dp(12), dp(8), dp(12), dp(8));
            all.setBackground(Ui.ripple(this, true));
            all.setOnClickListener(v -> showHistory());
            rh.addView(all);
            rc.addView(rh);
            for (Store.Item it : recent) {
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(8), dp(16), dp(8));
                r.setBackground(Ui.ripple(this, false));
                r.addView(tileIcon(it.t, it.u, false, 0, 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(14), 0, 0, 0);
                tx.addView(Ui.single(this, it.t, 14, Ui.TEXT));
                tx.addView(Ui.single(this, displayUrl(it.u), 12, Ui.TEXT2));
                r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
                r.setOnClickListener(v -> navigate(it.u));
                r.setOnLongClickListener(v -> { linkMenu(it.u, it.t, null); return true; });
                rc.addView(r);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
            rl.topMargin = dp(20);
            col.addView(rc, rl);
        }
        return wrapWall(sv, wp);
    }

    View addTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(2), dp(10), dp(2), dp(10));
        tile.setBackground(Ui.ripple(this, false));
        FrameLayout f = new FrameLayout(this);
        f.setBackground(ntpOnWall ? Ui.stroke(0x33FFFFFF, 0xCCFFFFFF, 1.5f, 28) : Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1.5f, 28));
        f.addView(Ui.icon(this, R.drawable.ic_add, Ui.ACCENT), new FrameLayout.LayoutParams(MATCH, MATCH));
        tile.addView(f, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView lbl = Ui.single(this, L.t("Добавить"), 12, ntpOnWall ? Color.WHITE : Ui.TEXT2);
        if (ntpOnWall) lbl.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPadding(0, dp(8), 0, 0);
        tile.addView(lbl, new LinearLayout.LayoutParams(MATCH, WRAP));
        tile.setOnClickListener(v -> editShortcut(-1, true));
        return tile;
    }

    View shortcutTile(Store.Item it, int idx) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(2), dp(10), dp(2), dp(10));
        tile.setBackground(Ui.ripple(this, false));
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout icon = tileIcon(it.t, it.u, it.letter, it.c, 56);
        wrap.addView(icon, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        if (ntpEdit) {
            FrameLayout x = new FrameLayout(this);
            x.setBackground(Ui.oval(Ui.dark ? 0xFF9AA0A6 : 0xFF5F6368));
            x.setElevation(dp(2));
            x.addView(Ui.icon(this, R.drawable.ic_close, Ui.dark ? 0xFF202124 : Color.WHITE), new FrameLayout.LayoutParams(MATCH, MATCH));
            ImageView xi = (ImageView) x.getChildAt(0);
            xi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            xi.setPadding(dp(3), dp(3), dp(3), dp(3));
            x.setOnClickListener(v -> deleteShortcut(idx));
            wrap.addView(x, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END));
            icon.animate().scaleX(0.9f).scaleY(0.9f).setDuration(150).start();
        }
        tile.addView(wrap, new LinearLayout.LayoutParams(dp(64), dp(60)));
        TextView lbl = Ui.single(this, it.t, 12, ntpOnWall && !ntpEdit ? Color.WHITE : Ui.TEXT);
        if (ntpOnWall && !ntpEdit) lbl.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPadding(dp(2), dp(6), dp(2), 0);
        tile.addView(lbl, new LinearLayout.LayoutParams(MATCH, WRAP));
        tile.setOnClickListener(v -> { if (ntpEdit) editShortcut(idx, false); else navigate(it.u); });
        tile.setOnLongClickListener(v -> {
            if (ntpEdit) {
                tile.startDragAndDrop(ClipData.newPlainText("shortcut", it.u), new View.DragShadowBuilder(wrap), idx, 0);
                tile.setAlpha(0.3f);
            } else shortcutMenu(it, idx);
            return true;
        });
        tile.setOnDragListener((v, ev) -> {
            switch (ev.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return ev.getLocalState() instanceof Integer;
                case DragEvent.ACTION_DRAG_ENTERED: v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start(); return true;
                case DragEvent.ACTION_DRAG_EXITED: v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); return true;
                case DragEvent.ACTION_DROP:
                    int from = (Integer) ev.getLocalState();
                    if (from != idx && from >= 0 && from < store.shortcuts.size()) {
                        Store.Item m = store.shortcuts.remove(from);
                        store.shortcuts.add(Math.min(idx, store.shortcuts.size()), m);
                        store.saveShortcuts();
                    }
                    ui.post(this::refreshChrome);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f); v.setScaleX(1f); v.setScaleY(1f);
                    return true;
            }
            return false;
        });
        return tile;
    }

    void shortcutMenu(Store.Item it, int idx) {
        new AlertDialog.Builder(this).setTitle(it.t)
                .setItems(new String[]{L.t("Открыть в новой вкладке"), L.t("Открыть в режиме инкогнито"), L.t("Изменить ярлык"), L.t("Удалить"), L.t("Упорядочить ярлыки")}, (d, w) -> {
                    if (w == 0) newTab(it.u, false, true, null);
                    else if (w == 1) newTab(it.u, true, true, null);
                    else if (w == 2) editShortcut(idx, false);
                    else if (w == 3) deleteShortcut(idx);
                    else { ntpEdit = true; refreshChrome(); }
                }).show();
    }

    void deleteShortcut(int idx) {
        if (idx < 0 || idx >= store.shortcuts.size()) return;
        Store.Item removed = store.shortcuts.remove(idx);
        store.saveShortcuts();
        refreshChrome();
        Toast.makeText(this, L.t("Ярлык «") + removed.t + L.t("» удалён"), Toast.LENGTH_SHORT).show();
    }

    void editShortcut(int idx) { editShortcut(idx, true); }

    void editShortcut(int idx, boolean prefillCurrent) {
        final Store.Item src = idx >= 0 && idx < store.shortcuts.size() ? store.shortcuts.get(idx) : null;
        final boolean[] letter = {src != null && src.letter};
        final int[] color = {src != null ? src.c : 0};
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), 0);
        sv.addView(box);
        FrameLayout preview = new FrameLayout(this);
        LinearLayout.LayoutParams pvl = new LinearLayout.LayoutParams(dp(72), dp(72));
        pvl.gravity = Gravity.CENTER_HORIZONTAL;
        pvl.bottomMargin = dp(8);
        box.addView(preview, pvl);
        EditText name = new EditText(this);
        name.setHint(L.t("Название"));
        name.setSingleLine(true);
        EditText url = new EditText(this);
        url.setHint(L.t("Адрес сайта"));
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (src != null) { name.setText(src.t); url.setText(src.u); }
        else if (prefillCurrent && current != null && !current.ntp && current.web.getUrl() != null) { name.setText(current.title); url.setText(current.web.getUrl()); }
        box.addView(name);
        box.addView(url);

        TextView il = Ui.medium(Ui.text(this, L.t("Значок"), 13, Ui.ACCENT));
        il.setPadding(0, dp(16), 0, dp(8));
        box.addView(il);
        LinearLayout modes = new LinearLayout(this);
        TextView mSite = chip(L.t("Значок сайта")), mLetter = chip(L.t("Буква"));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(WRAP, WRAP);
        ml.rightMargin = dp(8);
        modes.addView(mSite, ml);
        modes.addView(mLetter, ml);
        box.addView(modes);

        TextView cl = Ui.medium(Ui.text(this, L.t("Цвет фона"), 13, Ui.ACCENT));
        cl.setPadding(0, dp(16), 0, dp(8));
        box.addView(cl);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout colors = new LinearLayout(this);
        hs.addView(colors);
        box.addView(hs);
        TextView refresh = Ui.medium(Ui.text(this, L.t("Обновить значок сайта"), 14, Ui.ACCENT));
        refresh.setPadding(0, dp(16), 0, dp(8));
        box.addView(refresh);

        final Runnable[] update = new Runnable[1];
        update[0] = () -> {
            preview.removeAllViews();
            String u = toUrl(url.getText().toString());
            preview.addView(tileIcon(name.getText().toString().isEmpty() ? displayUrl(u) : name.getText().toString(), u, letter[0], color[0], 72),
                    new FrameLayout.LayoutParams(MATCH, MATCH));
            styleChip(mSite, !letter[0]);
            styleChip(mLetter, letter[0]);
            colors.removeAllViews();
            for (int c : TILE_COLORS) {
                FrameLayout sw = new FrameLayout(this);
                int fill = c != 0 ? c : (Ui.dark ? 0xFF3C4043 : 0xFFF1F3F4);
                sw.setBackground(c == color[0] ? Ui.stroke(fill, Ui.TEXT, 3, 20) : Ui.stroke(fill, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 20));
                if (c == 0) {
                    TextView a = Ui.text(this, "A", 13, Ui.TEXT2);
                    a.setGravity(Gravity.CENTER);
                    sw.addView(a, new FrameLayout.LayoutParams(MATCH, MATCH));
                }
                sw.setOnClickListener(v -> { color[0] = c; update[0].run(); });
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(36), dp(36));
                sl.rightMargin = dp(10);
                colors.addView(sw, sl);
            }
        };
        mSite.setOnClickListener(v -> { letter[0] = false; update[0].run(); });
        mLetter.setOnClickListener(v -> { letter[0] = true; update[0].run(); });
        refresh.setOnClickListener(v -> {
            String u = toUrl(url.getText().toString());
            if (u != null) { IconCache.forget(this, u); letter[0] = false; update[0].run(); }
        });
        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { update[0].run(); }
        };
        update[0].run();
        name.addTextChangedListener(tw);
        url.addTextChangedListener(tw);

        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(src != null ? L.t("Изменить ярлык") : L.t("Новый ярлык")).setView(sv)
                .setPositiveButton(L.t("Сохранить"), (d, w) -> {
                    String u = toUrl(url.getText().toString());
                    if (u == null) { toast(L.t("Введите адрес сайта")); return; }
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = displayUrl(u);
                    Store.Item it = src != null ? src : new Store.Item(n, u, 0);
                    it.t = n; it.u = u; it.letter = letter[0]; it.c = color[0];
                    if (src == null) store.shortcuts.add(it);
                    store.saveShortcuts();
                    refreshChrome();
                })
                .setNegativeButton(L.t("Отмена"), null);
        if (src != null) b.setNeutralButton(L.t("Удалить"), (d, w) -> deleteShortcut(idx));
        b.show();
    }

    TextView chip(String s) {
        TextView t = Ui.medium(Ui.text(this, s, 14, Ui.TEXT));
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        return t;
    }

    void styleChip(TextView t, boolean sel) {
        if (sel) { t.setBackground(Ui.round(Ui.TONAL, 18)); t.setTextColor(Ui.ON_TONAL); }
        else { t.setBackground(Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 18)); t.setTextColor(Ui.TEXT); }
    }

    // ------------------------------------------------------------------ suggestions


    // ------------------------------------------------------------------ tab switcher
    void showSwitcher() {
        if (current != null) { captureThumb(current); switcherIncognito = current.incognito; }
        unfocusOmni();
        hidePwBar();
        switcherAnim = true;
        buildSwitcher();
        switcher.setVisibility(View.VISIBLE);
        switcher.setAlpha(0f);
        switcher.setScaleX(0.97f);
        switcher.setScaleY(0.97f);
        switcher.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();
        videoFab.setVisibility(View.GONE);
    }

    void hideSwitcher() {
        switcher.setVisibility(View.GONE);
        switcher.removeAllViews();
        refreshChrome();
    }

    void buildSwitcher() {
        switcher.removeAllViews();
        final boolean inc = switcherIncognito;
        int bg = inc ? Ui.INC_BG : Ui.SWITCHER_BG, fg = inc ? Ui.INC_TEXT : Ui.TEXT, fg2 = inc ? Ui.INC_TEXT2 : Ui.TEXT2;
        switcher.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(!inc && !Ui.dark ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        switcher.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        // top bar: back · segmented control · menu
        FrameLayout bar = new FrameLayout(this);
        ImageView back = Ui.iconBtn(this, R.drawable.ic_back, fg);
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL));
        int nNorm = 0, nInc = 0;
        for (Tab t : tabs) if (t.incognito) nInc++; else nNorm++;
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        seg.setBackground(Ui.round(inc ? 0xFF303134 : (Ui.dark ? 0xFF303134 : 0xFFE1E5EA), 22));
        int selBg = inc ? 0xFF5F6368 : (Ui.dark ? Ui.TONAL : Color.WHITE);
        FrameLayout sN = new FrameLayout(this);
        TextView cnt = new TextView(this);
        cnt.setText(String.valueOf(nNorm));
        cnt.setGravity(Gravity.CENTER);
        cnt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        cnt.setTypeface(Typeface.DEFAULT_BOLD);
        int cc = !inc ? Ui.ACCENT : fg2;
        cnt.setTextColor(cc);
        cnt.setBackground(Ui.stroke(Color.TRANSPARENT, cc, 2, 4));
        sN.addView(cnt, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        if (!inc) sN.setBackground(Ui.round(selBg, 19));
        sN.setOnClickListener(v -> { if (switcherIncognito) { switcherIncognito = false; switcherAnim = true; buildSwitcher(); } });
        FrameLayout sI = new FrameLayout(this);
        ImageView ii = Ui.icon(this, R.drawable.ic_incognito, inc ? Ui.INC_TEXT : fg2);
        sI.addView(ii, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (nInc > 0) {
            TextView ib = Ui.text(this, String.valueOf(nInc), 9, Color.WHITE);
            ib.setGravity(Gravity.CENTER);
            ib.setBackground(Ui.oval(0xFF5F6368));
            sI.addView(ib, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP | Gravity.END));
        }
        if (inc) sI.setBackground(Ui.round(selBg, 19));
        sI.setOnClickListener(v -> { if (!switcherIncognito) { switcherIncognito = true; switcherAnim = true; buildSwitcher(); } });
        seg.addView(sN, new LinearLayout.LayoutParams(dp(68), dp(38)));
        seg.addView(sI, new LinearLayout.LayoutParams(dp(68), dp(38)));
        bar.addView(seg, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        ImageView more = Ui.iconBtn(this, R.drawable.ic_more, fg);
        more.setOnClickListener(v -> {
            ArrayList<Tab> mine = new ArrayList<>();
            for (Tab t : tabs) if (t.incognito == inc) mine.add(t);
            ArrayList<Object[]> mm = new ArrayList<>();
            mm.add(new Object[]{R.drawable.ic_add, L.t("Новая вкладка"), (Runnable) () -> { switcher.setVisibility(View.GONE); switcher.removeAllViews(); newTab(null, false, true, null); }});
            mm.add(new Object[]{R.drawable.ic_incognito, L.t("Новая вкладка инкогнито"), (Runnable) () -> { switcher.setVisibility(View.GONE); switcher.removeAllViews(); newTab(null, true, true, null); }});
            if (!closedStack.isEmpty()) mm.add(new Object[]{R.drawable.ic_history, L.t("Вернуть закрытые вкладки"), (Runnable) () -> reopen(closedStack.get(closedStack.size() - 1))});
            if (!mine.isEmpty()) mm.add(new Object[]{R.drawable.ic_close, inc ? L.t("Закрыть все вкладки инкогнито") : L.t("Закрыть все вкладки"), (Runnable) () -> closeTabs(mine)});
            sheetMenu(null, mm);
        });
        bar.addView(more, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(60)));

        ArrayList<Tab> list = new ArrayList<>();
        for (Tab t : tabs) if (t.incognito == inc) list.add(t);
        FrameLayout body = new FrameLayout(this);
        col.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1));
        if (list.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER);
            ImageView ei = Ui.icon(this, inc ? R.drawable.ic_incognito : R.drawable.ic_globe, fg2);
            ei.setScaleType(ImageView.ScaleType.FIT_CENTER);
            e.addView(ei, new LinearLayout.LayoutParams(dp(56), dp(56)));
            TextView et = Ui.text(this, inc ? L.t("Нет вкладок инкогнито") : L.t("Нет открытых вкладок"), 16, fg2);
            et.setPadding(0, dp(16), 0, 0);
            e.addView(et);
            body.addView(e, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            ScrollView sv = new ScrollView(this);
            sv.setClipToPadding(false);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            int sw = scrWpx();
            int cols = Math.max(2, Math.min(4, (int) (sw / Ui.density / 320)));
            int side = dp(10);
            grid.setPadding(side, dp(4), side, dp(110));
            sv.addView(grid);
            body.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
            int cardW = (sw - side * 2) / cols - dp(12);
            float aspect = content.getWidth() > 0 && content.getHeight() > 0 ? content.getHeight() / (float) content.getWidth() : 1.5f;
            aspect = Math.max(0.78f, Math.min(1.4f, aspect));
            int cardH = dp(53) + (int) (cardW * aspect);
            boolean anim = switcherAnim;
            switcherAnim = false;
            LinearLayout row = null;
            int selPos = 0;
            for (int i = 0; i < list.size(); i++) {
                if (i % cols == 0) { row = new LinearLayout(this); grid.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP)); }
                Tab t = list.get(i);
                if (t == current) selPos = i / cols;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cardH, 1);
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                final View card = tabCard(t, inc, fg, fg2);
                row.addView(card, lp);
                if (anim) {
                    card.setAlpha(0f);
                    card.setTranslationY(dp(18));
                    card.animate().alpha(1f).translationY(0).setStartDelay(Math.min(i, 12) * 22L).setDuration(220)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(() -> card.animate().setStartDelay(0)).start();
                }
            }
            if (list.size() % cols != 0) for (int k = list.size() % cols; k < cols; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cardH, 1);
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(new View(this), lp);
            }
            final int scrollTo = Math.max(0, selPos - 1) * (cardH + dp(12));
            sv.post(() -> sv.scrollTo(0, scrollTo));
        }

        // floating "new tab" button
        LinearLayout fab = new LinearLayout(this);
        fab.setGravity(Gravity.CENTER_VERTICAL);
        fab.setPadding(dp(18), 0, dp(22), 0);
        int fabBg = inc ? 0xFF5F6368 : Ui.TONAL, fabFg = inc ? Color.WHITE : Ui.ON_TONAL;
        fab.setBackground(Ui.round(fabBg, 18));
        fab.setElevation(dp(6));
        fab.addView(Ui.icon(this, R.drawable.ic_add, fabFg), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView ft = Ui.medium(Ui.text(this, inc ? L.t("Инкогнито") : L.t("Новая вкладка"), 15, fabFg));
        ft.setPadding(dp(10), 0, 0, 0);
        fab.addView(ft);
        fab.setOnClickListener(v -> { switcher.setVisibility(View.GONE); switcher.removeAllViews(); newTab(null, inc, true, null); });
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(WRAP, dp(56), Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, dp(18), dp(22));
        switcher.addView(fab, fl);
    }

    View tabCard(Tab t, boolean inc, int fg, int fg2) {
        boolean sel = t == current;
        int cardBg = inc ? 0xFF35363A : (Ui.dark ? (Ui.amoled ? 0xFF1C1C1E : 0xFF2D2E31) : Color.WHITE);
        int ring = inc ? 0xFFBDC1C6 : Ui.ACCENT;
        int headBg = sel ? (inc ? 0xFF5F6368 : Ui.TONAL) : cardBg;
        int headFg = sel ? (inc ? Color.WHITE : Ui.ON_TONAL) : fg;
        int headFg2 = sel ? Ui.blend(headFg, headBg, 0.3f) : fg2;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(sel ? Ui.stroke(headBg, ring, 3, 22) : Ui.round(cardBg, 22));
        int pd = dp(sel ? 3 : 0);
        card.setPadding(pd, pd, pd, pd);
        card.setClipToOutline(true);
        card.setElevation(dp(sel ? 6 : 2));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), 0, dp(2), 0);
        String u = t.pendingUrl != null ? t.pendingUrl : (t.web.getUrl() != null ? t.web.getUrl() : t.url);
        String title = t.ntp ? (inc ? L.t("Инкогнито") : L.t("Новая вкладка")) : (t.title == null || t.title.isEmpty() ? displayUrl(u) : t.title);
        String sub = t.ntp ? (inc ? L.t("Новая вкладка") : L.t("Главная страница")) : (t.loading ? L.t("Загрузка…") : hostOf(u));
        View ic;
        if (t.ntp) {
            ImageView li = new ImageView(this);
            if (inc) { li = Ui.icon(this, R.drawable.ic_incognito, headFg); li.setScaleType(ImageView.ScaleType.FIT_CENTER); }
            else li.setImageResource(R.drawable.logo);
            ic = li;
        } else if (t.favicon != null) {
            FrameLayout fb = new FrameLayout(this);
            fb.setBackground(Ui.oval(Color.WHITE));
            ImageView fi = new ImageView(this);
            fi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            fi.setImageBitmap(t.favicon);
            fb.addView(fi, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
            ic = fb;
        } else ic = tileIcon(title, u, false, 0, 22);
        head.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(10), 0, dp(2), 0);
        tx.addView(Ui.medium(Ui.single(this, title, 13, headFg)));
        tx.addView(Ui.single(this, sub, 11, headFg2));
        head.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        ImageView x = Ui.iconBtn(this, R.drawable.ic_close, headFg2);
        x.setScaleType(ImageView.ScaleType.FIT_CENTER);
        x.setPadding(dp(10), dp(10), dp(10), dp(10));
        x.setOnClickListener(v -> card.animate().setStartDelay(0).alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(140)
                .withEndAction(() -> closeTabs(one(t))).start());
        head.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        card.addView(head, new LinearLayout.LayoutParams(MATCH, dp(48)));

        FrameLayout thumbBox = new FrameLayout(this);
        thumbBox.setBackground(Ui.round(inc ? Ui.INC_BG : Ui.BG, 16));
        thumbBox.setClipToOutline(true);
        if (t.ntp) {
            Bitmap wp = null;
            if (!inc) {
                int scrW = scrWpx(), scrH = scrHpx();
                wp = Wallpaper.get(this, store.p.getInt("wp", Wallpaper.NONE), scrW / 2, scrH / 2);
            }
            if (wp != null) {
                ImageView wi = new ImageView(this);
                wi.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wi.setImageBitmap(wp);
                thumbBox.addView(wi, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            LinearLayout ph = new LinearLayout(this);
            ph.setOrientation(LinearLayout.VERTICAL);
            ph.setGravity(Gravity.CENTER);
            if (inc) {
                FrameLayout ring2 = new FrameLayout(this);
                ring2.setBackground(Ui.oval(0xFF3C4043));
                ImageView ii = Ui.icon(this, R.drawable.ic_incognito, Ui.INC_TEXT2);
                ii.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ring2.addView(ii, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
                ph.addView(ring2, new LinearLayout.LayoutParams(dp(48), dp(48)));
            } else {
                ph.setGravity(Gravity.CENTER_HORIZONTAL);
                ph.setPadding(dp(16), dp(40), dp(16), 0);
                View bar = new View(this);
                bar.setBackground(Ui.round(Ui.dark ? 0xFF3C4043 : (wp != null ? Color.WHITE : 0xFFF1F3F4), 10));
                ph.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(20)));
                for (int rr = 0; rr < 2; rr++) {
                    LinearLayout dots = new LinearLayout(this);
                    for (int k = 0; k < 4; k++) {
                        View dt = new View(this);
                        dt.setBackground(Ui.oval(wp != null ? 0x88FFFFFF : (Ui.dark ? 0xFF3C4043 : 0xFFE8EAED)));
                        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(18), dp(18));
                        dl.setMargins(dp(6), 0, dp(6), 0);
                        dots.addView(dt, dl);
                    }
                    LinearLayout.LayoutParams dsl = new LinearLayout.LayoutParams(WRAP, WRAP);
                    dsl.topMargin = dp(rr == 0 ? 18 : 12);
                    ph.addView(dots, dsl);
                }
            }
            thumbBox.addView(ph, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else if (t.thumb != null) {
            TopCropImageView th = new TopCropImageView(this);
            th.setImageBitmap(t.thumb);
            thumbBox.addView(th, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            FrameLayout big = tileIcon(title, u, false, 0, 52);
            thumbBox.addView(big, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        }
        if (t.video != null && !t.ntp) {
            LinearLayout vb = new LinearLayout(this);
            vb.setGravity(Gravity.CENTER_VERTICAL);
            vb.setPadding(dp(8), dp(4), dp(10), dp(4));
            vb.setBackground(Ui.round(0xCC202124, 12));
            vb.addView(Ui.icon(this, R.drawable.ic_play, Color.WHITE), new LinearLayout.LayoutParams(dp(14), dp(14)));
            TextView vt = Ui.text(this, L.t("Видео"), 11, Color.WHITE);
            vt.setPadding(dp(4), 0, 0, 0);
            vb.addView(vt);
            FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
            vl.setMargins(dp(8), 0, 0, dp(8));
            thumbBox.addView(vb, vl);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(MATCH, 0, 1);
        tl.setMargins(dp(5), 0, dp(5), dp(5));
        card.addView(thumbBox, tl);
        card.setOnClickListener(v -> { switcher.setVisibility(View.GONE); switcher.removeAllViews(); selectTab(t); });
        attachSwipe(card, t);
        return card;
    }

    void attachSwipe(View card, Tab t) {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0; boolean drag, moved, longed;
            final Runnable lp = () -> {
                longed = true;
                card.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                card.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(90).start();
                tabMenu(t);
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX(); y0 = e.getRawY(); drag = false; moved = false; longed = false;
                        v.animate().setStartDelay(0).scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                        v.postDelayed(lp, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (longed) return true;
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!drag && !moved) {
                            if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) { drag = true; v.removeCallbacks(lp); v.getParent().requestDisallowInterceptTouchEvent(true); }
                            else if (Math.abs(dy) > slop) { moved = true; v.removeCallbacks(lp); v.animate().scaleX(1f).scaleY(1f).setDuration(90).start(); }
                        }
                        if (drag) { v.setTranslationX(dx); v.setAlpha(Math.max(0.15f, 1f - Math.abs(dx) / v.getWidth())); }
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        v.removeCallbacks(lp);
                        if (longed) return true;
                        float dx = e.getRawX() - x0;
                        if (drag) {
                            if (Math.abs(dx) > v.getWidth() * 0.35f)
                                v.animate().translationX(Math.signum(dx) * v.getWidth() * 1.4f).alpha(0f).setDuration(150).withEndAction(() -> closeTabs(one(t))).start();
                            else v.animate().translationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                        } else {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                            if (!moved) v.performClick();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(lp);
                        v.animate().translationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                        return true;
                }
                return false;
            }
        });
    }

    // ------------------------------------------------------------------ menu
    void showMenu() {
        unfocusOmni();
        final Tab t = current;
        boolean page = t != null && !t.ntp;
        int surface = Ui.dark ? 0xFF2D2E31 : Color.WHITE;
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(6));
        sv.addView(box);
        final PopupWindow pw = new PopupWindow(sv, dp(272), WRAP, true);
        pw.setBackgroundDrawable(Ui.round(surface, 10));
        pw.setElevation(dp(10));

        LinearLayout top = new LinearLayout(this);
        int[] icons = {R.drawable.ic_forward, page && store.isBookmarked(t.web.getUrl()) ? R.drawable.ic_star : R.drawable.ic_star_border,
                R.drawable.ic_download, R.drawable.ic_info, R.drawable.ic_refresh};
        for (int i = 0; i < icons.length; i++) {
            final int k = i;
            int color = Ui.TEXT2;
            if (i == 0 && (t == null || !t.web.canGoForward())) color = Ui.dark ? 0xFF5F6368 : 0xFFBDC1C6;
            if (i == 1 && page && store.isBookmarked(t.web.getUrl())) color = Ui.ACCENT;
            ImageView b = Ui.iconBtn(this, icons[i], color);
            b.setOnClickListener(v -> {
                pw.dismiss();
                if (t == null) return;
                if (k == 0) { if (t.web.canGoForward()) { t.ntp = false; t.web.goForward(); } }
                else if (k == 1) { if (page) { boolean on = store.toggleBookmark(t.title, t.web.getUrl()); snack(on ? L.t("Добавлено в закладки") : L.t("Закладка удалена"), on ? L.t("Закладки") : null, this::showBookmarks); } }
                else if (k == 2) savePage(t);
                else if (k == 3) showSiteInfo();
                else { if (page) t.web.reload(); }
            });
            top.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        box.addView(top);
        View dv = new View(this); dv.setBackgroundColor(Ui.DIVIDER);
        box.addView(dv, new LinearLayout.LayoutParams(MATCH, 1));

        menuItem(box, pw, R.drawable.ic_add, L.t("Новая вкладка"), () -> newTab(null, false, true, null));
        menuItem(box, pw, R.drawable.ic_incognito, L.t("Новая вкладка инкогнито"), () -> newTab(null, true, true, null));
        if (!closedStack.isEmpty()) menuItem(box, pw, R.drawable.ic_history, L.t("Вернуть закрытую вкладку"), () -> reopen(closedStack.get(closedStack.size() - 1)));
        menuItem(box, pw, R.drawable.ic_history, L.t("История"), this::showHistory);
        menuItem(box, pw, R.drawable.ic_key, L.t("Пароли"), this::showPasswords);
        menuItem(box, pw, R.drawable.ic_download, L.t("Загрузки"), this::showDownloads);
        menuItem(box, pw, R.drawable.ic_bookmarks, L.t("Закладки"), this::showBookmarks);
        if (page) {
            menuItem(box, pw, R.drawable.ic_video, t.video != null ? L.t("Найденное видео") : L.t("Видео на странице"), this::showVideos);
            menuItem(box, pw, R.drawable.ic_translate, L.t("Перевести страницу"), () -> translate(t));
            menuItem(box, pw, R.drawable.ic_share, L.t("Поделиться…"), () -> share(t.web.getUrl(), t.title));
            menuItem(box, pw, R.drawable.ic_search, L.t("Найти на странице"), this::showFind);
            menuItem(box, pw, R.drawable.ic_add, L.t("Добавить ярлык на главную"), () -> editShortcut(-1, true));
            LinearLayout r = menuItem(box, pw, R.drawable.ic_desktop, L.t("Версия для ПК"), () -> toggleDesktop(t));
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setChecked(t.desktop);
            cb.setClickable(false);
            cb.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
            r.addView(cb);
        }
        menuItem(box, pw, R.drawable.ic_shield, L.t("Блокировка рекламы: ") + (AdBlocker.enabled ? L.t("вкл") : L.t("выкл"))
                + (page && AdBlocker.enabled ? " · " + t.blocked.get() : ""), this::showAdblock);
        menuItem(box, pw, R.drawable.ic_palette, L.t("Темы и обои"), this::showAppearance);
        menuItem(box, pw, R.drawable.ic_settings, L.t("Настройки"), this::showSettings);
        pw.showAsDropDown(menuBtn, 0, -menuBtn.getHeight());
    }

    LinearLayout menuItem(LinearLayout box, PopupWindow pw, int icon, String text, Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(12), 0);
        row.setBackground(Ui.ripple(this, false));
        row.addView(Ui.icon(this, icon, Ui.TEXT2), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView tv = Ui.single(this, text, 15, Ui.TEXT);
        tv.setPadding(dp(18), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
        row.setOnClickListener(v -> { pw.dismiss(); r.run(); });
        box.addView(row, new LinearLayout.LayoutParams(MATCH, dp(48)));
        return row;
    }

    void toggleDesktop(Tab t) {
        t.desktop = !t.desktop;
        t.web.getSettings().setUserAgentString(t.desktop ? desktopUA : mobileUA);
        t.ua = t.web.getSettings().getUserAgentString();
        t.web.reload();
        snack(t.desktop ? L.t("Открыта версия для ПК") : L.t("Открыта мобильная версия"), null, null);
    }

    void share(String url, String title) {
        if (url == null) return;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(i, L.t("Поделиться")));
    }

    void copy(String s) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("url", s));
        toast(L.t("Скопировано"));
    }


    void openDownloads() {
        try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast(L.t("Файлы сохраняются в папку Загрузки/Lasur")); }
    }

    void showSiteInfo() {
        if (current == null || current.ntp) { showKb(omni); return; }
        final Tab t = current;
        final String host = t.pageHost;
        String u = t.web.getUrl();
        boolean secure = u != null && u.startsWith("https://");
        boolean wl = host != null && AdBlocker.whitelist.contains(host);
        String msg = (secure ? L.t("🔒 Подключение защищено.\nДанные (пароли, номера карт) передаются в зашифрованном виде.")
                : L.t("⚠ Подключение не защищено.\nНе вводите на этом сайте конфиденциальные данные."))
                + L.t("\n\nБлокировка рекламы: ") + (!AdBlocker.enabled ? L.t("выключена в настройках") : wl ? L.t("отключена для этого сайта") : L.t("включена"))
                + L.t("\nЗаблокировано запросов на странице: ") + t.blocked.get()
                + L.t("\nВидео: ") + (t.video != null ? L.t("найдено") : L.t("не найдено"));
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(host == null ? displayUrl(u) : host).setMessage(msg)
                .setPositiveButton(L.t("ОК"), null);
        if (host != null && AdBlocker.enabled) b.setNeutralButton(wl ? L.t("Включить блокировку здесь") : L.t("Отключить блокировку здесь"), (d, w) -> {
            store.setWhitelisted(host, !wl);
            AdBlocker.whitelist = store.whitelist();
            t.web.reload();
        });
        b.show();
    }

    // ------------------------------------------------------------------ full-screen lists
    Dialog fullDialog(String title, View body, String action, Runnable onAction) {
        final Dialog d = new Dialog(this, Ui.dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(8), 0);
        ImageView back = Ui.iconBtn(this, R.drawable.ic_back, Ui.TEXT2);
        back.setOnClickListener(v -> d.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView tt = Ui.medium(Ui.text(this, title, 20, Ui.TEXT));
        tt.setPadding(dp(12), 0, 0, 0);
        bar.addView(tt, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (action != null) {
            TextView a = Ui.medium(Ui.text(this, action, 15, Ui.ACCENT));
            a.setPadding(dp(12), dp(12), dp(12), dp(12));
            a.setBackground(Ui.ripple(this, true));
            a.setOnClickListener(v -> onAction.run());
            bar.addView(a);
        }
        col.addView(bar, new LinearLayout.LayoutParams(MATCH, dp(56)));
        View dv = new View(this); dv.setBackgroundColor(Ui.DIVIDER);
        col.addView(dv, new LinearLayout.LayoutParams(MATCH, 1));
        col.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1));
        d.setContentView(col);
        Window w = d.getWindow();
        if (w != null) {
            w.setStatusBarColor(Ui.BG);
            w.setNavigationBarColor(Ui.BG);
            if (!Ui.dark) w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        d.show();
        return d;
    }

    void showHistory() { showItems(L.t("История"), store.history, true); }
    void showBookmarks() { showItems(L.t("Закладки"), store.bookmarks, false); }

    void showItems(String title, ArrayList<Store.Item> items, boolean history) {
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        final Dialog[] dl = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            if (items.isEmpty()) {
                TextView e = Ui.text(this, history ? L.t("История пуста") : L.t("Закладок пока нет.\nНажмите ☆ в меню, чтобы добавить страницу."), 15, Ui.TEXT2);
                e.setGravity(Gravity.CENTER);
                e.setPadding(dp(24), dp(64), dp(24), 0);
                list.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
                return;
            }
            java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
            int max = Math.min(items.size(), 400);
            for (int i = 0; i < max; i++) {
                final Store.Item it = items.get(i);
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(10), dp(8), dp(10));
                r.setBackground(Ui.ripple(this, false));
                r.addView(tileIcon(it.t, it.u, false, 0, 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(16), 0, dp(8), 0);
                tx.addView(Ui.single(this, it.t, 15, Ui.TEXT));
                tx.addView(Ui.single(this, displayUrl(it.u) + (history && it.d > 0 ? " · " + df.format(new java.util.Date(it.d)) : ""), 13, Ui.TEXT2));
                r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
                ImageView del = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
                del.setOnClickListener(v -> {
                    items.remove(it);
                    if (history) store.saveHistory(); else store.saveBookmarks();
                    fill[0].run();
                });
                r.addView(del, new LinearLayout.LayoutParams(dp(40), dp(40)));
                r.setOnClickListener(v -> { dl[0].dismiss(); navigate(it.u); });
                r.setOnLongClickListener(v -> { linkMenu(it.u, it.t, null); return true; });
                list.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
        };
        fill[0].run();
        dl[0] = fullDialog(title, sv, history ? L.t("Очистить") : null, () -> new AlertDialog.Builder(this)
                .setMessage(L.t("Очистить всю историю просмотров?"))
                .setPositiveButton(L.t("Очистить"), (d, w) -> { store.history.clear(); store.saveHistory(); fill[0].run(); })
                .setNegativeButton(L.t("Отмена"), null).show());
    }

    // ------------------------------------------------------------------ settings

    void section(LinearLayout box, String s) {
        TextView t = Ui.medium(Ui.text(this, s, 13, Ui.ACCENT));
        t.setPadding(dp(20), dp(20), dp(20), dp(6));
        box.addView(t);
    }

    TextView actionRow(LinearLayout box, String title, String sub, Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        if (r != null) { row.setBackground(Ui.ripple(this, false)); row.setOnClickListener(v -> r.run()); }
        row.addView(Ui.text(this, title, 16, Ui.TEXT));
        TextView s = Ui.text(this, sub, 13, Ui.TEXT2);
        row.addView(s);
        box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return s;
    }

    interface BoolCb { void set(boolean v); }

    void switchRow(LinearLayout box, String title, String sub, boolean checked, BoolCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(16), dp(12));
        row.setBackground(Ui.ripple(this, false));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(Ui.text(this, title, 16, Ui.TEXT));
        tx.addView(Ui.text(this, sub, 13, Ui.TEXT2));
        row.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, v) -> cb.set(v));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ------------------------------------------------------------------ videos
    static String typeOf(String url) {
        String p = "";
        try { p = Uri.parse(url).getPath().toLowerCase(); } catch (Exception ignored) { }
        if (p.endsWith(".m3u8")) return "HLS";
        if (p.endsWith(".mpd")) return "DASH";
        int d = p.lastIndexOf('.');
        return d >= 0 && p.length() - d <= 5 ? p.substring(d + 1).toUpperCase() : "VIDEO";
    }



    void downloadVideo(Tab.Video v, Tab t) {
        String type = typeOf(v.url);
        String base = v.title != null && !v.title.trim().isEmpty() ? v.title.trim() : "video_" + System.currentTimeMillis();
        if (type.equals("DASH")) { toast(L.t("Поток DASH нельзя сохранить одним файлом — откройте его во внешнем плеере")); return; }
        if (type.equals("HLS")) {
            EditText name = new EditText(this);
            name.setText(Saver.clean(base));
            name.setSingleLine(true);
            FrameLayout f = new FrameLayout(this); f.setPadding(dp(20), dp(8), dp(20), 0); f.addView(name);
            new AlertDialog.Builder(this).setTitle(L.t("Скачать видеопоток")).setMessage(L.t("Видео будет собрано из частей (HLS) в один файл."))
                    .setView(f).setPositiveButton(L.t("Скачать"), (d, w) -> {
                        String n = name.getText().toString();
                        withStorage(() -> withNotif(() -> {
                            Intent si = new Intent(this, HlsService.class).putExtra("url", v.url).putExtra("name", n)
                                    .putExtra("referer", v.page).putExtra("ua", uaOf(t));
                            startForegroundService(si);
                            toast(L.t("Загрузка началась — прогресс в уведомлениях"));
                        }));
                    }).setNegativeButton(L.t("Отмена"), null).show();
            return;
        }
        String fn = fileName(v.url, null, null);
        String ext = fn.contains(".") ? fn.substring(fn.lastIndexOf('.')) : ".mp4";
        confirmDownload(v.url, Saver.clean(base) + ext, null, v.page, uaOf(t), -1);
    }

    // ------------------------------------------------------------------ downloads
    void onDownload(Tab t, String url, String ua, String cd, String mime, long len) {
        String name = fileName(url, cd, mime);
        if (url.startsWith("blob:")) {
            String js = "(function(){fetch(" + JSONObject.quote(url) + ").then(function(r){return r.blob()}).then(function(b){var f=new FileReader();"
                    + "f.onload=function(){LumenBridge.saveBase64(f.result," + JSONObject.quote(name) + "," + JSONObject.quote(mime == null ? "" : mime) + ")};f.readAsDataURL(b)})})()";
            t.web.evaluateJavascript(js, null);
            toast(L.t("Сохранение файла…"));
            return;
        }
        if (url.startsWith("data:")) { withStorage(() -> new Thread(() -> saveDataUrl(url, name, mime)).start()); return; }
        boolean video = (mime != null && mime.startsWith("video/")) || isVideoUrl(url);
        if (video) {
            final Tab.Video v = new Tab.Video(url, t.web.getUrl(), name);
            addVideo(t, url, t.web.getUrl(), name);
            new AlertDialog.Builder(this).setTitle(name)
                    .setItems(new String[]{L.t("Скачать"), L.t("Открыть во внешнем плеере"), L.t("Копировать ссылку")}, (d, w) -> {
                        if (w == 0) confirmDownload(url, name, mime, t.web.getUrl(), ua, len);
                        else if (w == 1) openExternal(v, t);
                        else copy(url);
                    }).show();
            return;
        }
        confirmDownload(url, name, mime, t.web.getUrl(), ua, len);
    }

    void confirmDownload(String url, String name, String mime, String referer, String ua, long len) {
        EditText et = new EditText(this);
        et.setText(name);
        et.setSingleLine(true);
        FrameLayout f = new FrameLayout(this); f.setPadding(dp(20), dp(8), dp(20), 0); f.addView(et);
        String size = len > 0 ? android.text.format.Formatter.formatShortFileSize(this, len) : null;
        new AlertDialog.Builder(this).setTitle(L.t("Скачать файл?")).setMessage(displayUrl(url) + (size != null ? " · " + size : ""))
                .setView(f)
                .setPositiveButton(L.t("Скачать"), (d, w) -> startDownload(url, Saver.clean(et.getText().toString()), mime, referer, ua))
                .setNegativeButton(L.t("Отмена"), null).show();
    }

    void startDownload(String url, String name, String mime, String referer, String ua) {
        withStorage(() -> {
            try {
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String mt = mimeFor(name, mime);
                if (mt != null && !mt.isEmpty()) r.setMimeType(mt);
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) r.addRequestHeader("Cookie", ck);
                if (ua != null) r.addRequestHeader("User-Agent", ua);
                if (referer != null && referer.startsWith("http")) r.addRequestHeader("Referer", referer);
                r.setTitle(name);
                r.setDescription(displayUrl(url));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Lasur/" + name);
                dm.enqueue(r);
                snack(L.t("Загрузка началась: ") + name, L.t("Загрузки"), this::showDownloads);
            } catch (Exception e) { toast(L.t("Ошибка загрузки: ") + e.getMessage()); }
        });
    }

    void saveDataUrl(String dataUrl, String name, String mime) {
        try {
            int comma = dataUrl.indexOf(',');
            String meta = dataUrl.substring(5, comma);
            byte[] data = meta.contains(";base64") ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                    : Uri.decode(dataUrl.substring(comma + 1)).getBytes("UTF-8");
            String m = mimeFor(name, mime != null && !mime.isEmpty() ? mime : meta.split(";")[0]);
            Saver s = Saver.create(this, name, m);
            s.out.write(data);
            s.finish();
            ui.post(() -> toast(L.t("Сохранено в Загрузки/Lasur: ") + s.name));
        } catch (Exception e) { ui.post(() -> toast(L.t("Не удалось сохранить: ") + e.getMessage())); }
    }

    void withStorage(Runnable r) {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingPerm = r;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        } else r.run();
    }

    void withNotif(Runnable r) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPerm = r;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else r.run();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_SITE_PERM) { Runnable a = sitePermAfter; sitePermAfter = null; if (a != null) a.run(); return; }
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
        toolbar.setVisibility(View.GONE);
        findBar.setVisibility(View.VISIBLE);
        findInput.setText("");
        findCount.setText("");
        showKb(findInput);
    }

    void hideFind() {
        if (current != null) current.web.clearMatches();
        findBar.setVisibility(View.GONE);
        toolbar.setVisibility(View.VISIBLE);
        hideKb(findInput);
        root.requestFocus();
    }

    @Override public void onBackPressed() {
        if (customView != null) { hideCustomView(); return; }
        if (switcher.getVisibility() == View.VISIBLE) { hideSwitcher(); return; }
        if (findBar.getVisibility() == View.VISIBLE) { hideFind(); return; }
        if (omni.hasFocus()) { unfocusOmni(); return; }
        Tab t = current;
        if (t != null) {
            String u = t.web.getUrl();
            if (t.ntp && u != null && !u.equals("about:blank") && t.pendingUrl == null) { t.ntp = false; refreshChrome(); return; }
            if (!t.ntp && t.web.canGoBack()) { t.web.goBack(); return; }
            if (!t.ntp && t.fromNtp) { t.ntp = true; t.fromNtp = false; silence(t); refreshChrome(); return; }
            if (t.parent != null && tabs.contains(t.parent)) { closeTab(t); return; }
        }
        moveTaskToBack(true);
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
    void addVideo(Tab t, String url, String page, String title) {
        if (url == null || !url.startsWith("http")) return;
        try { if (AdBlocker.enabled && AdBlocker.isAd(Uri.parse(url).getHost())) return; } catch (Exception ignored) { }
        Tab.Video cur = t.video;
        if (cur != null && cur.url.equals(url)) return;
        if (cur != null && cur.master) for (String[] vr : cur.variants) if (vr[1].equals(url)) return;
        Tab.Video v = new Tab.Video(url, page, title == null || title.trim().isEmpty() ? t.title : title.trim());
        v.type = typeOf(url);
        if (v.type.equals("HLS")) { classifyHls(t, v); return; }
        setVideo(t, v);
    }

    void setVideo(Tab t, Tab.Video v) {
        boolean isNew = t.video == null || !t.video.url.equals(v.url);
        t.video = v;
        if (t == current) { updateVideoFab(); if (isNew) pulseFab(); }
    }

    static String httpText(String u, String ua, String ref, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        if (ua != null) c.setRequestProperty("User-Agent", ua);
        if (ref != null) c.setRequestProperty("Referer", ref);
        try { String ck = CookieManager.getInstance().getCookie(u); if (ck != null) c.setRequestProperty("Cookie", ck); } catch (Throwable ignored) { }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0 && bo.size() < max) bo.write(b, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } finally { c.disconnect(); }
    }

    void classifyHls(Tab t, Tab.Video v) {
        final String ua = t.ua;
        new Thread(() -> {
            String body = null;
            try { body = httpText(v.url, ua, v.page, 512 * 1024); } catch (Exception ignored) { }
            if (body != null && !body.contains("#EXTM3U")) return;
            boolean audio = false;
            if (body != null && body.contains("#EXT-X-STREAM-INF")) {
                v.master = true;
                ArrayList<long[]> bws = new ArrayList<>();
                String[] lines = body.split("\\r?\\n");
                for (int i = 0; i < lines.length; i++) {
                    String l = lines[i].trim();
                    if (!l.startsWith("#EXT-X-STREAM-INF")) continue;
                    long bw = 0;
                    try { bw = Long.parseLong(HlsService.attr(l, "BANDWIDTH")); } catch (Exception ignored) { }
                    String res = HlsService.attr(l, "RESOLUTION");
                    for (int j = i + 1; j < lines.length; j++) {
                        String u = lines[j].trim();
                        if (u.isEmpty() || u.startsWith("#")) continue;
                        try {
                            String abs = HlsService.resolve(v.url, u);
                            String label = res != null && res.contains("x") ? res.substring(res.indexOf('x') + 1) + "p" : (bw > 0 ? "" : L.t("Поток ") + (v.variants.size() + 1));
                            if (bw > 0) label += (label.isEmpty() ? "" : " · ") + String.format(Locale.US, L.t("%.1f Мбит/с"), bw / 1_000_000.0);
                            v.variants.add(new String[]{label, abs, String.valueOf(bw)});
                        } catch (Exception ignored) { }
                        break;
                    }
                }
                java.util.Collections.sort(v.variants, (a, b) -> Long.compare(Long.parseLong(b[2]), Long.parseLong(a[2])));
            } else if (body != null) {
                String lb = body.toLowerCase();
                audio = lb.contains(".aac") || lb.contains(".mp3") || lb.contains("/audio") || lb.contains("audio=");
            }
            final boolean isAudio = audio;
            ui.post(() -> {
                Tab.Video cur = t.video;
                if (!v.master && cur != null && cur.master) {
                    for (String[] vr : cur.variants) if (vr[1].equals(v.url)) return;
                    if (System.currentTimeMillis() - cur.time < 90000) return; // segment playlist of the same stream
                }
                if (isAudio && cur != null) return;
                setVideo(t, v);
            });
        }).start();
    }

    void pulseFab() {
        if (videoFab.getVisibility() != View.VISIBLE) return;
        videoFab.animate().cancel();
        videoFab.setScaleX(0.6f);
        videoFab.setScaleY(0.6f);
        videoFab.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.5f)).setDuration(320).start();
    }

    void updateVideoFab() {
        boolean show = current != null && !current.ntp && current.video != null && customView == null
                && switcher.getVisibility() != View.VISIBLE && !omni.hasFocus();
        videoFab.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && videoLabel != null) {
            String ty = current.video.type;
            videoLabel.setText(ty.equals("HLS") || ty.equals("DASH") || ty.equals("VIDEO") ? L.t("Видео") : L.t("Видео · ") + ty);
        }
    }

    String uaOf(Tab t) { return t != null && t.ua != null ? t.ua : mobileUA; }

    void showVideos() {
        final Tab t = current;
        final Tab.Video v = t == null ? null : t.video;
        if (v == null) { toast(L.t("Видео пока не найдено. Запустите воспроизведение — оно появится автоматически.")); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(8));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView bi = Ui.icon(this, R.drawable.ic_video, Ui.ON_TONAL);
        badge.addView(bi, new FrameLayout.LayoutParams(MATCH, MATCH));
        head.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(14), 0, 0, 0);
        TextView tt = Ui.medium(Ui.text(this, v.title == null || v.title.isEmpty() ? L.t("Видео") : v.title, 16, Ui.TEXT));
        tt.setMaxLines(2);
        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(tt);
        String kind = v.type.equals("HLS") ? L.t("Потоковое видео (HLS)") : v.type.equals("DASH") ? L.t("Потоковое видео (DASH)") : L.t("Файл ") + v.type;
        tx.addView(Ui.single(this, kind + " · " + displayUrl(v.url), 13, Ui.TEXT2));
        head.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        box.addView(head);

        final String[] chosen = {v.url};
        if (v.master && !v.variants.isEmpty()) {
            TextView ql = Ui.medium(Ui.text(this, L.t("Качество"), 13, Ui.ACCENT));
            ql.setPadding(0, dp(18), 0, dp(8));
            box.addView(ql);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(this);
            hs.addView(chips);
            ArrayList<TextView> all = new ArrayList<>();
            ArrayList<String[]> opts = new ArrayList<>();
            opts.add(new String[]{L.t("Авто (лучшее)"), v.url});
            opts.addAll(v.variants);
            for (String[] o : opts) {
                TextView c = chip(o[0]);
                all.add(c);
                c.setOnClickListener(x -> { chosen[0] = o[1]; for (TextView a : all) styleChip(a, a == c); });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
                cl.rightMargin = dp(8);
                chips.addView(c, cl);
            }
            for (TextView a : all) styleChip(a, a == all.get(0));
            box.addView(hs);
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(20), 0, 0);
        TextView play = pillButton(L.t("Смотреть"), R.drawable.ic_play, Ui.ACCENT, Ui.dark ? 0xFF202124 : Color.WHITE);
        TextView dl = pillButton(L.t("Скачать"), R.drawable.ic_download, Ui.TONAL, Ui.ON_TONAL);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(50), 1);
        bl.rightMargin = dp(10);
        btns.addView(play, bl);
        btns.addView(dl, new LinearLayout.LayoutParams(0, dp(50), 1));
        box.addView(btns);

        LinearLayout more = new LinearLayout(this);
        more.setGravity(Gravity.CENTER);
        more.setPadding(0, dp(10), 0, 0);
        TextView cp = Ui.medium(Ui.text(this, L.t("Копировать ссылку"), 14, Ui.ACCENT));
        TextView sh = Ui.medium(Ui.text(this, L.t("Поделиться"), 14, Ui.ACCENT));
        for (TextView x : new TextView[]{cp, sh}) { x.setPadding(dp(14), dp(10), dp(14), dp(10)); x.setBackground(Ui.ripple(this, true)); more.addView(x); }
        box.addView(more);
        TextView hint = Ui.text(this, L.t("Показано последнее видео, найденное на этой странице. «Смотреть» открывает его во внешнем плеере (VLC, MX Player и др.) без скачивания."), 12, Ui.TEXT2);
        hint.setPadding(0, dp(6), 0, 0);
        box.addView(hint);

        final Dialog d = sheet(box);
        play.setOnClickListener(x -> { d.dismiss(); openExternal(withUrl(v, chosen[0]), t); });
        dl.setOnClickListener(x -> { d.dismiss(); downloadVideo(withUrl(v, chosen[0]), t); });
        cp.setOnClickListener(x -> { d.dismiss(); copy(chosen[0]); });
        sh.setOnClickListener(x -> { d.dismiss(); share(chosen[0], v.title); });
    }

    Tab.Video withUrl(Tab.Video v, String url) {
        if (url.equals(v.url)) return v;
        Tab.Video n = new Tab.Video(url, v.page, v.title);
        n.type = typeOf(url);
        return n;
    }

    TextView pillButton(String text, int icon, int bg, int fg) {
        TextView t = Ui.medium(Ui.text(this, text, 15, fg));
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable dr = getDrawable(icon).mutate();
        dr.setTint(fg);
        dr.setBounds(0, 0, dp(20), dp(20));
        t.setCompoundDrawablesRelative(dr, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setBackground(Ui.round(bg, 25));
        t.setForeground(Ui.ripple(this, false));
        return t;
    }

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
        wrap.setPadding(0, dp(10), 0, dp(14));
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
            w.getDecorView().setPadding(0, 0, 0, 0);
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
            h.setPadding(dp(24), 0, dp(24), dp(10));
            box.addView(h);
            View dv = new View(this);
            dv.setBackgroundColor(Ui.DIVIDER);
            box.addView(dv, new LinearLayout.LayoutParams(MATCH, 1));
        }
        final Dialog[] d = new Dialog[1];
        for (Object[] it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(24), 0, dp(20), 0);
            row.setBackground(Ui.ripple(this, false));
            row.addView(Ui.icon(this, (Integer) it[0], Ui.TEXT2), new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView tv = Ui.single(this, (String) it[1], 15, Ui.TEXT);
            tv.setPadding(dp(20), 0, 0, 0);
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
            if (isVideoUrl(link)) it.add(new Object[]{R.drawable.ic_play, L.t("Смотреть во внешнем плеере"), (Runnable) () -> openExternal(new Tab.Video(link, page, title), current)});
            it.add(new Object[]{R.drawable.ic_copy, L.t("Копировать адрес ссылки"), (Runnable) () -> copy(link)});
            it.add(new Object[]{R.drawable.ic_download, L.t("Скачать по ссылке"), (Runnable) () -> confirmDownload(link, fileName(link, null, null), null, page, uaOf(current), -1)});
            it.add(new Object[]{R.drawable.ic_share, L.t("Поделиться ссылкой"), (Runnable) () -> share(link, title)});
        }
        if (img != null && !img.startsWith("data:")) {
            it.add(new Object[]{R.drawable.ic_wallpaper, L.t("Открыть изображение"), (Runnable) () -> newTab(img, inc, true, current)});
            it.add(new Object[]{R.drawable.ic_download, L.t("Скачать изображение"), (Runnable) () -> startDownload(img, Saver.clean(fileName(img, null, "image/jpeg")), null, page, uaOf(current))});
            it.add(new Object[]{R.drawable.ic_copy, L.t("Копировать адрес изображения"), (Runnable) () -> copy(img)});
        } else if (img != null) {
            it.add(new Object[]{R.drawable.ic_download, L.t("Скачать изображение"), (Runnable) () -> withStorage(() -> new Thread(() -> saveDataUrl(img, "image_" + System.currentTimeMillis() + ".png", null)).start())});
        }
        if (it.isEmpty()) return;
        sheetMenu(link != null ? link : img, it);
    }

    // ---------------------------------------------------------------- omnibox: suggestions & voice
    void updateOmniButtons() {
        boolean has = omni.hasFocus() && omni.getText().length() > 0;
        clearBtn.setVisibility(has ? View.VISIBLE : View.GONE);
        micBtn.setVisibility(has ? View.GONE : View.VISIBLE);
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
            new Thread(() -> {
                try {
                    JSONArray a = new JSONArray(httpText(base + Uri.encode(q), mobileUA, null, 64 * 1024)).getJSONArray(1);
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
                } catch (Exception ignored) { }
            }).start();
        }, 160);
    }

    void addSuggest(LinearLayout parent, int icon, String title, String sub, String target, boolean insert) {
        boolean inc = current != null && current.incognito;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(sub == null ? 14 : 10), dp(6), dp(sub == null ? 14 : 10));
        r.setBackground(Ui.ripple(this, false));
        if (icon == R.drawable.ic_history || icon == R.drawable.ic_star) {
            if (sub != null && sub.startsWith("http")) r.addView(tileIcon(title, sub, false, 0, 26), new LinearLayout.LayoutParams(dp(26), dp(26)));
            else r.addView(Ui.icon(this, icon, inc ? Ui.INC_TEXT2 : Ui.TEXT2), new LinearLayout.LayoutParams(dp(26), dp(26)));
        } else r.addView(Ui.icon(this, icon, inc ? Ui.INC_TEXT2 : Ui.TEXT2), new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(16), 0, 0, 0);
        tx.addView(Ui.single(this, title, 15, inc ? Ui.INC_TEXT : Ui.TEXT));
        if (sub != null) tx.addView(Ui.single(this, sub.startsWith("http") ? displayUrl(sub) : sub, 13, Ui.ACCENT));
        r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (insert) {
            ImageView ins = Ui.iconBtn(this, R.drawable.ic_up, inc ? Ui.INC_TEXT2 : Ui.TEXT2);
            ins.setRotation(-45);
            ins.setOnClickListener(v -> { omni.setText(title + " "); omni.setSelection(omni.getText().length()); });
            r.addView(ins, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        if (target == null) r.setOnClickListener(v -> { copy(current.web.getUrl()); unfocusOmni(); });
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
    void showDownloads() {
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(24));
        sv.addView(list);
        final Dialog[] dl = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        final java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
        fill[0] = () -> {
            list.removeAllViews();
            ArrayList<Object[]> items = new ArrayList<>(); // name, sub, uri, mime, progress, delete, time
            boolean running = false;
            HashSet<String> names = new HashSet<>();
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            try (Cursor c = dm.query(new DownloadManager.Query())) {
                while (c != null && c.moveToNext()) {
                    long id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    String title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                    int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long so = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    String mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE));
                    long ts = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
                    String sub; int prog = -1; Uri uri = null;
                    if (st == DownloadManager.STATUS_RUNNING || st == DownloadManager.STATUS_PENDING || st == DownloadManager.STATUS_PAUSED) {
                        running = true;
                        prog = tot > 0 ? (int) (so * 100 / tot) : 0;
                        sub = (st == DownloadManager.STATUS_PAUSED ? L.t("Пауза · ") : st == DownloadManager.STATUS_PENDING ? L.t("Ожидание · ") : L.t("Загрузка · "))
                                + fmtSize(so) + (tot > 0 ? L.t(" из ") + fmtSize(tot) : "");
                    } else if (st == DownloadManager.STATUS_FAILED) sub = L.t("Ошибка загрузки");
                    else { sub = fmtSize(tot) + " · " + df.format(new java.util.Date(ts)); uri = dm.getUriForDownloadedFile(id); }
                    if (title != null) names.add(title);
                    items.add(new Object[]{title, sub, uri, mime, prog, (Runnable) () -> dm.remove(id), ts});
                }
            } catch (Exception ignored) { }
            if (Build.VERSION.SDK_INT >= 29) {
                String[] proj = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED};
                try (Cursor c = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj,
                        MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{Environment.DIRECTORY_DOWNLOADS + "/Lasur%", Environment.DIRECTORY_DOWNLOADS + "/Lumen%"}, null)) {
                    while (c != null && c.moveToNext()) {
                        String name = c.getString(1);
                        if (name == null || names.contains(name)) continue;
                        Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0));
                        long ts = c.getLong(4) * 1000;
                        items.add(new Object[]{name, fmtSize(c.getLong(2)) + " · " + df.format(new java.util.Date(ts)), uri, c.getString(3), -1,
                                (Runnable) () -> { try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { } }, ts});
                    }
                } catch (Exception ignored) { }
            }
            java.util.Collections.sort(items, (a, b) -> Long.compare((Long) b[6], (Long) a[6]));
            if (items.isEmpty()) {
                TextView e = Ui.text(this, L.t("Загрузок пока нет.\nФайлы и видео сохраняются в папку «Загрузки/Lasur»."), 15, Ui.TEXT2);
                e.setGravity(Gravity.CENTER);
                e.setPadding(dp(24), dp(72), dp(24), 0);
                list.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            for (Object[] it : items) {
                final String name = (String) it[0], mime = (String) it[3];
                final Uri uri = (Uri) it[2];
                final int prog = (Integer) it[4];
                final Runnable del = (Runnable) it[5];
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(10), dp(6), dp(10));
                r.setBackground(Ui.ripple(this, false));
                FrameLayout ic = new FrameLayout(this);
                ic.setBackground(Ui.round(Ui.TONAL, 12));
                int res = mime != null && mime.startsWith("video") ? R.drawable.ic_video : mime != null && mime.startsWith("image") ? R.drawable.ic_wallpaper : R.drawable.ic_download;
                ic.addView(Ui.icon(this, res, Ui.ON_TONAL), new FrameLayout.LayoutParams(MATCH, MATCH));
                r.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(14), 0, dp(6), 0);
                tx.addView(Ui.single(this, name == null ? L.t("Файл") : name, 15, Ui.TEXT));
                tx.addView(Ui.single(this, (String) it[1], 12, Ui.TEXT2));
                if (prog >= 0) {
                    ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                    pb.setMax(100);
                    pb.setProgress(prog);
                    pb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
                    tx.addView(pb, new LinearLayout.LayoutParams(MATCH, dp(6)));
                }
                r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
                ImageView more = Ui.iconBtn(this, R.drawable.ic_more, Ui.TEXT2);
                r.addView(more, new LinearLayout.LayoutParams(dp(40), dp(44)));
                Runnable open = () -> {
                    if (uri == null) { toast(prog >= 0 ? L.t("Файл ещё загружается") : L.t("Файл недоступен")); return; }
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "*/*" : mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, L.t("Открыть с помощью")));
                    } catch (Exception e) { toast(L.t("Нет приложения для открытия файла")); }
                };
                r.setOnClickListener(v -> open.run());
                more.setOnClickListener(v -> {
                    ArrayList<Object[]> m = new ArrayList<>();
                    m.add(new Object[]{R.drawable.ic_play, L.t("Открыть"), open});
                    if (uri != null) m.add(new Object[]{R.drawable.ic_share, L.t("Поделиться"), (Runnable) () -> {
                        Intent i = new Intent(Intent.ACTION_SEND).setType(mime == null ? "*/*" : mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, L.t("Поделиться")));
                    }});
                    m.add(new Object[]{R.drawable.ic_close, prog >= 0 ? L.t("Отменить загрузку") : L.t("Удалить файл"), (Runnable) () -> { del.run(); fill[0].run(); }});
                    sheetMenu(name, m);
                });
                list.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            if (running) list.postDelayed(() -> { if (dl[0] != null && dl[0].isShowing()) fill[0].run(); }, 1000);
        };
        fill[0].run();
        dl[0] = fullDialog(L.t("Загрузки"), sv, L.t("Папка"), this::openDownloads);
    }

    String fmtSize(long b) { return b <= 0 ? "—" : android.text.format.Formatter.formatShortFileSize(this, b); }

    // ---------------------------------------------------------------- appearance: themes & wallpapers
    void applyTheme(int mode, int accent) {
        store.p.edit().putInt("themeMode", mode).putInt("accent", accent).commit();
        if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
        saveTabs();
        recreate();
    }

    void showAppearance() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), dp(8));
        box.addView(Ui.medium(Ui.text(this, L.t("Оформление"), 20, Ui.TEXT)));
        final Dialog[] d = new Dialog[1];

        TextView tl = Ui.medium(Ui.text(this, L.t("Тема"), 13, Ui.ACCENT));
        tl.setPadding(0, dp(18), 0, dp(8));
        box.addView(tl);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout modes = new LinearLayout(this);
        hs.addView(modes);
        for (int i = 0; i < Ui.MODE_NAMES.length; i++) {
            final int m = i;
            TextView c = chip(L.t(Ui.MODE_NAMES[i]));
            styleChip(c, Ui.mode == i);
            c.setOnClickListener(v -> { if (Ui.mode != m) { d[0].dismiss(); applyTheme(m, Ui.accent); } });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
            cl.rightMargin = dp(8);
            modes.addView(c, cl);
        }
        box.addView(hs);

        TextView al = Ui.medium(Ui.text(this, L.t("Цвет"), 13, Ui.ACCENT));
        al.setPadding(0, dp(18), 0, dp(8));
        box.addView(al);
        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setHorizontalScrollBarEnabled(false);
        LinearLayout acc = new LinearLayout(this);
        hs2.addView(acc);
        for (int i = 0; i < Ui.ACCENT_LIGHT.length; i++) {
            final int a = i;
            int col = Ui.dark ? Ui.ACCENT_DARK[i] : Ui.ACCENT_LIGHT[i];
            FrameLayout sw = new FrameLayout(this);
            sw.setBackground(Ui.accent == i ? Ui.stroke(col, Ui.TEXT, 3, 22) : Ui.oval(col));
            if (Ui.accent == i) {
                ImageView ok = Ui.icon(this, R.drawable.ic_check, Ui.dark ? 0xFF202124 : Color.WHITE);
                ok.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                sw.addView(ok, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            sw.setContentDescription(L.t(Ui.ACCENT_NAMES[i]));
            sw.setOnClickListener(v -> { if (Ui.accent != a) { d[0].dismiss(); applyTheme(Ui.mode, a); } });
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(44), dp(44));
            sl.rightMargin = dp(12);
            acc.addView(sw, sl);
        }
        box.addView(hs2);

        TextView wl = Ui.medium(Ui.text(this, L.t("Обои главной страницы"), 13, Ui.ACCENT));
        wl.setPadding(0, dp(18), 0, dp(8));
        box.addView(wl);
        int cur = store.p.getInt("wp", Wallpaper.NONE);
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(Wallpaper.NONE);
        for (int i = 0; i < Wallpaper.NAMES.length; i++) ids.add(i);
        ids.add(Wallpaper.CUSTOM);
        LinearLayout row = null;
        for (int k = 0; k < ids.size(); k++) {
            if (k % 3 == 0) { row = new LinearLayout(this); box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP)); }
            final int id = ids.get(k);
            FrameLayout card = new FrameLayout(this);
            card.setClipToOutline(true);
            card.setBackground(Ui.round(Ui.dark ? 0xFF3C4043 : 0xFFF1F3F4, 14));
            String label;
            if (id == Wallpaper.NONE) {
                label = L.t("Без обоев");
                ImageView ic = Ui.icon(this, R.drawable.ic_close, Ui.TEXT2);
                card.addView(ic, new FrameLayout.LayoutParams(MATCH, MATCH));
            } else if (id == Wallpaper.CUSTOM) {
                label = L.t("Своё фото");
                Bitmap b = Wallpaper.customFile(this).exists() ? Wallpaper.get(this, Wallpaper.CUSTOM, 0, 0) : null;
                if (b != null) {
                    ImageView iv = new ImageView(this);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(b);
                    card.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
                }
                ImageView ic = Ui.icon(this, R.drawable.ic_add, b != null ? Color.WHITE : Ui.TEXT2);
                card.addView(ic, new FrameLayout.LayoutParams(MATCH, MATCH));
            } else {
                label = L.t(Wallpaper.NAMES[id]);
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(Wallpaper.render(id, dp(60), dp(100)));
                card.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            TextView lb = Ui.single(this, label, 12, id == Wallpaper.NONE || (id == Wallpaper.CUSTOM && !Wallpaper.customFile(this).exists()) ? Ui.TEXT : Color.WHITE);
            lb.setShadowLayer(dp(3), 0, dp(1), id >= 0 ? 0x99000000 : 0);
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(dp(4), 0, dp(4), dp(8));
            card.addView(lb, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
            if (id == cur) card.setForeground(Ui.stroke(Color.TRANSPARENT, Ui.ACCENT, 3, 14));
            card.setOnClickListener(v -> {
                d[0].dismiss();
                if (id == Wallpaper.CUSTOM) {
                    try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_WALL); }
                    catch (Exception e) { toast(L.t("Нет приложения для выбора фото")); }
                } else { store.p.edit().putInt("wp", id).apply(); refreshChrome(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(150), 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(card, lp);
        }
        d[0] = sheet(box);
    }

    int ntpCard() { return ntpOnWall ? (Ui.dark ? 0xE6202124 : 0xEEFFFFFF) : Ui.CARD; }

    View wrapWall(View sv, Bitmap wp) {
        if (wp == null) return sv;
        FrameLayout fr = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(wp);
        fr.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        View dim = new View(this);
        dim.setBackgroundColor(Ui.dark ? 0x66000000 : 0x26000000);
        fr.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));
        fr.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        return fr;
    }

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
    void showSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(24));
        sv.addView(box);
        section(box, L.t("Оформление"));
        int wp = store.p.getInt("wp", Wallpaper.NONE);
        actionRow(box, L.t("Тема, цвет и обои"), L.t(Ui.MODE_NAMES[Ui.mode]) + " · " + L.t(Ui.ACCENT_NAMES[Ui.accent]) + " · "
                + (wp == Wallpaper.NONE ? L.t("без обоев") : wp == Wallpaper.CUSTOM ? L.t("своё фото") : L.t(Wallpaper.NAMES[wp])), this::showAppearance);
        final String[] zooms = {"80%", "90%", "100%", "110%", "125%", "150%", "175%", "200%"};
        final int[] zv = {80, 90, 100, 110, 125, 150, 175, 200};
        final TextView[] zs = new TextView[1];
        zs[0] = actionRow(box, L.t("Масштаб текста"), store.p.getInt("zoom", 100) + "%", () -> {
            int curZ = store.p.getInt("zoom", 100), sel = 2;
            for (int i = 0; i < zv.length; i++) if (zv[i] == curZ) sel = i;
            new AlertDialog.Builder(this).setTitle(L.t("Масштаб текста")).setSingleChoiceItems(zooms, sel, (d, w) -> {
                store.p.edit().putInt("zoom", zv[w]).apply();
                for (Tab t : tabs) t.web.getSettings().setTextZoom(zv[w]);
                zs[0].setText(zooms[w]);
                d.dismiss();
            }).show();
        });
        switchRow(box, L.t("Тёмная тема для сайтов"), L.t("Затемнять светлые сайты при тёмной теме"), store.bool("darkSites", false), v -> {
            store.setBool("darkSites", v);
            for (Tab t : tabs) applySiteSettings(t.web.getSettings());
        });
        section(box, L.t("Основные"));
        actionRow(box, L.t("Язык"), langName(), this::pickLanguage);
        final TextView[] engSub = new TextView[1];
        engSub[0] = actionRow(box, L.t("Поисковая система"), L.t(Store.ENGINES[store.engine()]), () ->
                new AlertDialog.Builder(this).setTitle(L.t("Поисковая система")).setSingleChoiceItems(L.ta(Store.ENGINES), store.engine(), (d, w) -> {
                    store.setEngine(w); engSub[0].setText(L.t(Store.ENGINES[w])); d.dismiss();
                }).show());
        switchRow(box, L.t("Поисковые подсказки"), L.t("Подсказки поисковика при вводе запроса"), store.bool("suggest", true), v -> store.setBool("suggest", v));
        switchRow(box, L.t("Потянуть вниз для обновления"), L.t("Обновлять страницу жестом сверху вниз"), store.bool("ptr", true), v -> store.setBool("ptr", v));
        switchRow(box, L.t("Картинка в картинке"), L.t("Видео на весь экран продолжает играть в окне при выходе"), store.bool("pip", true), v -> store.setBool("pip", v));
        switchRow(box, L.t("Восстанавливать вкладки"), L.t("Открывать прошлые вкладки при запуске"), store.restoreTabs(), v -> store.setBool("restore", v));
        if (Math.round(scrWpx() / Ui.density) >= 600)
            switchRow(box, L.t("Панель вкладок"), L.t("Вкладки над адресной строкой на большом экране"), store.bool("tabStrip", true), v -> { store.setBool("tabStrip", v); refreshStrip(); refreshChrome(); });
        actionRow(box, L.t("Сделать браузером по умолчанию"), L.t("Открывать ссылки из других приложений в Lasur"), () -> {
            makeDefaultBrowser();
        });
        section(box, L.t("Без рекламы"));
        actionRow(box, L.t("Блокировщик рекламы"), L.t("Статистика, исключения и фильтры"), this::showAdblock);
        switchRow(box, L.t("Блокировка рекламы"), L.t("Реклама, трекеры, баннеры (EasyList, RuAdList, AdGuard)"), store.adblock(), v -> {
            store.setBool("adblock", v); AdBlocker.enabled = v;
        });
        switchRow(box, L.t("Блокировать всплывающие окна"), L.t("И рекламные переходы без нажатия"), store.blockPopups(), v -> store.setBool("popups", v));
        final TextView[] listSub = new TextView[1];
        listSub[0] = actionRow(box, L.t("Обновить фильтры"), AdBlocker.stats(), () -> {
            listSub[0].setText(L.t("Загрузка…"));
            new Thread(() -> {
                try { AdBlocker.update(getApplicationContext()); ui.post(() -> listSub[0].setText(L.t("Обновлено · ") + AdBlocker.stats())); }
                catch (Exception e) { ui.post(() -> listSub[0].setText(L.t("Ошибка: ") + e.getMessage())); }
            }).start();
        });
        actionRow(box, L.t("Заблокировано всего"), AdBlocker.totalBlocked.get() + L.t(" запросов рекламы и трекеров"), null);
        if (!AdBlocker.whitelist.isEmpty())
            actionRow(box, L.t("Сайты-исключения"), AdBlocker.whitelist.size() + L.t(" — нажмите, чтобы очистить"), () -> {
                store.p.edit().remove("whitelist").apply(); AdBlocker.whitelist = store.whitelist(); toast(L.t("Исключения очищены"));
            });
        section(box, L.t("Сайты"));
        switchRow(box, "JavaScript", L.t("Нужен для работы большинства сайтов"), store.js(), v -> {
            store.setBool("js", v); for (Tab t : tabs) t.web.getSettings().setJavaScriptEnabled(v);
        });
        switchRow(box, L.t("Версия для ПК по умолчанию"), L.t("Для новых вкладок"), store.desktopDefault(), v -> store.setBool("desktop", v));
        actionRow(box, L.t("Разрешения сайтов"), L.t("Микрофон, камера, местоположение, новые вкладки"), this::showSitePermissions);
        section(box, L.t("Пароли"));
        actionRow(box, L.t("Пароли"), passwords.list.isEmpty() ? L.t("Сохранение и автозаполнение паролей") : L.t("Сохранено: ") + passwords.list.size(), this::showPasswords);
        section(box, L.t("Конфиденциальность"));
        actionRow(box, L.t("Очистить историю"), L.t("Удалить всю историю просмотров"), () -> { store.history.clear(); store.saveHistory(); toast(L.t("История очищена")); });
        actionRow(box, L.t("Очистить cookies и данные сайтов"), L.t("Вы выйдете из аккаунтов на сайтах"), () -> new AlertDialog.Builder(this)
                .setMessage(L.t("Удалить cookies, кэш и данные всех сайтов?"))
                .setPositiveButton(L.t("Удалить"), (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    WebStorage.getInstance().deleteAllData();
                    WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword();
                    for (Tab t : tabs) { t.web.clearCache(true); t.web.clearFormData(); }
                    toast(L.t("Данные удалены"));
                }).setNegativeButton(L.t("Отмена"), null).show());
        section(box, L.t("О браузере"));
        actionRow(box, "Lasur 1.7.1", L.t("Браузер без рекламы с загрузкой видео"), null);
        settingsDialog = fullDialog(L.t("Настройки"), sv, null, null);
    }

    void openExternal(Tab.Video v, Tab t) {
        String type = typeOf(v.url);
        String mime = type.equals("HLS") ? "application/x-mpegURL" : type.equals("DASH") ? "application/dash+xml" : "video/*";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(v.url), mime);
        ArrayList<String> h = new ArrayList<>();
        if (v.page != null) { h.add("Referer"); h.add(v.page); }
        h.add("User-Agent"); h.add(uaOf(t));
        String ck = CookieManager.getInstance().getCookie(v.url);
        if (ck != null) { h.add("Cookie"); h.add(ck); }
        i.putExtra("headers", h.toArray(new String[0]));
        i.putExtra("title", v.title);
        i.putExtra("http-referrer", v.page);
        Bundle hb = new Bundle();
        for (int k = 0; k + 1 < h.size(); k += 2) hb.putString(h.get(k), h.get(k + 1));
        i.putExtra("android.media.intent.extra.HTTP_HEADERS", hb);
        try { startActivity(Intent.createChooser(i, L.t("Открыть в видеоплеере"))); }
        catch (Exception e) {
            try { i.setDataAndType(Uri.parse(v.url), "video/*"); startActivity(Intent.createChooser(i, L.t("Открыть в видеоплеере"))); }
            catch (Exception e2) { toast(L.t("Установите видеоплеер, например VLC или MX Player")); }
        }
    }

    // ================================================================== v1.3 additions
    Passwords passwords;
    LinearLayout snackView, noticeView, pwBar;
    final Runnable snackHide = this::hideSnack;
    final Runnable pwBlurR = this::hidePwBar;
    static final class Closed { String url, title; boolean inc, ntp; int index; Bundle state; Bitmap thumb; }
    final ArrayList<ArrayList<Closed>> closedStack = new ArrayList<>();
    Tab autoTab;
    boolean switcherAnim;
    static final int REQ_AUTH = 16;
    Runnable pendingAuth;
    long authUntil, lastTouch;
    android.content.BroadcastReceiver dlReceiver;
    int fsScrollY = -1; String fsJsScroll; Tab fsTab; long fsExitAt;

    static final String PAUSE_JS = "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused)m.pause()})}catch(e){}})();";
    static final String SCROLL_JS = "(function(){var e=document.scrollingElement||document.documentElement;return (window.scrollX||0)+','+(window.scrollY||e.scrollTop||0)})()";

    /** Tells the browser whether a downward drag that starts here may become pull-to-refresh. */
    static final String PTR_JS = "(function(){if(window.__lumenPtr)return;window.__lumenPtr=1;"
            + "function ok(e){try{if(e.touches.length>1)return 0;var se=document.scrollingElement||document.documentElement;"
            + "if((window.scrollY||0)>0||(se&&se.scrollTop>0))return 0;if(document.fullscreenElement||document.webkitFullscreenElement)return 0;"
            + "var vh=window.innerHeight||1;for(var n=e.target;n&&n.nodeType==1&&n!=document.body&&n!=document.documentElement;n=n.parentElement){"
            + "var tg=n.tagName;if(tg=='VIDEO'||tg=='CANVAS'||tg=='IFRAME'||tg=='EMBED'||tg=='OBJECT'||tg=='SELECT'||tg=='TEXTAREA'||(tg=='INPUT'&&n.type=='range'))return 0;"
            + "if(n.isContentEditable)return 0;if(n.scrollTop>0)return 0;var cs=getComputedStyle(n),ta=cs.touchAction;"
            + "if(ta=='none'||ta=='pan-x'||ta=='pinch-zoom'||ta=='pan-left'||ta=='pan-right')return 0;"
            + "if(n.getElementsByTagName('video').length&&n.getBoundingClientRect().height<vh*0.85)return 0;"
            + "var r=n.getAttribute('role');if(r=='slider'||r=='application')return 0;"
            + "var oy=cs.overflowY,sn=cs.scrollSnapType||'';if(sn.indexOf('y')>=0||sn.indexOf('block')>=0||sn.indexOf('both')>=0)return 0;"
            + "if((oy=='auto'||oy=='scroll'||oy=='overlay')&&n.scrollHeight>n.clientHeight+4)return 0;}"
            + "var t0=e.touches[0],vs=document.getElementsByTagName('video');for(var i=0;i<vs.length;i++){var b=vs[i].getBoundingClientRect();"
            + "if(b.width*b.height>window.innerWidth*vh*0.25&&t0.clientX>=b.left&&t0.clientX<=b.right&&t0.clientY>=b.top&&t0.clientY<=b.bottom)return 0;}"
            + "var rs=getComputedStyle(document.documentElement).scrollSnapType+getComputedStyle(document.body).scrollSnapType;if(/y|block|both/.test(rs))return 0;"
            + "return 1}catch(x){return 1}}"
            + "var sent=0;window.addEventListener('touchstart',function(e){sent=0;LumenBridge.ptr(ok(e))},{capture:true,passive:true});"
            + "window.addEventListener('touchmove',function(e){if(e.defaultPrevented&&!sent){sent=1;LumenBridge.ptr(0)}},{passive:true});})();";

    /** Detects login forms: reports typed credentials, focus on login fields and provides a fill function. */
    static final String PW_JS = "(function(){if(window.__lumenPw)return;window.__lumenPw=1;var B=LumenBridge;"
            + "function vis(e){return !!(e&&(e.offsetWidth||e.offsetHeight||e.getClientRects().length))}"
            + "function pws(){return Array.prototype.filter.call(document.querySelectorAll('input[type=password]'),vis)}"
            + "function txt(e){var t=(e.type||'text').toLowerCase();return e.tagName=='INPUT'&&(t=='text'||t=='email'||t=='tel')}"
            + "function isU(e){if(!e||!txt(e))return false;var a=((e.autocomplete||'')+' '+(e.name||'')+' '+(e.id||'')+' '+(e.placeholder||''));"
            + "return e.type=='email'||/user|login|email|e-mail|phone|mail|account|identifier|логин|почт|телефон/i.test(a)}"
            + "function uf(p){var root=p&&p.form?p.form:document,ins=root.querySelectorAll('input'),u=null;"
            + "for(var i=0;i<ins.length;i++){var e=ins[i];if(e==p)break;if(txt(e)&&vis(e))u=e;}return u}"
            + "function cap(){var ps=pws(),p=null;for(var i=0;i<ps.length;i++)if(ps[i].value)p=ps[i];if(!p)return false;"
            + "var u=uf(p),uv=u?u.value:'';if(!uv){try{uv=sessionStorage.getItem('__lumenU')||''}catch(x){}}B.pwPending(location.href,uv,p.value);return true}"
            + "function sub(){if(cap()){B.pwSubmit();setTimeout(function(){if(!pws().some(function(p){return p.value}))B.pwDone()},2500)}}"
            + "document.addEventListener('submit',sub,true);"
            + "document.addEventListener('keydown',function(e){if(e.key=='Enter'&&e.target&&e.target.tagName=='INPUT'&&pws().length)sub()},true);"
            + "document.addEventListener('click',function(e){var b=e.target&&e.target.closest&&e.target.closest('button,input[type=submit],input[type=button],[role=button],a');"
            + "if(b&&pws().some(function(p){return p.value}))sub()},true);"
            + "document.addEventListener('change',function(e){var t=e.target;if(t&&(isU(t)||(txt(t)&&!pws().length))){try{sessionStorage.setItem('__lumenU',t.value)}catch(x){}}},true);"
            + "document.addEventListener('focusin',function(e){var t=e.target;if(!t||t.tagName!='INPUT')return;"
            + "if(t.type=='password')B.pwFocus(1);else if(isU(t)||(pws().length&&uf(pws()[0])==t))B.pwFocus(0)},true);"
            + "document.addEventListener('focusout',function(e){if(e.target&&e.target.tagName=='INPUT')B.pwBlur()},true);"
            + "window.__lumenFill=function(u,p,auto){function set(e,v){if(!e)return;try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(e,v)}catch(x){e.value=v}"
            + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))}"
            + "var ps=pws(),a=document.activeElement;if(ps.length){var pf=a&&a.type=='password'&&vis(a)?a:ps[0];if(auto&&pf.value)return;"
            + "var u0=uf(pf)||(a&&a!=pf&&txt(a)?a:null);if(u0&&u&&!(auto&&u0.value))set(u0,u);set(pf,p);return}"
            + "if(!auto&&a&&txt(a)&&u)set(a,u)};})();";

    // ---------------------------------------------------------------- snackbar & notices
    void toast(String s) { snack(s, null, null); }

    void snack(String msg, String action, Runnable r) {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(() -> snack(msg, action, r)); return; }
        if (root == null || msg == null) return;
        if (snackView != null) { final View old = snackView; snackView = null; old.animate().cancel(); old.setVisibility(View.GONE); ui.post(() -> root.removeView(old)); }
        ui.removeCallbacks(snackHide);
        LinearLayout s = new LinearLayout(this);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setMinimumHeight(dp(50));
        s.setPadding(dp(18), dp(6), dp(action != null ? 6 : 18), dp(6));
        s.setBackground(Ui.round(Ui.dark ? 0xFFE8EAED : 0xFF303134, 14));
        s.setElevation(dp(10));
        TextView tv = Ui.text(this, msg, 14, Ui.dark ? 0xFF202124 : 0xFFF1F3F4);
        tv.setMaxLines(3);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (action != null) {
            TextView a = Ui.medium(Ui.text(this, action, 14, Ui.dark ? Ui.ACCENT_LIGHT[Ui.accent] : Ui.ACCENT_DARK[Ui.accent]));
            a.setPadding(dp(14), dp(12), dp(14), dp(12));
            a.setBackground(Ui.ripple(this, true));
            a.setOnClickListener(v -> { hideSnack(); if (r != null) ui.post(r); });
            s.addView(a);
        }
        int sw = scrWpx();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(sw - dp(24), dp(560)), WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        boolean fab = videoFab != null && videoFab.getVisibility() == View.VISIBLE;
        boolean bar = pwBar != null && pwBar.getVisibility() == View.VISIBLE;
        lp.bottomMargin = dp(fab ? 92 : bar ? 70 : 20);
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
        if (noticeView != null) root.removeView(noticeView);
        LinearLayout n = new LinearLayout(this);
        n.setGravity(Gravity.CENTER_VERTICAL);
        n.setPadding(dp(14), dp(10), dp(18), dp(10));
        n.setBackground(Ui.round(0xE6202124, 22));
        n.setElevation(dp(8));
        n.addView(Ui.icon(this, icon, Color.WHITE), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView tv = Ui.text(this, msg, 14, Color.WHITE);
        tv.setPadding(dp(10), 0, 0, 0);
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
        if (t.pendingUrl == null && t.web.getUrl() != null) {
            try { Bundle b = new Bundle(); if (t.web.saveState(b) != null) c.state = b; } catch (Exception ignored) { }
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
                if (c.state != null) { try { ok = t.web.restoreState(c.state) != null; } catch (Exception ignored) { } }
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
            try { auto.web.destroy(); } catch (Exception ignored) { }
        }
        if (sw) { switcherIncognito = last.incognito; buildSwitcher(); }
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
                if (switcher.getVisibility() == View.VISIBLE) buildSwitcher();
            }});
            m.add(new Object[]{R.drawable.ic_copy, L.t("Копировать ссылку"), (Runnable) () -> copy(u)});
            m.add(new Object[]{R.drawable.ic_share, L.t("Поделиться"), (Runnable) () -> share(u, t.title)});
        }
        sheetMenu(t.ntp ? L.t("Новая вкладка") : (t.title == null || t.title.isEmpty() ? displayUrl(u) : t.title), m);
    }

    static String hostOf(String u) {
        try { String h = Uri.parse(u).getHost(); if (h != null) return h.startsWith("www.") ? h.substring(4) : h; } catch (Exception ignored) { }
        return u == null ? "" : u;
    }

    // ---------------------------------------------------------------- pull to refresh
    boolean ptrArmed, ptrEngaged, ptrReady, ptrSpinning;
    float ptrX0, ptrY0, ptrBase;
    android.animation.ObjectAnimator ptrSpin;

    void buildPtr() {
        ptr = new FrameLayout(this);
        ptr.setBackground(Ui.oval(Ui.dark ? 0xFF3C4043 : Color.WHITE));
        ptr.setElevation(dp(6));
        ptrIcon = Ui.icon(this, R.drawable.ic_refresh, Ui.ACCENT);
        ptrIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ptrIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
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
                ptrArmed = store.bool("ptr", true) && !ptrSpinning && customView == null && !t.ntp && !isReelsUrl(t.pageUrl)
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
                    ptr.setBackground(Ui.oval(Ui.dark ? 0xFF3C4043 : Color.WHITE));
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
            ptr.setBackground(Ui.oval(ready ? Ui.ACCENT : (Ui.dark ? 0xFF3C4043 : Color.WHITE)));
            Ui.tint(ptrIcon, ready ? (Ui.dark ? 0xFF202124 : Color.WHITE) : Ui.ACCENT);
        }
    }

    void startRefresh(Tab t) {
        ptrSpinning = true;
        ptr.setBackground(Ui.oval(Ui.dark ? 0xFF3C4043 : Color.WHITE));
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
        fsTab = t;
        fsScrollY = t.web.getScrollY();
        fsJsScroll = null;
        try { t.web.evaluateJavascript(SCROLL_JS, v -> { if (v != null && v.matches("\"[\\d.]+,[\\d.]+\"")) fsJsScroll = v.replace("\"", ""); }); }
        catch (Exception ignored) { }
    }

    void restoreScroll() {
        final Tab t = fsTab;
        if (t == null || fsScrollY < 0) return;
        final int y = fsScrollY;
        final String js = fsJsScroll;
        fsExitAt = System.currentTimeMillis();
        for (int delay : new int[]{50, 250, 600, 1100, 1800}) {
            ui.postDelayed(() -> {
                if (t != current || customView != null || lastTouch > fsExitAt) return;
                if (Math.abs(t.web.getScrollY() - y) <= dp(6)) return;
                if (js != null) t.web.evaluateJavascript("window.scrollTo(" + js + ")", null);
                else t.web.scrollTo(t.web.getScrollX(), y);
            }, delay);
        }
        ui.postDelayed(() -> { fsTab = null; fsScrollY = -1; }, 2000);
    }

    // ---------------------------------------------------------------- passwords
    void maybeOfferSave(Tab t) {
        String site = t.pwSite, user = t.pwUser == null ? "" : t.pwUser, pass = t.pwPass;
        long at = t.pwSubmitAt;
        t.pwPass = null;
        t.pwSubmitAt = 0;
        if (pass == null || pass.isEmpty() || site == null || at == 0 || System.currentTimeMillis() - at > 60000) return;
        if (t.incognito || !store.bool("pwSave", true) || passwords.never().contains(site)) return;
        Passwords.Cred c = passwords.find(site, user);
        if (c != null && pass.equals(passwords.pass(c))) return;
        offerSave(site, user, pass, c != null);
    }

    void offerSave(String site, String user, String pass, boolean update) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), dp(8));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView bi = Ui.icon(this, R.drawable.ic_key, Ui.ON_TONAL);
        bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bi.setPadding(dp(12), dp(12), dp(12), dp(12));
        badge.addView(bi, new FrameLayout.LayoutParams(MATCH, MATCH));
        head.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(14), 0, 0, 0);
        tx.addView(Ui.medium(Ui.text(this, update ? L.t("Обновить пароль?") : L.t("Сохранить пароль?"), 18, Ui.TEXT)));
        tx.addView(Ui.single(this, site, 13, Ui.TEXT2));
        head.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        box.addView(head);
        EditText ue = new EditText(this);
        ue.setSingleLine(true);
        ue.setHint(L.t("Имя пользователя"));
        ue.setText(user);
        ue.setTextColor(Ui.TEXT);
        ue.setHintTextColor(Ui.TEXT2);
        ue.setEnabled(!update);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(MATCH, WRAP);
        ul.topMargin = dp(14);
        box.addView(ue, ul);
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        final TextView pt = Ui.text(this, mask(pass), 16, Ui.TEXT);
        pt.setPadding(dp(4), 0, 0, 0);
        pr.addView(pt, new LinearLayout.LayoutParams(0, WRAP, 1));
        ImageView eye = Ui.iconBtn(this, R.drawable.ic_eye, Ui.TEXT2);
        final boolean[] shown = {false};
        eye.setOnClickListener(v -> { shown[0] = !shown[0]; pt.setText(shown[0] ? pass : mask(pass)); });
        pr.addView(eye, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(pr);
        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, dp(14), 0, 0);
        TextView never = Ui.medium(Ui.text(this, update ? L.t("Не сейчас") : L.t("Никогда"), 15, Ui.ACCENT));
        never.setPadding(dp(14), dp(12), dp(14), dp(12));
        never.setBackground(Ui.ripple(this, true));
        btns.addView(never);
        btns.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView save = pillButton(update ? L.t("Обновить") : L.t("Сохранить"), R.drawable.ic_key, Ui.ACCENT, Ui.dark ? 0xFF202124 : Color.WHITE);
        btns.addView(save, new LinearLayout.LayoutParams(WRAP, dp(48)));
        box.addView(btns);
        final Dialog d = sheet(box);
        never.setOnClickListener(v -> {
            d.dismiss();
            if (!update) { passwords.addNever(site); snack(L.t("Пароли для ") + site + L.t(" не будут сохраняться"), L.t("Отменить"), () -> { java.util.Set<String> s = passwords.never(); s.remove(site); store.p.edit().putStringSet("pwNever", s).apply(); }); }
        });
        save.setOnClickListener(v -> {
            d.dismiss();
            passwords.put(site, ue.getText().toString().trim(), pass);
            snack(update ? L.t("Пароль обновлён") : L.t("Пароль сохранён"), L.t("Пароли"), this::showPasswords);
        });
    }

    static String mask(String p) { StringBuilder b = new StringBuilder(); for (int i = 0; i < Math.min(p.length(), 16); i++) b.append('•'); return b.toString(); }

    void showPwBar(Tab t) {
        ui.removeCallbacks(pwBlurR);
        if (t != current || !store.bool("pwFill", true) || t.ntp) return;
        String site = Passwords.site(t.web.getUrl());
        ArrayList<Passwords.Cred> cs = passwords.forSite(site);
        if (cs.isEmpty()) { hidePwBar(); return; }
        if (pwBar == null) {
            pwBar = new LinearLayout(this);
            pwBar.setGravity(Gravity.CENTER_VERTICAL);
            pwBar.setElevation(dp(8));
            content.addView(pwBar, new FrameLayout.LayoutParams(MATCH, dp(52), Gravity.BOTTOM));
        }
        pwBar.removeAllViews();
        pwBar.setBackgroundColor(t.incognito ? 0xFF303134 : (Ui.dark ? Ui.SURFACE : Color.WHITE));
        pwBar.setPadding(dp(12), 0, dp(4), 0);
        pwBar.addView(Ui.icon(this, R.drawable.ic_key, Ui.ACCENT), new LinearLayout.LayoutParams(dp(22), dp(22)));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(dp(8), 0, 0, 0);
        hs.addView(chips);
        for (Passwords.Cred c : cs) {
            TextView ch = Ui.medium(Ui.single(this, c.user.isEmpty() ? L.t("Без имени · ••••") : c.user, 14, Ui.ON_TONAL));
            ch.setPadding(dp(14), dp(8), dp(14), dp(8));
            ch.setBackground(Ui.round(Ui.TONAL, 16));
            ch.setOnClickListener(v -> fillCred(t, c));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
            cl.rightMargin = dp(8);
            chips.addView(ch, cl);
        }
        pwBar.addView(hs, new LinearLayout.LayoutParams(0, MATCH, 1));
        ImageView mg = Ui.iconBtn(this, R.drawable.ic_settings, Ui.TEXT2);
        mg.setOnClickListener(v -> showPasswords());
        pwBar.addView(mg, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ImageView cl = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
        cl.setOnClickListener(v -> hidePwBar());
        pwBar.addView(cl, new LinearLayout.LayoutParams(dp(40), dp(44)));
        pwBar.setVisibility(View.VISIBLE);
        pwBar.bringToFront();
    }

    void hidePwBar() { if (pwBar != null) pwBar.setVisibility(View.GONE); }

    void fillCred(Tab t, Passwords.Cred c) {
        String site = Passwords.site(t.web.getUrl());
        if (site == null || !site.equals(c.site)) { hidePwBar(); return; }
        String p = passwords.pass(c);
        if (p == null) { toast(L.t("Не удалось расшифровать пароль")); return; }
        t.web.evaluateJavascript("window.__lumenFill&&window.__lumenFill(" + JSONObject.quote(c.user) + "," + JSONObject.quote(p) + ",0)", null);
        hidePwBar();
    }

    void autoFill(Tab t) {
        if (!store.bool("pwFill", true) || !store.bool("pwAuto", true)) return;
        String site = Passwords.site(t.web.getUrl());
        ArrayList<Passwords.Cred> cs = passwords.forSite(site);
        if (cs.size() != 1) return;
        String p = passwords.pass(cs.get(0));
        if (p == null) return;
        t.web.evaluateJavascript("window.__lumenFill&&window.__lumenFill(" + JSONObject.quote(cs.get(0).user) + "," + JSONObject.quote(p) + ",1)", null);
    }

    void withAuth(Runnable r) {
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure() || System.currentTimeMillis() < authUntil) { r.run(); return; }
        @SuppressWarnings("deprecation")
        Intent i = km.createConfirmDeviceCredentialIntent("Lasur", L.t("Подтвердите, что это вы, чтобы просмотреть пароль"));
        if (i == null) { r.run(); return; }
        pendingAuth = r;
        startActivityForResult(i, REQ_AUTH);
    }

    void copySecret(String s) {
        ClipData cd = ClipData.newPlainText("password", s);
        if (Build.VERSION.SDK_INT >= 24) {
            android.os.PersistableBundle e = new android.os.PersistableBundle();
            e.putBoolean("android.content.extra.IS_SENSITIVE", true);
            cd.getDescription().setExtras(e);
        }
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(cd);
        toast(L.t("Пароль скопирован"));
    }

    void showPasswords() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(24));
        sv.addView(box);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            switchRow(box, L.t("Предлагать сохранять пароли"), L.t("После входа на сайт"), store.bool("pwSave", true), v -> store.setBool("pwSave", v));
            switchRow(box, L.t("Автозаполнение"), L.t("Показывать сохранённые аккаунты над клавиатурой"), store.bool("pwFill", true), v -> store.setBool("pwFill", v));
            switchRow(box, L.t("Заполнять при открытии"), L.t("Если для сайта сохранён один аккаунт"), store.bool("pwAuto", true), v -> store.setBool("pwAuto", v));
            java.util.Set<String> nv = passwords.never();
            if (!nv.isEmpty()) actionRow(box, L.t("Сайты-исключения: ") + nv.size(), L.t("Нажмите, чтобы снова предлагать сохранение везде"), () -> {
                passwords.clearNever(); toast(L.t("Исключения очищены")); fill[0].run();
            });
            section(box, passwords.list.isEmpty() ? L.t("Сохранённые пароли") : L.t("Сохранённые пароли · ") + passwords.list.size());
            if (passwords.list.isEmpty()) {
                TextView e = Ui.text(this, L.t("Пока пусто. Войдите на любой сайт — Lasur предложит сохранить пароль. Пароли шифруются ключом в защищённом хранилище Android."), 14, Ui.TEXT2);
                e.setPadding(dp(20), dp(8), dp(20), 0);
                box.addView(e);
            }
            for (Passwords.Cred c : new ArrayList<>(passwords.list)) {
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(10), dp(8), dp(10));
                r.setBackground(Ui.ripple(this, false));
                r.addView(tileIcon(c.site, "https://" + c.site, false, 0, 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(16), 0, dp(8), 0);
                tx.addView(Ui.single(this, c.site, 15, Ui.TEXT));
                tx.addView(Ui.single(this, c.user.isEmpty() ? L.t("без имени пользователя") : c.user, 13, Ui.TEXT2));
                r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
                r.setOnClickListener(v -> {
                    ArrayList<Object[]> m = new ArrayList<>();
                    m.add(new Object[]{R.drawable.ic_eye, L.t("Показать пароль"), (Runnable) () -> withAuth(() -> {
                        String p = passwords.pass(c);
                        new AlertDialog.Builder(this).setTitle(c.site).setMessage((c.user.isEmpty() ? "" : c.user + "\n\n") + (p == null ? L.t("Не удалось расшифровать") : p))
                                .setPositiveButton(L.t("Готово"), null).setNeutralButton(L.t("Копировать"), (d, w) -> { if (p != null) copySecret(p); }).show();
                    })});
                    if (!c.user.isEmpty()) m.add(new Object[]{R.drawable.ic_copy, L.t("Копировать имя пользователя"), (Runnable) () -> copy(c.user)});
                    m.add(new Object[]{R.drawable.ic_key, L.t("Копировать пароль"), (Runnable) () -> withAuth(() -> { String p = passwords.pass(c); if (p != null) copySecret(p); })});
                    m.add(new Object[]{R.drawable.ic_globe, L.t("Открыть сайт"), (Runnable) () -> newTab("https://" + c.site, false, true, null)});
                    m.add(new Object[]{R.drawable.ic_close, L.t("Удалить"), (Runnable) () -> {
                        passwords.remove(c);
                        fill[0].run();
                        snack(L.t("Пароль удалён"), L.t("Отменить"), () -> { passwords.list.add(0, c); passwords.save(); fill[0].run(); });
                    }});
                    sheetMenu(c.site + (c.user.isEmpty() ? "" : " · " + c.user), m);
                });
                box.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
        };
        fill[0].run();
        fullDialog(L.t("Пароли"), sv, null, null);
    }

    // ---------------------------------------------------------------- download completion notice
    void registerDlReceiver() {
        dlReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id < 0) return;
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cur == null || !cur.moveToFirst()) return;
                    int st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    String title = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                    String mime = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE));
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        Uri uri = dm.getUriForDownloadedFile(id);
                        snack(L.t("Загружено: ") + title, L.t("Открыть"), () -> {
                            try {
                                startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "*/*" : mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), L.t("Открыть с помощью")));
                            } catch (Exception e) { showDownloads(); }
                        });
                    } else if (st == DownloadManager.STATUS_FAILED) snack(L.t("Не удалось загрузить: ") + title, L.t("Загрузки"), MainActivity.this::showDownloads);
                } catch (Exception ignored) { }
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(dlReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(dlReceiver, f);
        } catch (Exception ignored) { dlReceiver = null; }
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        ui.postDelayed(() -> {
            refreshStrip();
            if (switcher.getVisibility() == View.VISIBLE) buildSwitcher();
            else if (current != null && current.ntp && customView == null) refreshChrome();
        }, 150);
    }

    // ================================================================== v1.4 additions
    void savePage(Tab t) {
        if (t == null || t.ntp) { toast(L.t("Откройте страницу, чтобы скачать её")); return; }
        String u = t.web.getUrl();
        if (u == null || !(u.startsWith("http://") || u.startsWith("https://"))) { toast(L.t("Эту страницу нельзя скачать")); return; }
        String path = Uri.parse(u).getPath();
        String fn = fileName(u, null, null);
        boolean file = path != null && path.matches(".*\\.(pdf|zip|rar|7z|apk|docx?|xlsx?|pptx?|mp3|mp4|webm|mkv|jpg|jpeg|png|gif|webp|txt|epub)$");
        if (file) { confirmDownload(u, fn, null, u, uaOf(t), -1); return; }
        String title = t.title == null || t.title.trim().isEmpty() ? hostOf(u) : t.title.trim();
        final String name = Saver.clean(title.length() > 80 ? title.substring(0, 80) : title) + ".mht";
        final java.io.File tmp = new java.io.File(getCacheDir(), "page_" + System.currentTimeMillis() + ".mht");
        snack(L.t("Сохранение страницы…"), null, null);
        try {
            t.web.saveWebArchive(tmp.getAbsolutePath(), false, res -> {
                if (res == null || !tmp.exists()) { toast(L.t("Не удалось сохранить страницу")); return; }
                withStorage(() -> new Thread(() -> {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(tmp)) {
                        Saver s = Saver.create(this, name, "multipart/related");
                        byte[] b = new byte[65536];
                        int n;
                        while ((n = in.read(b)) > 0) s.out.write(b, 0, n);
                        s.finish();
                        snack(L.t("Страница сохранена: ") + s.name, L.t("Загрузки"), this::showDownloads);
                    } catch (Exception e) { toast(L.t("Не удалось сохранить страницу: ") + e.getMessage()); }
                    finally { tmp.delete(); }
                }).start());
            });
        } catch (Exception e) { toast(L.t("Не удалось сохранить страницу")); }
    }

    // ---------------------------------------------------------------- ad blocker panel
    void showAdblock() {
        final Tab t = current;
        final boolean page = t != null && !t.ntp && t.pageHost != null;
        final String host = page ? t.pageHost : null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(8));
        final Dialog[] d = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            boolean on = AdBlocker.enabled;
            boolean wl = host != null && AdBlocker.whitelist.contains(host);
            boolean active = on && !wl;
            // header
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(22), dp(2), dp(22), dp(10));
            FrameLayout badge = new FrameLayout(this);
            int good = Ui.dark ? 0xFF81C995 : 0xFF188038;
            badge.setBackground(Ui.round(active ? (Ui.dark ? 0xFF1E3A2B : 0xFFE6F4EA) : (Ui.dark ? 0xFF3C4043 : 0xFFF1F3F4), 16));
            ImageView bi = Ui.icon(this, R.drawable.ic_shield, active ? good : Ui.TEXT2);
            bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bi.setPadding(dp(12), dp(12), dp(12), dp(12));
            badge.addView(bi, new FrameLayout.LayoutParams(MATCH, MATCH));
            head.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(52)));
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(dp(14), 0, 0, 0);
            tx.addView(Ui.medium(Ui.text(this, L.t("Блокировщик рекламы"), 18, Ui.TEXT)));
            tx.addView(Ui.single(this, !on ? L.t("Выключен") : wl ? L.t("Отключён на ") + host : host != null ? L.t("Защищает ") + host : L.t("Включён"), 13, active ? good : Ui.TEXT2));
            head.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
            box.addView(head);
            // counters
            LinearLayout stats = new LinearLayout(this);
            stats.setPadding(dp(16), 0, dp(16), dp(6));
            String[][] st = {{page ? String.valueOf(t.blocked.get()) : "—", L.t("на этой странице")}, {String.valueOf(AdBlocker.totalBlocked.get()), L.t("заблокировано всего")}};
            for (String[] s : st) {
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(dp(16), dp(12), dp(16), dp(12));
                c.setBackground(Ui.round(Ui.dark ? 0xFF303134 : 0xFFF1F3F4, 16));
                c.addView(Ui.medium(Ui.text(this, s[0], 22, Ui.TEXT)));
                c.addView(Ui.single(this, s[1], 12, Ui.TEXT2));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, WRAP, 1);
                cl.setMargins(dp(6), 0, dp(6), 0);
                stats.addView(c, cl);
            }
            box.addView(stats);
            switchRow(box, L.t("Блокировать рекламу"), L.t("Реклама, трекеры и баннеры на всех сайтах"), on, v -> {
                store.setBool("adblock", v); AdBlocker.enabled = v; fill[0].run();
                if (page) t.web.reload();
            });
            if (host != null && on) switchRow(box, L.t("Блокировать на этом сайте"), wl ? L.t("Сайт в исключениях — реклама показывается") : L.t("Выключите, если сайт работает неправильно"), !wl, v -> {
                store.setWhitelisted(host, !v); AdBlocker.whitelist = store.whitelist(); fill[0].run(); t.web.reload();
            });
            switchRow(box, L.t("Блокировать всплывающие окна"), L.t("И рекламные переходы без нажатия"), store.blockPopups(), v -> store.setBool("popups", v));
            final TextView[] ls = new TextView[1];
            ls[0] = actionRow(box, L.t("Обновить фильтры"), AdBlocker.stats(), () -> {
                ls[0].setText(L.t("Загрузка…"));
                new Thread(() -> {
                    try { AdBlocker.update(getApplicationContext()); ui.post(() -> { ls[0].setText(L.t("Обновлено · ") + AdBlocker.stats()); snack(L.t("Фильтры обновлены"), null, null); }); }
                    catch (Exception e) { ui.post(() -> ls[0].setText(L.t("Ошибка: ") + e.getMessage())); }
                }).start();
            });
            java.util.Set<String> wls = AdBlocker.whitelist;
            if (!wls.isEmpty()) {
                section(box, L.t("Сайты-исключения · ") + wls.size());
                for (String h : new java.util.TreeSet<>(wls)) {
                    LinearLayout r = new LinearLayout(this);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    r.setPadding(dp(20), dp(6), dp(8), dp(6));
                    r.addView(tileIcon(h, "https://" + h, false, 0, 30), new LinearLayout.LayoutParams(dp(30), dp(30)));
                    TextView ht = Ui.single(this, h, 15, Ui.TEXT);
                    ht.setPadding(dp(14), 0, 0, 0);
                    r.addView(ht, new LinearLayout.LayoutParams(0, WRAP, 1));
                    ImageView del = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
                    del.setOnClickListener(v -> { store.setWhitelisted(h, false); AdBlocker.whitelist = store.whitelist(); fill[0].run(); if (h.equals(host)) t.web.reload(); });
                    r.addView(del, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    box.addView(r);
                }
            }
            TextView hint = Ui.text(this, L.t("Списки: EasyList, RuAdList, AdGuard Russian и базы рекламных доменов. Видеореклама YouTube пропускается автоматически."), 12, Ui.TEXT2);
            hint.setPadding(dp(22), dp(10), dp(22), 0);
            box.addView(hint);
        };
        fill[0].run();
        d[0] = sheet(box);
    }

    static boolean isReelsUrl(String u) {
        if (u == null) return false;
        String l = u.toLowerCase(Locale.ROOT);
        return l.contains("/shorts") || l.contains("/reels") || l.contains("/reel/") || l.contains("/clips") || l.contains("/clip/")
                || l.contains("tiktok.com") || l.contains("likee.") || l.contains("/video/") && l.contains("vk.com") || l.contains("yappy")
                || l.contains("rutube.ru/shorts") || l.contains("dzen.ru/shorts") || l.contains("/stories");
    }

    /** The home page covers the web page: stop its video/audio (also inside frames) so nothing keeps playing behind it. */
    void silence(Tab t) {
        if (t == null || t.web == null) return;
        if (customView != null) hideCustomView();
        try { t.web.evaluateJavascript(PAUSE_JS + "(function(){try{var f=document.querySelectorAll('iframe');for(var i=0;i<f.length;i++){try{f[i].contentWindow.postMessage('{\\\"event\\\":\\\"command\\\",\\\"func\\\":\\\"pauseVideo\\\",\\\"args\\\":\\\"\\\"}','*')}catch(e){}}}catch(e){}})();", null); } catch (Exception ignored) { }
        try { t.web.onPause(); t.silenced = true; } catch (Exception ignored) { }
        if (t.video != null) updateVideoFab();
    }

    // ================================================================== v1.5 additions
    // ---------------------------------------------------------------- file names for downloads
    static String extOf(String n) {
        if (n == null) return "";
        int q = n.indexOf('?'); if (q >= 0) n = n.substring(0, q);
        int d = n.lastIndexOf('.'), s = n.lastIndexOf('/');
        if (d < 0 || d < s || n.length() - d > 8) return "";
        return n.substring(d + 1).toLowerCase(Locale.ROOT);
    }

    static boolean genericMime(String m) {
        if (m == null || m.isEmpty()) return true;
        m = m.toLowerCase(Locale.ROOT);
        return m.startsWith("application/octet-stream") || m.startsWith("binary/") || m.startsWith("application/force-download")
                || m.startsWith("application/x-download") || m.startsWith("application/download") || m.startsWith("application/unknown");
    }

    /** Real file name of a download: Content-Disposition → URL path → query parameter → MIME type. Never ".bin" when the real type is known. */
    static String fileName(String url, String cd, String mime) {
        String n = null;
        if (cd != null) {
            Matcher m = Pattern.compile("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE).matcher(cd);
            if (m.find()) {
                try { n = java.net.URLDecoder.decode(m.group(2).trim().replace("+", "%2B"), m.group(1).trim().isEmpty() ? "UTF-8" : m.group(1).trim()); }
                catch (Exception ignored) { }
            }
            if (n == null || n.isEmpty()) {
                m = Pattern.compile("filename\\s*=\\s*(\"([^\"]*)\"|([^;]+))", Pattern.CASE_INSENSITIVE).matcher(cd);
                if (m.find()) {
                    n = (m.group(2) != null ? m.group(2) : m.group(3)).trim();
                    try { if (n.contains("%")) n = java.net.URLDecoder.decode(n.replace("+", "%2B"), "UTF-8"); } catch (Exception ignored) { }
                }
            }
        }
        Uri u = null;
        try { u = Uri.parse(url); } catch (Exception ignored) { }
        if ((n == null || n.isEmpty()) && u != null && u.getLastPathSegment() != null && !extOf(u.getLastPathSegment()).isEmpty()) n = u.getLastPathSegment();
        if ((n == null || n.isEmpty()) && u != null && u.isHierarchical()) {
            for (String k : new String[]{"filename", "file", "name", "fn", "title", "response-content-disposition"}) {
                try {
                    String v = u.getQueryParameter(k);
                    if (v == null) continue;
                    if (k.startsWith("response")) { String g = fileName("", v, null); if (!extOf(g).isEmpty()) { n = g; break; } continue; }
                    if (!extOf(v).isEmpty()) { n = v; break; }
                } catch (Exception ignored) { }
            }
        }
        if (n == null || n.isEmpty()) n = URLUtil.guessFileName(url, null, genericMime(mime) ? null : mime);
        n = n.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_").trim();
        if (n.isEmpty()) n = "download";
        String ext = extOf(n);
        if (ext.isEmpty() || ext.equals("bin")) {
            String base = ext.equals("bin") ? n.substring(0, n.length() - 4) : n;
            String urlExt = u != null && u.getLastPathSegment() != null ? extOf(u.getLastPathSegment()) : "";
            String mm = mime == null ? "" : mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
            String fromMime = mm.equals("application/vnd.android.package-archive") ? "apk" : android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mm);
            if (!urlExt.isEmpty() && !urlExt.equals("bin")) n = base + "." + urlExt;
            else if (fromMime != null && !genericMime(mm)) n = base + "." + fromMime;
            else if (ext.equals("bin") && !base.isEmpty()) n = base + ".bin";
        }
        return n;
    }

    /** Correct MIME type for the saved file so it opens in the right app (APK → installer, etc.). */
    static String mimeFor(String name, String mime) {
        String ext = extOf(name);
        if (ext.equals("apk")) return "application/vnd.android.package-archive";
        String m = ext.isEmpty() ? null : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (genericMime(mime)) return m != null ? m : (mime == null || mime.isEmpty() ? null : mime.split(";")[0].trim());
        return mime.split(";")[0].trim();
    }

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
        box.setPadding(dp(22), dp(4), dp(22), dp(8));
        box.addView(sheetHead(R.drawable.ic_add, L.t("Открыть новую вкладку?"),
                (oh != null ? oh : L.t("Сайт")) + L.t(" хочет открыть ") + (target != null ? hostOf(target) : L.t("новую вкладку"))));
        if (target != null) {
            TextView ut = Ui.text(this, target, 12, Ui.TEXT2);
            ut.setMaxLines(2);
            ut.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ut.setPadding(0, dp(10), 0, 0);
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
        try { nt.web.stopLoading(); nt.web.destroy(); } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- shared sheet pieces
    View sheetHead(int icon, String title, String sub) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView bi = Ui.icon(this, icon, Ui.ON_TONAL);
        bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bi.setPadding(dp(12), dp(12), dp(12), dp(12));
        badge.addView(bi, new FrameLayout.LayoutParams(MATCH, MATCH));
        head.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(14), 0, 0, 0);
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
        btns.setPadding(0, dp(16), 0, 0);
        TextView n = Ui.medium(Ui.text(this, no, 15, Ui.ACCENT));
        n.setGravity(Gravity.CENTER);
        n.setPadding(dp(16), 0, dp(16), 0);
        n.setBackground(Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 24));
        TextView y = Ui.medium(Ui.text(this, yes, 15, Ui.dark ? 0xFF202124 : Color.WHITE));
        y.setGravity(Gravity.CENTER);
        y.setBackground(Ui.round(Ui.ACCENT, 24));
        y.setForeground(Ui.ripple(this, false));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, dp(48), 1);
        nl.rightMargin = dp(10);
        btns.addView(n, nl);
        btns.addView(y, new LinearLayout.LayoutParams(0, dp(48), 1));
        box.addView(btns);
        return new TextView[]{n, y};
    }

    // ---------------------------------------------------------------- site permissions (camera, microphone, location)
    interface PermCb { void done(boolean allow); }
    Runnable sitePermAfter;
    static final int REQ_SITE_PERM = 17;
    static final String[] KIND_NAMES = {"mic", "cam", "geo"};

    static String kindLabel(String k) { return k.equals("mic") ? L.t("микрофон") : k.equals("cam") ? L.t("камеру") : L.t("ваше местоположение"); }
    static String kindTitle(String k) { return k.equals("mic") ? L.t("Микрофон") : k.equals("cam") ? L.t("Камера") : L.t("Местоположение"); }
    static int kindIcon(String k) { return k.equals("mic") ? R.drawable.ic_mic : k.equals("cam") ? R.drawable.ic_video : R.drawable.ic_globe; }
    static String[] kindPerms(String k) {
        return k.equals("mic") ? new String[]{Manifest.permission.RECORD_AUDIO} : k.equals("cam") ? new String[]{Manifest.permission.CAMERA}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }
    boolean hasAndroid(String k) {
        if (k.equals("geo")) return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(kindPerms(k)[0]) == PackageManager.PERMISSION_GRANTED;
    }
    int siteDecision(String host, String k) { return host == null ? 0 : store.p.getInt("sp_" + k + "_" + host, 0); }

    /** Asks the user (once per site, remembered) and then Android itself, if needed. */
    void sitePermission(Tab t, String host, ArrayList<String> kinds, java.util.function.Consumer<ArrayList<String>> result) {
        ArrayList<String> allowed = new ArrayList<>(), ask = new ArrayList<>();
        for (String k : kinds) {
            int dcs = siteDecision(host, k);
            if (dcs == 1) allowed.add(k); else if (dcs == 0) ask.add(k);
        }
        Runnable finish = () -> {
            ArrayList<String> need = new ArrayList<>();
            for (String k : allowed) if (!hasAndroid(k)) for (String p : kindPerms(k)) need.add(p);
            Runnable deliver = () -> {
                ArrayList<String> ok = new ArrayList<>();
                ArrayList<String> denied = new ArrayList<>();
                for (String k : allowed) if (hasAndroid(k)) ok.add(k); else denied.add(k);
                if (!denied.isEmpty()) snack(L.t("Нет доступа: ") + kindTitle(denied.get(0)).toLowerCase(Locale.ROOT) + L.t(". Разрешите его Lasur в настройках Android"), L.t("Настройки"), () -> {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
                });
                result.accept(ok);
            };
            if (need.isEmpty()) deliver.run();
            else { sitePermAfter = deliver; requestPermissions(need.toArray(new String[0]), REQ_SITE_PERM); }
        };
        if (ask.isEmpty()) { finish.run(); return; }
        StringBuilder what = new StringBuilder();
        for (int i = 0; i < ask.size(); i++) what.append(i == 0 ? "" : i == ask.size() - 1 ? L.t(" и ") : ", ").append(kindLabel(ask.get(i)));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), dp(8));
        box.addView(sheetHead(kindIcon(ask.get(0)), host == null ? L.t("Сайт") : host, L.t("запрашивает доступ: ") + what));
        TextView hint = Ui.text(this, t.incognito ? L.t("В режиме инкогнито решение действует до закрытия вкладки.") : L.t("Решение запомнится для этого сайта. Изменить его можно в Настройках → Разрешения сайтов."), 12, Ui.TEXT2);
        hint.setPadding(0, dp(10), 0, 0);
        box.addView(hint);
        final boolean[] answered = {false};
        TextView[] b = sheetButtons(box, L.t("Блокировать"), L.t("Разрешить"));
        final Dialog d = sheet(box);
        permDialog = d;
        d.setOnDismissListener(x -> { if (permDialog == d) permDialog = null; if (!answered[0]) { answered[0] = true; finish.run(); } });
        b[0].setOnClickListener(v -> {
            answered[0] = true;
            if (!t.incognito && host != null) for (String k : ask) store.p.edit().putInt("sp_" + k + "_" + host, 2).apply();
            d.dismiss();
            finish.run();
        });
        b[1].setOnClickListener(v -> {
            answered[0] = true;
            if (!t.incognito && host != null) for (String k : ask) store.p.edit().putInt("sp_" + k + "_" + host, 1).apply();
            allowed.addAll(ask);
            d.dismiss();
            finish.run();
        });
    }
    Dialog permDialog;
    android.webkit.PermissionRequest permReq;

    void handlePermission(Tab t, android.webkit.PermissionRequest r) {
        String host = hostOf(r.getOrigin().toString());
        ArrayList<String> kinds = new ArrayList<>();
        boolean drm = false;
        for (String res : r.getResources()) {
            if (res.equals(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) kinds.add("mic");
            else if (res.equals(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) kinds.add("cam");
            else if (res.equals(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) drm = true;
        }
        final boolean wantDrm = drm;
        if (kinds.isEmpty()) {
            if (wantDrm) r.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}); else r.deny();
            return;
        }
        permReq = r;
        sitePermission(t, host, kinds, ok -> {
            if (permReq == r) permReq = null;
            ArrayList<String> g = new ArrayList<>();
            if (ok.contains("mic")) g.add(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            if (ok.contains("cam")) g.add(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            if (wantDrm) g.add(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
            try { if (g.isEmpty()) r.deny(); else r.grant(g.toArray(new String[0])); } catch (Exception ignored) { }
        });
    }

    void handleGeo(Tab t, String origin, android.webkit.GeolocationPermissions.Callback cb) {
        ArrayList<String> k = new ArrayList<>();
        k.add("geo");
        sitePermission(t, hostOf(origin), k, ok -> cb.invoke(origin, ok.contains("geo"), false));
    }

    void showSitePermissions() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(24));
        sv.addView(box);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            java.util.TreeMap<String, ArrayList<String[]>> by = new java.util.TreeMap<>();
            for (java.util.Map.Entry<String, ?> e : store.p.getAll().entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("sp_") || !(e.getValue() instanceof Integer)) continue;
                String rest = key.substring(3);
                int us = rest.indexOf('_');
                if (us < 0) continue;
                String kind = rest.substring(0, us), host = rest.substring(us + 1);
                by.computeIfAbsent(host, x -> new ArrayList<>()).add(new String[]{kind, String.valueOf(e.getValue()), key});
            }
            java.util.Set<String> pa = store.p.getStringSet("popupAllow", new HashSet<>());
            if (by.isEmpty() && pa.isEmpty()) {
                TextView e = Ui.text(this, L.t("Здесь появятся сайты, которым вы разрешили или запретили доступ к микрофону, камере и местоположению."), 14, Ui.TEXT2);
                e.setPadding(dp(20), dp(24), dp(20), 0);
                box.addView(e);
            }
            for (java.util.Map.Entry<String, ArrayList<String[]>> e : by.entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (String[] k : e.getValue()) sb.append(sb.length() == 0 ? "" : " · ").append(kindTitle(k[0])).append(k[1].equals("1") ? L.t(": разрешено") : L.t(": заблокировано"));
                final String host = e.getKey();
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(10), dp(8), dp(10));
                r.addView(tileIcon(host, "https://" + host, false, 0, 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(16), 0, dp(8), 0);
                tx.addView(Ui.single(this, host, 15, Ui.TEXT));
                TextView st = Ui.text(this, sb.toString(), 13, Ui.TEXT2);
                tx.addView(st);
                r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
                TextView reset = Ui.medium(Ui.text(this, L.t("Сбросить"), 14, Ui.ACCENT));
                reset.setPadding(dp(12), dp(10), dp(12), dp(10));
                reset.setBackground(Ui.ripple(this, true));
                reset.setOnClickListener(v -> { android.content.SharedPreferences.Editor ed = store.p.edit(); for (String[] k : e.getValue()) ed.remove(k[2]); ed.apply(); fill[0].run(); });
                r.addView(reset);
                box.addView(r);
            }
            if (!pa.isEmpty()) {
                section(box, L.t("Всегда открывают новые вкладки без вопроса"));
                for (String h : new java.util.TreeSet<>(pa)) {
                    LinearLayout r = new LinearLayout(this);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    r.setPadding(dp(20), dp(6), dp(8), dp(6));
                    TextView ht = Ui.single(this, h, 15, Ui.TEXT);
                    r.addView(ht, new LinearLayout.LayoutParams(0, WRAP, 1));
                    ImageView del = Ui.iconBtn(this, R.drawable.ic_close, Ui.TEXT2);
                    del.setOnClickListener(v -> {
                        java.util.Set<String> s = new HashSet<>(store.p.getStringSet("popupAllow", new HashSet<>()));
                        s.remove(h); store.p.edit().putStringSet("popupAllow", s).apply(); fill[0].run();
                    });
                    r.addView(del, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    box.addView(r);
                }
            }
        };
        fill[0].run();
        fullDialog(L.t("Разрешения сайтов"), sv, null, null);
    }

    // ================================================================== v1.6 additions
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(L.wrap(base)); }

    // ---------------------------------------------------------------- launch animation
    FrameLayout splash;

    void showSplash() {
        splash = new FrameLayout(this);
        splash.setBackgroundColor(Ui.BG);
        splash.setClickable(true);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        c.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));
        TextView name = new TextView(this);
        name.setText("Lasur");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextColor(Ui.TEXT);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(WRAP, WRAP);
        nl.topMargin = dp(18);
        c.addView(name, nl);
        c.setClipChildren(false);
        c.setClipToPadding(false);
        c.setPadding(0, dp(24), 0, dp(24));
        splash.setClipChildren(false);
        splash.addView(c, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER));
        root.addView(splash, new FrameLayout.LayoutParams(MATCH, MATCH));
        boolean sys = Build.VERSION.SDK_INT >= 31; // system splash already showed the icon: continue from it
        logo.setScaleX(sys ? 1.25f : 0.55f);
        logo.setScaleY(sys ? 1.25f : 0.55f);
        logo.setAlpha(sys ? 1f : 0f);
        logo.setRotation(sys ? 0 : -25);
        name.setAlpha(0f);
        name.setTranslationY(dp(14));
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0).setDuration(360)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
        name.animate().alpha(1f).translationY(0).setStartDelay(140).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        ui.postDelayed(() -> {
            if (splash == null) return;
            final View s = splash;
            splash = null;
            logo.animate().scaleX(1.12f).scaleY(1.12f).setStartDelay(0).setDuration(220).start();
            s.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                root.removeView(s);
                if (!store.bool("onboarded", false)) showWelcome();
            }).start();
        }, 620);
    }

    // ---------------------------------------------------------------- welcome screen (first launch)
    FrameLayout welcome;
    int wStep, wEngine, wMode, wAccent;

    void showWelcome() {
        wStep = 0;
        wEngine = store.engine();
        wMode = Ui.mode;
        wAccent = Ui.accent;
        welcome = new FrameLayout(this);
        welcome.setBackgroundColor(Ui.BG);
        welcome.setClickable(true);
        root.addView(welcome, new FrameLayout.LayoutParams(MATCH, MATCH));
        welcome.setAlpha(0f);
        welcome.animate().alpha(1f).setDuration(200).start();
        buildWelcome();
    }

    void buildWelcome() {
        welcome.removeAllViews();
        int scrW = scrWpx();
        int side = Math.max(dp(24), (scrW - dp(560)) / 2);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        welcome.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(side - dp(12), dp(8), side - dp(12), 0);
        if (wStep > 0) {
            ImageView back = Ui.iconBtn(this, R.drawable.ic_back, Ui.TEXT2);
            back.setOnClickListener(v -> { wStep--; buildWelcome(); });
            top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        top.addView(new View(this), new LinearLayout.LayoutParams(0, dp(48), 1));
        if (wStep < 3) {
            TextView skip = Ui.medium(Ui.text(this, L.t("Пропустить"), 15, Ui.TEXT2));
            skip.setPadding(dp(12), dp(12), dp(12), dp(12));
            skip.setBackground(Ui.ripple(this, true));
            skip.setOnClickListener(v -> finishWelcome());
            top.addView(skip);
        }
        col.addView(top);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(side, dp(8), side, dp(16));
        sv.addView(box, new FrameLayout.LayoutParams(MATCH, WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1));

        if (wStep == 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.logo);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(96), dp(96));
            ll.topMargin = dp(16);
            box.addView(logo, ll);
            welcomeTitle(box, L.t("Добро пожаловать в Lasur"), L.t("Быстрый браузер без рекламы"));
            String[][] f = {{L.t("Без рекламы"), L.t("Блокировка рекламы, трекеров и всплывающих окон")},
                    {L.t("Видео с сайтов"), L.t("Скачивание и просмотр во внешнем плеере")},
                    {L.t("Пароли"), L.t("Безопасное хранение и автозаполнение")},
                    {L.t("Темы и обои"), L.t("Светлая, тёмная и чёрная тема, 7 цветов")}};
            int[] ic = {R.drawable.ic_shield, R.drawable.ic_download, R.drawable.ic_key, R.drawable.ic_palette};
            for (int i = 0; i < f.length; i++) box.addView(featureRow(ic[i], L.t(f[i][0]), L.t(f[i][1])), new LinearLayout.LayoutParams(MATCH, WRAP));
            LinearLayout lr = new LinearLayout(this);
            lr.setGravity(Gravity.CENTER_VERTICAL);
            lr.setPadding(dp(16), dp(10), dp(18), dp(10));
            lr.setBackground(Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 22));
            lr.addView(Ui.icon(this, R.drawable.ic_translate, Ui.ACCENT), new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView lt = Ui.medium(Ui.text(this, langName(), 14, Ui.TEXT));
            lt.setPadding(dp(10), 0, 0, 0);
            lr.addView(lt);
            lr.setOnClickListener(v -> pickLanguage());
            LinearLayout.LayoutParams lrl = new LinearLayout.LayoutParams(WRAP, WRAP);
            lrl.topMargin = dp(22);
            box.addView(lr, lrl);
        } else if (wStep == 1) {
            welcomeTitle(box, L.t("Поисковая система"), L.t("Её можно поменять в настройках"));
            for (int i = 0; i < Store.ENGINES.length; i++) {
                final int k = i;
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(14), dp(16), dp(14));
                boolean sel = wEngine == i;
                r.setBackground(sel ? Ui.stroke(Ui.TONAL, Ui.ACCENT, 2, 18) : Ui.round(Ui.dark ? 0xFF303134 : 0xFFF1F3F4, 18));
                r.addView(tileIcon(L.t(Store.ENGINES[i]), Store.ENGINE_URLS[i], false, 0, 32), new LinearLayout.LayoutParams(dp(32), dp(32)));
                TextView tv = Ui.medium(Ui.text(this, L.t(Store.ENGINES[i]), 16, sel ? Ui.ON_TONAL : Ui.TEXT));
                tv.setPadding(dp(14), 0, 0, 0);
                r.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
                if (sel) r.addView(Ui.icon(this, R.drawable.ic_check, Ui.ACCENT), new LinearLayout.LayoutParams(dp(22), dp(22)));
                r.setOnClickListener(v -> { wEngine = k; buildWelcome(); });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
                rl.topMargin = dp(10);
                box.addView(r, rl);
            }
        } else if (wStep == 2) {
            welcomeTitle(box, L.t("Оформление"), L.t("Тему, цвет и обои можно поменять в любой момент"));
            LinearLayout modes = new LinearLayout(this);
            modes.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < Ui.MODE_NAMES.length; i++) {
                final int m = i;
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(16), dp(13), dp(16), dp(13));
                boolean sel = wMode == i;
                r.setBackground(sel ? Ui.stroke(Ui.TONAL, Ui.ACCENT, 2, 18) : Ui.round(Ui.dark ? 0xFF303134 : 0xFFF1F3F4, 18));
                View sw2 = new View(this);
                int[] pv = {Ui.dark ? 0xFF202124 : 0xFFFFFFFF, 0xFFFFFFFF, 0xFF202124, 0xFF000000};
                sw2.setBackground(Ui.stroke(pv[i], Ui.dark ? 0xFF5F6368 : 0xFFBDC1C6, 1.5f, 14));
                r.addView(sw2, new LinearLayout.LayoutParams(dp(28), dp(28)));
                TextView tv = Ui.medium(Ui.text(this, L.t(Ui.MODE_NAMES[i]), 16, sel ? Ui.ON_TONAL : Ui.TEXT));
                tv.setPadding(dp(14), 0, 0, 0);
                r.addView(tv, new LinearLayout.LayoutParams(0, WRAP, 1));
                if (sel) r.addView(Ui.icon(this, R.drawable.ic_check, Ui.ACCENT), new LinearLayout.LayoutParams(dp(22), dp(22)));
                r.setOnClickListener(v -> { wMode = m; buildWelcome(); });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
                rl.topMargin = dp(10);
                modes.addView(r, rl);
            }
            box.addView(modes, new LinearLayout.LayoutParams(MATCH, WRAP));
            TextView al = Ui.medium(Ui.text(this, L.t("Цвет"), 14, Ui.ACCENT));
            al.setPadding(0, dp(22), 0, dp(10));
            box.addView(al, new LinearLayout.LayoutParams(MATCH, WRAP));
            LinearLayout acc = new LinearLayout(this);
            acc.setGravity(Gravity.CENTER);
            for (int i = 0; i < Ui.ACCENT_LIGHT.length; i++) {
                final int a = i;
                int colr = Ui.dark ? Ui.ACCENT_DARK[i] : Ui.ACCENT_LIGHT[i];
                FrameLayout s = new FrameLayout(this);
                s.setBackground(wAccent == i ? Ui.stroke(colr, Ui.TEXT, 3, 22) : Ui.oval(colr));
                if (wAccent == i) {
                    ImageView ok = Ui.icon(this, R.drawable.ic_check, Ui.dark ? 0xFF202124 : Color.WHITE);
                    ok.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    s.addView(ok, new FrameLayout.LayoutParams(MATCH, MATCH));
                }
                s.setOnClickListener(v -> { wAccent = a; buildWelcome(); });
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(40), dp(40));
                sl.setMargins(dp(5), 0, dp(5), 0);
                acc.addView(s, sl);
            }
            box.addView(acc, new LinearLayout.LayoutParams(MATCH, WRAP));
        } else {
            welcomeTitle(box, L.t("Почти готово"), L.t("Основные настройки"));
            LinearLayout sw = new LinearLayout(this);
            sw.setOrientation(LinearLayout.VERTICAL);
            sw.setBackground(Ui.round(Ui.dark ? 0xFF303134 : 0xFFF1F3F4, 20));
            switchRow(sw, L.t("Блокировка рекламы"), L.t("Реклама, трекеры и баннеры"), store.adblock(), v -> { store.setBool("adblock", v); AdBlocker.enabled = v; });
            switchRow(sw, L.t("Блокировать всплывающие окна"), L.t("И рекламные переходы без нажатия"), store.blockPopups(), v -> store.setBool("popups", v));
            switchRow(sw, L.t("Предлагать сохранять пароли"), L.t("После входа на сайт"), store.bool("pwSave", true), v -> store.setBool("pwSave", v));
            switchRow(sw, L.t("Восстанавливать вкладки"), L.t("Открывать прошлые вкладки при запуске"), store.restoreTabs(), v -> store.setBool("restore", v));
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(MATCH, WRAP);
            swl.topMargin = dp(8);
            box.addView(sw, swl);
            TextView def = pillButton(L.t("Сделать браузером по умолчанию"), R.drawable.ic_globe, Ui.TONAL, Ui.ON_TONAL);
            def.setOnClickListener(v -> makeDefaultBrowser());
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(MATCH, dp(52));
            dl.topMargin = dp(16);
            box.addView(def, dl);
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(side, dp(8), side, dp(22));
        LinearLayout dots = new LinearLayout(this);
        for (int i = 0; i < 4; i++) {
            View d = new View(this);
            d.setBackground(Ui.round(i == wStep ? Ui.ACCENT : (Ui.dark ? 0xFF5F6368 : 0xFFDADCE0), 4));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(i == wStep ? 22 : 8), dp(8));
            dlp.rightMargin = dp(6);
            dots.addView(d, dlp);
        }
        bottom.addView(dots, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView next = pillButton(wStep == 0 ? L.t("Начать") : wStep == 3 ? L.t("Готово") : L.t("Далее"), wStep == 3 ? R.drawable.ic_check : R.drawable.ic_forward,
                Ui.ACCENT, Ui.dark ? 0xFF202124 : Color.WHITE);
        next.setOnClickListener(v -> { if (wStep < 3) { wStep++; buildWelcome(); } else finishWelcome(); });
        bottom.addView(next, new LinearLayout.LayoutParams(WRAP, dp(52)));
        col.addView(bottom);
        box.setAlpha(0f);
        box.setTranslationX(dp(L.rtl() ? -24 : 24));
        box.animate().alpha(1f).translationX(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    void welcomeTitle(LinearLayout box, String title, String sub) {
        TextView t = Ui.medium(Ui.text(this, title, 26, Ui.TEXT));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(18), 0, dp(6));
        box.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView s = Ui.text(this, sub, 15, Ui.TEXT2);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, 0, 0, dp(18));
        box.addView(s, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    View featureRow(int icon, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(9), 0, dp(9));
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView i = Ui.icon(this, icon, Ui.ON_TONAL);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setPadding(dp(11), dp(11), dp(11), dp(11));
        b.addView(i, new FrameLayout.LayoutParams(MATCH, MATCH));
        r.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(14), 0, 0, 0);
        tx.addView(Ui.medium(Ui.text(this, title, 16, Ui.TEXT)));
        tx.addView(Ui.text(this, sub, 13, Ui.TEXT2));
        r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1));
        return r;
    }

    void finishWelcome() {
        store.p.edit().putBoolean("onboarded", true).apply();
        if (wEngine != store.engine()) store.setEngine(wEngine);
        if (wMode != Ui.mode || wAccent != Ui.accent) { applyTheme(wMode, wAccent); return; }
        final View w = welcome;
        welcome = null;
        if (w != null) w.animate().alpha(0f).setDuration(200).withEndAction(() -> root.removeView(w)).start();
        refreshChrome();
    }

    void makeDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER), 18);
                    return;
                }
                if (rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) { toast(L.t("Lasur уже браузер по умолчанию")); return; }
            }
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) { toast(L.t("Откройте настройки Android → Приложения по умолчанию")); }
    }

    // ---------------------------------------------------------------- language
    String langName() {
        String p = L.pref(this);
        if (p.isEmpty()) {
            String r = L.resolve("");
            for (int i = 1; i < L.CODES.length; i++) if (L.CODES[i].equals(r)) return L.t("Как в системе") + " · " + L.NAMES[i];
            return L.t("Как в системе");
        }
        for (int i = 1; i < L.CODES.length; i++) if (L.CODES[i].equals(p)) return L.NAMES[i];
        return p;
    }

    void pickLanguage() {
        String[] names = L.NAMES.clone();
        names[0] = L.t("Как в системе");
        String p = L.pref(this);
        int sel = 0;
        for (int i = 0; i < L.CODES.length; i++) if (L.CODES[i].equals(p)) sel = i;
        new AlertDialog.Builder(this).setTitle(L.t("Язык")).setSingleChoiceItems(names, sel, (d, w) -> {
            d.dismiss();
            if (L.CODES[w].equals(p)) return;
            store.p.edit().putString("lang", L.CODES[w]).commit();
            if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
            saveTabs();
            recreate();
        }).show();
    }

    // ---------------------------------------------------------------- picture-in-picture
    boolean pipExited;
    static final String MEDIA_JS = "(function(){if(window.__lasurMedia)return;window.__lasurMedia=1;var last='';"
            + "function playing(){var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++)if(!vs[i].paused&&!vs[i].ended)return true;return false}"
            + "function keep(){return window.__lasurKeep&&(window.__lasurPip||playing()||Date.now()-(window.__lasurLastPlay||0)<1500)}"
            + "try{var D=Document.prototype,hd=Object.getOwnPropertyDescriptor(D,'hidden'),vd=Object.getOwnPropertyDescriptor(D,'visibilityState');"
            + "Object.defineProperty(document,'hidden',{configurable:true,get:function(){return keep()?false:hd.get.call(document)}});"
            + "Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return keep()?'visible':vd.get.call(document)}});"
            + "Object.defineProperty(document,'webkitHidden',{configurable:true,get:function(){return document.hidden}});"
            + "Object.defineProperty(document,'webkitVisibilityState',{configurable:true,get:function(){return document.visibilityState}});}catch(e){}"
            + "['visibilitychange','webkitvisibilitychange','blur','pagehide','freeze'].forEach(function(n){window.addEventListener(n,function(e){if(keep())e.stopImmediatePropagation()},true);"
            + "document.addEventListener(n,function(e){if(keep())e.stopImmediatePropagation()},true)});"
            + "setInterval(function(){if(playing())window.__lasurLastPlay=Date.now()},500);"
            + "function rep(){try{var best=null,ba=0;var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){var v=vs[i];"
            + "if(!v.paused&&!v.ended&&v.readyState>1){var r=v.getBoundingClientRect(),a=r.width*r.height;if(a>=ba){ba=a;best=v}}}"
            + "var s=(best?1:0)+','+(best?best.videoWidth:0)+','+(best?best.videoHeight:0);if(s!=last){last=s;LumenBridge.media(best?1:0,best?best.videoWidth:0,best?best.videoHeight:0)}}catch(e){}}"
            + "['play','playing','pause','ended','emptied','loadedmetadata'].forEach(function(n){document.addEventListener(n,function(e){if(n=='playing')window.__lasurLastPlay=Date.now();if(n=='pause'&&window.__lasurPip&&Date.now()-(window.__lasurPipAt||0)<3000){var t=e.target;setTimeout(function(){try{t.play()}catch(x){}},60)}setTimeout(rep,50)},true)});setInterval(rep,1500);rep();})();";
    static final String PIP_ON_JS = "(function(){try{window.__lasurPip=1;window.__lasurPipAt=Date.now();var best=null,ba=-1;var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){var v=vs[i],r=v.getBoundingClientRect(),"
            + "a=r.width*r.height+(v.paused?0:1e9);if(a>ba){ba=a;best=v}}if(!best)return;var st=document.getElementById('__lasurPip');if(!st){st=document.createElement('style');st.id='__lasurPip';"
            + "st.textContent='.__lasurPipA{transform:none!important;filter:none!important;contain:none!important;perspective:none!important;will-change:auto!important;z-index:2147483646!important}'"
            + "+'.__lasurPipV{position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;"
            + "object-fit:contain!important;background:#000!important;z-index:2147483647!important;margin:0!important;transform:none!important;visibility:visible!important;opacity:1!important}'"
            + "+'html.__lasurPipH,html.__lasurPipH body{overflow:hidden!important;background:#000!important}';(document.head||document.documentElement).appendChild(st)}"
            + "best.classList.add('__lasurPipV');for(var n=best.parentElement;n&&n!=document.documentElement;n=n.parentElement)n.classList.add('__lasurPipA');"
            + "document.documentElement.classList.add('__lasurPipH');if(best.paused&&Date.now()-(window.__lasurLastPlay||0)<5000)best.play();}catch(e){}})();";
    static final String PIP_OFF_JS = "(function(){try{window.__lasurPip=0;var a=document.querySelectorAll('.__lasurPipA,.__lasurPipV');for(var i=0;i<a.length;i++)a[i].classList.remove('__lasurPipA','__lasurPipV');"
            + "document.documentElement.classList.remove('__lasurPipH');var s=document.getElementById('__lasurPip');if(s)s.remove();}catch(e){}})();";

    static final String PIP_FS_JS = "(function(){window.__lasurPip=1;window.__lasurPipAt=Date.now();try{var v=document.querySelectorAll('video');for(var i=0;i<v.length;i++)if(v[i].paused&&Date.now()-(window.__lasurLastPlay||0)<5000&&v[i].currentTime>0)v[i].play()}catch(e){}})();";

    boolean pipEligible() {
        return store.bool("pip", true) && current != null && !current.ntp && switcher.getVisibility() != View.VISIBLE
                && (customView != null || current.mediaPlaying);
    }

    PictureInPictureParams pipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
        int w = current != null ? current.mediaW : 0, h = current != null ? current.mediaH : 0;
        if (w > 0 && h > 0) {
            float r = w / (float) h;
            if (r > 2.39f) { w = 239; h = 100; } else if (r < 0.42f) { w = 42; h = 100; }
            b.setAspectRatio(new Rational(w, h));
        } else b.setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= 31) { b.setAutoEnterEnabled(pipEligible()); b.setSeamlessResizeEnabled(true); }
        return b.build();
    }

    void updatePipParams() {
        if (Build.VERSION.SDK_INT < 26) return;
        try { setPictureInPictureParams(pipParams()); } catch (Exception ignored) { }
    }

    void preparePip() {
        if (current != null) current.web.evaluateJavascript(customView == null ? PIP_ON_JS : PIP_FS_JS, null);
        toolbar.setVisibility(View.GONE);
        stripHidden = true;
        if (stripScroll != null) stripScroll.setVisibility(View.GONE);
        findBar.setVisibility(View.GONE);
        divider.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        videoFab.setVisibility(View.GONE);
        hidePwBar();
        if (snackView != null) snackView.setVisibility(View.GONE);
        if (noticeView != null) noticeView.setVisibility(View.GONE);
    }

    void restorePip() {
        if (current != null) current.web.evaluateJavascript(PIP_OFF_JS, null);
        toolbar.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);
        stripHidden = false;
        refreshChrome();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= 26 && pipEligible() && !isInPictureInPictureMode()) {
            preparePip();
            try { if (!enterPictureInPictureMode(pipParams())) restorePip(); } catch (Exception e) { restorePip(); }
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean in, android.content.res.Configuration c) {
        super.onPictureInPictureModeChanged(in, c);
        if (in) { pipExited = false; preparePip(); }
        else { pipExited = true; restorePip(); }
    }

    @Override protected void onResume() { super.onResume(); pipExited = false; }

    @Override protected void onStop() {
        super.onStop();
        if (pipExited && current != null) { try { current.web.evaluateJavascript(PAUSE_JS, null); } catch (Exception ignored) { } }
        pipExited = false;
    }
}
