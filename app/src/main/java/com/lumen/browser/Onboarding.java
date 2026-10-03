package com.lumen.browser;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Launch animation and the first-run welcome screen. */
final class Onboarding {
    final MainActivity act;

    Onboarding(MainActivity act) { this.act = act; }

    FrameLayout splash;

    void showSplash() {
        splash = new FrameLayout(act);
        splash.setBackgroundColor(Ui.BG);
        splash.setClickable(true);
        DropSplashView drop = new DropSplashView(act);
        splash.addView(drop, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        act.root.addView(splash, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, DropSplashView.END);
        a.setDuration((long) DropSplashView.END);
        a.setInterpolator(new android.view.animation.LinearInterpolator());
        a.addUpdateListener(v -> drop.setTime((float) v.getAnimatedValue()));
        a.start();
        act.ui.postDelayed(() -> {
            if (splash == null) return;
            final View s = splash;
            splash = null;
            s.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                a.cancel();
                act.root.removeView(s);
                if (!act.store.bool("onboarded", false)) showWelcome();
            }).start();
        }, 820);
    }

    FrameLayout welcome;
    int wStep, wEngine, wMode, wAccent;

    void showWelcome() {
        wStep = 0;
        wEngine = act.store.engine();
        wMode = Ui.mode;
        wAccent = Ui.accent;
        welcome = new FrameLayout(act);
        welcome.setBackgroundColor(Ui.BG);
        welcome.setClickable(true);
        act.root.addView(welcome, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        welcome.setAlpha(0f);
        welcome.animate().alpha(1f).setDuration(200).start();
        buildWelcome();
    }

    void buildWelcome() {
        welcome.removeAllViews();
        int scrW = act.scrWpx();
        int side = Math.max(act.dp(24), (scrW - act.dp(560)) / 2);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        welcome.addView(col, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));

        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPaddingRelative(side - act.dp(12), act.dp(8), side - act.dp(12), 0);
        if (wStep > 0) {
            ImageView back = Ui.iconBtn(act, R.drawable.ic_back, Ui.TEXT2);
            back.setOnClickListener(v -> { wStep--; buildWelcome(); });
            top.addView(back, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
        }
        top.addView(new View(act), new LinearLayout.LayoutParams(0, act.dp(48), 1));
        if (wStep < 3) {
            TextView skip = Ui.medium(Ui.text(act, L.t("Пропустить"), 15, Ui.TEXT2));
            skip.setPaddingRelative(act.dp(12), act.dp(12), act.dp(12), act.dp(12));
            skip.setBackground(Ui.ripple(act, true));
            skip.setOnClickListener(v -> finishWelcome());
            top.addView(skip);
        }
        col.addView(top);

        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPaddingRelative(side, act.dp(8), side, act.dp(16));
        sv.addView(box, new FrameLayout.LayoutParams(act.MATCH, act.WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(act.MATCH, 0, 1));

        if (wStep == 0) {
            ImageView logo = new ImageView(act);
            logo.setImageResource(R.drawable.ic_logo_drop);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(act.dp(96), act.dp(96));
            ll.topMargin = act.dp(16);
            box.addView(logo, ll);
            welcomeTitle(box, L.t("Добро пожаловать в Lasur"), L.t("Быстрый браузер без рекламы"));
            String[][] f = {{L.t("Без рекламы"), L.t("Блокировка рекламы, трекеров и всплывающих окон")},
                    {L.t("Видео с сайтов"), L.t("Скачивание и просмотр во внешнем плеере")},
                    {L.t("Пароли"), L.t("Безопасное хранение и автозаполнение")},
                    {L.t("Темы и обои"), L.t("Светлая, тёмная и чёрная тема, 7 цветов")}};
            int[] ic = {R.drawable.ic_shield, R.drawable.ic_download, R.drawable.ic_key, R.drawable.ic_palette};
            for (int i = 0; i < f.length; i++) box.addView(featureRow(ic[i], L.t(f[i][0]), L.t(f[i][1])), new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            LinearLayout lr = new LinearLayout(act);
            lr.setGravity(Gravity.CENTER_VERTICAL);
            lr.setPaddingRelative(act.dp(16), act.dp(10), act.dp(18), act.dp(10));
            lr.setBackground(Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 22));
            lr.addView(Ui.icon(act, R.drawable.ic_translate, Ui.ACCENT), new LinearLayout.LayoutParams(act.dp(20), act.dp(20)));
            TextView lt = Ui.medium(Ui.text(act, act.settingsUi.langName(), 14, Ui.TEXT));
            lt.setPaddingRelative(act.dp(10), 0, 0, 0);
            lr.addView(lt);
            lr.setOnClickListener(v -> act.settingsUi.pickLanguage());
            LinearLayout.LayoutParams lrl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
            lrl.topMargin = act.dp(22);
            box.addView(lr, lrl);
        } else if (wStep == 1) {
            welcomeTitle(box, L.t("Поисковая система"), L.t("Её можно поменять в настройках"));
            for (int i = 0; i < Store.ENGINES.length; i++) {
                final int k = i;
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(14), act.dp(16), act.dp(14));
                boolean sel = wEngine == i;
                r.setBackground(sel ? Ui.stroke(Ui.TONAL, Ui.ACCENT, 2, 18) : Ui.round(Ui.CHIP2, 18));
                r.addView(act.home.tileIcon(L.t(Store.ENGINES[i]), Store.ENGINE_URLS[i], false, 0, 32), new LinearLayout.LayoutParams(act.dp(32), act.dp(32)));
                TextView tv = Ui.medium(Ui.text(act, L.t(Store.ENGINES[i]), 16, sel ? Ui.ON_TONAL : Ui.TEXT));
                tv.setPaddingRelative(act.dp(14), 0, 0, 0);
                r.addView(tv, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                if (sel) r.addView(Ui.icon(act, R.drawable.ic_check, Ui.ACCENT), new LinearLayout.LayoutParams(act.dp(22), act.dp(22)));
                r.setOnClickListener(v -> { wEngine = k; buildWelcome(); });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(act.MATCH, act.WRAP);
                rl.topMargin = act.dp(10);
                box.addView(r, rl);
            }
        } else if (wStep == 2) {
            welcomeTitle(box, L.t("Оформление"), L.t("Тему, цвет и обои можно поменять в любой момент"));
            LinearLayout modes = new LinearLayout(act);
            modes.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < Ui.MODE_NAMES.length; i++) {
                final int m = i;
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(13), act.dp(16), act.dp(13));
                boolean sel = wMode == i;
                r.setBackground(sel ? Ui.stroke(Ui.TONAL, Ui.ACCENT, 2, 18) : Ui.round(Ui.CHIP2, 18));
                View sw2 = new View(act);
                int[] pv = {Ui.dark ? 0xFF202124 : 0xFFFFFFFF, 0xFFFFFFFF, 0xFF202124, 0xFF000000};
                sw2.setBackground(Ui.stroke(pv[i], Ui.dark ? 0xFF5F6368 : 0xFFBDC1C6, 1.5f, 14));
                r.addView(sw2, new LinearLayout.LayoutParams(act.dp(28), act.dp(28)));
                TextView tv = Ui.medium(Ui.text(act, L.t(Ui.MODE_NAMES[i]), 16, sel ? Ui.ON_TONAL : Ui.TEXT));
                tv.setPaddingRelative(act.dp(14), 0, 0, 0);
                r.addView(tv, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                if (sel) r.addView(Ui.icon(act, R.drawable.ic_check, Ui.ACCENT), new LinearLayout.LayoutParams(act.dp(22), act.dp(22)));
                r.setOnClickListener(v -> { wMode = m; buildWelcome(); });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(act.MATCH, act.WRAP);
                rl.topMargin = act.dp(10);
                modes.addView(r, rl);
            }
            box.addView(modes, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            TextView al = Ui.medium(Ui.text(act, L.t("Цвет"), 14, Ui.ACCENT));
            al.setPaddingRelative(0, act.dp(22), 0, act.dp(10));
            box.addView(al, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            LinearLayout acc = new LinearLayout(act);
            acc.setGravity(Gravity.CENTER);
            for (int i = 0; i < Ui.ACCENT_LIGHT.length; i++) {
                final int a = i;
                int colr = Ui.dark ? Ui.ACCENT_DARK[i] : Ui.ACCENT_LIGHT[i];
                FrameLayout s = new FrameLayout(act);
                s.setBackground(wAccent == i ? Ui.stroke(colr, Ui.TEXT, 3, 22) : Ui.oval(colr));
                if (wAccent == i) {
                    ImageView ok = Ui.icon(act, R.drawable.ic_check, Ui.dark ? 0xFF202124 : Color.WHITE);
                    ok.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    s.addView(ok, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
                }
                s.setOnClickListener(v -> { wAccent = a; buildWelcome(); });
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(act.dp(40), act.dp(40));
                sl.setMargins(act.dp(5), 0, act.dp(5), 0);
                acc.addView(s, sl);
            }
            box.addView(acc, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        } else {
            welcomeTitle(box, L.t("Почти готово"), L.t("Основные настройки"));
            LinearLayout sw = new LinearLayout(act);
            sw.setOrientation(LinearLayout.VERTICAL);
            sw.setBackground(Ui.round(Ui.CHIP2, 20));
            act.settingsUi.switchRow(sw, L.t("Блокировка рекламы"), L.t("Реклама, трекеры и баннеры"), act.store.adblock(), v -> { act.store.setBool("adblock", v); AdBlocker.enabled = v; });
            act.settingsUi.switchRow(sw, L.t("Блокировать всплывающие окна"), L.t("И рекламные переходы без нажатия"), act.store.blockPopups(), v -> act.store.setBool("popups", v));
            act.settingsUi.switchRow(sw, L.t("Предлагать сохранять пароли"), L.t("После входа на сайт"), act.store.bool("pwSave", true), v -> act.store.setBool("pwSave", v));
            act.settingsUi.switchRow(sw, L.t("Восстанавливать вкладки"), L.t("Открывать прошлые вкладки при запуске"), act.store.restoreTabs(), v -> act.store.setBool("restore", v));
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(act.MATCH, act.WRAP);
            swl.topMargin = act.dp(8);
            box.addView(sw, swl);
            TextView def = act.videoUi.pillButton(L.t("Сделать браузером по умолчанию"), R.drawable.ic_globe, Ui.TONAL, Ui.ON_TONAL);
            def.setOnClickListener(v -> makeDefaultBrowser());
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(act.MATCH, act.dp(52));
            dl.topMargin = act.dp(16);
            box.addView(def, dl);
        }

        LinearLayout bottom = new LinearLayout(act);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPaddingRelative(side, act.dp(8), side, act.dp(22));
        LinearLayout dots = new LinearLayout(act);
        for (int i = 0; i < 4; i++) {
            View d = new View(act);
            d.setBackground(Ui.round(i == wStep ? Ui.ACCENT : (Ui.dark ? 0xFF5F6368 : 0xFFDADCE0), 4));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(act.dp(i == wStep ? 22 : 8), act.dp(8));
            dlp.setMarginEnd(act.dp(6));
            dots.addView(d, dlp);
        }
        bottom.addView(dots, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        TextView next = act.videoUi.pillButton(wStep == 0 ? L.t("Начать") : wStep == 3 ? L.t("Готово") : L.t("Далее"), wStep == 3 ? R.drawable.ic_check : R.drawable.ic_forward,
                Ui.ACCENT, Ui.dark ? 0xFF202124 : Color.WHITE);
        next.setOnClickListener(v -> { if (wStep < 3) { wStep++; buildWelcome(); } else finishWelcome(); });
        bottom.addView(next, new LinearLayout.LayoutParams(act.WRAP, act.dp(52)));
        col.addView(bottom);
        box.setAlpha(0f);
        box.setTranslationX(act.dp(L.rtl() ? -24 : 24));
        box.animate().alpha(1f).translationX(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    void welcomeTitle(LinearLayout box, String title, String sub) {
        TextView t = Ui.medium(Ui.text(act, title, 26, Ui.TEXT));
        t.setGravity(Gravity.CENTER);
        t.setPaddingRelative(0, act.dp(18), 0, act.dp(6));
        box.addView(t, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        TextView s = Ui.text(act, sub, 15, Ui.TEXT2);
        s.setGravity(Gravity.CENTER);
        s.setPaddingRelative(0, 0, 0, act.dp(18));
        box.addView(s, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
    }

    View featureRow(int icon, String title, String sub) {
        LinearLayout r = new LinearLayout(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPaddingRelative(0, act.dp(9), 0, act.dp(9));
        FrameLayout b = new FrameLayout(act);
        b.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView i = Ui.icon(act, icon, Ui.ON_TONAL);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setPaddingRelative(act.dp(11), act.dp(11), act.dp(11), act.dp(11));
        b.addView(i, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        r.addView(b, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
        LinearLayout tx = new LinearLayout(act);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPaddingRelative(act.dp(14), 0, 0, 0);
        tx.addView(Ui.medium(Ui.text(act, title, 16, Ui.TEXT)));
        tx.addView(Ui.text(act, sub, 13, Ui.TEXT2));
        r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        return r;
    }

    void finishWelcome() {
        act.store.p.edit().putBoolean("onboarded", true).apply();
        if (wEngine != act.store.engine()) act.store.setEngine(wEngine);
        if (wMode != Ui.mode || wAccent != Ui.accent) { act.settingsUi.applyTheme(wMode, wAccent); return; }
        final View w = welcome;
        welcome = null;
        if (w != null) w.animate().alpha(0f).setDuration(200).withEndAction(() -> act.root.removeView(w)).start();
        act.refreshChrome();
    }

    void makeDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = (android.app.role.RoleManager) act.getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                    act.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER), 18);
                    return;
                }
                if (rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) { act.toast(L.t("Lasur уже браузер по умолчанию")); return; }
            }
            act.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) { act.toast(L.t("Откройте настройки Android → Приложения по умолчанию")); }
    }
}
