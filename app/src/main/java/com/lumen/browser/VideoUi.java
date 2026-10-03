package com.lumen.browser;
import android.util.Log;
import android.view.animation.OvershootInterpolator;
import android.widget.HorizontalScrollView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Video detection on pages, the video button and the video sheet. */
final class VideoUi {
    final MainActivity act;

    VideoUi(MainActivity act) { this.act = act; }

    static String typeOf(String url) {
        String p = "";
        try { p = Uri.parse(url).getPath().toLowerCase(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        if (p.endsWith(".m3u8")) return "HLS";
        if (p.endsWith(".mpd")) return "DASH";
        int d = p.lastIndexOf('.');
        return d >= 0 && p.length() - d <= 5 ? p.substring(d + 1).toUpperCase() : "VIDEO";
    }

    void downloadVideo(Tab.Video v, Tab t) {
        String type = typeOf(v.url);
        String base = v.title != null && !v.title.trim().isEmpty() ? v.title.trim() : "video_" + System.currentTimeMillis();
        if (type.equals("DASH")) { act.toast(L.t("Поток DASH нельзя сохранить одним файлом — откройте его во внешнем плеере")); return; }
        if (type.equals("HLS")) {
            EditText name = new EditText(act);
            name.setText(Saver.clean(base));
            name.setSingleLine(true);
            FrameLayout f = new FrameLayout(act); f.setPaddingRelative(act.dp(20), act.dp(8), act.dp(20), 0); f.addView(name);
            act.dialog().setTitle(L.t("Скачать видеопоток")).setMessage(L.t("Видео будет собрано из частей (HLS) в один файл."))
                    .setView(f).setPositiveButton(L.t("Скачать"), (d, w) -> {
                        String n = name.getText().toString();
                        act.downloads.withStorage(() -> act.downloads.withNotif(() -> {
                            Intent si = new Intent(act, HlsService.class).putExtra("url", v.url).putExtra("name", n)
                                    .putExtra("referer", v.page).putExtra("ua", uaOf(t)).putExtra("incognito", t.incognito);
                            if (t.incognito) { String ck = act.cookies(true).getCookie(v.url); if (ck != null) si.putExtra("cookie", ck); }
                            act.startForegroundService(si);
                            act.toast(L.t("Загрузка началась — прогресс в уведомлениях"));
                        }));
                    }).setNegativeButton(L.t("Отмена"), null).show();
            return;
        }
        String fn = Downloads.fileName(v.url, null, null);
        String ext = fn.contains(".") ? fn.substring(fn.lastIndexOf('.')) : ".mp4";
        act.downloads.confirmDownload(v.url, Saver.clean(base) + ext, null, v.page, uaOf(t), -1);
    }

    void addVideo(Tab t, String url, String page, String title) {
        if (url == null || !url.startsWith("http")) return;
        try { if (AdBlocker.enabled && AdBlocker.isAd(Uri.parse(url).getHost())) return; } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
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
        if (t == act.current) { updateVideoFab(); if (isNew) pulseFab(); }
    }

    static String httpText(String u, String ua, String ref, int max, CookieManager cm) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        if (ua != null) c.setRequestProperty("User-Agent", ua);
        if (ref != null) c.setRequestProperty("Referer", ref);
        if (cm != null) try { String ck = cm.getCookie(u); if (ck != null) c.setRequestProperty("Cookie", ck); } catch (Throwable ignored) { }
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
        act.BG.execute(() -> {
            String body = null;
            try { body = httpText(v.url, ua, v.page, 512 * 1024, act.cookies(t.incognito)); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
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
                    try { bw = Long.parseLong(HlsService.attr(l, "BANDWIDTH")); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                    String res = HlsService.attr(l, "RESOLUTION");
                    for (int j = i + 1; j < lines.length; j++) {
                        String u = lines[j].trim();
                        if (u.isEmpty() || u.startsWith("#")) continue;
                        try {
                            String abs = HlsService.resolve(v.url, u);
                            String label = res != null && res.contains("x") ? res.substring(res.indexOf('x') + 1) + "p" : (bw > 0 ? "" : L.t("Поток ") + (v.variants.size() + 1));
                            if (bw > 0) label += (label.isEmpty() ? "" : " · ") + String.format(Locale.US, L.t("%.1f Мбит/с"), bw / 1_000_000.0);
                            v.variants.add(new String[]{label, abs, String.valueOf(bw)});
                        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                        break;
                    }
                }
                java.util.Collections.sort(v.variants, (a, b) -> Long.compare(Long.parseLong(b[2]), Long.parseLong(a[2])));
            } else if (body != null) {
                String lb = body.toLowerCase();
                audio = lb.contains(".aac") || lb.contains(".mp3") || lb.contains("/audio") || lb.contains("audio=");
            }
            final boolean isAudio = audio;
            act.ui.post(() -> {
                Tab.Video cur = t.video;
                if (!v.master && cur != null && cur.master) {
                    for (String[] vr : cur.variants) if (vr[1].equals(v.url)) return;
                    if (System.currentTimeMillis() - cur.time < 90000) return; // segment playlist of the same stream
                }
                if (isAudio && cur != null) return;
                setVideo(t, v);
            });
        });
    }

    void pulseFab() {
        if (act.videoFab.getVisibility() != View.VISIBLE) return;
        act.videoFab.animate().cancel();
        act.videoFab.setScaleX(0.6f);
        act.videoFab.setScaleY(0.6f);
        act.videoFab.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.5f)).setDuration(320).start();
    }

