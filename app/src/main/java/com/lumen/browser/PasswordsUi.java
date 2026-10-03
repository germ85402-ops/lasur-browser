package com.lumen.browser;
import static com.lumen.browser.Scripts.*;
import android.widget.HorizontalScrollView;
import java.util.Locale;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;

/** Saved passwords screen, save prompts and autofill bar. */
final class PasswordsUi {
    final MainActivity act;

    PasswordsUi(MainActivity act) { this.act = act; }

    void maybeOfferSave(Tab t) {
        String site = t.pwSite, user = t.pwUser == null ? "" : t.pwUser, pass = t.pwPass;
        long at = t.pwSubmitAt;
        t.pwPass = null;
        t.pwSubmitAt = 0;
        if (pass == null || pass.isEmpty() || site == null || at == 0 || System.currentTimeMillis() - at > 60000) return;
        if (t.incognito || !act.store.bool("pwSave", true) || act.passwords.never().contains(site)) return;
        Passwords.Cred c = act.passwords.find(site, user);
        if (c != null && pass.equals(act.passwords.pass(c))) return;
        offerSave(site, user, pass, c != null);
    }

    void offerSave(String site, String user, String pass, boolean update) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(act.dp(22), act.dp(4), act.dp(22), act.dp(8));
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(act);
        badge.setBackground(Ui.round(Ui.TONAL, 14));
        ImageView bi = Ui.icon(act, R.drawable.ic_key, Ui.ON_TONAL);
        bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bi.setPaddingRelative(act.dp(12), act.dp(12), act.dp(12), act.dp(12));
        badge.addView(bi, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        head.addView(badge, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
        LinearLayout tx = new LinearLayout(act);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPaddingRelative(act.dp(14), 0, 0, 0);
        tx.addView(Ui.medium(Ui.text(act, update ? L.t("Обновить пароль?") : L.t("Сохранить пароль?"), 18, Ui.TEXT)));
        tx.addView(Ui.single(act, Passwords.label(site), 13, Ui.TEXT2));
        head.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        box.addView(head);
        EditText ue = new EditText(act);
        ue.setSingleLine(true);
        ue.setHint(L.t("Имя пользователя"));
        ue.setText(user);
        ue.setTextColor(Ui.TEXT);
        ue.setHintTextColor(Ui.TEXT2);
        ue.setEnabled(!update);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(act.MATCH, act.WRAP);
        ul.topMargin = act.dp(14);
        box.addView(ue, ul);
        LinearLayout pr = new LinearLayout(act);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        final TextView pt = Ui.text(act, mask(pass), 16, Ui.TEXT);
        pt.setPaddingRelative(act.dp(4), 0, 0, 0);
        pr.addView(pt, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        ImageView eye = Ui.iconBtn(act, R.drawable.ic_eye, Ui.TEXT2);
        final boolean[] shown = {false};
        eye.setOnClickListener(v -> { shown[0] = !shown[0]; pt.setText(shown[0] ? pass : mask(pass)); });
        pr.addView(eye, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
        box.addView(pr);
        LinearLayout btns = new LinearLayout(act);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPaddingRelative(0, act.dp(14), 0, 0);
        TextView never = Ui.medium(Ui.text(act, update ? L.t("Не сейчас") : L.t("Никогда"), 15, Ui.ACCENT));
        never.setPaddingRelative(act.dp(14), act.dp(12), act.dp(14), act.dp(12));
        never.setBackground(Ui.ripple(act, true));
        btns.addView(never);
        btns.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1));
        TextView save = act.videoUi.pillButton(update ? L.t("Обновить") : L.t("Сохранить"), R.drawable.ic_key, Ui.ACCENT, Ui.dark ? 0xFF202124 : Color.WHITE);
        btns.addView(save, new LinearLayout.LayoutParams(act.WRAP, act.dp(48)));
        box.addView(btns);
        final Dialog d = act.sheet(box);
        never.setOnClickListener(v -> {
            d.dismiss();
            if (!update) { act.passwords.addNever(site); act.snack(L.t("Пароли для ") + site + L.t(" не будут сохраняться"), L.t("Отменить"), () -> { java.util.Set<String> s = act.passwords.never(); s.remove(site); act.store.p.edit().putStringSet("pwNever", s).apply(); }); }
        });
        save.setOnClickListener(v -> {
            d.dismiss();
            act.passwords.put(site, ue.getText().toString().trim(), pass);
            act.snack(update ? L.t("Пароль обновлён") : L.t("Пароль сохранён"), L.t("Пароли"), this::showPasswords);
        });
    }

    static String mask(String p) { StringBuilder b = new StringBuilder(); for (int i = 0; i < Math.min(p.length(), 16); i++) b.append('•'); return b.toString(); }

    void showPwBar(Tab t) {
        if (act.isInPictureInPictureMode()) return;
        act.ui.removeCallbacks(act.pwBlurR);
        if (t != act.current || !act.store.bool("pwFill", true) || t.ntp || act.security(t) != 1) return;
        String site = Passwords.site(t.web.getUrl());
        ArrayList<Passwords.Cred> cs = act.passwords.forSite(site);
        if (cs.isEmpty()) { hidePwBar(); return; }
        if (act.pwBar == null) {
            act.pwBar = new LinearLayout(act);
            act.pwBar.setGravity(Gravity.CENTER_VERTICAL);
            act.pwBar.setElevation(act.dp(8));
            act.content.addView(act.pwBar, new FrameLayout.LayoutParams(act.MATCH, act.dp(52), Gravity.BOTTOM));
        }
        act.pwBar.removeAllViews();
        act.pwBar.setBackgroundColor(t.incognito ? Ui.INC_SURFACE : (Ui.dark ? Ui.SURFACE : Color.WHITE));
        act.pwBar.setPaddingRelative(act.dp(12), 0, act.dp(4), 0);
        act.pwBar.addView(Ui.icon(act, R.drawable.ic_key, Ui.ACCENT), new LinearLayout.LayoutParams(act.dp(22), act.dp(22)));
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(act);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPaddingRelative(act.dp(8), 0, 0, 0);
        hs.addView(chips);
        for (Passwords.Cred c : cs) {
            TextView ch = Ui.medium(Ui.single(act, c.user.isEmpty() ? L.t("Без имени · ••••") : c.user, 14, Ui.ON_TONAL));
            ch.setPaddingRelative(act.dp(14), act.dp(8), act.dp(14), act.dp(8));
            ch.setBackground(Ui.round(Ui.TONAL, 16));
            ch.setOnClickListener(v -> fillCred(t, c));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
            cl.setMarginEnd(act.dp(8));
            chips.addView(ch, cl);
        }
        act.pwBar.addView(hs, new LinearLayout.LayoutParams(0, act.MATCH, 1));
        ImageView mg = Ui.iconBtn(act, R.drawable.ic_settings, Ui.TEXT2);
        mg.setOnClickListener(v -> showPasswords());
        act.pwBar.addView(mg, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
        ImageView cl = Ui.iconBtn(act, R.drawable.ic_close, Ui.TEXT2);
        cl.setOnClickListener(v -> hidePwBar());
        act.pwBar.addView(cl, new LinearLayout.LayoutParams(act.dp(40), act.dp(44)));
        act.pwBar.setVisibility(View.VISIBLE);
        act.pwBar.bringToFront();
    }

    void hidePwBar() { if (act.pwBar != null) act.pwBar.setVisibility(View.GONE); }

    void fillCred(Tab t, Passwords.Cred c) {
        if (act.security(t) != 1) { hidePwBar(); act.toast(L.t("Пароли доступны только при защищённом подключении")); return; }
        String site = Passwords.site(t.web.getUrl());
        if (site == null || !site.equals(c.site)) { hidePwBar(); return; }
        String p = act.passwords.pass(c);
        if (p == null) { act.toast(L.t("Не удалось расшифровать пароль")); return; }
        t.web.evaluateJavascript(FILL_JS + "(" + JSONObject.quote(originOf(t.web.getUrl())) + "," + JSONObject.quote(c.user) + "," + JSONObject.quote(p) + ",0)", null);
        hidePwBar();
    }

    void autoFill(Tab t) {
        if (act.security(t) != 1) return;
        if (!act.store.bool("pwFill", true) || !act.store.bool("pwAuto", false)) return;
        String site = Passwords.site(t.web.getUrl());
        ArrayList<Passwords.Cred> cs = act.passwords.forSite(site);
        if (cs.size() != 1) return;
        String p = act.passwords.pass(cs.get(0));
        if (p == null) return;
        t.web.evaluateJavascript(FILL_JS + "(" + JSONObject.quote(originOf(t.web.getUrl())) + "," + JSONObject.quote(cs.get(0).user) + "," + JSONObject.quote(p) + ",1)", null);
    }

    /** scheme://host[:port] exactly as window.location.origin reports it. */
    static String originOf(String url) {
        try {
            Uri u = Uri.parse(url);
            if (u.getScheme() == null || u.getHost() == null) return "null";
            return u.getScheme().toLowerCase(Locale.ROOT) + "://" + u.getHost().toLowerCase(Locale.ROOT) + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) { return "null"; }
    }

    static String siteUrl(String site) { return site.startsWith("http://") ? site : "https://" + site; }

    void withAuth(Runnable r) {
        android.app.KeyguardManager km = (android.app.KeyguardManager) act.getSystemService(act.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure() || System.currentTimeMillis() < act.authUntil) { r.run(); return; }
        @SuppressWarnings("deprecation")
        Intent i = km.createConfirmDeviceCredentialIntent("Lasur", L.t("Подтвердите, что это вы, чтобы просмотреть пароль"));
        if (i == null) { r.run(); return; }
        act.pendingAuth = r;
        act.startActivityForResult(i, act.REQ_AUTH);
    }

    void copySecret(String s) {
        ClipData cd = ClipData.newPlainText("password", s);
        if (Build.VERSION.SDK_INT >= 24) {
            android.os.PersistableBundle e = new android.os.PersistableBundle();
            e.putBoolean("android.content.extra.IS_SENSITIVE", true);
            cd.getDescription().setExtras(e);
        }
        ((ClipboardManager) act.getSystemService(act.CLIPBOARD_SERVICE)).setPrimaryClip(cd);
        act.toast(L.t("Пароль скопирован"));
    }

    void showPasswords() {
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, 0, 0, act.dp(24));
        sv.addView(box);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            act.settingsUi.switchRow(box, L.t("Предлагать сохранять пароли"), L.t("После входа на сайт"), act.store.bool("pwSave", true), v -> act.store.setBool("pwSave", v));
            act.settingsUi.switchRow(box, L.t("Автозаполнение"), L.t("Показывать сохранённые аккаунты над клавиатурой"), act.store.bool("pwFill", true), v -> act.store.setBool("pwFill", v));
            act.settingsUi.switchRow(box, L.t("Заполнять при открытии"), L.t("Если для сайта сохранён один аккаунт"), act.store.bool("pwAuto", false), v -> act.store.setBool("pwAuto", v));
            java.util.Set<String> nv = act.passwords.never();
            if (!nv.isEmpty()) act.settingsUi.actionRow(box, L.t("Сайты-исключения: ") + nv.size(), L.t("Нажмите, чтобы снова предлагать сохранение везде"), () -> {
                act.passwords.clearNever(); act.toast(L.t("Исключения очищены")); fill[0].run();
            });
            act.settingsUi.section(box, act.passwords.list.isEmpty() ? L.t("Сохранённые пароли") : L.t("Сохранённые пароли · ") + act.passwords.list.size());
            if (act.passwords.list.isEmpty()) {
                TextView e = Ui.text(act, L.t("Пока пусто. Войдите на любой сайт — Lasur предложит сохранить пароль. Пароли шифруются ключом в защищённом хранилище Android."), 14, Ui.TEXT2);
                e.setPaddingRelative(act.dp(20), act.dp(8), act.dp(20), 0);
                box.addView(e);
            }
            for (Passwords.Cred c : new ArrayList<>(act.passwords.list)) {
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(10), act.dp(8), act.dp(10));
                r.setBackground(Ui.ripple(act, false));
                r.addView(act.home.tileIcon(c.site, siteUrl(c.site), false, 0, 36), new LinearLayout.LayoutParams(act.dp(36), act.dp(36)));
                LinearLayout tx = new LinearLayout(act);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPaddingRelative(act.dp(16), 0, act.dp(8), 0);
                tx.addView(Ui.single(act, Passwords.label(c.site), 15, Ui.TEXT));
                tx.addView(Ui.single(act, c.user.isEmpty() ? L.t("без имени пользователя") : c.user, 13, Ui.TEXT2));
                r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                r.setOnClickListener(v -> {
                    ArrayList<Object[]> m = new ArrayList<>();
                    m.add(new Object[]{R.drawable.ic_eye, L.t("Показать пароль"), (Runnable) () -> withAuth(() -> {
                        String p = act.passwords.pass(c);
                        act.dialog().setTitle(Passwords.label(c.site)).setMessage((c.user.isEmpty() ? "" : c.user + "\n\n") + (p == null ? L.t("Не удалось расшифровать") : p))
                                .setPositiveButton(L.t("Готово"), null).setNeutralButton(L.t("Копировать"), (d, w) -> { if (p != null) copySecret(p); }).show();
                    })});
                    if (!c.user.isEmpty()) m.add(new Object[]{R.drawable.ic_copy, L.t("Копировать имя пользователя"), (Runnable) () -> act.menu.copy(c.user)});
                    m.add(new Object[]{R.drawable.ic_key, L.t("Копировать пароль"), (Runnable) () -> withAuth(() -> { String p = act.passwords.pass(c); if (p != null) copySecret(p); })});
                    m.add(new Object[]{R.drawable.ic_globe, L.t("Открыть сайт"), (Runnable) () -> act.newTab(siteUrl(c.site), false, true, null)});
                    m.add(new Object[]{R.drawable.ic_close, L.t("Удалить"), (Runnable) () -> {
                        act.passwords.remove(c);
                        fill[0].run();
                        act.snack(L.t("Пароль удалён"), L.t("Отменить"), () -> { act.passwords.list.add(0, c); act.passwords.save(); fill[0].run(); });
                    }});
                    act.sheetMenu(Passwords.label(c.site) + (c.user.isEmpty() ? "" : " · " + c.user), m);
                });
                box.addView(r, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            }
        };
        fill[0].run();
        act.lists.fullDialog(L.t("Пароли"), sv, null, null);
    }
}
