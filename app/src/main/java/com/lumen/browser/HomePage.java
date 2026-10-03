package com.lumen.browser;
import android.widget.HorizontalScrollView;
import java.net.URL;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

/** The new tab (home) page. */
final class HomePage {
    final MainActivity act;

    HomePage(MainActivity act) { this.act = act; }

    boolean ntpEdit = false;
    static final int MAX_SHORTCUTS = 20;

    static final int[] TILE_COLORS = {0, 0xFF1A73E8, 0xFFD93025, 0xFF188038, 0xFFF9AB00, 0xFF9334E6, 0xFF007B83, 0xFFE8710A, 0xFF5F6368};

    /** Round shortcut icon: site favicon on a soft circle, or a colored letter. */
    FrameLayout tileIcon(String title, String url, boolean letter, int color, int size) { return tileIcon(title, url, letter, color, size, true); }

    /** @param net false for incognito tabs: never ask icon services about the sites they visit. */
    FrameLayout tileIcon(String title, String url, boolean letter, int color, int size, boolean net) {
        FrameLayout f = new FrameLayout(act);
        int bg = color != 0 ? color : (Ui.CHIP);
        f.setBackground(Ui.oval(bg));
        TextView lt = new TextView(act);
        String l = title == null || title.trim().isEmpty() ? "?" : title.trim().substring(0, 1).toUpperCase();
        lt.setText(l);
        lt.setGravity(Gravity.CENTER);
        lt.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.40f);
        lt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        lt.setTextColor(color != 0 ? Color.WHITE : Ui.letterColor(title == null ? "" : title));
        f.addView(lt, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        if (!letter && url != null) {
            ImageView iv = new ImageView(act);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int is = act.dp(size * 0.52f);
            f.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
            Bitmap b = IconCache.get(act, url, bm -> { iv.setImageBitmap(bm); lt.setVisibility(View.GONE); }, net);
            if (b != null) { iv.setImageBitmap(b); lt.setVisibility(View.GONE); }
        }
        return f;
    }

    String ntpCacheKey;
    TextView ntpBlockedText;

    String ntpKey(boolean inc) {
        StringBuilder k = new StringBuilder();
        int wp = act.store.p.getInt("wp", Wallpaper.NONE);
        k.append(inc).append('|').append(ntpEdit).append('|').append(act.scrWpx()).append('x').append(act.scrHpx()).append('|').append(wp)
                .append(':').append(wp == Wallpaper.CUSTOM ? Wallpaper.customFile(act).lastModified() : 0)
                .append('|').append(Ui.mode).append(Ui.accent).append(Ui.dark).append('|').append(AdBlocker.enabled).append('|').append(L.t("Закладки"));
        for (Store.Item it : act.store.shortcuts) k.append('|').append(it.t).append(' ').append(it.u).append(' ').append(it.c).append(it.letter);
        int n = 0;
        for (Store.Item it : act.store.history) { if (n++ >= 4) break; k.append('#').append(it.t).append(' ').append(it.u); }
        return k.toString();
    }

