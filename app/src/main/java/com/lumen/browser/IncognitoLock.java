package com.lumen.browser;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Locks incognito tabs behind the fingerprint / screen lock after the app was left. */
final class IncognitoLock {
    final MainActivity act;
    boolean locked, authing, declined;
    FrameLayout cover;

    IncognitoLock(MainActivity act) { this.act = act; }

    boolean deviceSecure() {
        KeyguardManager km = (KeyguardManager) act.getSystemService(act.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    boolean enabled() { return act.store.bool("incLock", false) && deviceSecure(); }

    boolean hasIncognito() { for (Tab t : act.tabs) if (t.incognito) return true; return false; }

    boolean showingIncognito() {
        if (act.switcher != null && act.switcher.getVisibility() == View.VISIBLE) return act.switcherIncognito;
        return act.current != null && act.current.incognito;
    }

    /** The app went to the background. */
    void onStop() {
        if (authing || act.pendingAuth != null || act.fileCb != null) return; // our own prompt / file picker
        if (!enabled() || !hasIncognito()) return;
        locked = true;
        declined = false;
        if (showingIncognito()) showCover(); // before the next frame, so recents / resume never flash the tabs
    }

    /** Called whenever the visible content may have changed. */
    void check() {
        if (!locked) { hideCover(); return; }
        if (!hasIncognito() || !enabled()) { locked = false; hideCover(); return; }
        if (!showingIncognito()) { hideCover(); return; }
        showCover();
        if (!authing && !declined && act.pip.activityVisible) auth();
    }

    void unlock() {
        locked = false; authing = false; declined = false;
        hideCover();
    }

    void failed(boolean byUser) {
        authing = false;
        if (byUser) declined = true;
    }

    void auth() {
        authing = true;
        String title = L.t("Вкладки инкогнито"), sub = L.t("Подтвердите, что это вы, чтобы открыть вкладки инкогнито");
        if (Build.VERSION.SDK_INT >= 29 && biometricUsable()) {
            try {
                android.hardware.biometrics.BiometricPrompt.Builder b = new android.hardware.biometrics.BiometricPrompt.Builder(act)
                        .setTitle(title).setSubtitle(sub);
                if (Build.VERSION.SDK_INT >= 30)
                    b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                else setCredentialAllowed(b);
                b.build().authenticate(new android.os.CancellationSignal(), act.getMainExecutor(),
                        new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                            @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) { unlock(); }
                            @Override public void onAuthenticationError(int code, CharSequence msg) {
                                // canceled by the system (app paused): prompt again on return; otherwise wait for a tap
                                failed(code != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED);
                            }
                        });
                return;
            } catch (Exception e) { android.util.Log.d("Lasur", "biometric prompt", e); }
        }
        KeyguardManager km = (KeyguardManager) act.getSystemService(act.KEYGUARD_SERVICE);
        @SuppressWarnings("deprecation")
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent(title, sub);
        if (i == null) { unlock(); return; }
        act.pendingAuth = this::unlock;
        act.pendingAuthFail = () -> failed(true);
        try { act.startActivityForResult(i, MainActivity.REQ_AUTH); }
        catch (Exception e) { act.pendingAuth = null; act.pendingAuthFail = null; unlock(); }
    }

    @SuppressWarnings("deprecation")
    private static void setCredentialAllowed(android.hardware.biometrics.BiometricPrompt.Builder b) { b.setDeviceCredentialAllowed(true); }

    @SuppressWarnings("deprecation")
    boolean biometricUsable() {
        try {
            android.hardware.biometrics.BiometricManager bm = act.getSystemService(android.hardware.biometrics.BiometricManager.class);
            if (bm == null) return false;
            if (Build.VERSION.SDK_INT >= 30)
                return bm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL) == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
            return bm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) { return false; }
    }

    void showCover() {
        if (cover != null) { cover.bringToFront(); return; }
        cover = new FrameLayout(act);
        cover.setBackgroundColor(Ui.INC_BG);
        cover.setClickable(true);
        cover.setFocusable(true);
        cover.setTranslationZ(act.dp(40));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout ring = new FrameLayout(act);
        ring.setBackground(Ui.oval(0xFF3C4043));
        ImageView ic = Ui.icon(act, R.drawable.ic_lock, Ui.INC_TEXT);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ring.addView(ic, new FrameLayout.LayoutParams(act.dp(32), act.dp(32), Gravity.CENTER));
        col.addView(ring, new LinearLayout.LayoutParams(act.dp(72), act.dp(72)));
        TextView t = Ui.medium(Ui.text(act, L.t("Вкладки инкогнито заблокированы"), 18, Ui.INC_TEXT));
        t.setGravity(Gravity.CENTER);
        t.setPaddingRelative(act.dp(24), act.dp(20), act.dp(24), act.dp(28));
        col.addView(t, new LinearLayout.LayoutParams(act.MATCH, act.WRAP));
        TextView un = Ui.medium(Ui.text(act, L.t("Разблокировать"), 15, Color.WHITE));
        un.setGravity(Gravity.CENTER);
        un.setPaddingRelative(act.dp(28), act.dp(12), act.dp(28), act.dp(12));
        un.setBackground(Ui.round(0xFF5F6368, 22));
        un.setOnClickListener(v -> { declined = false; if (!authing) auth(); });
        col.addView(un, new LinearLayout.LayoutParams(act.WRAP, act.WRAP));
        TextView norm = Ui.text(act, L.t("Обычные вкладки"), 15, Ui.INC_TEXT2);
        norm.setGravity(Gravity.CENTER);
        norm.setPaddingRelative(act.dp(28), act.dp(12), act.dp(28), act.dp(12));
        norm.setBackground(Ui.ripple(act, true));
        norm.setOnClickListener(v -> toNormal());
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(act.WRAP, act.WRAP);
        nl.topMargin = act.dp(8);
        col.addView(norm, nl);
        cover.addView(col, new FrameLayout.LayoutParams(act.MATCH, act.WRAP, Gravity.CENTER));
        act.root.addView(cover, new FrameLayout.LayoutParams(act.MATCH, act.MATCH));
        act.unfocusOmni();
    }

    void hideCover() {
        if (cover == null) return;
        act.root.removeView(cover);
        cover = null;
    }

    void toNormal() {
        if (act.switcher.getVisibility() == View.VISIBLE) {
            act.switcherIncognito = false;
            act.tabSwitcher.buildSwitcher();
        } else {
            Tab best = null;
            for (Tab t : act.tabs) if (!t.incognito && (best == null || t.lastUsed > best.lastUsed)) best = t;
            if (best != null) act.selectTab(best); else act.newTab(null, false, true, null);
        }
        check();
    }
}
