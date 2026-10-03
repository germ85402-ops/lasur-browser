package com.lumen.browser;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Export / import of settings, bookmarks, history, shortcuts and (optionally) passwords as a JSON file. */
final class Backup {
    static final int REQ_EXPORT = 21, REQ_IMPORT = 22;
    /** Not portable (device-bound encryption) or session-only state. */
    static final Set<String> SKIP = new HashSet<>(java.util.Arrays.asList("pw", "tabs", "tabIndex", "groups"));

    final MainActivity act;
    boolean withPasswords;

    Backup(MainActivity act) { this.act = act; }

    void startExport() {
        if (act.passwords.list.isEmpty()) { pickExportFile(false); return; }
        act.dialog().setTitle(L.t("Экспорт данных"))
                .setMessage(L.t("Добавить в файл сохранённые пароли? Они будут записаны в открытом виде — храните файл в надёжном месте."))
                .setPositiveButton(L.t("С паролями"), (d, w) -> act.passwordsUi.withAuth(() -> pickExportFile(true)))
                .setNeutralButton(L.t("Без паролей"), (d, w) -> pickExportFile(false))
                .setNegativeButton(L.t("Отмена"), null).show();
    }

    void pickExportFile(boolean pw) {
        withPasswords = pw;
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "lasur-backup-" + day + ".json");
        try { act.startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { act.toast(L.t("Не удалось открыть выбор файла")); }
    }

    void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { act.startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { act.toast(L.t("Не удалось открыть выбор файла")); }
    }

    void onResult(int req, Uri uri) {
        if (uri == null) return;
        if (req == REQ_EXPORT) export(uri); else readImport(uri);
    }

    static JSONObject prefsJson(Map<String, ?> all) throws Exception {
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (SKIP.contains(e.getKey()) || e.getValue() == null) continue;
            Object v = e.getValue();
            JSONObject o = new JSONObject();
            if (v instanceof Boolean) o.put("t", "b");
            else if (v instanceof Integer) o.put("t", "i");
            else if (v instanceof Long) o.put("t", "l");
            else if (v instanceof Float) o.put("t", "f");
            else if (v instanceof String) o.put("t", "s");
            else if (v instanceof Set) { o.put("t", "S"); JSONArray a = new JSONArray(); for (Object x : (Set<?>) v) a.put(String.valueOf(x)); v = a; }
            else continue;
            o.put("v", v instanceof Float ? (double) (Float) v : v);
            prefs.put(e.getKey(), o);
        }
        return prefs;
    }

    static void applyPrefs(SharedPreferences.Editor ed, JSONObject prefs) throws Exception {
        java.util.Iterator<String> it = prefs.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (SKIP.contains(k)) continue;
            JSONObject o = prefs.getJSONObject(k);
            switch (o.optString("t")) {
                case "b": ed.putBoolean(k, o.getBoolean("v")); break;
                case "i": ed.putInt(k, o.getInt("v")); break;
                case "l": ed.putLong(k, o.getLong("v")); break;
                case "f": ed.putFloat(k, (float) o.getDouble("v")); break;
                case "s": ed.putString(k, o.getString("v")); break;
                case "S": {
                    JSONArray a = o.getJSONArray("v");
                    Set<String> s = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                    ed.putStringSet(k, s);
                    break;
                }
                default: break;
            }
        }
    }

    void export(Uri uri) {
        act.store.flush();
        act.saveTabs();
        final boolean pw = withPasswords;
        final java.util.ArrayList<Passwords.Cred> creds = new java.util.ArrayList<>(act.passwords.list);
        // after the pending (ordered) preference writes
        Store.IO.execute(() -> {
            boolean ok = false;
            int nPw = 0;
            try {
                JSONObject root = new JSONObject();
                root.put("app", "Lasur").put("v", 1).put("version", BuildConfig.VERSION_NAME).put("time", System.currentTimeMillis());
                root.put("prefs", prefsJson(act.store.p.getAll()));
                if (pw) {
                    JSONArray a = new JSONArray();
                    for (Passwords.Cred c : creds) {
                        String p = act.passwords.pass(c);
                        if (p == null) continue;
                        a.put(new JSONObject().put("s", c.site).put("u", c.user).put("p", p));
                    }
                    nPw = a.length();
                    root.put("passwords", a);
                }
                try (OutputStream o = act.getContentResolver().openOutputStream(uri, "wt")) {
                    o.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
                }
                ok = true;
            } catch (Exception e) { android.util.Log.w("Lasur", "backup export", e); }
            final boolean fOk = ok; final int fPw = nPw;
            act.ui.post(() -> act.toast(fOk ? (pw ? L.t("Резервная копия сохранена. Паролей: ") + fPw : L.t("Резервная копия сохранена")) : L.t("Не удалось сохранить файл")));
        });
    }

    void readImport(Uri uri) {
        act.BG.execute(() -> {
            JSONObject root = null;
            try (InputStream in = act.getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > 32 * 1024 * 1024) throw new java.io.IOException("too big"); }
                root = new JSONObject(b.toString("UTF-8"));
                if (!"Lasur".equals(root.optString("app")) || root.optJSONObject("prefs") == null) root = null;
            } catch (Exception e) { android.util.Log.w("Lasur", "backup read", e); }
            final JSONObject r = root;
            act.ui.post(() -> {
                if (r == null) { act.toast(L.t("Это не файл резервной копии Lasur")); return; }
                JSONArray pws = r.optJSONArray("passwords");
                int np = pws == null ? 0 : pws.length();
                act.dialog().setTitle(L.t("Восстановить данные?"))
                        .setMessage(L.t("Настройки, закладки, история и ярлыки будут заменены данными из файла.") + (np > 0 ? L.t("\nПаролей в файле: ") + np : ""))
                        .setPositiveButton(L.t("Восстановить"), (d, w) -> apply(r))
                        .setNegativeButton(L.t("Отмена"), null).show();
            });
        });
    }

    void apply(JSONObject root) {
        act.store.flush();
        act.saveTabs();
        Store.IO.execute(() -> {
            boolean ok = false;
            try {
                SharedPreferences.Editor ed = act.store.p.edit();
                for (String k : act.store.p.getAll().keySet()) if (!SKIP.contains(k)) ed.remove(k);
                applyPrefs(ed, root.getJSONObject("prefs"));
                ok = ed.commit();
            } catch (Exception e) { android.util.Log.w("Lasur", "backup import", e); }
            final boolean fOk = ok;
            act.ui.post(() -> {
                if (!fOk) { act.toast(L.t("Не удалось восстановить данные")); return; }
                JSONArray pws = root.optJSONArray("passwords");
                if (pws != null) for (int i = 0; i < pws.length(); i++) {
                    JSONObject o = pws.optJSONObject(i);
                    if (o == null || o.optString("s").isEmpty() || o.optString("p").isEmpty()) continue;
                    act.passwords.put(o.optString("s"), o.optString("u"), o.optString("p"));
                }
                AdBlocker.totalBlocked.set(act.store.p.getLong("blockedTotal", 0));
                act.toast(L.t("Данные восстановлены"));
                act.recreate();
            });
        });
    }
}
