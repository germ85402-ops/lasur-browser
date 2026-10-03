package com.lumen.browser;
import android.util.Log;
import android.Manifest;
import android.content.ContentUris;
import android.database.Cursor;
import android.provider.MediaStore;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.view.Gravity;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads: file names, saving, the downloads screen and completion notices. */
final class Downloads {
    final MainActivity act;

    Downloads(MainActivity act) { this.act = act; }

    void onDownload(Tab t, String url, String ua, String cd, String mime, long len) {
        // The navigation turned into a download: the page did not change, restore its host.
        try { String pu = t.web.getUrl(); if (pu != null) t.pageHost = Uri.parse(pu).getHost(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        String name = fileName(url, cd, mime);
        if (url.startsWith("blob:")) {
            String origin = PasswordsUi.originOf(t.pageUrl);
            act.dialog().setTitle(L.t("Скачать файл?"))
                    .setMessage(name + "\n" + act.displayUrl(t.pageUrl))
                    .setPositiveButton(L.t("Скачать"), (d, which) -> withStorage(() -> {
                        if (!act.tabs.contains(t) || !origin.equals(PasswordsUi.originOf(t.pageUrl))) return;
                        if (blobSaves.size() >= 3) { act.toast(L.t("Дождитесь завершения текущих загрузок")); return; }
                        String token = java.util.UUID.randomUUID().toString();
                        MainActivity.BlobSave bs = new MainActivity.BlobSave(t, origin, name, mime);
                        blobSaves.put(token, bs);
                        String q = JSONObject.quote(token);
                        String js = "(function(){var B=LumenBridge,T=" + q + ";fetch(" + JSONObject.quote(url) + ").then(function(r){return r.blob()}).then(function(b){"
                                + "var CH=786432,o=0;function next(){if(o>=b.size){B.saveEnd(T,1);return}var f=new FileReader();"
                                + "f.onload=function(){var s=f.result;if(!B.saveChunk(T,s.substring(s.indexOf(',')+1))){B.saveEnd(T,0);return}o+=CH;next()};"
                                + "f.onerror=function(){B.saveEnd(T,0)};f.readAsDataURL(b.slice(o,o+CH))}"
                                + "if(B.saveBegin(T,b.type||'',b.size))next();else B.saveEnd(T,0)}).catch(function(){B.saveEnd(T,0)})})()";
                        t.web.evaluateJavascript(js, null);
                        act.ui.postDelayed(() -> expireBlob(token), 120000);
                    })).setNegativeButton(L.t("Отмена"), null).show();
            return;
        }
        if (url.startsWith("data:")) {
            act.dialog().setTitle(L.t("Скачать файл?")).setMessage(name)
                    .setPositiveButton(L.t("Скачать"), (d, which) -> withStorage(() -> act.BG.execute(() -> saveDataUrl(url, name, mime))))
                    .setNegativeButton(L.t("Отмена"), null).show();
            return;
        }
        boolean video = (mime != null && mime.startsWith("video/")) || act.isVideoUrl(url);
        if (video) {
            final Tab.Video v = new Tab.Video(url, t.web.getUrl(), name);
            act.videoUi.addVideo(t, url, t.web.getUrl(), name);
            act.dialog().setTitle(name)
                    .setItems(new String[]{L.t("Скачать"), L.t("Открыть во внешнем плеере"), L.t("Копировать ссылку")}, (d, w) -> {
                        if (w == 0) confirmDownload(url, name, mime, t.web.getUrl(), ua, len);
                        else if (w == 1) act.settingsUi.openExternal(v, t);
                        else act.menu.copy(url);
                    }).show();
            return;
        }
        confirmDownload(url, name, mime, t.web.getUrl(), ua, len);
    }

    MainActivity.BlobSave ownedBlob(String token, Tab t) {
        MainActivity.BlobSave b = blobSaves.get(token);
        if (b == null || b.owner != t) return null;
        if (!b.origin.equals(PasswordsUi.originOf(t.pageUrl)) || System.currentTimeMillis() - b.touched >= 120000) {
            abortBlob(token); return null;
        }
        return b;
    }

    void abortBlob(String token) {
        MainActivity.BlobSave b = blobSaves.remove(token);
        if (b != null) synchronized (b) { if (b.saver != null) b.saver.abort(); }
    }

    void expireBlob(String token) {
        MainActivity.BlobSave b = blobSaves.get(token);
        if (b == null) return;
        long remaining = 120000 - (System.currentTimeMillis() - b.touched);
        if (remaining <= 0) abortBlob(token);
        else act.ui.postDelayed(() -> expireBlob(token), remaining);
    }

    void abortBlobs(Tab owner) {
        for (java.util.Map.Entry<String, MainActivity.BlobSave> e : blobSaves.entrySet())
            if (owner == null || e.getValue().owner == owner) abortBlob(e.getKey());
    }

    final java.util.concurrent.ConcurrentHashMap<String, MainActivity.BlobSave> blobSaves = new java.util.concurrent.ConcurrentHashMap<>();

    void confirmDownload(String url, String name, String mime, String referer, String ua, long len) {
        EditText et = new EditText(act);
        et.setText(name);
        et.setSingleLine(true);
        FrameLayout f = new FrameLayout(act); f.setPaddingRelative(act.dp(20), act.dp(8), act.dp(20), 0); f.addView(et);
        String size = len > 0 ? android.text.format.Formatter.formatShortFileSize(act, len) : null;
        act.dialog().setTitle(L.t("Скачать файл?")).setMessage(act.displayUrl(url) + (size != null ? " · " + size : ""))
                .setView(f)
                .setPositiveButton(L.t("Скачать"), (d, w) -> startDownload(url, Saver.clean(et.getText().toString()), mime, referer, ua))
                .setNegativeButton(L.t("Отмена"), null).show();
    }

    void startDownload(String url, String name, String mime, String referer, String ua) {
        withStorage(() -> {
            try {
                DownloadManager dm = (DownloadManager) act.getSystemService(act.DOWNLOAD_SERVICE);
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String mt = mimeFor(name, mime);
                if (mt != null && !mt.isEmpty()) r.setMimeType(mt);
                String ck = act.cookies(act.current != null && act.current.incognito).getCookie(url);
                if (ck != null) r.addRequestHeader("Cookie", ck);
                if (ua != null) r.addRequestHeader("User-Agent", ua);
                if (referer != null && referer.startsWith("http")) r.addRequestHeader("Referer", referer);
                r.setTitle(name);
                r.setDescription(act.displayUrl(url));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Lasur/" + name);
                dm.enqueue(r);
                act.snack(L.t("Загрузка началась: ") + name, L.t("Загрузки"), this::showDownloads);
            } catch (Exception e) { act.toast(L.t("Ошибка загрузки: ") + e.getMessage()); }
        });
    }

    void saveDataUrl(String dataUrl, String name, String mime) {
        try {
            int comma = dataUrl.indexOf(',');
            String meta = dataUrl.substring(5, comma);
            byte[] data = meta.contains(";base64") ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                    : Uri.decode(dataUrl.substring(comma + 1)).getBytes("UTF-8");
            String m = mimeFor(name, mime != null && !mime.isEmpty() ? mime : meta.split(";")[0]);
            Saver s = Saver.create(act, name, m);
            s.out.write(data);
            s.finish();
            act.ui.post(() -> act.toast(L.t("Сохранено в Загрузки/Lasur: ") + s.name));
        } catch (Exception e) { act.ui.post(() -> act.toast(L.t("Не удалось сохранить: ") + e.getMessage())); }
    }

    void withStorage(Runnable r) {
        if (Build.VERSION.SDK_INT < 29 && act.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            act.pendingPerm = r;
            act.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, act.REQ_STORAGE);
        } else r.run();
    }

    void withNotif(Runnable r) {
        if (Build.VERSION.SDK_INT >= 33 && act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            act.pendingPerm = r;
            act.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, act.REQ_NOTIF);
        } else r.run();
    }

