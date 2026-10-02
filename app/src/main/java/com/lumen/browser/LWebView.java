package com.lumen.browser;

import android.content.Context;
import android.webkit.WebView;

/** WebView that reports when the page is over-scrolled at the very top (for pull-to-refresh). */
final class LWebView extends WebView {
    boolean overTop;
    LWebView(Context c) { super(c); setOverScrollMode(OVER_SCROLL_ALWAYS); }
    @Override protected void onOverScrolled(int sx, int sy, boolean cx, boolean cy) {
        super.onOverScrolled(sx, sy, cx, cy);
        if (cy && sy <= 0) overTop = true;
    }
}
