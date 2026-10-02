package com.lumen.browser;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Scales the image to the view width and anchors it to the top (like Chrome tab thumbnails). */
final class TopCropImageView extends ImageView {
    TopCropImageView(Context c) { super(c); setScaleType(ScaleType.MATRIX); }

    @Override protected boolean setFrame(int l, int t, int r, int b) {
        Drawable d = getDrawable();
        if (d != null && d.getIntrinsicWidth() > 0) {
            float vw = r - l, vh = b - t;
            float s = Math.max(vw / d.getIntrinsicWidth(), vh / d.getIntrinsicHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((vw - d.getIntrinsicWidth() * s) / 2f, 0);
            setImageMatrix(m);
        }
        return super.setFrame(l, t, r, b);
    }
}
