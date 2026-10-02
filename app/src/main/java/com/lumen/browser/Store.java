package com.lumen.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import java.util.Set;

final class Store {
    static final class Item {
        String t, u; long d;
        int c;          // custom tile color (0 = automatic)
        boolean letter; // show letter instead of site icon
        String f = "";  // bookmark folder ("" = top level)
        Item(String t, String u, long d) { this.t = t; this.u = u; this.d = d; }
        Item copy() { Item i = new Item(t, u, d); i.c = c; i.letter = letter; i.f = f; return i; }
    }

    /** Single background thread for ordered SharedPreferences / file writes. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HashMap<String, Runnable> pendingSaves = new HashMap<>();

    static final String[] ENGINES = {"Google", "DuckDuckGo", "Яндекс", "Bing"};
    static final String[] ENGINE_URLS = {
            "https://www.google.com/search?q=",
            "https://duckduckgo.com/?q=",
            "https://yandex.ru/search/?text=",
            "https://www.bing.com/search?q="};

    final SharedPreferences p;
    final ArrayList<Item> bookmarks, history, shortcuts;

    Store(Context c) {
        p = c.getSharedPreferences("lumen", 0);
        bookmarks = load("bookmarks");
        history = load("history");
        if (p.contains("shortcuts")) shortcuts = load("shortcuts");
        else {
            shortcuts = new ArrayList<>();
            String[][] d = {{"Google", "https://www.google.com"}, {"YouTube", "https://m.youtube.com"},
                    {"Википедия", "https://ru.wikipedia.org"}, {"Яндекс", "https://ya.ru"},
                    {"ВКонтакте", "https://vk.com"}, {"Rutube", "https://rutube.ru"}, {"GitHub", "https://github.com"}};
            for (String[] s : d) shortcuts.add(new Item(s[0], s[1], 0));
        }
    }

    ArrayList<Item> load(String k) {
        ArrayList<Item> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(k, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item(o.optString("t"), o.optString("u"), o.optLong("d"));
                it.c = o.optInt("c", 0);
                it.letter = o.optBoolean("l", false);
                it.f = o.optString("f", "");
                l.add(it);
            }
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        return l;
    }

    /**
     * Saves are debounced and serialized off the main thread: the history list (up to 1500 entries)
     * used to be re-encoded on the UI thread after every page load.
     */
    void save(String k, ArrayList<Item> l) {
        Runnable old = pendingSaves.remove(k);
        if (old != null) ui.removeCallbacks(old);
        Runnable r = () -> { pendingSaves.remove(k); writeNow(k, l); };
        pendingSaves.put(k, r);
        ui.postDelayed(r, 700);
    }

    /** Writes pending changes immediately (e.g. when the app goes to background). */
    void flush() {
        for (Runnable r : new ArrayList<>(pendingSaves.values())) { ui.removeCallbacks(r); r.run(); }
    }

    private void writeNow(String k, ArrayList<Item> l) {
        final ArrayList<Item> snap = new ArrayList<>(l.size());
        for (Item it : l) snap.add(it.copy());
        IO.execute(() -> {
            JSONArray a = new JSONArray();
            try {
                for (Item it : snap) {
                    JSONObject o = new JSONObject();
                    o.put("t", it.t); o.put("u", it.u); o.put("d", it.d);
                    if (it.c != 0) o.put("c", it.c);
                    if (it.letter) o.put("l", true);
                    if (it.f != null && !it.f.isEmpty()) o.put("f", it.f);
                    a.put(o);
                }
            } catch (Exception e) { android.util.Log.w("Lasur", "save " + k, e); }
            p.edit().putString(k, a.toString()).apply();
        });
    }

    /** Bookmark folders in display order. */
    ArrayList<String> folders() {
        ArrayList<String> r = new ArrayList<>();
        for (Item it : bookmarks) if (it.f != null && !it.f.isEmpty() && !r.contains(it.f)) r.add(it.f);
        java.util.Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    void saveBookmarks() { save("bookmarks", bookmarks); }
    void saveHistory() { save("history", history); }
    void saveShortcuts() { save("shortcuts", shortcuts); }

    void addHistory(String t, String u) {
        if (u == null || !(u.startsWith("http://") || u.startsWith("https://"))) return;
        for (int i = 0; i < history.size(); i++) if (history.get(i).u.equals(u)) { history.remove(i); break; }
        history.add(0, new Item(t == null || t.isEmpty() ? u : t, u, System.currentTimeMillis()));
        while (history.size() > 1500) history.remove(history.size() - 1);
        saveHistory();
    }

    void updateHistoryTitle(String u, String t) {
        if (history.isEmpty() || t == null || t.isEmpty()) return;
        Item it = history.get(0);
        if (it.u.equals(u)) { it.t = t; saveHistory(); }
    }

    boolean isBookmarked(String u) {
        if (u == null) return false;
        for (Item it : bookmarks) if (it.u.equals(u)) return true;
        return false;
    }

    boolean toggleBookmark(String t, String u) {
        for (int i = 0; i < bookmarks.size(); i++) if (bookmarks.get(i).u.equals(u)) { bookmarks.remove(i); saveBookmarks(); return false; }
        bookmarks.add(0, new Item(t == null || t.isEmpty() ? u : t, u, System.currentTimeMillis()));
        saveBookmarks();
        return true;
    }

    boolean bool(String k, boolean def) { return p.getBoolean(k, def); }
    void setBool(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
    int engine() { return p.getInt("engine", 0); }
    void setEngine(int i) { p.edit().putInt("engine", i).apply(); }
    String searchUrl() { int e = engine(); return ENGINE_URLS[e < 0 || e >= ENGINE_URLS.length ? 0 : e]; }

    boolean bottomBar() { return bool("bottomBar", false); }
    boolean hideOnScroll() { return bool("hideBar", false); }
    boolean adblock() { return bool("adblock", true); }
    boolean blockPopups() { return bool("popups", true); }
    boolean js() { return bool("js", true); }
    boolean desktopDefault() { return bool("desktop", false); }
    boolean restoreTabs() { return bool("restore", true); }

    Set<String> whitelist() { return new HashSet<>(p.getStringSet("whitelist", new HashSet<>())); }
    /** Exceptions are stored per site ("www." / "m." stripped) and cover all its subdomains. */
    void setWhitelisted(String host, boolean w) {
        Set<String> s = whitelist();
        String key = AdBlocker.siteKey(host);
        if (w) s.add(key);
        else for (String e : new java.util.ArrayList<>(s)) if (e.equals(host) || e.equals(key) || AdBlocker.domainIn(host, e)) s.remove(e);
        p.edit().putStringSet("whitelist", s).apply();
    }
}
