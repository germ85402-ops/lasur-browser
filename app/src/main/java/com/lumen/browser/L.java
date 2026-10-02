package com.lumen.browser;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

/** Interface languages. Russian source strings are the keys; translations live in assets/i18n/<code>.json. */
final class L {
    static final String[] CODES = {"", "en", "ru", "es", "pt", "fr", "de", "tr", "ar", "hi", "zh", "ja", "id"};
    static final String[] NAMES = {"", "English", "Русский", "Español", "Português", "Français", "Deutsch", "Türkçe", "العربية", "हिन्दी", "中文", "日本語", "Bahasa Indonesia"};
    static String lang = "ru";
    private static HashMap<String, String> map, en;

    static String pref(Context c) { return c.getSharedPreferences("lumen", 0).getString("lang", ""); }

    /** Saved language, or the system language if the browser supports it (otherwise English). */
    static String resolve(String pref) {
        if (pref != null && !pref.isEmpty()) return pref;
        String sys = Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        if (sys.equals("in")) sys = "id";
        for (String c : CODES) if (!c.isEmpty() && c.equals(sys)) return c;
        return "en";
    }

    static Context wrap(Context base) {
        String code = resolve(pref(base));
        Locale loc = new Locale(code);
        Locale.setDefault(loc);
        Configuration cf = new Configuration(base.getResources().getConfiguration());
        cf.setLocale(loc);
        cf.setLayoutDirection(loc);
        Context c = base.createConfigurationContext(cf);
        init(base, code);
        return c;
    }

    static synchronized void init(Context c, String code) {
        if (code.equals(lang) && (map != null || code.equals("ru"))) return;
        lang = code;
        map = null;
        if (code.equals("ru")) return;
        try {
            JSONArray keys = new JSONArray(read(c, "i18n/keys.json"));
            map = load(c, keys, code);
            en = code.equals("en") ? map : load(c, keys, "en");
        } catch (Exception e) { map = null; }
    }

    static void ensure(Context c) { init(c, resolve(pref(c))); }

    private static HashMap<String, String> load(Context c, JSONArray keys, String code) throws Exception {
        JSONArray v = new JSONArray(read(c, "i18n/" + code + ".json"));
        HashMap<String, String> m = new HashMap<>();
        for (int i = 0; i < keys.length() && i < v.length(); i++) {
            String s = v.optString(i, "");
            if (!s.isEmpty()) m.put(keys.getString(i), s);
        }
        return m;
    }

    private static String read(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        }
    }

    static String t(String s) {
        if (s == null || map == null) return s;
        String r = map.get(s);
        if (r == null && en != null) r = en.get(s);
        return r != null ? r : s;
    }

    static String[] ta(String[] a) { String[] r = new String[a.length]; for (int i = 0; i < a.length; i++) r[i] = t(a[i]); return r; }

    static boolean rtl() { return lang.equals("ar"); }
}
