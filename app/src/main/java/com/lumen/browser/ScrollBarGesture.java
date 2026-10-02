package com.lumen.browser;

/** Direction hysteresis; ignores scroll callbacks caused by resizing or scripted navigation. */
final class ScrollBarGesture {
    private int distance;

    void reset() { distance = 0; }

    Boolean scroll(int y, int oldY, boolean hidden, boolean userScroll, boolean transitioning,
                   int top, int hideDistance, int showDistance) {
        if (transitioning || !userScroll) return null;
        if (y <= top) { reset(); return hidden ? Boolean.FALSE : null; }
        int dy = y - oldY;
        if (dy == 0) return null;
        if ((dy > 0) != (distance > 0)) distance = 0;
        distance += dy;
        if (!hidden && distance > hideDistance) { reset(); return Boolean.TRUE; }
        if (hidden && distance < -showDistance) { reset(); return Boolean.FALSE; }
        return null;
    }
}
