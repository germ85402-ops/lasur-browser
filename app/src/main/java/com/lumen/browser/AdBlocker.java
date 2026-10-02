package com.lumen.browser;

import android.content.Context;
import android.util.LruCache;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ad-block engine: hosts lists + Adblock Plus / uBlock compatible filter lists
 * (network rules with tokens, exceptions, $third-party / $domain options and cosmetic ## rules).
 */
final class AdBlocker {
    static final String[][] LISTS = {
            {"hosts.txt", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"},
            {"filters/easylist.txt", "https://easylist.to/easylist/easylist.txt"},
            {"filters/advblock.txt", "https://easylist-downloads.adblockplus.org/advblock.txt"},
            {"filters/adguard_ru.txt", "https://filters.adtidy.org/extension/ublock/filters/1.txt"},
    };
    static final String BASE_CSS_SELECTORS = ".adsbygoogle,ins.adsbygoogle,[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],[data-ad-slot],[id^=\"yandex_rtb\"],"
            + "[id^=\"adfox\"],.taboola,[id^=\"taboola\"],.OUTBRAIN,iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication\"]";

    static final class Rule {
        String pat; boolean hostAnchor, startAnchor, endAnchor;
        int party; // 0 any, 1 third-party only, 2 first-party only
        String[] inc, exc;
    }

    static final class Engine {
        final HashSet<String> hosts = new HashSet<>(200000), allowHosts = new HashSet<>(), docAllow = new HashSet<>(), noGenericHide = new HashSet<>();
        final HashMap<String, ArrayList<Rule>> block = new HashMap<>(), allow = new HashMap<>();
        final ArrayList<Rule> blockAny = new ArrayList<>(), allowAny = new ArrayList<>();
        final ArrayList<String> generic = new ArrayList<>();
        final HashSet<String> genericExc = new HashSet<>();
        final HashMap<String, ArrayList<String>> domainCss = new HashMap<>();
        final HashMap<String, HashSet<String>> domainExc = new HashMap<>();
        String genericCss = "";
        int netRules, cssRules;
    }

    private static volatile Engine eng = new Engine();
    static volatile Set<String> whitelist = new HashSet<>();
    static volatile boolean enabled = true;
    static final AtomicLong totalBlocked = new AtomicLong();
    private static final LruCache<String, String> cssCache = new LruCache<>(40);
    private static boolean started;

    static synchronized void init(Context c) {
        if (started) return;
        started = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> reload(app), "adblock-load").start();
    }

    static int size() { Engine e = eng; return e.hosts.size() + e.netRules + e.cssRules; }
    static String stats() { Engine e = eng; return L.t("Домены: ") + e.hosts.size() + L.t(" · правила: ") + e.netRules + L.t(" · скрытие: ") + e.cssRules; }

    static void reload(Context c) {
        Engine e = new Engine();
        for (String[] l : LISTS) {
            try {
                File f = new File(c.getFilesDir(), l[0].replace('/', '_'));
                InputStream in = f.exists() && f.length() > 10000 ? new FileInputStream(f) : c.getAssets().open(l[0]);
                parse(in, e, l[0].equals("hosts.txt"));
            } catch (Exception ignored) { }
        }
        try { parse(c.getAssets().open("extra_hosts.txt"), e, true); } catch (Exception ignored) { }
        e.generic.removeAll(e.genericExc);
        e.genericCss = buildCss(e.generic, null);
        eng = e;
        synchronized (cssCache) { cssCache.evictAll(); }
    }

    static String buildCss(ArrayList<String> sels, Set<String> skip) {
        StringBuilder sb = new StringBuilder(sels.size() * 40);
        int n = 0;
        for (String s : sels) {
            if (skip != null && skip.contains(s)) continue;
            if (n > 0) sb.append(',');
            sb.append(s);
            if (++n == 20) { sb.append("{display:none!important}\n"); n = 0; }
        }
        if (n > 0) sb.append("{display:none!important}\n");
        return sb.toString();
    }

    private static void parse(InputStream in, Engine e, boolean hostsFormat) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536);
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (hostsFormat) {
                int h = line.indexOf('#');
                if (h >= 0) line = line.substring(0, h).trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                String d = (parts.length >= 2 ? parts[1] : parts[0]).toLowerCase();
                if (d.contains("localhost") || d.equals("0.0.0.0") || !d.contains(".")) continue;
                e.hosts.add(d);
                continue;
            }
            char c0 = line.charAt(0);
            if (c0 == '!' || c0 == '[') continue;
            try { parseFilter(line, e); } catch (Exception ignored) { }
        }
        r.close();
    }

    static boolean badSelector(String s) {
        return s.startsWith("+js") || s.startsWith("^") || s.contains(":-abp-") || s.contains(":has-text") || s.contains(":xpath")
                || s.contains(":matches-") || s.contains(":style(") || s.contains(":remove") || s.contains(":upward") || s.contains(":watch-attr")
                || s.contains(":min-text-length") || s.contains(":contains(") || s.contains(":others(") || s.contains(":matches-path") || s.isEmpty();
    }

    private static void parseFilter(String line, Engine e) {
        int hi;
        if ((hi = line.indexOf("#@#")) >= 0) {
            String doms = line.substring(0, hi), sel = line.substring(hi + 3);
            if (doms.isEmpty()) e.genericExc.add(sel);
            else for (String d : doms.split(",")) {
                d = d.trim().toLowerCase();
                if (d.isEmpty() || d.startsWith("~")) continue;
                HashSet<String> s = e.domainExc.get(d);
                if (s == null) e.domainExc.put(d, s = new HashSet<>());
                s.add(sel);
            }
            return;
        }
        if (line.contains("#$#") || line.contains("#%#") || line.contains("#?#") || line.contains("$$") || line.contains("#@$#")) return;
        if ((hi = line.indexOf("##")) >= 0) {
            String doms = line.substring(0, hi), sel = line.substring(hi + 2);
            if (badSelector(sel)) return;
            if (doms.isEmpty()) { e.generic.add(sel); e.cssRules++; return; }
            for (String d : doms.split(",")) {
                d = d.trim().toLowerCase();
                if (d.isEmpty() || d.startsWith("~") || d.contains("*")) continue;
                ArrayList<String> l = e.domainCss.get(d);
                if (l == null) e.domainCss.put(d, l = new ArrayList<>());
                l.add(sel);
                e.cssRules++;
            }
            return;
        }
        boolean allow = line.startsWith("@@");
        String p = allow ? line.substring(2) : line;
        String opts = null;
        int di = p.lastIndexOf('$');
        if (di >= 0 && !(p.startsWith("/") && p.endsWith("/"))) { opts = p.substring(di + 1); p = p.substring(0, di); }
        if (p.startsWith("/") && p.endsWith("/") && p.length() > 1) return; // regex rules are skipped
        Rule rule = new Rule();
        boolean docOpt = false, hideOpt = false;
        if (opts != null) {
            for (String o : opts.toLowerCase().split(",")) {
                o = o.trim();
                if (o.equals("third-party") || o.equals("3p")) rule.party = 1;
                else if (o.equals("~third-party") || o.equals("first-party") || o.equals("1p")) rule.party = 2;
                else if (o.startsWith("domain=") || o.startsWith("from=")) {
                    ArrayList<String> inc = new ArrayList<>(), exc = new ArrayList<>();
                    for (String d : o.substring(o.indexOf('=') + 1).split("\\|")) {
                        if (d.startsWith("~")) exc.add(d.substring(1)); else if (!d.isEmpty()) inc.add(d);
                    }
                    if (!inc.isEmpty()) rule.inc = inc.toArray(new String[0]);
                    if (!exc.isEmpty()) rule.exc = exc.toArray(new String[0]);
                } else if (o.equals("document") || o.equals("doc")) docOpt = true;
                else if (o.equals("elemhide") || o.equals("generichide") || o.equals("ehide") || o.equals("ghide")) hideOpt = true;
                else if (o.equals("popup") || o.startsWith("csp") || o.startsWith("redirect") || o.startsWith("removeparam") || o.startsWith("rewrite")
                        || o.startsWith("replace") || o.equals("badfilter") || o.startsWith("header") || o.startsWith("permissions")
                        || o.equals("specifichide") || o.equals("shide") || o.startsWith("urltransform") || o.startsWith("method")
                        || o.startsWith("to=") || o.startsWith("denyallow") || o.equals("cname") || o.equals("inline-script") || o.equals("inline-font")) return;
            }
        }
        p = p.toLowerCase();
        if (allow && (docOpt || hideOpt)) {
            String h = simpleHost(p);
            if (h != null) (docOpt ? e.docAllow : e.noGenericHide).add(h);
            return;
        }
        if (docOpt || hideOpt) return;
        if (p.isEmpty() || p.equals("*") || p.equals("|") || p.equals("||")) return;
        String sh = simpleHost(p);
        if (sh != null && rule.inc == null && rule.exc == null && rule.party != 2) {
            (allow ? e.allowHosts : e.hosts).add(sh);
            e.netRules++;
            return;
        }
        if (p.startsWith("||")) { rule.hostAnchor = true; p = p.substring(2); }
        else if (p.startsWith("|")) { rule.startAnchor = true; p = p.substring(1); }
        if (p.endsWith("|")) { rule.endAnchor = true; p = p.substring(0, p.length() - 1); }
        while (p.startsWith("*") && !rule.hostAnchor && !rule.startAnchor) p = p.substring(1);
        while (p.endsWith("*") && !rule.endAnchor) p = p.substring(0, p.length() - 1);
        if (p.length() < 3) return;
        rule.pat = p;
        String tok = bestToken(p, rule.hostAnchor || rule.startAnchor, rule.endAnchor);
        HashMap<String, ArrayList<Rule>> map = allow ? e.allow : e.block;
        if (tok == null) {
            if (allow) e.allowAny.add(rule); else if (p.length() >= 5 && e.blockAny.size() < 400) e.blockAny.add(rule); else return;
        } else {
            ArrayList<Rule> l = map.get(tok);
            if (l == null) map.put(tok, l = new ArrayList<>(2));
            l.add(rule);
        }
        e.netRules++;
    }

    /** returns host if pattern is "||host^" or "||host" style. */
    static String simpleHost(String p) {
        if (!p.startsWith("||")) return null;
        String h = p.substring(2);
        if (h.endsWith("^")) h = h.substring(0, h.length() - 1);
        else if (h.endsWith("^|")) h = h.substring(0, h.length() - 2);
        if (h.isEmpty() || !h.contains(".")) return null;
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_')) return null;
        }
        return h;
    }

    static boolean tokChar(char c) { return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '%'; }

    static String bestToken(String p, boolean startAnch, boolean endAnch) {
        String best = null;
        int n = p.length(), i = 0;
        while (i < n) {
            if (!tokChar(p.charAt(i))) { i++; continue; }
            int s = i;
            while (i < n && tokChar(p.charAt(i))) i++;
            boolean leftOk = s > 0 ? p.charAt(s - 1) != '*' : startAnch;
            boolean rightOk = i < n ? p.charAt(i) != '*' : endAnch;
            int len = i - s;
            if (leftOk && rightOk && len >= 3) {
                String t = p.substring(s, i);
                if (t.equals("http") || t.equals("https") || t.equals("www") || t.equals("com")) continue;
                if (best == null || len > best.length()) best = t;
            }
        }
        return best;
    }

    static boolean sep(char c) { return !(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '%'); }

    static boolean matchAt(String u, int i, String p, int j, boolean end) {
        int un = u.length(), pn = p.length();
        while (j < pn) {
            char c = p.charAt(j);
            if (c == '*') {
                j++;
                if (j == pn) return true;
                for (int k = i; k <= un; k++) if (matchAt(u, k, p, j, end)) return true;
                return false;
            }
            if (c == '^') {
                if (i == un) { j++; continue; }
                if (sep(u.charAt(i))) { i++; j++; continue; }
                return false;
            }
            if (i >= un || u.charAt(i) != c) return false;
            i++; j++;
        }
        return !end || i == un;
    }

    static boolean match(Rule r, String u, int hostStart, int hostEnd) {
        if (r.hostAnchor) {
            for (int i = hostStart; i < hostEnd; i++) {
                if (i == hostStart || u.charAt(i - 1) == '.') if (matchAt(u, i, r.pat, 0, r.endAnchor)) return true;
            }
            return false;
        }
        if (r.startAnchor) return matchAt(u, 0, r.pat, 0, r.endAnchor);
        char f = r.pat.charAt(0);
        if (f == '*' || f == '^') {
            for (int i = 0; i <= u.length(); i++) if (matchAt(u, i, r.pat, 0, r.endAnchor)) return true;
            return false;
        }
        int i = u.indexOf(f);
        while (i >= 0) {
            if (matchAt(u, i, r.pat, 0, r.endAnchor)) return true;
            i = u.indexOf(f, i + 1);
        }
        return false;
    }

    static String base(String h) {
        if (h == null) return "";
        String[] p = h.split("\\.");
        if (p.length <= 2) return h;
        String sld = p[p.length - 2];
        int take = (p[p.length - 1].length() == 2 && (sld.length() <= 3 || sld.equals("msk") || sld.equals("spb"))) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = p.length - take; i < p.length; i++) { if (sb.length() > 0) sb.append('.'); sb.append(p[i]); }
        return sb.toString();
    }

    static boolean domainIn(String host, String d) { return host != null && (host.equals(d) || host.endsWith("." + d)); }

    static boolean opts(Rule r, boolean third, String pageHost) {
        if (r.party == 1 && !third) return false;
        if (r.party == 2 && third) return false;
        if (r.exc != null) for (String d : r.exc) if (domainIn(pageHost, d)) return false;
        if (r.inc != null) {
            for (String d : r.inc) if (domainIn(pageHost, d)) return true;
            return false;
        }
        return true;
    }

    static boolean hostIn(Set<String> s, String host) {
        String h = host;
        while (true) {
            if (s.contains(h)) return true;
            int i = h.indexOf('.');
            if (i < 0) return false;
            h = h.substring(i + 1);
            if (h.indexOf('.') < 0) return false;
        }
    }

    static boolean isAd(String host) { return host != null && hostIn(eng.hosts, host.toLowerCase()); }

    static boolean siteAllowed(String pageHost) {
        if (pageHost == null) return false;
        String ph = pageHost.toLowerCase();
        return whitelist.contains(ph) || hostIn(eng.docAllow, ph);
    }

    static boolean anyRule(HashMap<String, ArrayList<Rule>> map, ArrayList<Rule> any, String u, int hs, int he, boolean third, String ph) {
        int n = u.length(), i = 0;
        while (i < n) {
            if (!tokChar(u.charAt(i))) { i++; continue; }
            int s = i;
            while (i < n && tokChar(u.charAt(i))) i++;
            if (i - s < 3) continue;
            ArrayList<Rule> l = map.get(u.substring(s, i));
            if (l != null) for (Rule r : l) if (opts(r, third, ph) && match(r, u, hs, he)) return true;
        }
        for (Rule r : any) if (opts(r, third, ph) && match(r, u, hs, he)) return true;
        return false;
    }

    /** Main entry for sub-resource requests. */
    static boolean shouldBlock(String url, String host, String pageHost) {
        if (!enabled || host == null || url == null) return false;
        if (siteAllowed(pageHost)) return false;
        Engine e = eng;
        String h = host.toLowerCase(), ph = pageHost == null ? null : pageHost.toLowerCase();
        boolean third = ph == null || !base(h).equals(base(ph));
        String u = url.toLowerCase();
        int hs = u.indexOf("://");
        hs = hs < 0 ? 0 : hs + 3;
        int he = hs;
        while (he < u.length() && u.charAt(he) != '/' && u.charAt(he) != '?' && u.charAt(he) != ':' && u.charAt(he) != '#') he++;
        if (hostIn(e.allowHosts, h)) return false;
        boolean blocked = (third && hostIn(e.hosts, h)) || anyRule(e.block, e.blockAny, u, hs, he, third, ph);
        if (!blocked) return false;
        if (anyRule(e.allow, e.allowAny, u, hs, he, third, ph)) return false;
        totalBlocked.incrementAndGet();
        return true;
    }

    /** Cosmetic CSS for a page host. */
    static String cssFor(String host) {
        if (!enabled || host == null) return "";
        String h = host.toLowerCase();
        if (siteAllowed(h)) return "";
        synchronized (cssCache) { String c = cssCache.get(h); if (c != null) return c; }
        Engine e = eng;
        HashSet<String> exc = new HashSet<>();
        ArrayList<String> spec = new ArrayList<>();
        String d = h;
        while (true) {
            HashSet<String> x = e.domainExc.get(d);
            if (x != null) exc.addAll(x);
            ArrayList<String> l = e.domainCss.get(d);
            if (l != null) spec.addAll(l);
            int i = d.indexOf('.');
            if (i < 0) break;
            d = d.substring(i + 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(BASE_CSS_SELECTORS).append("{display:none!important}\n");
        boolean noGeneric = hostIn(e.noGenericHide, h);
        if (!noGeneric) {
            if (exc.isEmpty()) sb.append(e.genericCss);
            else sb.append(buildCss(e.generic, exc));
        }
        sb.append(buildCss(spec, exc));
        String css = sb.toString();
        synchronized (cssCache) { cssCache.put(h, css); }
        return css;
    }

    static void update(Context c) throws Exception {
        int ok = 0;
        Exception last = null;
        for (String[] l : LISTS) {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(l[1]).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(60000);
                File tmp = new File(c.getFilesDir(), "dl.tmp");
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                if (tmp.length() < 10000) throw new Exception(L.t("Пустой список"));
                File f = new File(c.getFilesDir(), l[0].replace('/', '_'));
                if (!tmp.renameTo(f)) throw new Exception(L.t("Не удалось сохранить"));
                ok++;
            } catch (Exception ex) { last = ex; }
        }
        if (ok == 0 && last != null) throw last;
        reload(c);
    }
}
