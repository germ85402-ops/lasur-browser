package com.lumen.browser;
import java.util.Locale;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/** The main overflow menu. */
final class MenuUi {
    final MainActivity act;

    MenuUi(MainActivity act) { this.act = act; }

    void showMenu() {
        act.unfocusOmni();
        final Tab t = act.current;
        boolean page = t != null && !t.ntp;
        boolean incMenu = t != null && t.incognito;
        int surface = incMenu ? Ui.INC_SURFACE : Ui.MENU_SURFACE;
        menuFg = incMenu ? Ui.INC_TEXT : Ui.TEXT;
        menuFg2 = incMenu ? Ui.INC_TEXT2 : Ui.TEXT2;
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, act.dp(4), 0, act.dp(6));
        sv.addView(box);
        final int mw = act.dp(Math.round(act.scrWpx() / Ui.density) >= 600 ? 320 : 272);
        final PopupWindow pw = new PopupWindow(sv, mw, act.WRAP, true);
        pw.setBackgroundDrawable(Ui.round(surface, 10));
        pw.setElevation(act.dp(10));

        LinearLayout top = new LinearLayout(act);
        int[] icons = {R.drawable.ic_forward, page && act.store.isBookmarked(t.web.getUrl()) ? R.drawable.ic_star : R.drawable.ic_star_border,
                R.drawable.ic_download, R.drawable.ic_info, page && t.loading ? R.drawable.ic_close : R.drawable.ic_refresh};
        for (int i = 0; i < icons.length; i++) {
            final int k = i;
            int color = menuFg2;
            if (i == 0 && (t == null || !t.web.canGoForward())) color = Ui.DISABLED;
            if (i == 4 && !page) color = Ui.DISABLED;
            if (i == 1 && page && act.store.isBookmarked(t.web.getUrl())) color = Ui.ACCENT;
            ImageView b = Ui.iconBtn(act, icons[i], color);
            if (i == 4 && page && t.loading) Ui.describe(b, L.t("Остановить загрузку"));
            b.setOnClickListener(v -> {
                pw.dismiss();
                if (t == null) return;
                if (k == 0) { if (t.web.canGoForward()) { t.ntp = false; t.web.goForward(); } }
                else if (k == 1) { if (page) { boolean on = act.store.toggleBookmark(t.title, t.web.getUrl()); act.snack(on ? L.t("Добавлено в закладки") : L.t("Закладка удалена"), on ? L.t("Закладки") : null, act.lists::showBookmarks); } }
                else if (k == 2) act.savePage(t);
                else if (k == 3) showSiteInfo();
                else if (page) { if (t.loading) { t.web.stopLoading(); t.loading = false; act.refreshChrome(); } else t.web.reload(); }
            });
            top.addView(b, new LinearLayout.LayoutParams(0, act.dp(52), 1));
        }
        box.addView(top);
        View dv = new View(act); dv.setBackgroundColor(incMenu ? Ui.INC_DIVIDER : Ui.DIVIDER);
        box.addView(dv, new LinearLayout.LayoutParams(act.MATCH, 1));

