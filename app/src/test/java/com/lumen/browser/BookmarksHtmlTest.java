package com.lumen.browser;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import org.junit.Test;

public class BookmarksHtmlTest {
    @Test public void roundTrip() {
        ArrayList<Store.Item> items = new ArrayList<>();
        Store.Item a = new Store.Item("Wiki & <co>", "https://ru.wikipedia.org/?a=1&b=2", 1700000000000L);
        Store.Item b = new Store.Item("GitHub", "https://github.com", 0);
        b.f = "Dev";
        items.add(a); items.add(b);
        ArrayList<String> folders = new ArrayList<>();
        folders.add("Dev");
        ArrayList<Store.Item> got = Lists.parseBookmarksHtml(Lists.exportBookmarksHtml(items, folders));
        assertEquals(2, got.size());
        Store.Item g0 = got.get(0), g1 = got.get(1);
        assertEquals("GitHub", g0.t);
        assertEquals("Dev", g0.f);
        assertEquals("Wiki & <co>", g1.t);
        assertEquals("https://ru.wikipedia.org/?a=1&b=2", g1.u);
        assertEquals("", g1.f);
        assertEquals(1700000000000L, g1.d);
    }

    @Test public void chromeExportWithNestedFolders() {
        String html = "<DL><p>\n<DT><H3 PERSONAL_TOOLBAR_FOLDER=\"true\">Bookmarks bar</H3>\n<DL><p>\n"
                + "<DT><A HREF=\"https://a.example/\">A</A>\n<DT><H3>News</H3>\n<DL><p>\n<DT><A HREF=\"https://n.example/\">N &#8212; 1</A>\n</DL><p>\n"
                + "<DT><A HREF=\"javascript:alert(1)\">bad</A>\n</DL><p>\n</DL><p>";
        ArrayList<Store.Item> got = Lists.parseBookmarksHtml(html);
        assertEquals(2, got.size());
        assertEquals("Bookmarks bar", got.get(0).f);
        assertEquals("News", got.get(1).f);
        assertEquals("N \u2014 1", got.get(1).t);
    }
}
