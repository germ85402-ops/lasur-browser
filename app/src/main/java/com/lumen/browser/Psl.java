package com.lumen.browser;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * Public Suffix List (publicsuffix.org, downloaded at build time) to find the registrable domain:
 * "a.b.github.io" -> "b.github.io", "news.bbc.co.uk" -> "bbc.co.uk". Used to tell first- from third-party requests.
 */
final class Psl {
    private Psl() { }

    private static volatile HashSet<String> rules, wild, exc;

    static synchronized void init(Context c) {
        if (rules != null) return;
        HashSet<String> r = new HashSet<>(16000), w = new HashSet<>(), x = new HashSet<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getAssets().open("psl.dat"), "UTF-8"))) {
            String l;
            while ((l = in.readLine()) != null) add(l, r, w, x);
        } catch (Exception e) { android.util.Log.w("Lasur", "psl", e); }
        rules = r; wild = w; exc = x;
    }

    static void load(Iterable<String> lines) {
        HashSet<String> r = new HashSet<>(16000), w = new HashSet<>(), x = new HashSet<>();
        for (String l0 : lines) add(l0, r, w, x);
        rules = r; wild = w; exc = x;
    }

    static void add(String l, HashSet<String> r, HashSet<String> w, HashSet<String> x) {
        l = l.trim();
        if (l.isEmpty() || l.startsWith("//")) return;
        int sp = l.indexOf(' ');
        if (sp > 0) l = l.substring(0, sp);
        HashSet<String> target = r;
        if (l.startsWith("!")) { target = x; l = l.substring(1); }
        else if (l.startsWith("*.")) { target = w; l = l.substring(2); }
        try { l = java.net.IDN.toASCII(l, java.net.IDN.ALLOW_UNASSIGNED); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        if (!l.isEmpty()) target.add(l.toLowerCase());
    }

    /** Registrable domain (eTLD+1), the host itself if it is an IP / suffix, or null if the list is not loaded. */
    static String registrable(String host) {
        HashSet<String> r = rules, w = wild, x = exc;
        if (r == null || r.isEmpty() || host == null) return null;
        String h = host.toLowerCase();
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.indexOf(':') >= 0 || h.matches("[0-9.]+")) return h; // IP address
        String[] lab = h.split("\\.");
        int n = lab.length;
        for (int i = 0; i < n; i++) {
            String cand = join(lab, i);
            if (x.contains(cand)) return cand;                         // exception rule: cand itself is registrable
            boolean wildHit = i + 1 < n && w.contains(join(lab, i + 1));
            if (r.contains(cand) || wildHit) return i > 0 ? join(lab, i - 1) : h;
        }
        return n >= 2 ? join(lab, n - 2) : h;                          // default rule "*"
    }

    private static String join(String[] lab, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lab.length; i++) { if (sb.length() > 0) sb.append('.'); sb.append(lab[i]); }
        return sb.toString();
    }
}