        menuItem(box, pw, R.drawable.ic_add, L.t("Новая вкладка"), () -> act.newTab(null, false, true, null));
        menuItem(box, pw, R.drawable.ic_incognito, L.t("Новая вкладка инкогнито"), () -> act.newTab(null, true, true, null));
        if (!act.closedStack.isEmpty()) menuItem(box, pw, R.drawable.ic_history, L.t("Вернуть закрытую вкладку"), () -> act.reopen(act.closedStack.get(act.closedStack.size() - 1)));
        menuItem(box, pw, R.drawable.ic_history, L.t("История"), act.lists::showHistory);
        menuItem(box, pw, R.drawable.ic_key, L.t("Пароли"), act.passwordsUi::showPasswords);
        menuItem(box, pw, R.drawable.ic_download, L.t("Загрузки"), act.downloads::showDownloads);
        menuItem(box, pw, R.drawable.ic_bookmarks, L.t("Закладки"), act.lists::showBookmarks);
        if (page) {
            menuItem(box, pw, R.drawable.ic_video, t.video != null ? L.t("Найденное видео") : L.t("Видео на странице"), act.videoUi::showVideos);
            menuItem(box, pw, R.drawable.ic_translate, L.t("Перевести страницу"), () -> act.translate(t));
            menuItem(box, pw, R.drawable.ic_share, L.t("Поделиться…"), () -> share(t.web.getUrl(), t.title));
            menuItem(box, pw, R.drawable.ic_reader, t.readerOn ? L.t("Закрыть режим чтения") : L.t("Режим чтения"), () -> act.reader.toggle(t));
            menuItem(box, pw, R.drawable.ic_search, L.t("Найти на странице"), act::showFind);
            menuItem(box, pw, R.drawable.ic_print, L.t("Печать / PDF"), () -> act.printPage(t));
            menuItem(box, pw, R.drawable.ic_add, L.t("Добавить ярлык на главную"), () -> act.home.editShortcut(-1, true));
            LinearLayout r = menuItem(box, pw, R.drawable.ic_desktop, L.t("Версия для ПК"), () -> toggleDesktop(t));
            android.widget.CheckBox cb = new android.widget.CheckBox(act);
            cb.setChecked(t.desktop);
            cb.setClickable(false);
            cb.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
            r.addView(cb);
        }
        menuItem(box, pw, R.drawable.ic_shield, L.t("Блокировка рекламы: ") + (AdBlocker.enabled ? L.t("вкл") : L.t("выкл"))
                + (page && AdBlocker.enabled ? " · " + t.blocked.get() : ""), act.settingsUi::showAdblock);
        menuItem(box, pw, R.drawable.ic_palette, L.t("Темы и обои"), act.settingsUi::showAppearance);
        menuItem(box, pw, R.drawable.ic_settings, L.t("Настройки"), act.settingsUi::showSettings);
        if (act.store.bottomBar()) {
            // bottom address bar: open upwards, above the toolbar, and never taller than the free space
            int[] loc = new int[2];
            act.menuBtn.getLocationInWindow(loc);
            WindowInsets wi = act.root.getRootWindowInsets();
            int avail = loc[1] - act.dp(8) - (wi != null ? act.insetsOf(wi)[1] : 0);
            sv.measure(View.MeasureSpec.makeMeasureSpec(mw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int h = Math.min(sv.getMeasuredHeight(), avail);
            pw.setHeight(h);
            pw.showAtLocation(act.root, Gravity.TOP | Gravity.START, Math.max(0, loc[0] + act.menuBtn.getWidth() - mw), loc[1] - h + act.dp(4));
        } else pw.showAsDropDown(act.menuBtn, 0, -act.menuBtn.getHeight());
    }

    int menuFg = Ui.TEXT, menuFg2 = Ui.TEXT2;

    LinearLayout menuItem(LinearLayout box, PopupWindow pw, int icon, String text, Runnable r) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(act.dp(16), 0, act.dp(12), 0);
        row.setBackground(Ui.ripple(act, false));
        row.addView(Ui.icon(act, icon, menuFg2), new LinearLayout.LayoutParams(act.dp(24), act.dp(24)));
        TextView tv = Ui.single(act, text, 15, menuFg);
        tv.setPaddingRelative(act.dp(18), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        row.setOnClickListener(v -> { pw.dismiss(); r.run(); });
        box.addView(row, new LinearLayout.LayoutParams(act.MATCH, act.dp(48)));
        return row;
    }

    void toggleDesktop(Tab t) {
        t.desktop = !t.desktop;
        t.web.getSettings().setUserAgentString(t.desktop ? act.desktopUA : act.mobileUA);
        t.ua = t.web.getSettings().getUserAgentString();
        t.web.reload();
        act.snack(t.desktop ? L.t("Открыта версия для ПК") : L.t("Открыта мобильная версия"), null, null);
    }

    void share(String url, String title) {
        if (url == null) return;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title);
        act.startActivity(Intent.createChooser(i, L.t("Поделиться")));
    }

    void copy(String s) {
        ((ClipboardManager) act.getSystemService(act.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("url", s));
        act.toast(L.t("Скопировано"));
    }

    void openDownloads() {
        try { act.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { act.toast(L.t("Файлы сохраняются в папку Загрузки/Lasur")); }
    }

    void showSiteInfo() {
        if (act.current == null || act.current.ntp) { act.showKb(act.omni); return; }
        final Tab t = act.current;
        final String host = t.pageHost;
        String u = t.web.getUrl();
        int sec = act.security(t);
        boolean secure = sec == 1;
        boolean badCert = sec == 2 && u != null && u.startsWith("https://") && host != null && t.sslHosts.contains(host.toLowerCase(Locale.ROOT));
        boolean wl = host != null && AdBlocker.siteAllowed(host);
        String msg = (secure ? L.t("🔒 Подключение защищено.\nДанные (пароли, номера карт) передаются в зашифрованном виде.")
                : badCert ? L.t("⚠ Сертификат сайта недействителен, но вы решили открыть его.\nНе вводите на этом сайте пароли и платёжные данные.")
                : t.mixed ? L.t("⚠ Часть страницы загружена без шифрования (смешанное содержимое).\nЕё могут подменить или подсмотреть в сети.")
                : L.t("⚠ Подключение не защищено.\nНе вводите на этом сайте конфиденциальные данные."))
                + L.t("\n\nБлокировка рекламы: ") + (!AdBlocker.enabled ? L.t("выключена в настройках") : wl ? L.t("отключена для этого сайта") : L.t("включена"))
                + L.t("\nЗаблокировано запросов на странице: ") + t.blocked.get()
                + L.t("\nВидео: ") + (t.video != null ? L.t("найдено") : L.t("не найдено"));
        AlertDialog.Builder b = act.dialog().setTitle(host == null ? act.displayUrl(u) : host).setMessage(msg)
                .setPositiveButton(L.t("ОК"), null);
        if (host != null && AdBlocker.enabled) b.setNeutralButton(wl ? L.t("Включить блокировку здесь") : L.t("Отключить блокировку здесь"), (d, w) -> {
            act.store.setWhitelisted(host, !wl);
            AdBlocker.whitelist = act.store.whitelist();
            t.web.reload();
        });
        b.show();
    }
}
