package com.lumen.browser;
import android.util.Log;
import android.Manifest;
import java.util.HashSet;
import java.util.Locale;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Camera, microphone and location permissions per site. */
final class SitePermissions {
    final MainActivity act;

    SitePermissions(MainActivity act) { this.act = act; }

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
        if (k.equals("geo")) return act.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || act.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return act.checkSelfPermission(kindPerms(k)[0]) == PackageManager.PERMISSION_GRANTED;
    }

    int siteDecision(Tab t, String host, String k) {
        if (host == null) return 0;
        String key = "sp_" + k + "_" + host;
        return t.incognito ? t.privatePermissions.getOrDefault(key, 0) : act.store.p.getInt(key, 0);
    }

    void rememberPermission(Tab t, String host, String k, int decision) {
        if (host == null) return;
        String key = "sp_" + k + "_" + host;
        if (t.incognito) t.privatePermissions.put(key, decision);
        else act.store.p.edit().putInt(key, decision).apply();
    }

    /** Asks the user (once per site, remembered) and then Android itself, if needed. */
    void sitePermission(Tab t, String host, ArrayList<String> kinds, java.util.function.Consumer<ArrayList<String>> result) {
        ArrayList<String> allowed = new ArrayList<>(), ask = new ArrayList<>();
        for (String k : kinds) {
            int dcs = siteDecision(t, host, k);
            if (dcs == 1) allowed.add(k); else if (dcs == 0) ask.add(k);
        }
        Runnable finish = () -> {
            ArrayList<String> need = new ArrayList<>();
            for (String k : allowed) if (!hasAndroid(k)) for (String p : kindPerms(k)) need.add(p);
            Runnable deliver = () -> {
                ArrayList<String> ok = new ArrayList<>();
                ArrayList<String> denied = new ArrayList<>();
                for (String k : allowed) if (hasAndroid(k)) ok.add(k); else denied.add(k);
                if (!denied.isEmpty()) act.snack(L.t("Нет доступа: ") + kindTitle(denied.get(0)).toLowerCase(Locale.ROOT) + L.t(". Разрешите его Lasur в настройках Android"), L.t("Настройки"), () -> {
                    try { act.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.getPackageName()))); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                });
                result.accept(ok);
            };
            if (need.isEmpty()) deliver.run();
            else { sitePermAfter = deliver; act.requestPermissions(need.toArray(new String[0]), REQ_SITE_PERM); }
        };
        if (ask.isEmpty()) { finish.run(); return; }
        StringBuilder what = new StringBuilder();
        for (int i = 0; i < ask.size(); i++) what.append(i == 0 ? "" : i == ask.size() - 1 ? L.t(" и ") : ", ").append(kindLabel(ask.get(i)));
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(act.dp(22), act.dp(4), act.dp(22), act.dp(8));
        box.addView(act.sheetHead(kindIcon(ask.get(0)), host == null ? L.t("Сайт") : host, L.t("запрашивает доступ: ") + what));
        TextView hint = Ui.text(act, t.incognito ? L.t("В режиме инкогнито решение действует до закрытия вкладки.") : L.t("Решение запомнится для этого сайта. Изменить его можно в Настройках → Разрешения сайтов."), 12, Ui.TEXT2);
        hint.setPaddingRelative(0, act.dp(10), 0, 0);
        box.addView(hint);
        final boolean[] answered = {false};
        TextView[] b = act.sheetButtons(box, L.t("Блокировать"), L.t("Разрешить"));
        final Dialog d = act.sheet(box);
        permDialog = d;
        d.setOnDismissListener(x -> { if (permDialog == d) permDialog = null; if (!answered[0]) { answered[0] = true; finish.run(); } });
        b[0].setOnClickListener(v -> {
            answered[0] = true;
            for (String k : ask) rememberPermission(t, host, k, 2);
            d.dismiss();
            finish.run();
        });
        b[1].setOnClickListener(v -> {
            answered[0] = true;
            for (String k : ask) rememberPermission(t, host, k, 1);
            allowed.addAll(ask);
            d.dismiss();
            finish.run();
        });
    }

    Dialog permDialog;
    android.webkit.PermissionRequest permReq;

    void handlePermission(Tab t, android.webkit.PermissionRequest r) {
        String host = act.hostOf(r.getOrigin().toString());
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
            try { if (g.isEmpty()) r.deny(); else r.grant(g.toArray(new String[0])); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        });
    }

    void handleGeo(Tab t, String origin, android.webkit.GeolocationPermissions.Callback cb) {
        ArrayList<String> k = new ArrayList<>();
        k.add("geo");
        sitePermission(t, act.hostOf(origin), k, ok -> cb.invoke(origin, ok.contains("geo"), false));
    }

    void showSitePermissions() {
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, 0, 0, act.dp(24));
        sv.addView(box);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            java.util.TreeMap<String, ArrayList<String[]>> by = new java.util.TreeMap<>();
            for (java.util.Map.Entry<String, ?> e : act.store.p.getAll().entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("sp_") || !(e.getValue() instanceof Integer)) continue;
                String rest = key.substring(3);
                int us = rest.indexOf('_');
                if (us < 0) continue;
                String kind = rest.substring(0, us), host = rest.substring(us + 1);
                by.computeIfAbsent(host, x -> new ArrayList<>()).add(new String[]{kind, String.valueOf(e.getValue()), key});
            }
            java.util.Set<String> pa = act.store.p.getStringSet("popupAllow", new HashSet<>());
            if (by.isEmpty() && pa.isEmpty()) {
                TextView e = Ui.text(act, L.t("Здесь появятся сайты, которым вы разрешили или запретили доступ к микрофону, камере и местоположению."), 14, Ui.TEXT2);
                e.setPaddingRelative(act.dp(20), act.dp(24), act.dp(20), 0);
                box.addView(e);
            }
            for (java.util.Map.Entry<String, ArrayList<String[]>> e : by.entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (String[] k : e.getValue()) sb.append(sb.length() == 0 ? "" : " · ").append(kindTitle(k[0])).append(k[1].equals("1") ? L.t(": разрешено") : L.t(": заблокировано"));
                final String host = e.getKey();
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(10), act.dp(8), act.dp(10));
                r.addView(act.home.tileIcon(host, "https://" + host, false, 0, 36), new LinearLayout.LayoutParams(act.dp(36), act.dp(36)));
                LinearLayout tx = new LinearLayout(act);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPaddingRelative(act.dp(16), 0, act.dp(8), 0);
                tx.addView(Ui.single(act, host, 15, Ui.TEXT));
                TextView st = Ui.text(act, sb.toString(), 13, Ui.TEXT2);
                tx.addView(st);
                r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                TextView reset = Ui.medium(Ui.text(act, L.t("Сбросить"), 14, Ui.ACCENT));
                reset.setPaddingRelative(act.dp(12), act.dp(10), act.dp(12), act.dp(10));
                reset.setBackground(Ui.ripple(act, true));
                reset.setOnClickListener(v -> { android.content.SharedPreferences.Editor ed = act.store.p.edit(); for (String[] k : e.getValue()) ed.remove(k[2]); ed.apply(); fill[0].run(); });
                r.addView(reset);
                box.addView(r);
            }
            if (!pa.isEmpty()) {
                act.settingsUi.section(box, L.t("Всегда открывают новые вкладки без вопроса"));
                for (String h : new java.util.TreeSet<>(pa)) {
                    LinearLayout r = new LinearLayout(act);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    r.setPaddingRelative(act.dp(20), act.dp(6), act.dp(8), act.dp(6));
                    TextView ht = Ui.single(act, h, 15, Ui.TEXT);
                    r.addView(ht, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                    ImageView del = Ui.iconBtn(act, R.drawable.ic_close, Ui.TEXT2);
                    del.setOnClickListener(v -> {
                        java.util.Set<String> s = new HashSet<>(act.store.p.getStringSet("popupAllow", new HashSet<>()));
                        s.remove(h); act.store.p.edit().putStringSet("popupAllow", s).apply(); fill[0].run();
                    });
                    r.addView(del, new LinearLayout.LayoutParams(act.dp(40), act.dp(40)));
                    box.addView(r);
                }
            }
        };
        fill[0].run();
        act.lists.fullDialog(L.t("Разрешения сайтов"), sv, null, null);
    }
}
