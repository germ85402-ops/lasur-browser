package com.lumen.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class AdBlockerTest {
    @Before public void setUp() {
        Psl.load(Arrays.asList("com", "io", "github.io", "uk", "co.uk", "*.ck", "!www.ck", "ru"));
    }

    @Test public void hostRuleBlocksThirdParty() throws Exception {
        AdBlocker.loadForTest("||ads.example.com^\n");
        assertTrue(AdBlocker.shouldBlock("https://ads.example.com/x.js", "ads.example.com", "news.ru", AdBlocker.T_SCRIPT));
        assertTrue(AdBlocker.shouldBlock("https://cdn.ads.example.com/x.js", "cdn.ads.example.com", "news.ru", AdBlocker.T_SCRIPT));
        assertFalse(AdBlocker.shouldBlock("https://example.com/x.js", "example.com", "news.ru", AdBlocker.T_SCRIPT));
    }

    @Test public void resourceTypeOptions() throws Exception {
        AdBlocker.loadForTest("||tracker.net^$script\n/banner/*$image,~third-party\n");
        assertTrue(AdBlocker.shouldBlock("https://tracker.net/t.js", "tracker.net", "site.com", AdBlocker.T_SCRIPT));
        assertFalse(AdBlocker.shouldBlock("https://tracker.net/p.png", "tracker.net", "site.com", AdBlocker.T_IMAGE));
        assertTrue(AdBlocker.shouldBlock("https://site.com/banner/1.png", "site.com", "site.com", AdBlocker.T_IMAGE));
        assertFalse(AdBlocker.shouldBlock("https://other.com/banner/1.png", "other.com", "site.com", AdBlocker.T_IMAGE));
    }

    @Test public void exceptionAndImportant() throws Exception {
        AdBlocker.loadForTest("||ads.net^$third-party\n@@||ads.net/ok/\n||bad.net^$important\n@@||bad.net^\n");
        assertFalse(AdBlocker.shouldBlock("https://ads.net/ok/a.js", "ads.net", "site.com", AdBlocker.T_SCRIPT));
        assertTrue(AdBlocker.shouldBlock("https://ads.net/no/a.js", "ads.net", "site.com", AdBlocker.T_SCRIPT));
        assertTrue(AdBlocker.shouldBlock("https://bad.net/a.js", "bad.net", "site.com", AdBlocker.T_SCRIPT));
    }

    @Test public void badfilterDisablesRule() throws Exception {
        AdBlocker.loadForTest("||cdn.org/ads/$script\n||cdn.org/ads/$script,badfilter\n");
        assertFalse(AdBlocker.shouldBlock("https://cdn.org/ads/a.js", "cdn.org", "site.com", AdBlocker.T_SCRIPT));
    }

    @Test public void regexRule() throws Exception {
        AdBlocker.loadForTest("/^https?:\\/\\/[a-z]{8}\\.xyz\\/[0-9]+\\.js/$script\n");
        assertTrue(AdBlocker.shouldBlock("https://abcdefgh.xyz/123.js", "abcdefgh.xyz", "site.com", AdBlocker.T_SCRIPT));
        assertFalse(AdBlocker.shouldBlock("https://abc.xyz/123.js", "abc.xyz", "site.com", AdBlocker.T_SCRIPT));
    }

    @Test public void publicSuffixList() {
        assertEquals("b.github.io", Psl.registrable("a.b.github.io"));
        assertEquals("bbc.co.uk", Psl.registrable("news.bbc.co.uk"));
        assertEquals("example.com", Psl.registrable("www.example.com"));
        assertEquals("x.foo.ck", Psl.registrable("a.x.foo.ck"));
        assertEquals("www.ck", Psl.registrable("www.ck"));
        // github.io pages of different users are different sites
        assertTrue(AdBlocker.shouldBlockThirdParty("a.github.io", "b.github.io"));
    }

    @Test public void siteExceptionCoversSubdomains() throws Exception {
        AdBlocker.loadForTest("||ads.net^\n");
        AdBlocker.whitelist = new java.util.HashSet<>(Arrays.asList("site.com"));
        assertFalse(AdBlocker.shouldBlock("https://ads.net/a.js", "ads.net", "m.site.com", AdBlocker.T_SCRIPT));
        assertFalse(AdBlocker.shouldBlock("https://ads.net/a.js", "ads.net", "www.site.com", AdBlocker.T_SCRIPT));
        assertTrue(AdBlocker.shouldBlock("https://ads.net/a.js", "ads.net", "other.com", AdBlocker.T_SCRIPT));
    }
}
