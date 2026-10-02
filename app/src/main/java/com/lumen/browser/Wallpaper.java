package com.lumen.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Built-in generated wallpapers + user picture for the new tab page. */
final class Wallpaper {
    static final int NONE = -1, CUSTOM = 100;
    static final String[] NAMES = {"Рассвет", "Океан", "Лес", "Закат", "Космос", "Лаванда", "Мята", "Графит", "Аврора", "Песок"};
    static final int[][] COLS = {
            {0xFFFF9A8B, 0xFFFF6A88, 0xFFFFE29F},
            {0xFF2E3192, 0xFF1BBFE0, 0xFF4FACFE},
            {0xFF134E5E, 0xFF71B280, 0xFFB8F5A0},
            {0xFF3A1C71, 0xFFD76D77, 0xFFFFAF7B},
            {0xFF0F0C29, 0xFF302B63, 0xFF8E2DE2},
            {0xFFA18CD1, 0xFFFBC2EB, 0xFFE0C3FC},
            {0xFF11998E, 0xFF38EF7D, 0xFFC1FFD7},
            {0xFF232526, 0xFF414345, 0xFF606C88},
            {0xFF0B486B, 0xFF1D976C, 0xFF93F9B9},
            {0xFFC79081, 0xFFDFA579, 0xFFF6E0B5},
    };
    private static Bitmap cache;
    private static String cacheKey;

    static Bitmap render(int i, int w, int h) {
        int[] c = COLS[i % COLS.length];
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.35f, h, c[0], c[1], Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        float[][] blobs = {{0.85f, 0.12f, 0.55f}, {0.1f, 0.55f, 0.6f}, {0.7f, 0.92f, 0.5f}};
        int[] bc = {c[2], c[0], c[2]};
        for (int k = 0; k < blobs.length; k++) {
            float cx = blobs[k][0] * w, cy = blobs[k][1] * h, r = blobs[k][2] * Math.max(w, h * 0.6f);
            int col = (bc[k] & 0x00FFFFFF) | 0x99000000;
            p.setShader(new RadialGradient(cx, cy, r, col, bc[k] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, p);
        }
        return b;
    }

    static File customFile(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }

    static Bitmap get(Context c, int id, int w, int h) {
        if (id == NONE) return null;
        String key = id + ":" + w + "x" + h + ":" + (id == CUSTOM ? customFile(c).lastModified() : 0);
        if (key.equals(cacheKey) && cache != null) return cache;
        Bitmap b = null;
        try {
            if (id == CUSTOM) {
                File f = customFile(c);
                if (f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
            } else b = render(id, w, h);
        } catch (Throwable ignored) { }
        cache = b;
        cacheKey = key;
        return b;
    }

    static boolean saveCustom(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 2400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, o2); }
            if (b == null) return false;
            try (FileOutputStream out = new FileOutputStream(customFile(c))) { b.compress(Bitmap.CompressFormat.JPEG, 90, out); }
            cache = null; cacheKey = null;
            return true;
        } catch (Exception e) { return false; }
    }
}