    View buildNtp(boolean inc) {
        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        sv.setBackgroundColor(inc ? Ui.INC_BG : Ui.BG);
        int scrW = act.scrWpx(), scrH = act.scrHpx();
        Bitmap wp = inc ? null : Wallpaper.get(act, act.store.p.getInt("wp", Wallpaper.NONE), scrW / 2, scrH / 2);
        act.ntpOnWall = wp != null;
        if (act.ntpOnWall) sv.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Math.max(act.dp(16), (scrW - act.dp(680)) / 2);
        col.setPaddingRelative(side, act.dp(40), side, act.dp(32));
        sv.addView(col, new FrameLayout.LayoutParams(act.MATCH, act.WRAP));
        if (inc) {
            FrameLayout ring = new FrameLayout(act);
            ring.setBackground(Ui.oval(0xFF3C4043));
            ImageView ic = Ui.icon(act, R.drawable.ic_incognito, Ui.INC_TEXT);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ring.addView(ic, new FrameLayout.LayoutParams(act.dp(48), act.dp(48), Gravity.CENTER));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(act.dp(96), act.dp(96));
            rl.topMargin = act.dp(24);
            col.addView(ring, rl);
            TextView h = Ui.medium(Ui.text(act, L.t("Вы в режиме инкогнито"), 24, Ui.INC_TEXT));
            h.setGravity(Gravity.CENTER);
            h.setPaddingRelative(0, act.dp(24), 0, act.dp(16));
            col.addView(h);
            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPaddingRelative(act.dp(20), act.dp(16), act.dp(20), act.dp(16));
            card.setBackground(Ui.round(0xFF2D2E31, 16));
            String[][] pts = {{L.t("Не сохраняется"), act.incProfile() ? L.t("история, cookies, данные сайтов и форм — всё удаляется, когда закрыта последняя вкладка инкогнито")
                    : L.t("история просмотров, кэш, данные форм")},
                    {L.t("Сохраняется"), L.t("скачанные файлы и закладки")},
                    {L.t("Закрытие"), L.t("все вкладки инкогнито закрываются кнопкой в переключателе вкладок")}};
            for (String[] p : pts) {
                TextView a = Ui.medium(Ui.text(act, p[0], 14, Ui.INC_TEXT));
                TextView b = Ui.text(act, p[1], 14, Ui.INC_TEXT2);
                b.setPaddingRelative(0, act.dp(2), 0, act.dp(12));
                card.addView(a); card.addView(b);
            }
            col.addView(card, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            if (act.current != null && !((LWebView) act.current.web).privateProfile) {
                TextView w = Ui.text(act, L.t("Инкогнито недоступно: обновите Android System WebView. Загрузка сайтов отключена, чтобы защитить данные обычных вкладок."), 13, 0xFFFDD663);
                w.setPaddingRelative(act.dp(4), act.dp(16), act.dp(4), 0);
                col.addView(w, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            }
            return sv;
        }

        col.addView(new View(act), new LinearLayout.LayoutParams(1, act.dp(28)));

        // search box
        LinearLayout pill = new LinearLayout(act);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPaddingRelative(act.dp(18), 0, act.dp(10), 0);
        pill.setBackground(Ui.round(Ui.NTP_PILL, 28));
        pill.setElevation(act.dp(Ui.dark ? 0 : 3));
        pill.addView(Ui.icon(act, R.drawable.ic_search, Ui.TEXT2), new LinearLayout.LayoutParams(act.dp(24), act.dp(24)));
        TextView hint = Ui.single(act, L.t("Введите запрос или URL"), 16, Ui.TEXT2);
        hint.setPaddingRelative(act.dp(14), 0, 0, 0);
        pill.addView(hint, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        ImageView mic = Ui.iconBtn(act, R.drawable.ic_mic, Ui.TEXT2);
        mic.setOnClickListener(v -> act.voiceSearch());
        pill.addView(mic, new LinearLayout.LayoutParams(act.dp(40), act.dp(40)));
        ImageView paste = Ui.iconBtn(act, R.drawable.ic_copy, Ui.TEXT2);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) act.getSystemService(act.CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(act);
                if (s != null && s.toString().trim().length() > 0) { act.navigate(s.toString()); return; }
            }
            act.toast(L.t("Буфер обмена пуст"));
        });
        pill.addView(paste, new LinearLayout.LayoutParams(act.dp(40), act.dp(40)));
        pill.setOnClickListener(v -> act.showKb(act.omni));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(act.MATCH, act.dp(56));
        pl.setMargins(act.dp(8), act.dp(28), act.dp(8), act.dp(20));
        col.addView(pill, pl);

        // shortcuts card
        LinearLayout grid = new LinearLayout(act);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPaddingRelative(act.dp(4), act.dp(8), act.dp(4), act.dp(8));
        if (ntpEdit) grid.setBackground(Ui.stroke(act.settingsUi.ntpCard(), Ui.ACCENT, 1.5f, 20));
        if (ntpEdit) {
            LinearLayout hdr = new LinearLayout(act);
            hdr.setGravity(Gravity.CENTER_VERTICAL);
            hdr.setPaddingRelative(act.dp(14), 0, act.dp(4), act.dp(4));
            TextView ht = Ui.text(act, L.t("Нажмите, чтобы изменить. Удерживайте и перетащите, чтобы переместить."), 12, Ui.TEXT2);
            hdr.addView(ht, new LinearLayout.LayoutParams(0, act.WRAP, 1));
            TextView done = Ui.medium(Ui.text(act, L.t("Готово"), 14, Ui.ACCENT));
            done.setPaddingRelative(act.dp(12), act.dp(10), act.dp(12), act.dp(10));
            done.setBackground(Ui.ripple(act, true));
            done.setOnClickListener(v -> { ntpEdit = false; act.refreshChrome(); });
            hdr.addView(done);
            grid.addView(hdr);
        }
        ArrayList<Store.Item> sc = act.store.shortcuts;
        boolean showAdd = sc.size() < MAX_SHORTCUTS;
        int total = sc.size() + (showAdd ? 1 : 0);
        LinearLayout row = null;
        for (int i = 0; i < total; i++) {
            if (i % 4 == 0) { row = new LinearLayout(act); grid.addView(row, new LinearLayout.LayoutParams(act.MATCH, act.WRAP)); }
            row.addView(i < sc.size() ? shortcutTile(sc.get(i), i) : addTile(), new LinearLayout.LayoutParams(0, act.WRAP, 1));
        }
        if (row != null && total % 4 != 0) for (int k = total % 4; k < 4; k++) row.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1));
        col.addView(grid, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));

        // ad-block stats
        LinearLayout chip = new LinearLayout(act);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPaddingRelative(act.dp(14), act.dp(8), act.dp(16), act.dp(8));
        chip.setBackground(Ui.round(Ui.dark ? 0xFF1E3A2B : 0xFFE6F4EA, 18));
        chip.addView(Ui.icon(act, R.drawable.ic_shield, Ui.dark ? 0xFF81C995 : 0xFF188038), new LinearLayout.LayoutParams(act.dp(18), act.dp(18)));
        TextView ct = ntpBlockedText = Ui.text(act, AdBlocker.enabled ? L.t("Заблокировано рекламы и трекеров: ") + AdBlocker.totalBlocked.get() : L.t("Блокировка рекламы выключена"), 13,
                Ui.dark ? 0xFF81C995 : 0xFF137333);
        ct.setPaddingRelative(act.dp(8), 0, 0, 0);
        chip.addView(ct);
        chip.setOnClickListener(v -> act.settingsUi.showAdblock());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
        cl.topMargin = act.dp(20);
        col.addView(chip, cl);

        // recent pages
        ArrayList<Store.Item> recent = new ArrayList<>();
        for (Store.Item it : act.store.history) { if (recent.size() >= 4) break; recent.add(it); }
        if (!recent.isEmpty() && !ntpEdit) {
            LinearLayout rc = new LinearLayout(act);
            rc.setOrientation(LinearLayout.VERTICAL);
            rc.setPaddingRelative(0, act.dp(6), 0, act.dp(6));
            rc.setBackground(Ui.round(act.settingsUi.ntpCard(), 20));
            LinearLayout rh = new LinearLayout(act);
            rh.setGravity(Gravity.CENTER_VERTICAL);
            rh.setPaddingRelative(act.dp(18), act.dp(6), act.dp(4), 0);
            rh.addView(Ui.medium(Ui.text(act, L.t("Недавние"), 15, Ui.TEXT)), new LinearLayout.LayoutParams(0, act.WRAP, 1));
            TextView all = Ui.medium(Ui.text(act, L.t("История"), 13, Ui.ACCENT));
            all.setPaddingRelative(act.dp(12), act.dp(8), act.dp(12), act.dp(8));
            all.setBackground(Ui.ripple(act, true));
            all.setOnClickListener(v -> act.lists.showHistory());
            rh.addView(all);
            rc.addView(rh);
            for (Store.Item it : recent) {
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(8), act.dp(16), act.dp(8));
                r.setBackground(Ui.ripple(act, false));
                r.addView(tileIcon(it.t, it.u, false, 0, 36), new LinearLayout.LayoutParams(act.dp(36), act.dp(36)));
                LinearLayout tx = new LinearLayout(act);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPaddingRelative(act.dp(14), 0, 0, 0);
                tx.addView(Ui.single(act, it.t, 14, Ui.TEXT));
                tx.addView(Ui.single(act, act.displayUrl(it.u), 12, Ui.TEXT2));
                r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                r.setOnClickListener(v -> act.navigate(it.u));
                r.setOnLongClickListener(v -> { act.linkMenu(it.u, it.t, null); return true; });
                rc.addView(r);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(act.MATCH, act.WRAP);
            rl.topMargin = act.dp(20);
            col.addView(rc, rl);
        }
        return act.settingsUi.wrapWall(sv, wp);
    }

    View addTile() {
        LinearLayout tile = new LinearLayout(act);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPaddingRelative(act.dp(2), act.dp(10), act.dp(2), act.dp(10));
        tile.setBackground(Ui.ripple(act, false));
        FrameLayout f = new FrameLayout(act);
        f.setBackground(act.ntpOnWall ? Ui.stroke(0x33FFFFFF, 0xCCFFFFFF, 1.5f, 28) : Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1.5f, 28));
        f.addView(Ui.icon(act, R.drawable.ic_add, Ui.ACCENT), new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        tile.addView(f, new LinearLayout.LayoutParams(act.dp(56), act.dp(56)));
        TextView lbl = Ui.single(act, L.t("Добавить"), 12, act.ntpOnWall ? Color.WHITE : Ui.TEXT2);
        if (act.ntpOnWall) lbl.setShadowLayer(act.dp(4), 0, act.dp(1), 0x99000000);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPaddingRelative(0, act.dp(8), 0, 0);
        tile.addView(lbl, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        tile.setOnClickListener(v -> editShortcut(-1, true));
        return tile;
    }

    View shortcutTile(Store.Item it, int idx) {
        LinearLayout tile = new LinearLayout(act);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPaddingRelative(act.dp(2), act.dp(10), act.dp(2), act.dp(10));
        tile.setBackground(Ui.ripple(act, false));
        FrameLayout wrap = new FrameLayout(act);
        FrameLayout icon = tileIcon(it.t, it.u, it.letter, it.c, 56);
        wrap.addView(icon, new FrameLayout.LayoutParams(act.dp(56), act.dp(56), Gravity.CENTER));
        if (ntpEdit) {
            FrameLayout x = new FrameLayout(act);
            x.setBackground(Ui.oval(Ui.dark ? 0xFF9AA0A6 : 0xFF5F6368));
            x.setElevation(act.dp(2));
            x.addView(Ui.icon(act, R.drawable.ic_close, Ui.dark ? 0xFF202124 : Color.WHITE), new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            ImageView xi = (ImageView) x.getChildAt(0);
            xi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            xi.setPaddingRelative(act.dp(3), act.dp(3), act.dp(3), act.dp(3));
            x.setOnClickListener(v -> deleteShortcut(idx));
            wrap.addView(x, new FrameLayout.LayoutParams(act.dp(22), act.dp(22), Gravity.TOP | Gravity.END));
            icon.animate().scaleX(0.9f).scaleY(0.9f).setDuration(150).start();
        }
        tile.addView(wrap, new LinearLayout.LayoutParams(act.dp(64), act.dp(60)));
        TextView lbl = Ui.single(act, it.t, 12, act.ntpOnWall && !ntpEdit ? Color.WHITE : Ui.TEXT);
        if (act.ntpOnWall && !ntpEdit) lbl.setShadowLayer(act.dp(4), 0, act.dp(1), 0x99000000);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPaddingRelative(act.dp(2), act.dp(6), act.dp(2), 0);
        tile.addView(lbl, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        tile.setOnClickListener(v -> { if (ntpEdit) editShortcut(idx, false); else act.navigate(it.u); });
        tile.setOnLongClickListener(v -> {
            if (ntpEdit) {
                tile.startDragAndDrop(ClipData.newPlainText("shortcut", it.u), new View.DragShadowBuilder(wrap), idx, 0);
                tile.setAlpha(0.3f);
            } else shortcutMenu(it, idx);
            return true;
        });
        tile.setOnDragListener((v, ev) -> {
            switch (ev.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return ev.getLocalState() instanceof Integer;
                case DragEvent.ACTION_DRAG_ENTERED: v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start(); return true;
                case DragEvent.ACTION_DRAG_EXITED: v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); return true;
                case DragEvent.ACTION_DROP:
                    int from = (Integer) ev.getLocalState();
                    if (from != idx && from >= 0 && from < act.store.shortcuts.size()) {
                        Store.Item m = act.store.shortcuts.remove(from);
                        act.store.shortcuts.add(Math.min(idx, act.store.shortcuts.size()), m);
                        act.store.saveShortcuts();
                    }
                    act.ui.post(act::refreshChrome);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f); v.setScaleX(1f); v.setScaleY(1f);
                    return true;
            }
            return false;
        });
        return tile;
    }

    void shortcutMenu(Store.Item it, int idx) {
        act.dialog().setTitle(it.t)
                .setItems(new String[]{L.t("Открыть в новой вкладке"), L.t("Открыть в режиме инкогнито"), L.t("Изменить ярлык"), L.t("Удалить"), L.t("Упорядочить ярлыки")}, (d, w) -> {
                    if (w == 0) act.newTab(it.u, false, true, null);
                    else if (w == 1) act.newTab(it.u, true, true, null);
                    else if (w == 2) editShortcut(idx, false);
                    else if (w == 3) deleteShortcut(idx);
                    else { ntpEdit = true; act.refreshChrome(); }
                }).show();
    }

    void deleteShortcut(int idx) {
        if (idx < 0 || idx >= act.store.shortcuts.size()) return;
        Store.Item removed = act.store.shortcuts.remove(idx);
        act.store.saveShortcuts();
        act.refreshChrome();
        Toast.makeText(act, L.t("Ярлык «") + removed.t + L.t("» удалён"), Toast.LENGTH_SHORT).show();
    }

    void editShortcut(int idx) { editShortcut(idx, true); }

    void editShortcut(int idx, boolean prefillCurrent) {
        final Store.Item src = idx >= 0 && idx < act.store.shortcuts.size() ? act.store.shortcuts.get(idx) : null;
        final boolean[] letter = {src != null && src.letter};
        final int[] color = {src != null ? src.c : 0};
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(act.dp(24), act.dp(12), act.dp(24), 0);
        sv.addView(box);
        FrameLayout preview = new FrameLayout(act);
        LinearLayout.LayoutParams pvl = new LinearLayout.LayoutParams(act.dp(72), act.dp(72));
        pvl.gravity = Gravity.CENTER_HORIZONTAL;
        pvl.bottomMargin = act.dp(8);
        box.addView(preview, pvl);
        EditText name = new EditText(act);
        name.setHint(L.t("Название"));
        name.setSingleLine(true);
        EditText url = new EditText(act);
        url.setHint(L.t("Адрес сайта"));
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (src != null) { name.setText(src.t); url.setText(src.u); }
        else if (prefillCurrent && act.current != null && !act.current.ntp && act.current.web.getUrl() != null) { name.setText(act.current.title); url.setText(act.current.web.getUrl()); }
        box.addView(name);
        box.addView(url);

        TextView il = Ui.medium(Ui.text(act, L.t("Значок"), 13, Ui.ACCENT));
        il.setPaddingRelative(0, act.dp(16), 0, act.dp(8));
        box.addView(il);
        LinearLayout modes = new LinearLayout(act);
        TextView mSite = chip(L.t("Значок сайта")), mLetter = chip(L.t("Буква"));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
        ml.setMarginEnd(act.dp(8));
        modes.addView(mSite, ml);
        modes.addView(mLetter, ml);
        box.addView(modes);

        TextView cl = Ui.medium(Ui.text(act, L.t("Цвет фона"), 13, Ui.ACCENT));
        cl.setPaddingRelative(0, act.dp(16), 0, act.dp(8));
        box.addView(cl);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout colors = new LinearLayout(act);
        hs.addView(colors);
        box.addView(hs);
        TextView refresh = Ui.medium(Ui.text(act, L.t("Обновить значок сайта"), 14, Ui.ACCENT));
        refresh.setPaddingRelative(0, act.dp(16), 0, act.dp(8));
        box.addView(refresh);

        final Runnable[] update = new Runnable[1];
        update[0] = () -> {
            preview.removeAllViews();
            String u = act.toUrl(url.getText().toString());
            preview.addView(tileIcon(name.getText().toString().isEmpty() ? act.displayUrl(u) : name.getText().toString(), u, letter[0], color[0], 72),
                    new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            styleChip(mSite, !letter[0]);
            styleChip(mLetter, letter[0]);
            colors.removeAllViews();
            for (int c : TILE_COLORS) {
                FrameLayout sw = new FrameLayout(act);
                int fill = c != 0 ? c : (Ui.CHIP);
                sw.setBackground(c == color[0] ? Ui.stroke(fill, Ui.TEXT, 3, 20) : Ui.stroke(fill, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 20));
                if (c == 0) {
                    TextView a = Ui.text(act, "A", 13, Ui.TEXT2);
                    a.setGravity(Gravity.CENTER);
                    sw.addView(a, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
                }
                sw.setOnClickListener(v -> { color[0] = c; update[0].run(); });
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(act.dp(48), act.dp(48));
                sl.setMarginEnd(act.dp(10));
                colors.addView(sw, sl);
            }
        };
        mSite.setOnClickListener(v -> { letter[0] = false; update[0].run(); });
        mLetter.setOnClickListener(v -> { letter[0] = true; update[0].run(); });
        refresh.setOnClickListener(v -> {
            String u = act.toUrl(url.getText().toString());
            if (u != null) { IconCache.forget(act, u); letter[0] = false; update[0].run(); }
        });
        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { update[0].run(); }
        };
        update[0].run();
        name.addTextChangedListener(tw);
        url.addTextChangedListener(tw);

        AlertDialog.Builder b = act.dialog().setTitle(src != null ? L.t("Изменить ярлык") : L.t("Новый ярлык")).setView(sv)
                .setPositiveButton(L.t("Сохранить"), (d, w) -> {
                    String u = act.toUrl(url.getText().toString());
                    if (u == null) { act.toast(L.t("Введите адрес сайта")); return; }
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = act.displayUrl(u);
                    Store.Item it = src != null ? src : new Store.Item(n, u, 0);
                    it.t = n; it.u = u; it.letter = letter[0]; it.c = color[0];
                    if (src == null) act.store.shortcuts.add(it);
                    act.store.saveShortcuts();
                    act.refreshChrome();
                })
                .setNegativeButton(L.t("Отмена"), null);
        if (src != null) b.setNeutralButton(L.t("Удалить"), (d, w) -> deleteShortcut(idx));
        b.show();
    }

    TextView chip(String s) {
        TextView t = Ui.medium(Ui.text(act, s, 14, Ui.TEXT));
        t.setPaddingRelative(act.dp(16), act.dp(8), act.dp(16), act.dp(8));
        return t;
    }

    void styleChip(TextView t, boolean sel) {
        if (sel) { t.setBackground(Ui.round(Ui.TONAL, 18)); t.setTextColor(Ui.ON_TONAL); }
        else { t.setBackground(Ui.stroke(Color.TRANSPARENT, Ui.dark ? 0xFF5F6368 : 0xFFDADCE0, 1, 18)); t.setTextColor(Ui.TEXT); }
    }
}
