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
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Downloads HLS (m3u8) streams by joining segments into a single file.
 * Supports AES-128, fMP4 (EXT-X-MAP), byte ranges, separate audio renditions (saved as a second file),
 * live streams (recorded until "Stop") and cancelling from the notification.
 */
public class HlsService extends Service {
    static final String CH = "lumen_downloads";
    static final String ACTION_CANCEL = "com.lumen.browser.HLS_CANCEL";
    static final long LIVE_MAX_MS = 4 * 3600_000L;
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    NotificationManager nm;
    int active = 0, nid = 1000;
    String ua, referer, cookie;
    boolean incognito;
    volatile boolean cancel;

    static final class Seg { String url, method, keyUrl; byte[] iv; long seq, rangeStart = -1, rangeLen = -1; }
    static final class Playlist { ArrayList<Seg> segs = new ArrayList<>(); String mapUrl; long mapStart = -1, mapLen = -1; boolean ended; int target = 6; }
    static final class Cancelled extends IOException { Cancelled() { super(L.t("Загрузка отменена")); } }

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

    Notification.Builder withCancel(Notification.Builder b, boolean live) {
        PendingIntent pi = PendingIntent.getService(this, 3, new Intent(this, HlsService.class).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return b.addAction(new Notification.Action.Builder(null, live ? L.t("Остановить и сохранить") : L.t("Отмена"), pi).build());
    }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        Notification n = withCancel(nb(L.t("Загрузка видео"), L.t("Подготовка…")).setProgress(0, 0, true).setOngoing(true), false).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);
        if (in == null) { checkStop(); return START_NOT_STICKY; }
        if (ACTION_CANCEL.equals(in.getAction())) { cancel = true; ex.execute(() -> { }); checkStop(); return START_NOT_STICKY; }
        synchronized (this) { active++; }
        final String url = in.getStringExtra("url"), name = in.getStringExtra("name"),
                ref = in.getStringExtra("referer"), agent = in.getStringExtra("ua"), ck = in.getStringExtra("cookie");
        final boolean inc = in.getBooleanExtra("incognito", false);
        ex.execute(() -> {
            try {
                ua = agent; referer = ref; cookie = ck; incognito = inc; cancel = false;
                download(url, name);
            } catch (Cancelled c) {
                nm.notify(nid++, nb(L.t("Загрузка отменена"), name).setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true).build());
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

    /** Android 15+: data-sync services get ~6 h a day. Stop cleanly instead of being killed. */
    @Override public void onTimeout(int startId, int fgsType) {
        cancel = true;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    synchronized void checkStop() {
        if (active <= 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }

    byte[] get(String u) throws IOException { return get(u, -1, -1); }

    byte[] get(String u, long start, long len) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (cancel) throw new Cancelled();
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setInstanceFollowRedirects(true);
                if (ua != null) c.setRequestProperty("User-Agent", ua);
                if (referer != null) c.setRequestProperty("Referer", referer);
                if (start >= 0 && len > 0) c.setRequestProperty("Range", "bytes=" + start + "-" + (start + len - 1));
                // Incognito downloads never touch the normal cookie jar; they only get the cookie captured for the stream.
                if (incognito) { if (cookie != null) c.setRequestProperty("Cookie", cookie); }
                else try { String ck = CookieManager.getInstance().getCookie(u); if (ck != null) c.setRequestProperty("Cookie", ck); } catch (Throwable ignored) { }
                int code = c.getResponseCode();
                if (code >= 400) {
                    IOException he = new IOException("HTTP " + code);
                    // client errors will not fix themselves: do not wait and retry (except timeouts / rate limits)
                    if (code < 500 && code != 408 && code != 429) throw new FatalHttp(he);
                    throw he;
                }
                try (InputStream is = c.getInputStream()) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] b = new byte[32768];
                    int n;
                    while ((n = is.read(b)) > 0) { if (cancel) throw new Cancelled(); bo.write(b, 0, n); }
                    byte[] d = bo.toByteArray();
                    // server ignored the Range header and sent the whole resource
                    if (start >= 0 && len > 0 && code == 200 && d.length > len && start + len <= d.length) {
                        byte[] part = new byte[(int) len];
                        System.arraycopy(d, (int) start, part, 0, (int) len);
                        return part;
                    }
                    return d;
                }
            } catch (FatalHttp f) {
                throw f.cause;
            } catch (Cancelled ce) {
                throw ce;
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(800L * (attempt + 1)); } catch (InterruptedException ignored) { }
            } finally { if (c != null) c.disconnect(); }
        }
        throw last;
    }

    static final class FatalHttp extends IOException { final IOException cause; FatalHttp(IOException c) { super(c.getMessage()); cause = c; } }

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

    /** "n[@o]" -> {len, start}; start -1 = continues after the previous range of the same resource. */
    static long[] range(String v) {
        if (v == null) return null;
        v = v.trim();
        int at = v.indexOf('@');
        try {
            long n = Long.parseLong(at >= 0 ? v.substring(0, at) : v);
            long o = at >= 0 ? Long.parseLong(v.substring(at + 1)) : -1;
            return new long[]{n, o};
        } catch (Exception e) { return null; }
    }

    static Playlist parseMedia(String pl, String mediaUrl) throws IOException {
        Playlist p = new Playlist();
        long seq = 0; String method = "NONE", keyUrl = null; byte[] iv = null;
        long[] nextRange = null;
        HashMap<String, Long> rangeEnd = new HashMap<>();
        for (String raw : pl.split("\\r?\\n")) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                try { seq = Long.parseLong(l.substring(22).trim()); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            } else if (l.startsWith("#EXT-X-TARGETDURATION:")) {
                try { p.target = Math.max(1, Integer.parseInt(l.substring(22).trim())); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            } else if (l.startsWith("#EXT-X-ENDLIST")) {
                p.ended = true;
            } else if (l.startsWith("#EXT-X-PLAYLIST-TYPE:VOD")) {
                p.ended = true;
            } else if (l.startsWith("#EXT-X-KEY:")) {
                method = attr(l, "METHOD");
                if (method == null) method = "NONE";
                String ku = attr(l, "URI");
                keyUrl = ku != null ? resolve(mediaUrl, ku) : null;
                String ivs = attr(l, "IV");
                iv = ivs != null ? hex(ivs) : null;
            } else if (l.startsWith("#EXT-X-MAP:")) {
                String mu = attr(l, "URI");
                if (mu != null) p.mapUrl = resolve(mediaUrl, mu);
                long[] r = range(attr(l, "BYTERANGE"));
                if (r != null) { p.mapLen = r[0]; p.mapStart = r[1] < 0 ? 0 : r[1]; }
            } else if (l.startsWith("#EXT-X-BYTERANGE:")) {
                nextRange = range(l.substring(17));
            } else if (!l.startsWith("#")) {
                Seg s = new Seg();
                s.url = resolve(mediaUrl, l); s.method = method; s.keyUrl = keyUrl; s.iv = iv; s.seq = seq++;
                if (nextRange != null) {
                    long start = nextRange[1] >= 0 ? nextRange[1] : rangeEnd.getOrDefault(s.url, 0L);
                    s.rangeStart = start; s.rangeLen = nextRange[0];
                    rangeEnd.put(s.url, start + nextRange[0]);
                    nextRange = null;
                }
                p.segs.add(s);
            }
        }
        return p;
    }

    void download(String url, String name) throws Exception {
        String pl = new String(get(url), "UTF-8");
        if (!pl.contains("#EXTM3U")) throw new IOException(L.t("Это не плейлист HLS"));
        String mediaUrl = url, audioUrl = null;
        if (pl.contains("#EXT-X-STREAM-INF")) {
            String[] lines = pl.split("\\r?\\n");
            String best = null, bestAudio = null; long bw = -1;
            HashMap<String, String> audioGroups = new HashMap<>(); // GROUP-ID -> URI (DEFAULT preferred)
            for (String l0 : lines) {
                String l = l0.trim();
                if (!l.startsWith("#EXT-X-MEDIA:") || !"AUDIO".equals(attr(l, "TYPE"))) continue;
                String g = attr(l, "GROUP-ID"), u = attr(l, "URI");
                if (g == null || u == null) continue;
                if (!audioGroups.containsKey(g) || "YES".equals(attr(l, "DEFAULT"))) audioGroups.put(g, resolve(url, u));
            }
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim();
                if (!l.startsWith("#EXT-X-STREAM-INF")) continue;
                long b = 0;
                try { b = Long.parseLong(attr(l, "BANDWIDTH")); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                String ag = attr(l, "AUDIO");
                for (int j = i + 1; j < lines.length; j++) {
                    String u = lines[j].trim();
                    if (u.isEmpty() || u.startsWith("#")) continue;
                    if (b > bw) { bw = b; best = resolve(url, u); bestAudio = ag != null ? audioGroups.get(ag) : null; }
                    break;
                }
            }
            if (best == null) throw new IOException(L.t("Потоки не найдены"));
            mediaUrl = best;
            audioUrl = bestAudio;
            pl = new String(get(best), "UTF-8");
        }
        String file = downloadMedia(mediaUrl, pl, Saver.clean(name), L.t("видео"));
        if (audioUrl != null && !audioUrl.equals(mediaUrl) && !cancel) {
            // The video variant has no sound of its own: save the audio rendition next to it.
            String apl = new String(get(audioUrl), "UTF-8");
            String af = downloadMedia(audioUrl, apl, Saver.clean(name) + L.t(" (аудио)"), L.t("аудио"));
            nm.notify(nid++, nb(L.t("Звук сохранён отдельным файлом"), af).setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true).build());
        }
    }

    /** Downloads one media playlist into a file and returns the file name. Live playlists are polled until they end or "Stop". */
    String downloadMedia(String mediaUrl, String pl, String baseName, String what) throws Exception {
        Playlist p = parseMedia(pl, mediaUrl);
        if (p.segs.isEmpty()) throw new IOException(L.t("Сегменты не найдены"));
        for (Seg s : p.segs) if (!"NONE".equals(s.method) && !"AES-128".equals(s.method))
            throw new IOException(L.t("Видео защищено DRM (") + s.method + ")");
        boolean live = !p.ended;
        boolean fmp4 = p.mapUrl != null;
        boolean audioOnly = what.equals(L.t("аудио")) && !fmp4 && p.segs.get(0).url.toLowerCase().contains(".aac");
        String fileName = baseName + (fmp4 ? (audioOnly ? ".m4a" : ".mp4") : audioOnly ? ".aac" : ".ts");
        String mime = fmp4 ? "video/mp4" : audioOnly ? "audio/aac" : "video/mp2t";
        Saver out = Saver.create(this, fileName, mime);
        HashMap<String, byte[]> keys = new HashMap<>();
        HashSet<Long> done = new HashSet<>();
        long lastNotify = 0, started = System.currentTimeMillis();
        int count = 0, idle = 0;
        boolean stoppedByUser = false;
        try {
            if (p.mapUrl != null) out.out.write(get(p.mapUrl, p.mapStart, p.mapLen));
            while (true) {
                for (int i = 0; i < p.segs.size(); i++) {
                    Seg s = p.segs.get(i);
                    if (done.contains(s.seq)) continue;
                    if (cancel) { if (live && count > 0) { stoppedByUser = true; break; } throw new Cancelled(); }
                    byte[] data = get(s.url, s.rangeStart, s.rangeLen);
                    if ("AES-128".equals(s.method) && s.keyUrl != null) {
                        byte[] key = keys.get(s.keyUrl);
                        if (key == null) { key = get(s.keyUrl); keys.put(s.keyUrl, key); }
                        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(s.iv != null ? s.iv : seqIv(s.seq)));
                        data = c.doFinal(data);
                    }
                    out.out.write(data);
                    done.add(s.seq);
                    count++;
                    long now = System.currentTimeMillis();
                    if (now - lastNotify > 700) {
                        lastNotify = now;
                        Notification.Builder b;
                        if (live) b = nb(L.t("Запись трансляции: ") + fileName, count + L.t(" частей") + " · " + ((now - started) / 60000) + L.t(" мин")).setProgress(0, 0, true);
                        else {
                            int pct = (i + 1) * 100 / p.segs.size();
                            b = nb(L.t("Загрузка: ") + fileName, pct + "% · " + (i + 1) + "/" + p.segs.size() + L.t(" частей")).setProgress(100, pct, false);
                        }
                        nm.notify(1, withCancel(b.setOngoing(true), live).build());
                    }
                }
                if (!live || stoppedByUser || p.ended) break;
                if (System.currentTimeMillis() - started > LIVE_MAX_MS) break;
                // live: wait about one segment and fetch the playlist again
                for (int w = 0; w < p.target * 10 && !cancel; w++) Thread.sleep(100);
                if (cancel) { stoppedByUser = true; break; }
                int before = done.size();
                p = parseMedia(new String(get(mediaUrl), "UTF-8"), mediaUrl);
                boolean fresh = false;
                for (Seg s : p.segs) if (!done.contains(s.seq)) { fresh = true; break; }
                idle = fresh ? 0 : idle + 1;
                if (idle >= 10 && before == done.size()) break; // stream stalled
            }
            out.finish();
        } catch (Exception e) {
            out.abort();
            throw e;
        }
        Notification.Builder done2 = nb(live ? L.t("Запись сохранена") : L.t("Видео загружено"), fileName + L.t(" · Загрузки/Lasur"))
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true);
        Uri u = out.shareUri();
        if (u != null) {
            Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            done2.setContentIntent(PendingIntent.getActivity(this, nid, v, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        nm.notify(nid++, done2.build());
        return fileName;
    }
}
