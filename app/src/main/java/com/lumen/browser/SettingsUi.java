package com.lumen.browser;
import static com.lumen.browser.Scripts.*;
import android.util.Log;
import android.widget.HorizontalScrollView;
import java.util.Locale;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebViewDatabase;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;

/** Settings, appearance, language and ad blocker panels. */
final class SettingsUi {
    final MainActivity act;

    SettingsUi(MainActivity act) { this.act = act; }

    void section(LinearLayout box, String s) {
        TextView t = Ui.medium(Ui.text(act, s, 13, Ui.ACCENT));
        t.setPaddingRelative(act.dp(20), act.dp(20), act.dp(20), act.dp(6));
        box.addView(t);
    }

    TextView actionRow(LinearLayout box, String title, String sub, Runnable r) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPaddingRelative(act.dp(20), act.dp(12), act.dp(20), act.dp(12));
        if (r != null) { row.setBackground(Ui.ripple(act, false)); row.setOnClickListener(v -> r.run()); }
        row.addView(Ui.text(act, title, 16, Ui.TEXT));
        TextView s = Ui.text(act, sub, 13, Ui.TEXT2);
        row.addView(s);
        box.addView(row, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        return s;
    }

    void switchRow(LinearLayout box, String title, String sub, boolean checked, MainActivity.BoolCb cb) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(act.dp(20), act.dp(12), act.dp(16), act.dp(12));
        row.setBackground(Ui.ripple(act, false));
        LinearLayout tx = new LinearLayout(act);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(Ui.text(act, title, 16, Ui.TEXT));
        tx.addView(Ui.text(act, sub, 13, Ui.TEXT2));
        row.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        Switch sw = new Switch(act);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, v) -> cb.set(v));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        box.addView(row, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
    }

    void applyTheme(int mode, int accent) {
        act.store.p.edit().putInt("themeMode", mode).putInt("accent", accent).commit();
        if (act.settingsDialog != null && act.settingsDialog.isShowing()) act.settingsDialog.dismiss();
        act.saveTabs();
        act.recreate();
    }

    void showAppearance() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(act.dp(20), 0, act.dp(20), act.dp(8));
        box.addView(Ui.medium(Ui.text(act, L.t("Оформление"), 20, Ui.TEXT)));
        final Dialog[] d = new Dialog[1];

        TextView tl = Ui.medium(Ui.text(act, L.t("Тема"), 13, Ui.ACCENT));
        tl.setPaddingRelative(0, act.dp(18), 0, act.dp(8));
        box.addView(tl);
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout modes = new LinearLayout(act);
        hs.addView(modes);
        for (int i = 0; i < Ui.MODE_NAMES.length; i++) {
            final int m = i;
            TextView c = act.home.chip(L.t(Ui.MODE_NAMES[i]));
            act.home.styleChip(c, Ui.mode == i);
            c.setOnClickListener(v -> { if (Ui.mode != m) { d[0].dismiss(); applyTheme(m, Ui.accent); } });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
            cl.setMarginEnd(act.dp(8));
            modes.addView(c, cl);
        }
        box.addView(hs);

        TextView al = Ui.medium(Ui.text(act, L.t("Цвет"), 13, Ui.ACCENT));
        al.setPaddingRelative(0, act.dp(18), 0, act.dp(8));
        box.addView(al);
        HorizontalScrollView hs2 = new HorizontalScrollView(act);
        hs2.setHorizontalScrollBarEnabled(false);
        LinearLayout acc = new LinearLayout(act);
        hs2.addView(acc);
        for (int i = 0; i < Ui.ACCENT_LIGHT.length; i++) {
            final int a = i;
            int col = Ui.dark ? Ui.ACCENT_DARK[i] : Ui.ACCENT_LIGHT[i];
            FrameLayout sw = new FrameLayout(act);
            sw.setBackground(Ui.accent == i ? Ui.stroke(col, Ui.TEXT, 3, 22) : Ui.oval(col));
            if (Ui.accent == i) {
                ImageView ok = Ui.icon(act, R.drawable.ic_check, Ui.dark ? 0xFF202124 : Color.WHITE);
                ok.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                sw.addView(ok, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            }
            sw.setContentDescription(L.t(Ui.ACCENT_NAMES[i]));
            sw.setOnClickListener(v -> { if (Ui.accent != a) { d[0].dismiss(); applyTheme(Ui.mode, a); } });
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(act.dp(48), act.dp(48));
            sl.setMarginEnd(act.dp(12));
            acc.addView(sw, sl);
        }
        box.addView(hs2);

        TextView wl = Ui.medium(Ui.text(act, L.t("Обои главной страницы"), 13, Ui.ACCENT));
        wl.setPaddingRelative(0, act.dp(18), 0, act.dp(8));
        box.addView(wl);
        int cur = act.store.p.getInt("wp", Wallpaper.NONE);
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(Wallpaper.NONE);
        for (int i = 0; i < Wallpaper.NAMES.length; i++) ids.add(i);
        ids.add(Wallpaper.CUSTOM);
        LinearLayout row = null;
        for (int k = 0; k < ids.size(); k++) {
            if (k % 3 == 0) { row = new LinearLayout(act); box.addView(row, new LinearLayout.LayoutParams(act.MATCH, act.WRAP)); }
            final int id = ids.get(k);
            FrameLayout card = new FrameLayout(act);
            card.setClipToOutline(true);
            card.setBackground(Ui.round(Ui.CHIP, 14));
            String label;
            if (id == Wallpaper.NONE) {
                label = L.t("Без обоев");
                ImageView ic = Ui.icon(act, R.drawable.ic_close, Ui.TEXT2);
                card.addView(ic, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            } else if (id == Wallpaper.CUSTOM) {
                label = L.t("Своё фото");
                Bitmap b = Wallpaper.customFile(act).exists() ? Wallpaper.get(act, Wallpaper.CUSTOM, 0, 0) : null;
                if (b != null) {
                    ImageView iv = new ImageView(act);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(b);
                    card.addView(iv, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
                }
                ImageView ic = Ui.icon(act, R.drawable.ic_add, b != null ? Color.WHITE : Ui.TEXT2);
                card.addView(ic, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            } else {
                label = L.t(Wallpaper.NAMES[id]);
                ImageView iv = new ImageView(act);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(Wallpaper.render(id, act.dp(60), act.dp(100)));
                card.addView(iv, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            }
            TextView lb = Ui.single(act, label, 12, id == Wallpaper.NONE || (id == Wallpaper.CUSTOM && !Wallpaper.customFile(act).exists()) ? Ui.TEXT : Color.WHITE);
            lb.setShadowLayer(act.dp(3), 0, act.dp(1), id >= 0 ? 0x99000000 : 0);
            lb.setGravity(Gravity.CENTER);
            lb.setPaddingRelative(act.dp(4), 0, act.dp(4), act.dp(8));
            card.addView(lb, new FrameLayout.LayoutParams(act.MATCH, act.WRAP, Gravity.BOTTOM));
            if (id == cur) card.setForeground(Ui.stroke(Color.TRANSPARENT, Ui.ACCENT, 3, 14));
            card.setOnClickListener(v -> {
                d[0].dismiss();
                if (id == Wallpaper.CUSTOM) {
                    try { act.startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), act.REQ_WALL); }
                    catch (Exception e) { act.toast(L.t("Нет приложения для выбора фото")); }
                } else { act.store.p.edit().putInt("wp", id).apply(); act.refreshChrome(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, act.dp(150), 1);
            lp.setMargins(act.dp(4), act.dp(4), act.dp(4), act.dp(4));
            row.addView(card, lp);
        }
        d[0] = act.sheet(box);
    }

    int ntpCard() { return act.ntpOnWall ? (Ui.dark ? 0xE6202124 : 0xEEFFFFFF) : Ui.CARD; }

    View wrapWall(View sv, Bitmap wp) {
        if (wp == null) return sv;
        FrameLayout fr = new FrameLayout(act);
        ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(wp);
        fr.addView(iv, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        View dim = new View(act);
        dim.setBackgroundColor(Ui.dark ? 0x66000000 : 0x26000000);
        fr.addView(dim, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        fr.addView(sv, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        return fr;
    }

    void showSettings() {
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, 0, 0, act.dp(24));
        sv.addView(box);
        section(box, L.t("Оформление"));
        int wp = act.store.p.getInt("wp", Wallpaper.NONE);
        actionRow(box, L.t("Тема, цвет и обои"), L.t(Ui.MODE_NAMES[Ui.mode]) + " · " + L.t(Ui.ACCENT_NAMES[Ui.accent]) + " · "
                + (wp == Wallpaper.NONE ? L.t("без обоев") : wp == Wallpaper.CUSTOM ? L.t("своё фото") : L.t(Wallpaper.NAMES[wp])), this::showAppearance);
        final String[] zooms = {"80%", "90%", "100%", "110%", "125%", "150%", "175%", "200%"};
        final int[] zv = {80, 90, 100, 110, 125, 150, 175, 200};
        final TextView[] zs = new TextView[1];
        zs[0] = actionRow(box, L.t("Масштаб текста"), act.store.p.getInt("zoom", 100) + "%", () -> {
            int curZ = act.store.p.getInt("zoom", 100), sel = 2;
            for (int i = 0; i < zv.length; i++) if (zv[i] == curZ) sel = i;
            act.dialog().setTitle(L.t("Масштаб текста")).setSingleChoiceItems(zooms, sel, (d, w) -> {
                act.store.p.edit().putInt("zoom", zv[w]).apply();
                for (Tab t : act.tabs) t.web.getSettings().setTextZoom(zv[w]);
                zs[0].setText(zooms[w]);
                d.dismiss();
            }).show();
        });
        switchRow(box, L.t("Тёмная тема для сайтов"), L.t("Затемнять светлые сайты при тёмной теме"), act.store.bool("darkSites", false), v -> {
            act.store.setBool("darkSites", v);
            for (Tab t : act.tabs) act.applySiteSettings(t.web.getSettings());
        });
        section(box, L.t("Основные"));
        actionRow(box, L.t("Язык"), langName(), this::pickLanguage);
        final TextView[] engSub = new TextView[1];
        engSub[0] = actionRow(box, L.t("Поисковая система"), L.t(Store.ENGINES[act.store.engine()]), () ->
                act.dialog().setTitle(L.t("Поисковая система")).setSingleChoiceItems(L.ta(Store.ENGINES), act.store.engine(), (d, w) -> {
                    act.store.setEngine(w); engSub[0].setText(L.t(Store.ENGINES[w])); d.dismiss();
                }).show());
        switchRow(box, L.t("Поисковые подсказки"), L.t("Подсказки поисковика при вводе запроса"), act.store.bool("suggest", true), v -> act.store.setBool("suggest", v));
        switchRow(box, L.t("Потянуть вниз для обновления"), L.t("Обновлять страницу жестом сверху вниз"), act.store.bool("ptr", true), v -> act.store.setBool("ptr", v));
        switchRow(box, L.t("Картинка в картинке"), L.t("Видео на весь экран продолжает играть в окне при выходе"), act.store.bool("pip", true), v -> {
            act.store.setBool("pip", v);
            if (act.current != null) act.current.web.evaluateJavascript(Scripts.R("window.__lasurKeep=" + v + ";"), null);
            act.pip.updatePipParams();
        });
        switchRow(box, L.t("Восстанавливать вкладки"), L.t("Открывать прошлые вкладки при запуске"), act.store.restoreTabs(), v -> act.store.setBool("restore", v));
        if (Math.round(act.scrWpx() / Ui.density) >= 600)
            switchRow(box, L.t("Панель вкладок"), L.t("Вкладки над адресной строкой на большом экране"), act.store.bool("tabStrip", true), v -> { act.store.setBool("tabStrip", v); act.refreshStrip(); act.refreshChrome(); });
        switchRow(box, L.t("Адресная строка снизу"), L.t("Удобнее нажимать одной рукой"), act.store.bottomBar(), v -> { act.store.setBool("bottomBar", v); act.layoutBars(); act.refreshChrome(); });
        switchRow(box, L.t("Скрывать панель при прокрутке"), L.t("Больше места для страницы"), act.store.hideOnScroll(), v -> { act.store.setBool("hideBar", v); if (!v) act.setBarsHidden(false); });
        actionRow(box, L.t("Сделать браузером по умолчанию"), L.t("Открывать ссылки из других приложений в Lasur"), () -> {
            act.onboarding.makeDefaultBrowser();
        });
        section(box, L.t("Без рекламы"));
        actionRow(box, L.t("Блокировщик рекламы"), L.t("Статистика, исключения и фильтры"), this::showAdblock);
        switchRow(box, L.t("Блокировка рекламы"), L.t("Реклама, трекеры, баннеры (EasyList, RuAdList, AdGuard)"), act.store.adblock(), v -> {
            act.store.setBool("adblock", v); AdBlocker.enabled = v;
        });
        switchRow(box, L.t("Блокировать всплывающие окна"), L.t("И рекламные переходы без нажатия"), act.store.blockPopups(), v -> act.store.setBool("popups", v));
        final TextView[] listSub = new TextView[1];
        listSub[0] = actionRow(box, L.t("Обновить фильтры"), AdBlocker.stats(), () -> {
            listSub[0].setText(L.t("Загрузка…"));
            act.BG.execute(() -> {
                try { AdBlocker.update(act.getApplicationContext()); act.ui.post(() -> listSub[0].setText(L.t("Обновлено · ") + AdBlocker.stats())); }
                catch (Exception e) { act.ui.post(() -> listSub[0].setText(L.t("Ошибка: ") + e.getMessage())); }
            });
        });
        actionRow(box, L.t("Заблокировано всего"), AdBlocker.totalBlocked.get() + L.t(" запросов рекламы и трекеров"), null);
        if (!AdBlocker.whitelist.isEmpty())
            actionRow(box, L.t("Сайты-исключения"), AdBlocker.whitelist.size() + L.t(" — нажмите, чтобы очистить"), () -> {
                act.store.p.edit().remove("whitelist").apply(); AdBlocker.whitelist = act.store.whitelist(); act.toast(L.t("Исключения очищены"));
            });
        section(box, L.t("Сайты"));
        switchRow(box, "JavaScript", L.t("Нужен для работы большинства сайтов"), act.store.js(), v -> {
            act.store.setBool("js", v); for (Tab t : act.tabs) t.web.getSettings().setJavaScriptEnabled(v && (!t.incognito || ((LWebView) t.web).privateProfile));
        });
        switchRow(box, L.t("Версия для ПК по умолчанию"), L.t("Для новых вкладок"), act.store.desktopDefault(), v -> act.store.setBool("desktop", v));
        actionRow(box, L.t("Разрешения сайтов"), L.t("Микрофон, камера, местоположение, новые вкладки"), act.perms::showSitePermissions);
        section(box, L.t("Пароли"));
        actionRow(box, L.t("Пароли"), act.passwords.list.isEmpty() ? L.t("Сохранение и автозаполнение паролей") : L.t("Сохранено: ") + act.passwords.list.size(), act.passwordsUi::showPasswords);
        section(box, L.t("Конфиденциальность"));
        actionRow(box, L.t("Очистить историю"), L.t("Удалить всю историю просмотров"), () -> { act.store.history.clear(); act.store.saveHistory(); act.toast(L.t("История очищена")); });
        actionRow(box, L.t("Очистить cookies и данные сайтов"), L.t("Вы выйдете из аккаунтов на сайтах"), () -> act.dialog()
                .setMessage(L.t("Удалить cookies, кэш и данные всех сайтов?"))
                .setPositiveButton(L.t("Удалить"), (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    WebStorage.getInstance().deleteAllData();
                    WebViewDatabase.getInstance(act).clearHttpAuthUsernamePassword();
                    for (Tab t : act.tabs) { t.web.clearCache(true); t.web.clearFormData(); }
                    act.toast(L.t("Данные удалены"));
                }).setNegativeButton(L.t("Отмена"), null).show());
        switchRow(box, L.t("Блокировка вкладок инкогнито"), L.t("Отпечаток пальца или PIN-код при возврате в браузер"), act.store.bool("incLock", false), v -> {
            act.store.setBool("incLock", v);
            if (v && !act.incLock.deviceSecure()) act.toast(L.t("Сначала включите блокировку экрана в настройках Android"));
        });
        final TextView[] dnsSub = new TextView[1];
        dnsSub[0] = actionRow(box, L.t("Безопасный DNS"), dnsStatus(), () -> showSecureDns(dnsSub[0]));
        section(box, L.t("Данные"));
        actionRow(box, L.t("Экспорт данных"), L.t("Настройки, закладки, история, ярлыки и пароли в файл"), act.backup::startExport);
        actionRow(box, L.t("Импорт данных"), L.t("Восстановить из файла резервной копии Lasur"), act.backup::startImport);
        section(box, L.t("О браузере"));
        actionRow(box, "Lasur " + BuildConfig.VERSION_NAME, L.t("Браузер без рекламы с загрузкой видео"), null);
        act.settingsDialog = act.lists.fullDialog(L.t("Настройки"), sv, null, null);
    }

    static final String[][] DNS = {
            {"AdGuard DNS", "dns.adguard-dns.com", "Блокирует рекламу и трекеры"},
            {"Cloudflare", "one.one.one.one", "Быстрый, без журналов"},
            {"Google Public DNS", "dns.google", "Надёжный и быстрый"},
            {"Quad9", "dns.quad9.net", "Блокирует вредоносные сайты"}};

    /** Android's Private DNS (DNS over TLS) for the active network; WebView always uses the system resolver. */
    String dnsStatus() {
        if (android.os.Build.VERSION.SDK_INT < 28) return L.t("Доступно на Android 9 и новее");
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) act.getSystemService(act.CONNECTIVITY_SERVICE);
            android.net.Network n = cm == null ? null : cm.getActiveNetwork();
            android.net.LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp != null && lp.isPrivateDnsActive()) {
                String name = lp.getPrivateDnsServerName();
                return name != null ? L.t("Включён: ") + name : L.t("Автоматически (если сеть поддерживает)");
            }
            String mode = Settings.Global.getString(act.getContentResolver(), "private_dns_mode");
            if ("hostname".equals(mode)) {
                String h = Settings.Global.getString(act.getContentResolver(), "private_dns_specifier");
                return L.t("Включён: ") + (h == null ? "" : h);
            }
            if (lp != null) return L.t("Выключен — запросы к сайтам видны сети");
        } catch (Exception e) { Log.d("Lasur", "dns status", e); }
        return L.t("Системный DNS");
    }

    void showSecureDns(TextView sub) {
        if (android.os.Build.VERSION.SDK_INT < 28) { act.toast(L.t("Доступно на Android 9 и новее")); return; }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView info = Ui.text(act, L.t("Встроенный в Android WebView движок всегда использует системный DNS, поэтому зашифровать DNS можно только для всего телефона — через «Частный DNS». Выберите провайдера: его адрес будет скопирован, затем откроются настройки сети — вставьте адрес в поле «Имя хоста провайдера».")
                + "\n\n" + L.t("Сейчас: ") + dnsStatus(), 14, Ui.TEXT2);
        info.setPaddingRelative(act.dp(24), act.dp(8), act.dp(24), act.dp(8));
        box.addView(info);
        final android.app.AlertDialog[] d = new android.app.AlertDialog[1];
        for (String[] p : DNS) {
            LinearLayout r = new LinearLayout(act);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPaddingRelative(act.dp(24), act.dp(10), act.dp(24), act.dp(10));
            r.setBackground(Ui.ripple(act, false));
            r.addView(Ui.text(act, p[0], 16, Ui.TEXT));
            r.addView(Ui.text(act, p[1] + " · " + L.t(p[2]), 13, Ui.TEXT2));
            r.setOnClickListener(v -> {
                if (d[0] != null) d[0].dismiss();
                ((android.content.ClipboardManager) act.getSystemService(act.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("dns", p[1]));
                openDnsSettings();
                act.toast(L.t("Скопировано: ") + p[1] + L.t(". Откройте «Частный DNS» и вставьте адрес"));
            });
            box.addView(r, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        }
        ScrollView sv = new ScrollView(act);
        sv.addView(box);
        d[0] = act.dialog().setTitle(L.t("Безопасный DNS")).setView(sv)
                .setNeutralButton(L.t("Открыть настройки"), (x, w) -> openDnsSettings())
                .setNegativeButton(L.t("Закрыть"), null).show();
        d[0].setOnDismissListener(x -> act.ui.postDelayed(() -> sub.setText(dnsStatus()), 300));
    }

    void openDnsSettings() {
        String[] actions = {"android.settings.PRIVATE_DNS_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS};
        for (String a : actions) {
            try { act.startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
            catch (Exception e) { Log.d("Lasur", "no " + a); }
        }
    }

    void openExternal(Tab.Video v, Tab t) {
        String type = VideoUi.typeOf(v.url);
        String mime = type.equals("HLS") ? "application/x-mpegURL" : type.equals("DASH") ? "application/dash+xml" : "video/*";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(v.url), mime);
        ArrayList<String> h = new ArrayList<>();
        if (v.page != null) { h.add("Referer"); h.add(v.page); }
        h.add("User-Agent"); h.add(act.videoUi.uaOf(t));
        // Session cookies are deliberately NOT handed to third-party players (they would get the user's logins).
        i.putExtra("headers", h.toArray(new String[0]));
        i.putExtra("title", v.title);
        i.putExtra("http-referrer", v.page);
        Bundle hb = new Bundle();
        for (int k = 0; k + 1 < h.size(); k += 2) hb.putString(h.get(k), h.get(k + 1));
        i.putExtra("android.media.intent.extra.HTTP_HEADERS", hb);
        try { act.startActivity(Intent.createChooser(i, L.t("Открыть в видеоплеере"))); }
        catch (Exception e) {
            try { i.setDataAndType(Uri.parse(v.url), "video/*"); act.startActivity(Intent.createChooser(i, L.t("Открыть в видеоплеере"))); }
            catch (Exception e2) { act.toast(L.t("Установите видеоплеер, например VLC или MX Player")); }
        }
    }

    void showAdblock() {
        final Tab t = act.current;
        final boolean page = t != null && !t.ntp && t.pageHost != null;
        final String host = page ? t.pageHost : null;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, 0, 0, act.dp(8));
        final Dialog[] d = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            boolean on = AdBlocker.enabled;
            boolean wl = host != null && AdBlocker.siteAllowed(host);
            boolean active = on && !wl;
            // header
            LinearLayout head = new LinearLayout(act);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPaddingRelative(act.dp(22), act.dp(2), act.dp(22), act.dp(10));
            FrameLayout badge = new FrameLayout(act);
            int good = Ui.dark ? 0xFF81C995 : 0xFF188038;
            badge.setBackground(Ui.round(active ? (Ui.dark ? 0xFF1E3A2B : 0xFFE6F4EA) : (Ui.CHIP), 16));
            ImageView bi = Ui.icon(act, R.drawable.ic_shield, active ? good : Ui.TEXT2);
            bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bi.setPaddingRelative(act.dp(12), act.dp(12), act.dp(12), act.dp(12));
            badge.addView(bi, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            head.addView(badge, new LinearLayout.LayoutParams(act.dp(52), act.dp(52)));
            LinearLayout tx = new LinearLayout(act);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPaddingRelative(act.dp(14), 0, 0, 0);
            tx.addView(Ui.medium(Ui.text(act, L.t("Блокировщик рекламы"), 18, Ui.TEXT)));
            tx.addView(Ui.single(act, !on ? L.t("Выключен") : wl ? L.t("Отключён на ") + host : host != null ? L.t("Защищает ") + host : L.t("Включён"), 13, active ? good : Ui.TEXT2));
            head.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
            box.addView(head);
            // counters
            LinearLayout stats = new LinearLayout(act);
            stats.setPaddingRelative(act.dp(16), 0, act.dp(16), act.dp(6));
            String[][] st = {{page ? String.valueOf(t.blocked.get()) : "—", L.t("на этой странице")}, {String.valueOf(AdBlocker.totalBlocked.get()), L.t("заблокировано всего")}};
            for (String[] s : st) {
                LinearLayout c = new LinearLayout(act);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPaddingRelative(act.dp(16), act.dp(12), act.dp(16), act.dp(12));
                c.setBackground(Ui.round(Ui.CHIP2, 16));
                c.addView(Ui.medium(Ui.text(act, s[0], 22, Ui.TEXT)));
                c.addView(Ui.single(act, s[1], 12, Ui.TEXT2));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, act.WRAP, 1);
                cl.setMargins(act.dp(6), 0, act.dp(6), 0);
                stats.addView(c, cl);
            }
            box.addView(stats);
            switchRow(box, L.t("Блокировать рекламу"), L.t("Реклама, трекеры и баннеры на всех сайтах"), on, v -> {
                act.store.setBool("adblock", v); AdBlocker.enabled = v; fill[0].run();
                if (page) t.web.reload();
            });
            if (host != null && on) switchRow(box, L.t("Блокировать на этом сайте"), wl ? L.t("Сайт в исключениях — реклама показывается") : L.t("Выключите, если сайт работает неправильно"), !wl, v -> {
                act.store.setWhitelisted(host, !v); AdBlocker.whitelist = act.store.whitelist(); fill[0].run(); t.web.reload();
            });
            switchRow(box, L.t("Блокировать всплывающие окна"), L.t("И рекламные переходы без нажатия"), act.store.blockPopups(), v -> act.store.setBool("popups", v));
            final TextView[] ls = new TextView[1];
            ls[0] = actionRow(box, L.t("Обновить фильтры"), AdBlocker.stats(), () -> {
                ls[0].setText(L.t("Загрузка…"));
                act.BG.execute(() -> {
                    try { AdBlocker.update(act.getApplicationContext()); act.ui.post(() -> { ls[0].setText(L.t("Обновлено · ") + AdBlocker.stats()); act.snack(L.t("Фильтры обновлены"), null, null); }); }
                    catch (Exception e) { act.ui.post(() -> ls[0].setText(L.t("Ошибка: ") + e.getMessage())); }
                });
            });
            java.util.Set<String> wls = AdBlocker.whitelist;
            if (!wls.isEmpty()) {
                section(box, L.t("Сайты-исключения · ") + wls.size());
                for (String h : new java.util.TreeSet<>(wls)) {
                    LinearLayout r = new LinearLayout(act);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    r.setPaddingRelative(act.dp(20), act.dp(6), act.dp(8), act.dp(6));
                    r.addView(act.home.tileIcon(h, "https://" + h, false, 0, 30), new LinearLayout.LayoutParams(act.dp(30), act.dp(30)));
                    TextView ht = Ui.single(act, h, 15, Ui.TEXT);
                    ht.setPaddingRelative(act.dp(14), 0, 0, 0);
                    r.addView(ht, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                    ImageView del = Ui.iconBtn(act, R.drawable.ic_close, Ui.TEXT2);
                    del.setOnClickListener(v -> { act.store.setWhitelisted(h, false); AdBlocker.whitelist = act.store.whitelist(); fill[0].run(); if (h.equals(host)) t.web.reload(); });
                    r.addView(del, new LinearLayout.LayoutParams(act.dp(40), act.dp(40)));
                    box.addView(r);
                }
            }
            TextView hint = Ui.text(act, L.t("Списки: EasyList, RuAdList, AdGuard Russian и базы рекламных доменов. Видеореклама YouTube пропускается автоматически."), 12, Ui.TEXT2);
            hint.setPaddingRelative(act.dp(22), act.dp(10), act.dp(22), 0);
            box.addView(hint);
        };
        fill[0].run();
        d[0] = act.sheet(box);
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
        if (act.customView != null) act.hideCustomView();
        try { t.web.evaluateJavascript(PAUSE_JS + "(function(){try{var f=document.querySelectorAll('iframe');for(var i=0;i<f.length;i++){try{f[i].contentWindow.postMessage('{\\\"event\\\":\\\"command\\\",\\\"func\\\":\\\"pauseVideo\\\",\\\"args\\\":\\\"\\\"}','*')}catch(e){}}}catch(e){}})();", null); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        t.mediaPlaying = false; t.mediaPipEligible = false;
        if (t == act.current) act.pip.updatePipParams();
        try { t.web.onPause(); t.silenced = true; } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        if (t.video != null) act.videoUi.updateVideoFab();
    }

    String langName() {
        String p = L.pref(act);
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
        String p = L.pref(act);
        int sel = 0;
        for (int i = 0; i < L.CODES.length; i++) if (L.CODES[i].equals(p)) sel = i;
        act.dialog().setTitle(L.t("Язык")).setSingleChoiceItems(names, sel, (d, w) -> {
            d.dismiss();
            if (L.CODES[w].equals(p)) return;
            act.store.p.edit().putString("lang", L.CODES[w]).commit();
            if (act.settingsDialog != null && act.settingsDialog.isShowing()) act.settingsDialog.dismiss();
            act.saveTabs();
            act.recreate();
        }).show();
    }
}
