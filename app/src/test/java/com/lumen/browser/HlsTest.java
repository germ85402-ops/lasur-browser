package com.lumen.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HlsTest {
    @Test public void byteRangesAndMap() throws Exception {
        String pl = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"700@0\"\n"
                + "#EXTINF:4,\n#EXT-X-BYTERANGE:1000@700\nmain.mp4\n#EXTINF:4,\n#EXT-X-BYTERANGE:500\nmain.mp4\n#EXT-X-ENDLIST\n";
        HlsService.Playlist p = HlsService.parseMedia(pl, "https://cdn.test/v/index.m3u8");
        assertTrue(p.ended);
        assertEquals("https://cdn.test/v/init.mp4", p.mapUrl);
        assertEquals(700, p.mapLen);
        assertEquals(2, p.segs.size());
        assertEquals(700, p.segs.get(0).rangeStart);
        assertEquals(1700, p.segs.get(1).rangeStart);
        assertEquals(500, p.segs.get(1).rangeLen);
        assertEquals(4, p.target);
    }

    @Test public void liveWithoutEndlist() throws Exception {
        HlsService.Playlist p = HlsService.parseMedia("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:42\n#EXTINF:6,\na.ts\n", "https://x.test/live.m3u8");
        assertFalse(p.ended);
        assertEquals(42, p.segs.get(0).seq);
    }
}
