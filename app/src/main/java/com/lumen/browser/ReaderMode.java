package com.lumen.browser;
import org.json.JSONObject;

/** Reader mode: the article text in a clean full-screen overlay inside the page (assets/reader.js). */
final class ReaderMode {
    final MainActivity act;
    private String js;

    ReaderMode(MainActivity act) { this.act = act; }

    private String script() {
        if (js == null) {
            try (java.io.InputStream in = act.getAssets().open("reader.js")) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                js = Scripts.R(b.toString("UTF-8"));
            } catch (Exception e) { android.util.Log.w("Lasur", "reader.js", e); js = ""; }
        }
        return js;
    }

    void toggle(Tab t) {
        if (t == null || t.ntp || t.web == null) return;
        if (t.readerOn) { close(t); return; }
        String s = script();
        if (s.isEmpty()) return;
        JSONObject o = new JSONObject();
        try {
            o.put("size", act.store.p.getInt("readerSize", 19));
            o.put("theme", act.store.p.getInt("readerTheme", Ui.dark ? 2 : 0));
            o.put("close", L.t("Закрыть режим чтения"));
            o.put("smaller", L.t("Уменьшить текст"));
            o.put("bigger", L.t("Увеличить текст"));
            o.put("themeLabel", L.t("Тема"));
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        t.web.evaluateJavascript(s + Scripts.R(";window.__lasurReader(" + o + ")"), r -> {
            if ("\"on\"".equals(r)) { t.readerOn = true; if (t == act.current) act.setBarsHidden(false); }
            else act.toast(L.t("Не удалось найти текст статьи на этой странице"));
        });
    }

    void close(Tab t) {
        t.readerOn = false;
        if (t.web != null) t.web.evaluateJavascript(Scripts.R("window.__lasurReaderClose&&window.__lasurReaderClose()"), null);
    }

    /** From the page (LumenBridge.reader). */
    void onState(Tab t, boolean on, int size, int theme) {
        t.readerOn = on;
        if (size >= 10 && size <= 40 && theme >= 0 && theme <= 2)
            act.store.p.edit().putInt("readerSize", size).putInt("readerTheme", theme).apply();
    }
}
