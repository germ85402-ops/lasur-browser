package com.lumen.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.webkit.CookieManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Downloads HLS (m3u8) streams by joining segments into a single file. */
public class HlsService extends Service {
    static final String CH = "lumen_downloads";
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    NotificationManager nm;
    int active = 0, nid = 1000;
    String ua, referer;

    static final class Seg { String url, method, keyUrl; byte[] iv; long seq; }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        L.ensure(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, L.t("Загрузки"), NotificationManager.IMPORTANCE_LOW));
    }

    Notification.Builder nb(String title, String text) {
        return new Notification.Builder(this, CH).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title).setContentText(text).setOnlyAlertOnce(true);
    }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        Notification n = nb(L.t("Загрузка видео"), L.t("Подготовка…")).setProgress(0, 0, true).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);
        if (in == null) { checkStop(); return START_NOT_STICKY; }
        synchronized (this) { active++; }
        final String url = in.getStringExtra("url"), name = in.getStringExtra("name"),
                ref = in.getStringExtra("referer"), agent = in.getStringExtra("ua");
        ex.execute(() -> {
            try {
                ua = agent; referer = ref;
                download(url, name);
            } catch (Throwable e) {
                nm.notify(nid++, nb(L.t("Ошибка загрузки"), name + ": " + e.getMessage())
                        .setSmallIcon(android.R.drawable.stat_notify_error).setAutoCancel(true).build());
            } finally {
                synchronized (HlsService.this) { active--; }
                checkStop();
            }
        });
        return START_NOT_STICKY;
    }

    synchronized void checkStop() {
        if (active <= 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }

    byte[] get(String u) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setInstanceFollowRedirects(true);
                if (ua != null) c.setRequestProperty("User-Agent", ua);
                if (referer != null) c.setRequestProperty("Referer", referer);
                try { String ck = CookieManager.getInstance().getCookie(u); if (ck != null) c.setRequestProperty("Cookie", ck); } catch (Throwable ignored) { }
                int code = c.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);
                try (InputStream is = c.getInputStream()) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] b = new byte[32768];
                    int n;
                    while ((n = is.read(b)) > 0) bo.write(b, 0, n);
                    return bo.toByteArray();
                }
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(800L * (attempt + 1)); } catch (InterruptedException ignored) { }
            } finally { if (c != null) c.disconnect(); }
        }
        throw last;
    }

    static String resolve(String base, String rel) throws IOException { return new URL(new URL(base), rel).toString(); }

    static String attr(String line, String name) {
        Matcher m = Pattern.compile("(?:^|[,:])" + name + "=(\"([^\"]*)\"|[^,]*)").matcher(line);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(1);
    }

    static byte[] hex(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        while (s.length() < 32) s = "0" + s;
        byte[] b = new byte[16];
        for (int i = 0; i < 16; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    static byte[] seqIv(long seq) {
        byte[] b = new byte[16];
        for (int i = 15; i >= 8; i--) { b[i] = (byte) (seq & 0xff); seq >>= 8; }
        return b;
    }

    void download(String url, String name) throws Exception {
        String pl = new String(get(url), "UTF-8");
        if (!pl.contains("#EXTM3U")) throw new IOException(L.t("Это не плейлист HLS"));
        String mediaUrl = url;
        if (pl.contains("#EXT-X-STREAM-INF")) {
            String[] lines = pl.split("\\r?\\n");
            String best = null; long bw = -1;
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim();
                if (!l.startsWith("#EXT-X-STREAM-INF")) continue;
                long b = 0;
                try { b = Long.parseLong(attr(l, "BANDWIDTH")); } catch (Exception ignored) { }
                for (int j = i + 1; j < lines.length; j++) {
                    String u = lines[j].trim();
                    if (u.isEmpty() || u.startsWith("#")) continue;
                    if (b > bw) { bw = b; best = resolve(url, u); }
                    break;
                }
            }
            if (best == null) throw new IOException(L.t("Потоки не найдены"));
            mediaUrl = best;
            pl = new String(get(best), "UTF-8");
        }
        ArrayList<Seg> segs = new ArrayList<>();
        long seq = 0; String method = "NONE", keyUrl = null, mapUrl = null; byte[] iv = null;
        for (String raw : pl.split("\\r?\\n")) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                try { seq = Long.parseLong(l.substring(22).trim()); } catch (Exception ignored) { }
            } else if (l.startsWith("#EXT-X-KEY:")) {
                method = attr(l, "METHOD");
                if (method == null) method = "NONE";
                String ku = attr(l, "URI");
                keyUrl = ku != null ? resolve(mediaUrl, ku) : null;
                String ivs = attr(l, "IV");
                iv = ivs != null ? hex(ivs) : null;
            } else if (l.startsWith("#EXT-X-MAP:")) {
                String mu = attr(l, "URI");
                if (mu != null) mapUrl = resolve(mediaUrl, mu);
            } else if (!l.startsWith("#")) {
                Seg s = new Seg();
                s.url = resolve(mediaUrl, l); s.method = method; s.keyUrl = keyUrl; s.iv = iv; s.seq = seq++;
                segs.add(s);
            }
        }
        if (segs.isEmpty()) throw new IOException(L.t("Сегменты не найдены"));
        for (Seg s : segs) if (!"NONE".equals(s.method) && !"AES-128".equals(s.method))
            throw new IOException(L.t("Видео защищено DRM (") + s.method + ")");

        boolean fmp4 = mapUrl != null;
        String fileName = Saver.clean(name) + (fmp4 ? ".mp4" : ".ts");
        String mime = fmp4 ? "video/mp4" : "video/mp2t";
        Saver out = Saver.create(this, fileName, mime);
        HashMap<String, byte[]> keys = new HashMap<>();
        long lastNotify = 0;
        try {
            if (mapUrl != null) out.out.write(get(mapUrl));
            for (int i = 0; i < segs.size(); i++) {
                Seg s = segs.get(i);
                byte[] data = get(s.url);
                if ("AES-128".equals(s.method) && s.keyUrl != null) {
                    byte[] key = keys.get(s.keyUrl);
                    if (key == null) { key = get(s.keyUrl); keys.put(s.keyUrl, key); }
                    Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(s.iv != null ? s.iv : seqIv(s.seq)));
                    data = c.doFinal(data);
                }
                out.out.write(data);
                long now = System.currentTimeMillis();
                if (now - lastNotify > 700) {
                    lastNotify = now;
                    int pct = (i + 1) * 100 / segs.size();
                    nm.notify(1, nb(L.t("Загрузка: ") + fileName, pct + "% · " + (i + 1) + "/" + segs.size() + L.t(" частей"))
                            .setProgress(100, pct, false).setOngoing(true).build());
                }
            }
            out.finish();
        } catch (Exception e) {
            out.abort();
            throw e;
        }
        Notification.Builder done = nb(L.t("Видео загружено"), fileName + L.t(" · Загрузки/Lasur"))
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true);
        Uri u = out.shareUri();
        if (u != null) {
            Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            done.setContentIntent(PendingIntent.getActivity(this, nid, v, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        nm.notify(nid++, done.build());
    }
}
