package com.lumen.browser;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Named, colored tab groups (registry in prefs "groups"; membership in Tab.group). */
final class TabGroups {
    static final int[] COLORS = {0xFF1A73E8, 0xFFD93025, 0xFFF9AB00, 0xFF188038, 0xFFD01884, 0xFF9334E6, 0xFF12B5CB, 0xFFE8710A};

    static final class G {
        final String id; String name; int color;
        G(String id, String name, int color) { this.id = id; this.name = name; this.color = color; }
    }

    final MainActivity act;
    private ArrayList<G> list;

    TabGroups(MainActivity act) { this.act = act; }

    ArrayList<G> all() {
        if (list == null) {
            list = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(act.store.p.getString("groups", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    list.add(new G(o.getString("i"), o.optString("n"), o.optInt("c", COLORS[i % COLORS.length])));
                }
            } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        }
        return list;
    }

    void save() {
        JSONArray a = new JSONArray();
        try { for (G g : all()) a.put(new JSONObject().put("i", g.id).put("n", g.name).put("c", g.color)); }
        catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        act.store.p.edit().putString("groups", a.toString()).apply();
    }

    G get(String id) {
        if (id == null) return null;
        for (G g : all()) if (g.id.equals(id)) return g;
        return null;
    }

    G create(String name) {
        int used = all().size();
        G g = new G("g" + Long.toString(System.currentTimeMillis(), 36) + (int) (Math.random() * 1000), name, COLORS[used % COLORS.length]);
        list.add(g);
        save();
        return g;
    }

    /** Groups used by tabs of the given mode, in registry order. */
    ArrayList<G> used(boolean inc) {
        ArrayList<G> r = new ArrayList<>();
        for (G g : all()) if (count(g.id, inc) > 0) r.add(g);
        return r;
    }

    int count(String id, boolean inc) {
        int n = 0;
        for (Tab t : act.tabs) if (t.incognito == inc && id.equals(t.group)) n++;
        return n;
    }

    /** Forget groups no tab belongs to any more. */
    void prune() {
        boolean ch = false;
        for (int i = all().size() - 1; i >= 0; i--) {
            G g = list.get(i);
            if (count(g.id, false) == 0 && count(g.id, true) == 0) { list.remove(i); ch = true; }
        }
        if (ch) save();
    }

    String nextName() {
        int n = all().size() + 1;
        while (true) {
            String s = L.t("Группа ") + n;
            boolean taken = false;
            for (G g : list) if (s.equals(g.name)) taken = true;
            if (!taken) return s;
            n++;
        }
    }

    void refresh() { if (act.switcher.getVisibility() == View.VISIBLE) act.tabSwitcher.buildSwitcher(); }

    /** "Add to group…" from the tab menu. */
    void pickFor(Tab t) {
        ArrayList<Object[]> m = new ArrayList<>();
        for (G g : all()) {
            if (g.id.equals(t.group)) continue;
            m.add(new Object[]{R.drawable.ic_folder, g.name, (Runnable) () -> { t.group = g.id; prune(); refresh(); }});
        }
        m.add(new Object[]{R.drawable.ic_add, L.t("Новая группа…"), (Runnable) () -> askName(null, nextName(), name -> {
            G g = create(name);
            t.group = g.id;
            prune();
            if (act.switcher.getVisibility() == View.VISIBLE) act.switcherGroup = g.id;
            refresh();
        })});
        act.sheetMenu(L.t("Добавить в группу"), m);
    }

    void ungroup(Tab t) { t.group = null; prune(); refresh(); }

    void askName(String title, String def, java.util.function.Consumer<String> cb) {
        EditText et = new EditText(act);
        et.setText(def);
        et.setSingleLine(true);
        et.selectAll();
        FrameLayout f = new FrameLayout(act); f.setPaddingRelative(act.dp(20), act.dp(8), act.dp(20), 0); f.addView(et);
        android.app.AlertDialog d = act.dialog().setTitle(title != null ? title : L.t("Новая группа")).setView(f)
                .setPositiveButton(L.t("Готово"), (x, w) -> {
                    String s = et.getText().toString().trim();
                    cb.accept(s.isEmpty() ? def : s);
                })
                .setNegativeButton(L.t("Отмена"), null).show();
        et.requestFocus();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    /** Long press on a group chip in the tab switcher. */
    void groupMenu(G g, boolean inc) {
        ArrayList<Object[]> m = new ArrayList<>();
        m.add(new Object[]{R.drawable.ic_edit, L.t("Переименовать"), (Runnable) () -> askName(L.t("Переименовать"), g.name, n -> { g.name = n; save(); refresh(); })});
        m.add(new Object[]{R.drawable.ic_palette, L.t("Цвет"), (Runnable) () -> pickColor(g)});
        m.add(new Object[]{R.drawable.ic_folder, L.t("Разгруппировать"), (Runnable) () -> {
            for (Tab t : act.tabs) if (g.id.equals(t.group)) t.group = null;
            act.switcherGroup = null;
            prune(); refresh();
        }});
        m.add(new Object[]{R.drawable.ic_close, L.t("Закрыть вкладки группы"), (Runnable) () -> {
            ArrayList<Tab> l = new ArrayList<>();
            for (Tab t : act.tabs) if (t.incognito == inc && g.id.equals(t.group)) l.add(t);
            act.switcherGroup = null;
            act.closeTabs(l);
        }});
        act.sheetMenu(g.name, m);
    }

    void pickColor(G g) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER);
        row.setPaddingRelative(act.dp(16), act.dp(20), act.dp(16), act.dp(8));
        final android.app.AlertDialog[] d = new android.app.AlertDialog[1];
        for (int c : COLORS) {
            View v = new View(act);
            v.setBackground(c == g.color ? Ui.stroke(c, Ui.TEXT, 3, 15) : Ui.oval(c));
            v.setOnClickListener(x -> { g.color = c; save(); refresh(); if (d[0] != null) d[0].dismiss(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(act.dp(30), act.dp(30));
            lp.setMargins(act.dp(5), 0, act.dp(5), 0);
            row.addView(v, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(act);
        hs.addView(row);
        d[0] = act.dialog().setTitle(L.t("Цвет группы")).setView(hs).setNegativeButton(L.t("Отмена"), null).show();
    }
}
