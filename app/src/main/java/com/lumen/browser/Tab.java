package com.lumen.browser;

import android.graphics.Bitmap;
import android.webkit.WebView;
import java.util.concurrent.atomic.AtomicInteger;

final class Tab {
    static final class Video {
        String url, page, title, type = "VIDEO";
        boolean master;
        long time = System.currentTimeMillis();
        java.util.ArrayList<String[]> variants = new java.util.ArrayList<>(); // {label, url}
        Video(String url, String page, String title) { this.url = url; this.page = page; this.title = title; }
    }
    WebView web;
    boolean incognito, ntp = true, desktop, fromNtp, popup, loading;
    String title = "", url = "", pendingUrl;
    volatile String pageUrl, pageHost;
    int progress;
    Bitmap thumb, favicon;
    Tab parent;
    volatile Video video;          // only the most recently detected video
    volatile String ua;
    final AtomicInteger blocked = new AtomicInteger();
    volatile int ptrJs = -1;       // page says pull-to-refresh is allowed (1), forbidden (0) or unknown (-1)
    String pwUser, pwPass, pwSite; // credentials typed into the current login form
    long pwSubmitAt;
    boolean silenced;
    boolean held, asked, popupGesture; // popup window waiting for the user's confirmation
    Tab opener;
    boolean mediaPlaying; int mediaW, mediaH; // largest playing video (for picture-in-picture)            // web view paused because the home page covers it
}
