package com.lumen.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Site icons for shortcuts and tabs: memory + disk cache, fetched from icon services. */
final class IconCache {
    interface Cb { void done(Bitmap b); }

    static final LruCache<String, Bitmap> mem = new LruCache<>(96);
    static final ExecutorService ex = Executors.newFixedThreadPool(3);
    static final HashSet<String> pending = new HashSet<>(), failed = new HashSet<>();
    static final Handler ui = new Handler(Looper.getMainLooper());

    static String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return null;
            h = h.toLowerCase();
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) { return null; }
    }

    static File file(Context c, String host) {
        File d = new File(c.getFilesDir(), "icons");
        d.mkdirs();
        return new File(d, host.replaceAll("[^a-z0-9.-]", "_") + ".png");
    }

    static Bitmap get(Context c, String url, Cb cb) {
        String h = host(url);
        if (h == null) return null;
        Bitmap b = mem.get(h);
        if (b != null) return b;
        File f = file(c, h);
        if (f.exists()) {
            b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) { mem.put(h, b); return b; }
        }
        synchronized (pending) {
            if (failed.contains(h) || pending.contains(h)) return null;
            pending.add(h);
        }
        final Context app = c.getApplicationContext();
        ex.execute(() -> {
            Bitmap r = fetch("https://www.google.com/s2/favicons?domain=" + h + "&sz=128");
            if (r == null) r = fetch("https://icons.duckduckgo.com/ip3/" + h + ".ico");
            if (r == null) r = fetch("https://" + h + "/apple-touch-icon.png");
            final Bitmap res = r;
            synchronized (pending) { pending.remove(h); if (res == null) failed.add(h); }
            if (res != null) {
                save(app, h, res);
                ui.post(() -> { mem.put(h, res); if (cb != null) cb.done(res); });
            }
        });
        return null;
    }

    static void offer(Context c, String url, Bitmap b) {
        String h = host(url);
        if (h == null || b == null || b.getWidth() < 32) return;
        Bitmap cur = mem.get(h);
        if (cur != null && cur.getWidth() >= b.getWidth()) return;
        File f = file(c, h);
        if (f.exists() && cur == null) return;
        mem.put(h, b);
        ex.execute(() -> save(c.getApplicationContext(), h, b));
    }

    static void forget(Context c, String url) {
        String h = host(url);
        if (h == null) return;
        mem.remove(h);
        file(c, h).delete();
        synchronized (pending) { failed.remove(h); }
    }

    static void save(Context c, String h, Bitmap b) {
        try (FileOutputStream o = new FileOutputStream(file(c, h))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); } catch (Exception ignored) { }
    }

    static Bitmap fetch(String u) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            if (con.getResponseCode() >= 400) return null;
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                byte[] d = bo.toByteArray();
                Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length);
                if (b == null || b.getWidth() < 24) return null; // tiny default globe = no real icon
                return b;
            }
        } catch (Exception e) { return null; }
    }
}
