package com.lumen.browser;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** The tab switcher grid. */
final class TabSwitcher {
    final MainActivity act;

    TabSwitcher(MainActivity act) { this.act = act; }

    void showSwitcher() {
        if (act.current != null) { act.captureThumb(act.current); act.switcherIncognito = act.current.incognito; }
        act.unfocusOmni();
        act.passwordsUi.hidePwBar();
        act.switcherAnim = true;
        buildSwitcher();
        act.switcher.setVisibility(View.VISIBLE);
        act.switcher.setAlpha(0f);
        act.switcher.setScaleX(0.97f);
        act.switcher.setScaleY(0.97f);
        act.switcher.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();
        act.videoFab.setVisibility(View.GONE);
        act.incLock.check();
        act.pip.updatePipParams();
    }

    void hideSwitcher() {
        act.switcher.setVisibility(View.GONE);
        act.switcher.removeAllViews();
        act.refreshChrome();
    }

    void buildSwitcher() {
        act.switcher.removeAllViews();
        final boolean inc = act.switcherIncognito;
        int bg = inc ? Ui.INC_BG : Ui.SWITCHER_BG, fg = inc ? Ui.INC_TEXT : Ui.TEXT, fg2 = inc ? Ui.INC_TEXT2 : Ui.TEXT2;
        act.switcher.setBackgroundColor(bg);
        act.setSecure(inc || (act.current != null && act.current.incognito));
        act.setBarColors(bg, bg);
        act.setLightBars(!inc && !Ui.dark);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        act.switcher.addView(col, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));