    void showDownloads() {
        ScrollView sv = new ScrollView(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPaddingRelative(0, act.dp(6), 0, act.dp(24));
        sv.addView(list);
        final Dialog[] dl = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        final java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
        fill[0] = () -> {
            list.removeAllViews();
            ArrayList<Object[]> items = new ArrayList<>(); // name, sub, uri, mime, progress, delete, time
            boolean running = false;
            HashSet<String> names = new HashSet<>();
            DownloadManager dm = (DownloadManager) act.getSystemService(act.DOWNLOAD_SERVICE);
            try (Cursor c = dm.query(new DownloadManager.Query())) {
                while (c != null && c.moveToNext()) {
                    long id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    String title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                    int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long so = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    String mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE));
                    long ts = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
                    String sub; int prog = -1; Uri uri = null;
                    if (st == DownloadManager.STATUS_RUNNING || st == DownloadManager.STATUS_PENDING || st == DownloadManager.STATUS_PAUSED) {
                        running = true;
                        prog = tot > 0 ? (int) (so * 100 / tot) : 0;
                        sub = (st == DownloadManager.STATUS_PAUSED ? L.t("Пауза · ") : st == DownloadManager.STATUS_PENDING ? L.t("Ожидание · ") : L.t("Загрузка · "))
                                + fmtSize(so) + (tot > 0 ? L.t(" из ") + fmtSize(tot) : "");
                    } else if (st == DownloadManager.STATUS_FAILED) sub = L.t("Ошибка загрузки");
                    else { sub = fmtSize(tot) + " · " + df.format(new java.util.Date(ts)); uri = dm.getUriForDownloadedFile(id); }
                    if (title != null) names.add(title);
                    items.add(new Object[]{title, sub, uri, mime, prog, (Runnable) () -> dm.remove(id), ts});
                }
            } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            if (Build.VERSION.SDK_INT >= 29) {
                String[] proj = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED};
                try (Cursor c = act.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj,
                        MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{Environment.DIRECTORY_DOWNLOADS + "/Lasur%", Environment.DIRECTORY_DOWNLOADS + "/Lumen%"}, null)) {
                    while (c != null && c.moveToNext()) {
                        String name = c.getString(1);
                        if (name == null || names.contains(name)) continue;
                        Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0));
                        long ts = c.getLong(4) * 1000;
                        items.add(new Object[]{name, fmtSize(c.getLong(2)) + " · " + df.format(new java.util.Date(ts)), uri, c.getString(3), -1,
                                (Runnable) () -> { try { act.getContentResolver().delete(uri, null, null); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); } }, ts});
                    }
                } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            }
            java.util.Collections.sort(items, (a, b) -> Long.compare((Long) b[6], (Long) a[6]));
            if (items.isEmpty()) {
                TextView e = Ui.text(act, L.t("Загрузок пока нет.\nФайлы и видео сохраняются в папку «Загрузки/Lasur»."), 15, Ui.TEXT2);
                e.setGravity(Gravity.CENTER);
                e.setPaddingRelative(act.dp(24), act.dp(72), act.dp(24), 0);
                list.addView(e, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            }
            for (Object[] it : items) {
                final String name = (String) it[0], mime = (String) it[3];
                final Uri uri = (Uri) it[2];
                final int prog = (Integer) it[4];
                final Runnable del = (Runnable) it[5];
                LinearLayout r = new LinearLayout(act);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPaddingRelative(act.dp(16), act.dp(10), act.dp(6), act.dp(10));
                r.setBackground(Ui.ripple(act, false));
                FrameLayout ic = new FrameLayout(act);
                ic.setBackground(Ui.round(Ui.TONAL, 12));
                int res = mime != null && mime.startsWith("video") ? R.drawable.ic_video : mime != null && mime.startsWith("image") ? R.drawable.ic_wallpaper : R.drawable.ic_download;
                ic.addView(Ui.icon(act, res, Ui.ON_TONAL), new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
                r.addView(ic, new LinearLayout.LayoutParams(act.dp(48), act.dp(48)));
                LinearLayout tx = new LinearLayout(act);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPaddingRelative(act.dp(14), 0, act.dp(6), 0);
                tx.addView(Ui.single(act, name == null ? L.t("Файл") : name, 15, Ui.TEXT));
                tx.addView(Ui.single(act, (String) it[1], 12, Ui.TEXT2));
                if (prog >= 0) {
                    ProgressBar pb = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
                    pb.setMax(100);
                    pb.setProgress(prog);
                    pb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
                    tx.addView(pb, new LinearLayout.LayoutParams(act.MATCH, act.dp(6)));
                }
                r.addView(tx, new LinearLayout.LayoutParams(0, act.WRAP, 1));
                ImageView more = Ui.iconBtn(act, R.drawable.ic_more, Ui.TEXT2);
                r.addView(more, new LinearLayout.LayoutParams(act.dp(40), act.dp(44)));
                Runnable open = () -> {
                    if (uri == null) { act.toast(prog >= 0 ? L.t("Файл ещё загружается") : L.t("Файл недоступен")); return; }
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "*/*" : mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        act.startActivity(Intent.createChooser(i, L.t("Открыть с помощью")));
                    } catch (Exception e) { act.toast(L.t("Нет приложения для открытия файла")); }
                };
                r.setOnClickListener(v -> open.run());
                more.setOnClickListener(v -> {
                    ArrayList<Object[]> m = new ArrayList<>();
                    m.add(new Object[]{R.drawable.ic_play, L.t("Открыть"), open});
                    if (uri != null) m.add(new Object[]{R.drawable.ic_share, L.t("Поделиться"), (Runnable) () -> {
                        Intent i = new Intent(Intent.ACTION_SEND).setType(mime == null ? "*/*" : mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        act.startActivity(Intent.createChooser(i, L.t("Поделиться")));
                    }});
                    m.add(new Object[]{R.drawable.ic_close, prog >= 0 ? L.t("Отменить загрузку") : L.t("Удалить файл"), (Runnable) () -> { del.run(); fill[0].run(); }});
                    act.sheetMenu(name, m);
                });
                list.addView(r, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
            }
            if (running) list.postDelayed(() -> { if (dl[0] != null && dl[0].isShowing()) fill[0].run(); }, 1000);
        };
        fill[0].run();
        dl[0] = act.lists.fullDialog(L.t("Загрузки"), sv, L.t("Папка"), act.menu::openDownloads);
    }

    String fmtSize(long b) { return b <= 0 ? "—" : android.text.format.Formatter.formatShortFileSize(act, b); }

    void registerDlReceiver() {
        act.dlReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id < 0) return;
                DownloadManager dm = (DownloadManager) act.getSystemService(act.DOWNLOAD_SERVICE);
                try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cur == null || !cur.moveToFirst()) return;
                    int st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    String title = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                    String mime = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE));
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        Uri uri = dm.getUriForDownloadedFile(id);
                        act.snack(L.t("Загружено: ") + title, L.t("Открыть"), () -> {
                            try {
                                act.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "*/*" : mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), L.t("Открыть с помощью")));
                            } catch (Exception e) { showDownloads(); }
                        });
                    } else if (st == DownloadManager.STATUS_FAILED) act.snack(L.t("Не удалось загрузить: ") + title, L.t("Загрузки"), Downloads.this::showDownloads);
                } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        try {
            if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(act.dlReceiver, f, Context.RECEIVER_EXPORTED);
            else act.registerReceiver(act.dlReceiver, f);
        } catch (Exception ignored) { act.dlReceiver = null; }
    }

    static String extOf(String n) {
        if (n == null) return "";
        int q = n.indexOf('?'); if (q >= 0) n = n.substring(0, q);
        int d = n.lastIndexOf('.'), s = n.lastIndexOf('/');
        if (d < 0 || d < s || n.length() - d > 8) return "";
        return n.substring(d + 1).toLowerCase(Locale.ROOT);
    }

    static boolean genericMime(String m) {
        if (m == null || m.isEmpty()) return true;
        m = m.toLowerCase(Locale.ROOT);
        return m.startsWith("application/octet-stream") || m.startsWith("binary/") || m.startsWith("application/force-download")
                || m.startsWith("application/x-download") || m.startsWith("application/download") || m.startsWith("application/unknown");
    }

    /** Real file name of a download: Content-Disposition → URL path → query parameter → MIME type. Never ".bin" when the real type is known. */
    static String fileName(String url, String cd, String mime) {
        String n = null;
        if (cd != null) {
            Matcher m = Pattern.compile("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE).matcher(cd);
            if (m.find()) {
                try { n = java.net.URLDecoder.decode(m.group(2).trim().replace("+", "%2B"), m.group(1).trim().isEmpty() ? "UTF-8" : m.group(1).trim()); }
                catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            }
            if (n == null || n.isEmpty()) {
                m = Pattern.compile("filename\\s*=\\s*(\"([^\"]*)\"|([^;]+))", Pattern.CASE_INSENSITIVE).matcher(cd);
                if (m.find()) {
                    n = (m.group(2) != null ? m.group(2) : m.group(3)).trim();
                    try { if (n.contains("%")) n = java.net.URLDecoder.decode(n.replace("+", "%2B"), "UTF-8"); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
                }
            }
        }
        Uri u = null;
        try { u = Uri.parse(url); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        if ((n == null || n.isEmpty()) && u != null && u.getLastPathSegment() != null && !extOf(u.getLastPathSegment()).isEmpty()) n = u.getLastPathSegment();
        if ((n == null || n.isEmpty()) && u != null && u.isHierarchical()) {
            for (String k : new String[]{"filename", "file", "name", "fn", "title", "response-content-disposition"}) {
                try {
                    String v = u.getQueryParameter(k);
                    if (v == null) continue;
                    if (k.startsWith("response")) { String g = fileName("", v, null); if (!extOf(g).isEmpty()) { n = g; break; } continue; }
                    if (!extOf(v).isEmpty()) { n = v; break; }
                } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
            }
        }
        if (n == null || n.isEmpty()) n = URLUtil.guessFileName(url, null, genericMime(mime) ? null : mime);
        n = n.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_").trim();
        if (n.isEmpty()) n = "download";
        String ext = extOf(n);
        if (ext.isEmpty() || ext.equals("bin")) {
            String base = ext.equals("bin") ? n.substring(0, n.length() - 4) : n;
            String urlExt = u != null && u.getLastPathSegment() != null ? extOf(u.getLastPathSegment()) : "";
            String mm = mime == null ? "" : mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
            String fromMime = mm.equals("application/vnd.android.package-archive") ? "apk" : android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mm);
            if (!urlExt.isEmpty() && !urlExt.equals("bin")) n = base + "." + urlExt;
            else if (fromMime != null && !genericMime(mm)) n = base + "." + fromMime;
            else if (ext.equals("bin") && !base.isEmpty()) n = base + ".bin";
        }
        return n;
    }

    /** Correct MIME type for the saved file so it opens in the right app (APK → installer, etc.). */
    static String mimeFor(String name, String mime) {
        String ext = extOf(name);
        if (ext.equals("apk")) return "application/vnd.android.package-archive";
        String m = ext.isEmpty() ? null : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (genericMime(mime)) return m != null ? m : (mime == null || mime.isEmpty() ? null : mime.split(";")[0].trim());
        return mime.split(";")[0].trim();
    }
}
