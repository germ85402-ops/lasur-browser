package com.lumen.browser;

import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Built-in password manager. Passwords are encrypted with an AES-GCM key kept in the Android Keystore. */
final class Passwords {
    static final class Cred {
        String site, user, enc; long time;
        Cred(String site, String user, String enc, long time) { this.site = site; this.user = user; this.enc = enc; this.time = time; }
    }

    private static final String ALIAS = "lumen_passwords";
    final SharedPreferences p;
    final ArrayList<Cred> list = new ArrayList<>();

    Passwords(SharedPreferences p) {
        this.p = p;
        try {
            JSONArray a = new JSONArray(p.getString("pw", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Cred(o.getString("s"), o.optString("u"), o.getString("p"), o.optLong("t")));
            }
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
    }

    /**
     * Normalized site key: host without "www." / "m." (and port, if any).
     * HTTPS sites use the bare host (compatible with older saved entries); plain-HTTP sites get an
     * "http://" prefix so a password saved on a secure page is never offered on an insecure one.
     */
    static String site(String url) {
        try {
            Uri u = Uri.parse(url);
            String s = u.getScheme();
            if (!"https".equals(s) && !"http".equals(s)) return null;
            String h = u.getHost();
            if (h == null) return null;
            h = h.toLowerCase();
            if (h.startsWith("www.")) h = h.substring(4);
            else if (h.startsWith("m.")) h = h.substring(2);
            String k = u.getPort() > 0 ? h + ":" + u.getPort() : h;
            return "http".equals(s) ? "http://" + k : k;
        } catch (Exception e) { return null; }
    }

    ArrayList<Cred> forSite(String site) {
        ArrayList<Cred> r = new ArrayList<>();
        if (site == null) return r;
        for (Cred c : list) if (c.site.equals(site)) r.add(c);
        return r;
    }

    Cred find(String site, String user) {
        for (Cred c : list) if (c.site.equals(site) && c.user.equals(user)) return c;
        return null;
    }

    void put(String site, String user, String pass) {
        String enc = encrypt(pass);
        if (enc == null) return;
        Cred c = find(site, user);
        if (c != null) { c.enc = enc; c.time = System.currentTimeMillis(); }
        else list.add(0, new Cred(site, user, enc, System.currentTimeMillis()));
        save();
    }

    void remove(Cred c) { list.remove(c); save(); }

    void save() {
        JSONArray a = new JSONArray();
        try {
            for (Cred c : list) a.put(new JSONObject().put("s", c.site).put("u", c.user).put("p", c.enc).put("t", c.time));
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        p.edit().putString("pw", a.toString()).apply();
    }

    Set<String> never() { return new HashSet<>(p.getStringSet("pwNever", new HashSet<>())); }
    void addNever(String site) { Set<String> s = never(); s.add(site); p.edit().putStringSet("pwNever", s).apply(); }
    void clearNever() { p.edit().remove("pwNever").apply(); }

    String pass(Cred c) { return decrypt(c.enc); }

    /** Label for UI: the site key without the internal "http://" marker, but with a warning sign. */
    static String label(String site) { return site != null && site.startsWith("http://") ? "⚠ " + site : site; }

    // ---------------------------------------------------------------- crypto
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return kg.generateKey();
    }

    static String encrypt(String s) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV(), ct = c.doFinal(s.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + iv.length + ct.length];
            out[0] = (byte) iv.length;
            System.arraycopy(iv, 0, out, 1, iv.length);
            System.arraycopy(ct, 0, out, 1 + iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }

    static String decrypt(String s) {
        try {
            byte[] in = Base64.decode(s, Base64.NO_WRAP);
            int n = in[0];
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, in, 1, n));
            return new String(c.doFinal(in, 1 + n, in.length - 1 - n), StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }
}
