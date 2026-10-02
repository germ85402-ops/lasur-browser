package com.lumen.browser;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class Saver {
    Context c; Uri uri; File file; OutputStream out; String name;

    static String clean(String name) {
        String n = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_").trim();
        if (n.isEmpty()) n = "file";
        if (n.length() > 120) n = n.substring(0, 120);
        return n;
    }

    static Saver create(Context c, String name, String mime) throws IOException {
        Saver s = new Saver();
        s.c = c;
        s.name = clean(name);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, s.name);
            if (mime != null) v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lasur");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            s.uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (s.uri == null) throw new IOException(L.t("Не удалось создать файл"));
            s.out = c.getContentResolver().openOutputStream(s.uri);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lasur");
            dir.mkdirs();
            File f = new File(dir, s.name);
            int i = 1;
            String base = s.name, ext = "";
            int dot = base.lastIndexOf('.');
            if (dot > 0) { ext = base.substring(dot); base = base.substring(0, dot); }
            while (f.exists()) f = new File(dir, base + " (" + (i++) + ")" + ext);
            s.file = f;
            s.out = new FileOutputStream(f);
        }
        if (s.out == null) throw new IOException(L.t("Нет доступа к файлу"));
        return s;
    }

    void finish() throws IOException {
        out.close();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, v, null, null);
        } else if (file != null) {
            MediaScannerConnection.scanFile(c, new String[]{file.getAbsolutePath()}, null, null);
        }
    }

    void abort() {
        try { out.close(); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        try {
            if (Build.VERSION.SDK_INT >= 29 && uri != null) c.getContentResolver().delete(uri, null, null);
            else if (file != null) file.delete();
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
    }

    /** Content URI that can be opened by other apps (Android 10+), else null. */
    Uri shareUri() { return Build.VERSION.SDK_INT >= 29 ? uri : null; }
}
