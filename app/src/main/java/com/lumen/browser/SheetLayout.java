package com.lumen.browser;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Bottom-sheet container that can be dragged down to dismiss. */
final class SheetLayout extends LinearLayout {
    ScrollView scroll;
    Runnable onDismiss;
    private float x0, y0;
    private boolean dragging, closing;
    private VelocityTracker vt;
    private final int slop;

    SheetLayout(Context c) {
        super(c);
        setOrientation(VERTICAL);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    private void track(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        MotionEvent m = MotionEvent.obtain(e);
        m.setLocation(e.getRawX(), e.getRawY());
        vt.addMovement(m);
        m.recycle();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (closing) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = e.getRawX(); y0 = e.getRawY(); dragging = false;
                if (vt != null) { vt.recycle(); vt = null; }
                track(e);
                break;
            case MotionEvent.ACTION_MOVE: {
                track(e);
                float dy = e.getRawY() - y0, dx = e.getRawX() - x0;
                if (dy > slop && dy > Math.abs(dx) * 1.2f && (scroll == null || !scroll.canScrollVertically(-1))) {
                    dragging = true;
                    y0 = e.getRawY();
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (closing) return true;
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                y0 = e.getRawY(); dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) setTranslationY(Math.max(0, e.getRawY() - y0));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                float v = 0;
                if (vt != null) { vt.computeCurrentVelocity(1000); v = vt.getYVelocity(); vt.recycle(); vt = null; }
                float density = getResources().getDisplayMetrics().density;
                if (getTranslationY() > getHeight() * 0.25f || (v > 1200 * density && getTranslationY() > 8 * density)) {
                    closing = true;
                    animate().translationY(getHeight()).setDuration(170).withEndAction(() -> { if (onDismiss != null) onDismiss.run(); }).start();
                } else animate().translationY(0).setDuration(180).start();
                return true;
            }
        }
        return true;
    }
}
