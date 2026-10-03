package com.lumen.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Launch animation: the brand drop falls, splashes and dissolves into the water with ripples. Times are in ms. */
final class DropSplashView extends View {
    static final float END = 1000f, HIT = 280f;
    private static final float[] ANGLE = {-62, -40, -16, 16, 40, 62}, SPEED = {.40f, .52f, .48f, .48f, .52f, .40f};
    private static final float GRAVITY = .0022f;
    private final Drawable drop;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float dp;
    private float t;

    DropSplashView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        drop = c.getDrawable(R.drawable.ic_logo_drop);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * dp);
        glow.setShader(new RadialGradient(0, 0, 1, 0x7338BDF8, 0x002563EB, Shader.TileMode.CLAMP));
    }

    void setTime(float ms) { t = ms; invalidate(); }

    private static float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }
    private static float dec(float x) { return 1f - (1f - x) * (1f - x); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), size = 112 * dp, cx = w / 2f, surface = h / 2f + 36 * dp;
        float ty, sx, sy, sink = 0f;
        if (t < HIT) {
            float q = t / HIT;
            ty = -(surface + 20 * dp) * (1f - q * q);
            sx = .9f; sy = 1.14f;
        } else {
            float q = clamp((t - HIT) / 420f), wob = (float) Math.sin(Math.min(1f, q * 2f) * Math.PI);
            sink = dec(q);
            ty = size * .95f * sink;
            sx = 1f + .12f * wob; sy = 1f - .1f * wob;
        }
        // ripples
        for (int k = 0; k < 2; k++) {
            float t0 = HIT + k * 130f;
            if (t < t0 || t > t0 + 560f) continue;
            float q = dec((t - t0) / 560f), rx = (8 + 64 * q) * dp, ry = rx * .22f;
            ring.setColor(0x3B82F6);
            ring.setAlpha((int) (230 * (1f - q)));
            oval.set(cx - rx, surface - ry, cx + rx, surface + ry);
            c.drawOval(oval, ring);
        }
        // dissolving colour under the surface
        if (t >= HIT) {
            float q = clamp((t - HIT) / 600f), d = dec(q);
            glow.setAlpha((int) (255 * (1f - q)));
            c.save();
            c.translate(cx, surface + 3 * dp);
            c.scale((18 + 56 * d) * dp, (5 + 9 * d) * dp);
            c.drawCircle(0, 0, 1, glow);
            c.restore();
        }
        // the drop: only the part above the surface is visible
        if (sink < .999f) {
            c.save();
            c.clipRect(0, 0, w, surface);
            c.translate(cx, surface + ty);
            c.scale(sx, sy);
            drop.setBounds((int) (-size / 2), (int) (-size + 3 * dp), (int) (size / 2), (int) (3 * dp));
            drop.setAlpha((int) (255 * (t < HIT ? 1f : Math.max(0f, 1f - sink * .6f))));
            drop.draw(c);
            c.restore();
        }
        // splash droplets
        if (t >= HIT) {
            float e = t - HIT, life = clamp(1f - e / 460f);
            if (life > 0f) for (int i = 0; i < ANGLE.length; i++) {
                double a = Math.toRadians(ANGLE[i]);
                float x = (float) Math.sin(a) * SPEED[i] * e, y = -(float) Math.cos(a) * SPEED[i] * e + GRAVITY * e * e / 2f;
                if (y > 0 && e > 60) continue;
                dot.setColor(0x60A5FA);
                dot.setAlpha((int) (255 * Math.min(1f, life * 2f)));
                c.drawCircle(cx + (Math.signum(ANGLE[i]) * 16 + x * 1.3f) * dp, surface + (y - 2) * dp, (4.6f - 1.8f * (1f - life)) * dp, dot);
            }
        }
    }
}
