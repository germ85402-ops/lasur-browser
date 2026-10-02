package com.lumen.browser;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScrollBarGestureTest {
    @Test public void deliberateScrollingHidesAndReveals() {
        ScrollBarGesture g = new ScrollBarGesture();
        assertNull(g.scroll(40, 0, false, true, false, 8, 48, 32));
        assertEquals(Boolean.TRUE, g.scroll(60, 40, false, true, false, 8, 48, 32));
        assertNull(g.scroll(55, 60, true, true, false, 8, 48, 32));
        assertEquals(Boolean.FALSE, g.scroll(20, 55, true, true, false, 8, 48, 32));
    }
    @Test public void layoutAndScriptCallbacksCannotReverseTheBar() {
        ScrollBarGesture g = new ScrollBarGesture();
        assertNull(g.scroll(0, 200, true, true, true, 8, 48, 32));
        assertNull(g.scroll(0, 200, true, false, false, 8, 48, 32));
        assertEquals(Boolean.FALSE, g.scroll(0, 100, true, true, false, 8, 48, 32));
    }
    @Test public void directionChangesDiscardPreviousDistance() {
        ScrollBarGesture g = new ScrollBarGesture();
        assertNull(g.scroll(100, 140, true, true, true, 8, 48, 32));
        assertNull(g.scroll(120, 140, true, true, false, 8, 48, 32));
        assertNull(g.scroll(130, 120, true, true, false, 8, 48, 32));
        assertNull(g.scroll(110, 130, true, true, false, 8, 48, 32));
        assertEquals(Boolean.FALSE, g.scroll(90, 110, true, true, false, 8, 48, 32));
    }
}
