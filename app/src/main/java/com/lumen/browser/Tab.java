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
    final java.util.Map<String, Integer> privatePermissions = new java.util.HashMap<>();
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
    boolean mediaPlaying, mediaPipEligible; int mediaW, mediaH; long mediaPlayAt; // selected full player, excluding feed previews
    android.os.Bundle pendingState; // saved back/forward history, restored when the tab is shown
    long lastUsed;                  // for unloading the least recently used background tabs
    final java.util.Set<String> sslHosts = java.util.concurrent.ConcurrentHashMap.newKeySet(); // hosts whose bad certificate the user accepted
    volatile boolean mixed;         // https page loaded insecure (http) sub-resources
    String injectedFor;             // url the page scripts were injected for (avoid re-injecting every progress tick)
}
