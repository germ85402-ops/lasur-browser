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

    // request types ($script, $image, …); 0 in Rule.types means "any type"
    static final int T_SCRIPT = 1, T_IMAGE = 2, T_CSS = 4, T_XHR = 8, T_FRAME = 16, T_MEDIA = 32, T_FONT = 64,
            T_OBJECT = 128, T_PING = 256, T_WS = 512, T_OTHER = 1024, T_ALL = 2047;

    static final class Rule {
        String pat; boolean hostAnchor, startAnchor, endAnchor;
        int party; // 0 any, 1 third-party only, 2 first-party only
        String[] inc, exc;
        int types;          // allowed request types, 0 = all
        boolean important;  // $important: wins over exception rules
        java.util.regex.Pattern re; // /regex/ rules
        String lit;         // literal every matching URL must contain (cheap pre-check), or null

        /** Identity used by $badfilter. */
        String key() {
            return (re != null ? "/" + re.pattern() + "/" : pat) + "|" + hostAnchor + startAnchor + endAnchor + "|" + party + "|" + types + "|"
                    + (inc == null ? "" : String.join("|", inc)) + "|" + (exc == null ? "" : String.join("|", exc));
        }
    }

    static final class Engine {
        final HashSet<String> filterHosts = new HashSet<>(); // unconditional ABP rules, including first-party
        final HashSet<String> hosts = new HashSet<>(200000), allowHosts = new HashSet<>(), docAllow = new HashSet<>(), noGenericHide = new HashSet<>();
        final HashMap<String, ArrayList<Rule>> block = new HashMap<>(), allow = new HashMap<>();
        final ArrayList<Rule> blockAny = new ArrayList<>(), allowAny = new ArrayList<>();
        final ArrayList<String> badfilters = new ArrayList<>();
        final ArrayList<String> generic = new ArrayList<>();
        final HashSet<String> genericExc = new HashSet<>();
        final HashMap<String, ArrayList<String>> domainCss = new HashMap<>();
        final HashMap<String, HashSet<String>> domainExc = new HashMap<>();
        String genericCss = "";
        int netRules, cssRules;
    }

    static volatile Engine eng = new Engine();
    static volatile Set<String> whitelist = new HashSet<>();
    static volatile boolean enabled = true;
    static final AtomicLong totalBlocked = new AtomicLong();
    private static final LruCache<String, String> cssCache = new LruCache<>(40);
    private static boolean started;

    static synchronized void init(Context c) {
        if (started) return;
        started = true;
        final Context app = c.getApplicationContext();
        Thread th = new Thread(() -> reload(app), "adblock-load");
        th.setPriority(Thread.NORM_PRIORITY - 1);
        th.start();
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
            } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        }
        try { parse(c.getAssets().open("extra_hosts.txt"), e, true); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        applyBadfilters(e);
        Psl.init(c);
        e.generic.removeAll(e.genericExc);
        e.genericCss = buildCss(e.generic, null);
        eng = e;
        synchronized (cssCache) { cssCache.evictAll(); }
    }

    /** Removes the rules disabled by "$badfilter" entries. */
    static void applyBadfilters(Engine e) {
        if (e.badfilters.isEmpty()) return;
        HashSet<String> keys = new HashSet<>();
        for (String bf : e.badfilters) {
            Engine tmp = new Engine();
            try { parseFilter(bf, tmp); } catch (Exception ignored) { continue; }
            e.hosts.removeAll(tmp.hosts);
            e.filterHosts.removeAll(tmp.filterHosts);
            e.allowHosts.removeAll(tmp.allowHosts);
            for (ArrayList<Rule> l : tmp.block.values()) for (Rule r : l) keys.add(r.key());
            for (ArrayList<Rule> l : tmp.allow.values()) for (Rule r : l) keys.add(r.key());
            for (Rule r : tmp.blockAny) keys.add(r.key());
            for (Rule r : tmp.allowAny) keys.add(r.key());
        }
        if (keys.isEmpty()) return;
        for (ArrayList<Rule> l : e.block.values()) l.removeIf(r -> keys.contains(r.key()));
        for (ArrayList<Rule> l : e.allow.values()) l.removeIf(r -> keys.contains(r.key()));
        e.blockAny.removeIf(r -> keys.contains(r.key()));
        e.allowAny.removeIf(r -> keys.contains(r.key()));
    }

    static int typeBit(String o) {
        switch (o) {
            case "script": return T_SCRIPT;
            case "image": return T_IMAGE;
            case "stylesheet": case "css": return T_CSS;
            case "xmlhttprequest": case "xhr": return T_XHR;
            case "subdocument": case "frame": return T_FRAME;
            case "media": return T_MEDIA;
            case "font": return T_FONT;
            case "object": case "object-subrequest": return T_OBJECT;
            case "ping": case "beacon": return T_PING;
            case "websocket": return T_WS;
            case "other": return T_OTHER;
            case "all": return T_ALL;
            default: return 0;
        }
    }

    /** Best guess of the request type from fetch metadata, the Accept header and the file extension. */
    static int typeOf(android.webkit.WebResourceRequest r) {
        java.util.Map<String, String> h = r.getRequestHeaders();
        String dest = null, accept = null;
        if (h != null) for (java.util.Map.Entry<String, String> e : h.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
            if (k.equals("sec-fetch-dest")) dest = e.getValue();
            else if (k.equals("accept")) accept = e.getValue();
        }
        if (dest != null) {
            switch (dest) {
                case "script": case "worker": case "sharedworker": case "serviceworker": return T_SCRIPT;
                case "image": return T_IMAGE;
                case "style": return T_CSS;
                case "iframe": case "frame": case "document": return T_FRAME;
                case "audio": case "video": case "track": return T_MEDIA;
                case "font": return T_FONT;
                case "object": case "embed": return T_OBJECT;
                case "empty": return T_XHR;
            }
        }
        String path = r.getUrl().getPath();
        path = path == null ? "" : path.toLowerCase();
        if (path.endsWith(".js") || path.endsWith(".mjs")) return T_SCRIPT;
        if (path.endsWith(".css")) return T_CSS;
        if (path.matches(".*\\.(png|jpe?g|gif|webp|avif|svg|ico|bmp)$")) return T_IMAGE;
        if (path.matches(".*\\.(woff2?|ttf|otf|eot)$")) return T_FONT;
        if (path.matches(".*\\.(mp4|webm|m3u8|ts|m4s|mp3|aac|ogg|mpd)$")) return T_MEDIA;
        if (accept != null) {
            if (accept.startsWith("text/css")) return T_CSS;
            if (accept.startsWith("image/")) return T_IMAGE;
            if (accept.startsWith("text/html")) return T_FRAME;
        }
        String s = r.getUrl().getScheme();
        if ("ws".equals(s) || "wss".equals(s)) return T_WS;
        return T_XHR | T_OTHER; // most likely fetch()/XHR
    }

    /** Builds the engine from filter text (unit tests). */
    static void loadForTest(String filters) throws Exception {
        Engine e = new Engine();
        parse(new java.io.ByteArrayInputStream(filters.getBytes("UTF-8")), e, false);
        applyBadfilters(e);
        e.genericCss = buildCss(e.generic, null);
        eng = e;
        enabled = true;
        whitelist = new HashSet<>();
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
            try { parseFilter(line, e); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
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
        Rule rule = new Rule();
        boolean docOpt = false, hideOpt = false;
        int incTypes = 0, excTypes = 0;
        if (opts != null) {
            String lo = opts.toLowerCase();
            if ((","+lo+",").contains(",badfilter,")) {
                // keep the filter text without "badfilter" and disable the matching rule after all lists are read
                StringBuilder o2 = new StringBuilder();
                for (String o : opts.split(",")) if (!o.trim().equalsIgnoreCase("badfilter")) { if (o2.length() > 0) o2.append(','); o2.append(o); }
                e.badfilters.add((allow ? "@@" : "") + p + (o2.length() > 0 ? "$" + o2 : ""));
                return;
            }
            for (String o : lo.split(",")) {
                o = o.trim();
                boolean neg = o.startsWith("~");
                int tb = typeBit(neg ? o.substring(1) : o);
                if (tb != 0) { if (neg) excTypes |= tb; else incTypes |= tb; continue; }
                if (o.equals("important")) { rule.important = true; continue; }
                if (o.equals("match-case") || o.isEmpty()) continue;
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
                        || o.startsWith("to=") || o.startsWith("denyallow") || o.equals("cname") || o.equals("inline-script") || o.equals("inline-font")
                        || o.equals("badfilter") || o.equals("genericblock") || o.equals("webrtc") || o.startsWith("sitekey")) return;
            }
            if (incTypes != 0) rule.types = incTypes & ~excTypes;
            else if (excTypes != 0) rule.types = T_ALL & ~excTypes;
            if ((incTypes != 0 || excTypes != 0) && rule.types == 0) return;
        }
        if (p.startsWith("/") && p.endsWith("/") && p.length() > 2) {
            if (docOpt || hideOpt) return;
            ArrayList<Rule> any = allow ? e.allowAny : e.blockAny;
            if (any.size() >= 4000) return;
            try { rule.re = java.util.regex.Pattern.compile(p.substring(1, p.length() - 1), java.util.regex.Pattern.CASE_INSENSITIVE); }
            catch (Exception ex) { return; }
            rule.lit = regexLiteral(p.substring(1, p.length() - 1));
            any.add(rule);
            e.netRules++;
            return;
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
        if (sh != null && rule.inc == null && rule.exc == null && rule.party == 0 && rule.types == 0 && !rule.important) {
            (allow ? e.allowHosts : e.filterHosts).add(sh);
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
        rule.lit = patLiteral(p);
        String tok = bestToken(p, rule.hostAnchor || rule.startAnchor, rule.endAnchor);
        HashMap<String, ArrayList<Rule>> map = allow ? e.allow : e.block;
        if (tok == null) {
            if (allow) e.allowAny.add(rule); else if (p.length() >= 5 && e.blockAny.size() < 4000) e.blockAny.add(rule); else return;
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

    /** Longest piece of a filter pattern without wildcards / separators. */
    static String patLiteral(String p) {
        String best = null;
        for (String part : p.split("[*^|]")) if (part.length() >= 3 && (best == null || part.length() > best.length())) best = part;
        return best;
    }

    /** Longest literal run a regex certainly requires at top level (null if unsure, e.g. with alternation). */
    static String regexLiteral(String re) {
        int depth0 = 0;
        boolean inClass = false;
        for (int k = 0; k < re.length(); k++) {
            char x = re.charAt(k);
            if (x == '\\') { k++; continue; }
            if (inClass) { if (x == ']') inClass = false; continue; }
            if (x == '[') inClass = true;
            else if (x == '(') depth0++;
            else if (x == ')') depth0--;
            else if (x == '|' && depth0 <= 0) return null; // top-level alternation
        }
        String best = null;
        StringBuilder run = new StringBuilder();
        int n = re.length(), i = 0;
        while (i <= n) {
            char lit = 0;
            int next;
            if (i == n) next = n + 1;
            else {
                char c = re.charAt(i);
                if (c == '\\' && i + 1 < n) { char d = re.charAt(i + 1); if (!Character.isLetterOrDigit(d)) lit = d; next = i + 2; }
                else if (c == '[') { int j = re.indexOf(']', i + 2); next = j < 0 ? n + 1 : j + 1; }
                else if (c == '{') { int j = re.indexOf('}', i + 1); next = j < 0 ? n + 1 : j + 1; }
                else if (c == '(') {
                    int depth = 0, j = i;
                    for (; j < n; j++) {
                        char x = re.charAt(j);
                        if (x == '\\') { j++; continue; }
                        if (x == '(') depth++; else if (x == ')' && --depth == 0) break;
                    }
                    next = j + 1;
                }
                else if (".*+?^$)]}".indexOf(c) >= 0) next = i + 1;
                else { lit = c; next = i + 1; }
            }
            char q = next < n ? re.charAt(next) : 0;
            boolean optional = q == '?' || q == '*' || q == '{';
            if (lit != 0 && !optional) {
                run.append(Character.toLowerCase(lit));
                if (q != '+') { i = next; continue; }
            }
            if (run.length() >= 3 && (best == null || run.length() > best.length())) best = run.toString();
            run.setLength(0);
            i = next;
        }
        return best;
    }

    static boolean match(Rule r, String u, int hostStart, int hostEnd) {
        if (r.lit != null && u.indexOf(r.lit) < 0) return false;
        if (r.re != null) return r.re.matcher(u).find();
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
        String reg = Psl.registrable(h);
        if (reg != null) return reg;
        String[] p = h.split("\\.");
        if (p.length <= 2) return h;
        String sld = p[p.length - 2];
        int take = (p[p.length - 1].length() == 2 && (sld.length() <= 3 || sld.equals("msk") || sld.equals("spb"))) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = p.length - take; i < p.length; i++) { if (sb.length() > 0) sb.append('.'); sb.append(p[i]); }
        return sb.toString();
    }

    static boolean domainIn(String host, String d) { return host != null && (host.equals(d) || host.endsWith("." + d)); }

    static boolean opts(Rule r, boolean third, String pageHost, int type) {
        if (r.types != 0 && (r.types & type) == 0) return false;
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

    static boolean isAd(String host) { return host != null && (hostIn(eng.hosts, host.toLowerCase()) || hostIn(eng.filterHosts, host.toLowerCase())); }

    /** True if a request to host from pageHost counts as third-party (different registrable domains). */
    static boolean shouldBlockThirdParty(String host, String pageHost) { return !base(host).equals(base(pageHost)); }

    /** Site key for user exceptions: lower-case host without "www." / "m.". */
    static String siteKey(String host) {
        if (host == null) return null;
        String h = host.toLowerCase();
        if (h.startsWith("www.")) h = h.substring(4);
        else if (h.startsWith("m.")) h = h.substring(2);
        return h;
    }

    /** True if blocking is off for this page: user exception (site and all its subdomains) or $document rule. */
    static boolean siteAllowed(String pageHost) {
        if (pageHost == null) return false;
        String ph = pageHost.toLowerCase();
        Set<String> w = whitelist;
        return (!w.isEmpty() && (w.contains(ph) || hostIn(w, ph) || w.contains(siteKey(ph)))) || hostIn(eng.docAllow, ph);
    }

    /** Returns the first matching rule, or null. */
    static Rule anyRule(HashMap<String, ArrayList<Rule>> map, ArrayList<Rule> any, String u, int hs, int he, boolean third, String ph, int type) {
        int n = u.length(), i = 0;
        Rule found = null;
        while (i < n) {
            if (!tokChar(u.charAt(i))) { i++; continue; }
            int s = i;
            while (i < n && tokChar(u.charAt(i))) i++;
            if (i - s < 3) continue;
            ArrayList<Rule> l = map.get(u.substring(s, i));
            if (l != null) for (Rule r : l) if (opts(r, third, ph, type) && match(r, u, hs, he)) { if (r.important) return r; if (found == null) found = r; }
        }
        if (found != null) return found;
        for (Rule r : any) if (opts(r, third, ph, type) && match(r, u, hs, he)) return r;
        return null;
    }

    static boolean shouldBlock(String url, String host, String pageHost) { return shouldBlock(url, host, pageHost, T_FRAME); }

    /** Main entry for sub-resource requests. */
    static boolean shouldBlock(String url, String host, String pageHost, int type) {
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
        Rule br = anyRule(e.block, e.blockAny, u, hs, he, third, ph, type);
        boolean important = br != null && br.important;
        if (!important && hostIn(e.allowHosts, h)) return false;
        boolean blocked = br != null || hostIn(e.filterHosts, h) || (third && hostIn(e.hosts, h));
        if (!blocked) return false;
        if (!important && anyRule(e.allow, e.allowAny, u, hs, he, third, ph, type) != null) return false;
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

    static synchronized void update(Context c) throws Exception {
        int ok = 0;
        Exception last = null;
        for (String[] l : LISTS) {
            File tmp = new File(c.getFilesDir(), l[0].replace('/', '_') + ".tmp");
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(l[1]).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(60000);
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Lasur)");
                int code = con.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                if (tmp.length() < 10000) throw new Exception(L.t("Пустой список"));
                File f = new File(c.getFilesDir(), l[0].replace('/', '_'));
                if (!tmp.renameTo(f)) throw new Exception(L.t("Не удалось сохранить"));
                ok++;
            } catch (Exception ex) { last = ex; tmp.delete(); }
        }
        if (ok == 0 && last != null) throw last;
        reload(c);
    }
}
