package com.lumen.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

final class Store {
    static final class Item {
        String t, u; long d;
        int c;          // custom tile color (0 = automatic)
        boolean letter; // show letter instead of site icon
        Item(String t, String u, long d) { this.t = t; this.u = u; this.d = d; }
    }

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
                l.add(it);
            }
        } catch (Exception ignored) { }
        return l;
    }

    void save(String k, ArrayList<Item> l) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : l) {
                JSONObject o = new JSONObject();
                o.put("t", it.t); o.put("u", it.u); o.put("d", it.d);
                if (it.c != 0) o.put("c", it.c);
                if (it.letter) o.put("l", true);
                a.put(o);
            }
        } catch (Exception ignored) { }
        p.edit().putString(k, a.toString()).apply();
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

    boolean adblock() { return bool("adblock", true); }
    boolean blockPopups() { return bool("popups", true); }
    boolean js() { return bool("js", true); }
    boolean desktopDefault() { return bool("desktop", false); }
    boolean restoreTabs() { return bool("restore", true); }

    Set<String> whitelist() { return new HashSet<>(p.getStringSet("whitelist", new HashSet<>())); }
    void setWhitelisted(String host, boolean w) {
        Set<String> s = whitelist();
        if (w) s.add(host); else s.remove(host);
        p.edit().putStringSet("whitelist", s).apply();
    }
}