    void updateVideoFab() {
        boolean show = act.current != null && !act.current.ntp && act.current.video != null && act.customView == null
                && act.switcher.getVisibility() != View.VISIBLE && !act.omni.hasFocus() && !act.isInPictureInPictureMode();
        act.videoFab.setVisibility(show ? View.VISIBLE : View.GONE);
        act.placeFloating();
        if (show && act.videoLabel != null) {
            String ty = act.current.video.type;
            act.videoLabel.setText(ty.equals("HLS") || ty.equals("DASH") || ty.equals("VIDEO") ? L.t("Видео") : L.t("Видео · ") + ty);
        }
    }

    String uaOf(Tab t) { return t != null && t.ua != null ? t.ua : act.mobileUA; }

    void showVideos() {
        final Tab t = act.current;
        final Tab.Video v = t == null ? null : t.video;
        if (v == null) { act.toast(L.t("Видео пока не найдено. Запустите воспроизведение — оно появится автоматически.")); return; }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(act.dp(20), act.dp(4), act.dp(20), act.dp(8));
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(act);
        badge.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView bi = Ui.icon(act, R.drawable.ic_video, Ui.ON_TONAL);
        badge.addView(bi, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        head.addView(badge, new LinearLayout.LayoutParams(act.dp(52), act.dp(52)));
        LinearLayout tx = new LinearLayout(act);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPaddingRelative(act.dp(14), 0, 0, 0);
        TextView tt = Ui.medium(Ui.text(act, v.title == null || v.title.isEmpty() ? L.t("Видео") : v.title, 16, Ui.TEXT));
        tt.setMaxLines(2);
        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(tt);
        String kind = v.type.equals("HLS") ? L.t("Потоковое видео (HLS)") : v.type.equals("DASH") ? L.t("Потоковое видео (DASH)") : L.t("Файл ") + v.type;
        tx.addView(Ui.single(act, kind + " · " + act.displayUrl(v.url), 13, Ui.TEXT2));
        head.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        box.addView(head);

        final String[] chosen = {v.url};
        if (v.master && !v.variants.isEmpty()) {
            TextView ql = Ui.medium(Ui.text(act, L.t("Качество"), 13, Ui.ACCENT));
            ql.setPaddingRelative(0, act.dp(18), 0, act.dp(8));
            box.addView(ql);
            HorizontalScrollView hs = new HorizontalScrollView(act);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(act);
            hs.addView(chips);
            ArrayList<TextView> all = new ArrayList<>();
            ArrayList<String[]> opts = new ArrayList<>();
            opts.add(new String[]{L.t("Авто (лучшее)"), v.url});
            opts.addAll(v.variants);
            for (String[] o : opts) {
                TextView c = act.home.chip(o[0]);
                all.add(c);
                c.setOnClickListener(x -> { chosen[0] = o[1]; for (TextView a : all) act.home.styleChip(a, a == c); });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
                cl.setMarginEnd(act.dp(8));
                chips.addView(c, cl);
            }
            for (TextView a : all) act.home.styleChip(a, a == all.get(0));
            box.addView(hs);
        }

        LinearLayout btns = new LinearLayout(act);
        btns.setPaddingRelative(0, act.dp(20), 0, 0);
        TextView play = pillButton(L.t("Смотреть"), R.drawable.ic_play, Ui.ACCENT, Ui.dark ? 0xFF202124 : Color.WHITE);
        TextView dl = pillButton(L.t("Скачать"), R.drawable.ic_download, Ui.TONAL, Ui.ON_TONAL);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, act.dp(50), 1);
        bl.setMarginEnd(act.dp(10));
        btns.addView(play, bl);
        btns.addView(dl, new LinearLayout.LayoutParams(0, act.dp(50), 1));
        box.addView(btns);

        LinearLayout more = new LinearLayout(act);
        more.setGravity(Gravity.CENTER);
        more.setPaddingRelative(0, act.dp(10), 0, 0);
        TextView cp = Ui.medium(Ui.text(act, L.t("Копировать ссылку"), 14, Ui.ACCENT));
        TextView sh = Ui.medium(Ui.text(act, L.t("Поделиться"), 14, Ui.ACCENT));
        for (TextView x : new TextView[]{cp, sh}) { x.setPaddingRelative(act.dp(14), act.dp(10), act.dp(14), act.dp(10)); x.setBackground(Ui.ripple(act, true)); more.addView(x); }
        box.addView(more);
        TextView hint = Ui.text(act, L.t("Показано последнее видео, найденное на этой странице. «Смотреть» открывает его во внешнем плеере (VLC, MX Player и др.) без скачивания."), 12, Ui.TEXT2);
        hint.setPaddingRelative(0, act.dp(6), 0, 0);
        box.addView(hint);

        final Dialog d = act.sheet(box);
        play.setOnClickListener(x -> { d.dismiss(); act.settingsUi.openExternal(withUrl(v, chosen[0]), t); });
        dl.setOnClickListener(x -> { d.dismiss(); downloadVideo(withUrl(v, chosen[0]), t); });
        cp.setOnClickListener(x -> { d.dismiss(); act.menu.copy(chosen[0]); });
        sh.setOnClickListener(x -> { d.dismiss(); act.menu.share(chosen[0], v.title); });
    }

    Tab.Video withUrl(Tab.Video v, String url) {
        if (url.equals(v.url)) return v;
        Tab.Video n = new Tab.Video(url, v.page, v.title);
        n.type = typeOf(url);
        return n;
    }

    TextView pillButton(String text, int icon, int bg, int fg) {
        TextView t = Ui.medium(Ui.text(act, text, 15, fg));
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable dr = act.getDrawable(icon).mutate();
        dr.setTint(fg);
        dr.setBounds(0, 0, act.dp(20), act.dp(20));
        t.setCompoundDrawablesRelative(dr, null, null, null);
        t.setCompoundDrawablePadding(act.dp(8));
        t.setPaddingRelative(act.dp(16), 0, act.dp(16), 0);
        t.setBackground(Ui.round(bg, 25));
        t.setForeground(Ui.ripple(act, false));
        return t;
    }
}