        // top bar: back · segmented control · menu
        FrameLayout bar = new FrameLayout(act);
        ImageView back = Ui.iconBtn(act, R.drawable.ic_back, fg);
        back.setOnClickListener(v -> act.onBackPressed());
        bar.addView(back, new FrameLayout.LayoutParams(act.dp(48), act.dp(48), Gravity.START | Gravity.CENTER_VERTICAL));
        int nNorm = 0, nInc = 0;
        for (Tab t : act.tabs) if (t.incognito) nInc++; else nNorm++;
        LinearLayout seg = new LinearLayout(act);
        seg.setPaddingRelative(act.dp(3), act.dp(3), act.dp(3), act.dp(3));
        seg.setBackground(Ui.round(inc ? 0xFF303134 : (Ui.dark ? 0xFF303134 : 0xFFE1E5EA), 22));
        int selBg = inc ? 0xFF5F6368 : (Ui.dark ? Ui.TONAL : Color.WHITE);
        FrameLayout sN = new FrameLayout(act);
        TextView cnt = new TextView(act);
        cnt.setText(String.valueOf(nNorm));
        cnt.setGravity(Gravity.CENTER);
        cnt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        cnt.setTypeface(Typeface.DEFAULT_BOLD);
        int cc = !inc ? Ui.ACCENT : fg2;
        cnt.setTextColor(cc);
        cnt.setBackground(Ui.stroke(Color.TRANSPARENT, cc, 2, 4));
        sN.addView(cnt, new FrameLayout.LayoutParams(act.dp(20), act.dp(20), Gravity.CENTER));
        if (!inc) sN.setBackground(Ui.round(selBg, 19));
        sN.setOnClickListener(v -> { if (act.switcherIncognito) { act.switcherIncognito = false; act.switcherAnim = true; buildSwitcher(); } });
        FrameLayout sI = new FrameLayout(act);
        ImageView ii = Ui.icon(act, R.drawable.ic_incognito, inc ? Ui.INC_TEXT : fg2);
        sI.addView(ii, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        if (nInc > 0) {
            TextView ib = Ui.text(act, String.valueOf(nInc), 9, Color.WHITE);
            ib.setGravity(Gravity.CENTER);
            ib.setBackground(Ui.oval(0xFF5F6368));
            sI.addView(ib, new FrameLayout.LayoutParams(act.dp(14), act.dp(14), Gravity.TOP | Gravity.END));
        }
        if (inc) sI.setBackground(Ui.round(selBg, 19));
        sI.setOnClickListener(v -> { if (!act.switcherIncognito) { act.switcherIncognito = true; act.switcherAnim = true; buildSwitcher(); } });
        seg.addView(sN, new LinearLayout.LayoutParams(act.dp(68), act.dp(38)));
        seg.addView(sI, new LinearLayout.LayoutParams(act.dp(68), act.dp(38)));
        bar.addView(seg, new FrameLayout.LayoutParams(act.WRAP, act.WRAP, Gravity.CENTER));
        ImageView more = Ui.iconBtn(act, R.drawable.ic_more, fg);
        more.setOnClickListener(v -> {
            ArrayList<Tab> mine = new ArrayList<>();
            for (Tab t : act.tabs) if (t.incognito == inc) mine.add(t);
            ArrayList<Object[]> mm = new ArrayList<>();
            mm.add(new Object[]{R.drawable.ic_add, L.t("Новая вкладка"), (Runnable) () -> { act.switcher.setVisibility(View.GONE); act.switcher.removeAllViews(); act.newTab(null, false, true, null); }});
            mm.add(new Object[]{R.drawable.ic_incognito, L.t("Новая вкладка инкогнито"), (Runnable) () -> { act.switcher.setVisibility(View.GONE); act.switcher.removeAllViews(); act.newTab(null, true, true, null); }});
            if (!act.closedStack.isEmpty()) mm.add(new Object[]{R.drawable.ic_history, L.t("Вернуть закрытые вкладки"), (Runnable) () -> act.reopen(act.closedStack.get(act.closedStack.size() - 1))});
            if (!mine.isEmpty()) mm.add(new Object[]{R.drawable.ic_close, inc ? L.t("Закрыть все вкладки инкогнито") : L.t("Закрыть все вкладки"), (Runnable) () -> act.closeTabs(mine)});
            act.sheetMenu(null, mm);
        });
        bar.addView(more, new FrameLayout.LayoutParams(act.dp(48), act.dp(48), Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(bar, new LinearLayout.LayoutParams(act.MATCH, act.dp(60)));

        act.groups.prune();
        ArrayList<TabGroups.G> gs = act.groups.used(inc);
        if (act.switcherGroup != null && (act.groups.get(act.switcherGroup) == null || act.groups.count(act.switcherGroup, inc) == 0)) act.switcherGroup = null;
        final String grp = act.switcherGroup;
        if (!gs.isEmpty()) col.addView(groupChips(gs, inc, fg, fg2), new LinearLayout.LayoutParams(act.MATCH, act.dp(48)));
        ArrayList<Tab> list = new ArrayList<>();
        for (Tab t : act.tabs) if (t.incognito == inc && (grp == null || grp.equals(t.group))) list.add(t);
        FrameLayout body = new FrameLayout(act);
        col.addView(body, new LinearLayout.LayoutParams(act.MATCH, 0, 1));
        if (list.isEmpty()) {
            LinearLayout e = new LinearLayout(act);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER);
            ImageView ei = Ui.icon(act, inc ? R.drawable.ic_incognito : R.drawable.ic_globe, fg2);
            ei.setScaleType(ImageView.ScaleType.FIT_CENTER);
            e.addView(ei, new LinearLayout.LayoutParams(act.dp(56), act.dp(56)));
            TextView et = Ui.text(act, inc ? L.t("Нет вкладок инкогнито") : L.t("Нет открытых вкладок"), 16, fg2);
            et.setPaddingRelative(act.dp(24), act.dp(16), act.dp(24), 0);
            et.setGravity(Gravity.CENTER);
            et.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            e.addView(et, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            body.addView(e, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        } else {
            ScrollView sv = new ScrollView(act);
            sv.setClipToPadding(false);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout grid = new LinearLayout(act);
            grid.setOrientation(LinearLayout.VERTICAL);
            int sw = act.scrWpx();
            int cols = Math.max(2, Math.min(4, (int) (sw / Ui.density / 320)));
            int side = act.dp(10);
            grid.setPaddingRelative(side, act.dp(4), side, act.dp(110));
            sv.addView(grid);
            body.addView(sv, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            int cardW = (sw - side * 2) / cols - act.dp(12);
            float aspect = act.content.getWidth() > 0 && act.content.getHeight() > 0 ? act.content.getHeight() / (float) act.content.getWidth() : 1.5f;
            aspect = Math.max(0.78f, Math.min(1.4f, aspect));
            int cardH = act.dp(53) + (int) (cardW * aspect);
            boolean anim = act.switcherAnim;
            act.switcherAnim = false;
            LinearLayout row = null;
            int selPos = 0;
            // cards (with their thumbnails) are created lazily for the rows near the viewport
            final FrameLayout[] holders = new FrameLayout[list.size()];
            for (int i = 0; i < list.size(); i++) {
                if (i % cols == 0) { row = new LinearLayout(act); grid.addView(row, new LinearLayout.LayoutParams(act.MATCH, act.WRAP)); }
                if (list.get(i) == act.current) selPos = i / cols;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cardH, 1);
                lp.setMargins(act.dp(6), act.dp(6), act.dp(6), act.dp(6));
                holders[i] = new FrameLayout(act);
                row.addView(holders[i], lp);
            }
            final int rowH = cardH + act.dp(12), nCols = cols;
            final int viewRows = Math.max(3, act.scrHpx() / Math.max(1, rowH) + 2);
            final java.util.function.IntConsumer fillRows = firstRow -> {
                int lo = Math.max(0, (firstRow - 1) * nCols), hi = Math.min(list.size(), (firstRow + viewRows + 1) * nCols);
                for (int i = lo; i < hi; i++) {
                    if (holders[i].getChildCount() > 0) continue;
                    final View card = tabCard(list.get(i), inc, fg, fg2);
                    holders[i].addView(card, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
                    if (anim) {
                        card.setAlpha(0f);
                        card.setTranslationY(act.dp(18));
                        card.animate().alpha(1f).translationY(0).setStartDelay(Math.min(i - lo, 12) * 22L).setDuration(220)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .withEndAction(() -> card.animate().setStartDelay(0)).start();
                    }
                }
            };
            fillRows.accept(Math.max(0, selPos - 1));
            sv.setOnScrollChangeListener((v, x, y, ox, oy) -> fillRows.accept(y / Math.max(1, rowH)));
            if (list.size() % cols != 0) for (int k = list.size() % cols; k < cols; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cardH, 1);
                lp.setMargins(act.dp(6), act.dp(6), act.dp(6), act.dp(6));
                row.addView(new View(act), lp);
            }
            final int scrollTo = Math.max(0, selPos - 1) * (cardH + act.dp(12));
            sv.post(() -> sv.scrollTo(0, scrollTo));
        }

        // floating "new tab" button
        LinearLayout fab = new LinearLayout(act);
        fab.setGravity(Gravity.CENTER_VERTICAL);
        fab.setPaddingRelative(act.dp(18), 0, act.dp(22), 0);
        int fabBg = inc ? 0xFF5F6368 : Ui.TONAL, fabFg = inc ? Color.WHITE : Ui.ON_TONAL;
        fab.setBackground(Ui.round(fabBg, 18));
        fab.setElevation(act.dp(6));
        fab.addView(Ui.icon(act, R.drawable.ic_add, fabFg), new LinearLayout.LayoutParams(act.dp(24), act.dp(24)));
        TextView ft = Ui.medium(Ui.text(act, inc ? L.t("Инкогнито") : L.t("Новая вкладка"), 15, fabFg));
        ft.setPaddingRelative(act.dp(10), 0, 0, 0);
        fab.addView(ft);
        fab.setOnClickListener(v -> {
            act.switcher.setVisibility(View.GONE); act.switcher.removeAllViews();
            Tab n = act.createTab(inc, null);
            n.group = grp;
            act.selectTab(n);
        });
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(act.WRAP, act.dp(56), Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, act.dp(18), act.dp(22));
        act.switcher.addView(fab, fl);
        act.incLock.check();
    }

    /** "All" + one chip per group; tap filters, long press opens the group menu. */
    View groupChips(ArrayList<TabGroups.G> gs, boolean inc, int fg, int fg2) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(act.dp(12), 0, act.dp(12), 0);
        hs.addView(row, new FrameLayout.LayoutParams(act.WRAP, act.MATCH));
        int all = 0;
        for (Tab t : act.tabs) if (t.incognito == inc) all++;
        row.addView(chip(L.t("Все") + " · " + all, 0, act.switcherGroup == null, inc, fg, fg2, () -> { act.switcherGroup = null; buildSwitcher(); }, null));
        for (TabGroups.G g : gs)
            row.addView(chip(g.name + " · " + act.groups.count(g.id, inc), g.color, g.id.equals(act.switcherGroup), inc, fg, fg2,
                    () -> { act.switcherGroup = g.id; act.switcherAnim = true; buildSwitcher(); }, () -> act.groups.groupMenu(g, inc)));
        return hs;
    }

    View chip(String text, int dot, boolean sel, boolean inc, int fg, int fg2, Runnable tap, Runnable longTap) {
        LinearLayout c = new LinearLayout(act);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPaddingRelative(act.dp(dot != 0 ? 10 : 14), 0, act.dp(14), 0);
        int selBg = inc ? 0xFF5F6368 : Ui.TONAL;
        int border = inc ? 0xFF5F6368 : (Ui.dark ? 0xFF5F6368 : 0xFFDADCE0);
        c.setBackground(sel ? Ui.round(selBg, 16) : Ui.stroke(android.graphics.Color.TRANSPARENT, border, 1, 16));
        if (dot != 0) {
            View d = new View(act);
            d.setBackground(Ui.oval(dot));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(act.dp(10), act.dp(10));
            dl.setMarginEnd(act.dp(8));
            c.addView(d, dl);
        }
        c.addView(Ui.medium(Ui.single(act, text, 13, sel ? (inc ? android.graphics.Color.WHITE : Ui.ON_TONAL) : fg)));
        c.setOnClickListener(v -> tap.run());
        if (longTap != null) c.setOnLongClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); longTap.run(); return true; });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(act.WRAP, act.dp(34));
        lp.setMarginEnd(act.dp(8));
        c.setLayoutParams(lp);
        return c;
    }

    View tabCard(Tab t, boolean inc, int fg, int fg2) {
        boolean sel = t == act.current;
        int cardBg = inc ? Ui.INC_SURFACE : (Ui.dark ? (Ui.amoled ? 0xFF1C1C1E : Ui.MENU_SURFACE) : Color.WHITE);
        int ring = inc ? 0xFFBDC1C6 : Ui.ACCENT;
        int headBg = sel ? (inc ? 0xFF5F6368 : Ui.TONAL) : cardBg;
        int headFg = sel ? (inc ? Color.WHITE : Ui.ON_TONAL) : fg;
        int headFg2 = sel ? Ui.blend(headFg, headBg, 0.3f) : fg2;
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(sel ? Ui.stroke(headBg, ring, 3, 22) : Ui.round(cardBg, 22));
        int pd = act.dp(sel ? 3 : 0);
        card.setPaddingRelative(pd, pd, pd, pd);
        card.setClipToOutline(true);
        card.setElevation(act.dp(sel ? 6 : 2));

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPaddingRelative(act.dp(12), 0, act.dp(2), 0);
        String u = t.pendingUrl != null ? t.pendingUrl : (t.web.getUrl() != null ? t.web.getUrl() : t.url);
        String title = t.ntp ? (inc ? L.t("Инкогнито") : L.t("Новая вкладка")) : (t.title == null || t.title.isEmpty() ? act.displayUrl(u) : t.title);
        String sub = t.ntp ? (inc ? L.t("Новая вкладка") : L.t("Главная страница")) : (t.loading ? L.t("Загрузка…") : act.hostOf(u));
        View ic;
        if (t.ntp) {
            ImageView li = new ImageView(act);
            if (inc) { li = Ui.icon(act, R.drawable.ic_incognito, headFg); li.setScaleType(ImageView.ScaleType.FIT_CENTER); }
            else li.setImageResource(R.drawable.ic_logo_drop);
            ic = li;
        } else if (t.favicon != null) {
            FrameLayout fb = new FrameLayout(act);
            fb.setBackground(Ui.oval(Color.WHITE));
            ImageView fi = new ImageView(act);
            fi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            fi.setImageBitmap(t.favicon);
            fb.addView(fi, new FrameLayout.LayoutParams(act.dp(15), act.dp(15), Gravity.CENTER));
            ic = fb;
        } else ic = act.home.tileIcon(title, u, false, 0, 22, !t.incognito);
        head.addView(ic, new LinearLayout.LayoutParams(act.dp(22), act.dp(22)));
        LinearLayout tx = new LinearLayout(act);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPaddingRelative(act.dp(10), 0, act.dp(2), 0);
        tx.addView(Ui.medium(Ui.single(act, title, 13, headFg)));
        tx.addView(Ui.single(act, sub, 11, headFg2));
        head.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
        ImageView x = Ui.iconBtn(act, R.drawable.ic_close, headFg2);
        x.setScaleType(ImageView.ScaleType.FIT_CENTER);
        x.setPaddingRelative(act.dp(10), act.dp(10), act.dp(10), act.dp(10));
        x.setOnClickListener(v -> card.animate().setStartDelay(0).alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(140)
                .withEndAction(() -> act.closeTabs(act.one(t))).start());
        head.addView(x, new LinearLayout.LayoutParams(act.dp(40), act.dp(40)));
        TabGroups.G g = act.groups.get(t.group);
        if (g != null) {
            View stripe = new View(act);
            stripe.setBackground(Ui.round(g.color, 2));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(act.MATCH, act.dp(4));
            sl.setMargins(act.dp(14), act.dp(sel ? 3 : 6), act.dp(14), 0);
            card.addView(stripe, sl);
        }
        card.addView(head, new LinearLayout.LayoutParams(act.MATCH, act.dp(g != null ? 42 : 48)));

        FrameLayout thumbBox = new FrameLayout(act);
        thumbBox.setBackground(Ui.round(inc ? Ui.INC_BG : Ui.BG, 16));
        thumbBox.setClipToOutline(true);
        if (t.ntp) {
            Bitmap wp = null;
            if (!inc) {
                int scrW = act.scrWpx(), scrH = act.scrHpx();
                wp = Wallpaper.get(act, act.store.p.getInt("wp", Wallpaper.NONE), scrW / 2, scrH / 2);
            }
            if (wp != null) {
                ImageView wi = new ImageView(act);
                wi.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wi.setImageBitmap(wp);
                thumbBox.addView(wi, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
            }
            LinearLayout ph = new LinearLayout(act);
            ph.setOrientation(LinearLayout.VERTICAL);
            ph.setGravity(Gravity.CENTER);
            if (inc) {
                FrameLayout ring2 = new FrameLayout(act);
                ring2.setBackground(Ui.oval(0xFF3C4043));
                ImageView ii = Ui.icon(act, R.drawable.ic_incognito, Ui.INC_TEXT2);
                ii.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ring2.addView(ii, new FrameLayout.LayoutParams(act.dp(26), act.dp(26), Gravity.CENTER));
                ph.addView(ring2, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
            } else {
                ph.setGravity(Gravity.CENTER_HORIZONTAL);
                ph.setPaddingRelative(act.dp(16), act.dp(40), act.dp(16), 0);
                View bar = new View(act);
                bar.setBackground(Ui.round(Ui.dark ? 0xFF3C4043 : (wp != null ? Color.WHITE : 0xFFF1F3F4), 10));
                ph.addView(bar, new LinearLayout.LayoutParams(act.MATCH, act.dp(20)));
                for (int rr = 0; rr < 2; rr++) {
                    LinearLayout dots = new LinearLayout(act);
                    for (int k = 0; k < 4; k++) {
                        View dt = new View(act);
                        dt.setBackground(Ui.oval(wp != null ? 0x88FFFFFF : (Ui.dark ? 0xFF3C4043 : 0xFFE8EAED)));
                        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(act.dp(18), act.dp(18));
                        dl.setMargins(act.dp(6), 0, act.dp(6), 0);
                        dots.addView(dt, dl);
                    }
                    LinearLayout.LayoutParams dsl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
                    dsl.topMargin = act.dp(rr == 0 ? 18 : 12);
                    ph.addView(dots, dsl);
                }
            }
            thumbBox.addView(ph, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        } else if (t.thumb != null) {
            TopCropImageView th = new TopCropImageView(act);
            th.setImageBitmap(t.thumb);
            thumbBox.addView(th, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        } else {
            FrameLayout big = act.home.tileIcon(title, u, false, 0, 52, !t.incognito);
            thumbBox.addView(big, new FrameLayout.LayoutParams(act.dp(52), act.dp(52), Gravity.CENTER));
        }
        if (t.video != null && !t.ntp) {
            LinearLayout vb = new LinearLayout(act);
            vb.setGravity(Gravity.CENTER_VERTICAL);
            vb.setPaddingRelative(act.dp(8), act.dp(4), act.dp(10), act.dp(4));
            vb.setBackground(Ui.round(0xCC202124, 12));
            vb.addView(Ui.icon(act, R.drawable.ic_play, Color.WHITE), new LinearLayout.LayoutParams(act.dp(14), act.dp(14)));
            TextView vt = Ui.text(act, L.t("Видео"), 11, Color.WHITE);
            vt.setPaddingRelative(act.dp(4), 0, 0, 0);
            vb.addView(vt);
            FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(act.WRAP, act.WRAP, Gravity.BOTTOM | Gravity.START);
            vl.setMargins(act.dp(8), 0, 0, act.dp(8));
            thumbBox.addView(vb, vl);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(act.MATCH, 0, 1);
        tl.setMargins(act.dp(5), 0, act.dp(5), act.dp(5));
        card.addView(thumbBox, tl);
        card.setOnClickListener(v -> { act.switcher.setVisibility(View.GONE); act.switcher.removeAllViews(); act.selectTab(t); });
        attachSwipe(card, t);
        return card;
    }

    void attachSwipe(View card, Tab t) {
        final int slop = ViewConfiguration.get(act).getScaledTouchSlop();
        card.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0; boolean drag, moved, longed;
            final Runnable lp = () -> {
                longed = true;
                card.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                card.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(90).start();
                act.tabMenu(t);
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX(); y0 = e.getRawY(); drag = false; moved = false; longed = false;
                        v.animate().setStartDelay(0).scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                        v.postDelayed(lp, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (longed) return true;
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!drag && !moved) {
                            if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) { drag = true; v.removeCallbacks(lp); v.getParent().requestDisallowInterceptTouchEvent(true); }
                            else if (Math.abs(dy) > slop) { moved = true; v.removeCallbacks(lp); v.animate().scaleX(1f).scaleY(1f).setDuration(90).start(); }
                        }
                        if (drag) { v.setTranslationX(dx); v.setAlpha(Math.max(0.15f, 1f - Math.abs(dx) / v.getWidth())); }
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        v.removeCallbacks(lp);
                        if (longed) return true;
                        float dx = e.getRawX() - x0;
                        if (drag) {
                            if (Math.abs(dx) > v.getWidth() * 0.35f)
                                v.animate().translationX(Math.signum(dx) * v.getWidth() * 1.4f).alpha(0f).setDuration(150).withEndAction(() -> act.closeTabs(act.one(t))).start();
                            else v.animate().translationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                        } else {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                            if (!moved) v.performClick();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(lp);
                        v.animate().translationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                        return true;
                }
                return false;
            }
        });
    }
}
