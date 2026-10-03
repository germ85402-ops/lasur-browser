package com.lumen.browser;
import static com.lumen.browser.Scripts.*;
import android.util.Log;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import java.util.ArrayList;

/** Picture-in-picture: eligibility, parameters and page preparation. */
final class PipController {
    final MainActivity act;

    PipController(MainActivity act) { this.act = act; }

    boolean pipExited, pipClosed, activityVisible, pipEntering;
    boolean pipChromeSaved, pipBarsHidden, pipStripHidden, pipFindVisible;
    boolean pipHadFullscreen, pipReturnFullscreen;
    Tab pipFullscreenTab;
    String pipFullscreenUrl;

    void rememberPipFullscreen() {
        if (act.current == null || act.customView == null) return;
        pipHadFullscreen = true;
        pipFullscreenTab = act.current;
        pipFullscreenUrl = act.current.web.getUrl();
        act.current.web.evaluateJavascript(PIP_REMEMBER_FULLSCREEN_JS, null);
    }

    static final String ACTION_PIP_TOGGLE = "com.lumen.browser.PIP_TOGGLE";
    android.content.BroadcastReceiver pipReceiver;

    void registerPipReceiver() {
        pipReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (act.current == null || !act.isInPictureInPictureMode()) return;
                act.current.web.evaluateJavascript(PIP_TOGGLE_JS, null);
            }
        };
        try {
            android.content.IntentFilter f = new android.content.IntentFilter(ACTION_PIP_TOGGLE);
            if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(pipReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else act.registerReceiver(pipReceiver, f);
        } catch (Exception e) { pipReceiver = null; }
    }

    boolean pipAllowed() {
        return !act.leavingByBack && !pipClosed && act.store.bool("pip", true) && act.current != null && !act.current.ntp && act.switcher.getVisibility() != View.VISIBLE
                && act.current.mediaPipEligible;
    }

    boolean pipEligible() { return pipAllowed() && (act.current.mediaPlaying || pipEntering); }

    /** Sites such as YouTube may pause a moment before onUserLeaveHint; treat very recent playback as playing. */
    boolean pipRecent() {
        return pipAllowed() && (act.current.mediaPlaying || android.os.SystemClock.uptimeMillis() - act.current.mediaPlayAt < 2000);
    }

    /** Last auto-enter value handed to the system (Android 12+). */
    boolean pipArmed;

    PictureInPictureParams pipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
        int w = act.current != null ? act.current.mediaW : 0, h = act.current != null ? act.current.mediaH : 0;
        if (w > 0 && h > 0) {
            float r = w / (float) h;
            if (r > 2.39f) { w = 239; h = 100; } else if (r < 0.42f) { w = 42; h = 100; }
            b.setAspectRatio(new Rational(w, h));
        } else b.setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= 31) { pipArmed = pipEligible(); b.setAutoEnterEnabled(pipArmed); b.setSeamlessResizeEnabled(true); }
        try {
            boolean playing = act.current != null && act.current.mediaPlaying;
            Intent ti = new Intent(ACTION_PIP_TOGGLE).setPackage(act.getPackageName());
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(act, 7, ti,
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            String label = playing ? "Pause" : "Play";
            ArrayList<android.app.RemoteAction> acts = new ArrayList<>();
            acts.add(new android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(act,
                    playing ? R.drawable.ic_pip_pause : R.drawable.ic_pip_play), label, label, pi));
            b.setActions(acts);
        } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
        return b.build();
    }

    void updatePipParams() {
        if (Build.VERSION.SDK_INT < 26) return;
        try { act.setPictureInPictureParams(pipParams()); } catch (Exception ex) { android.util.Log.d("Lasur", "ignored", ex); }
    }

    void preparePip() {
        if (act.current != null) {
            ((LWebView) act.current.web).setPipVisible(true);
            act.current.web.evaluateJavascript(Scripts.R("window.__lasurPipBackground=true;"), null);
        }
        if (!pipChromeSaved) {
            pipChromeSaved = true; pipBarsHidden = act.barsHidden; pipStripHidden = act.stripHidden;
            pipFindVisible = act.findBar.getVisibility() == View.VISIBLE;
            act.unfocusOmni(); act.suggestScroll.setVisibility(View.GONE);
        }
        act.resetBars(true);
        if (act.current != null) act.current.web.evaluateJavascript(act.customView == null ? PIP_ON_JS : PIP_FS_JS, null);
        act.toolbar.setVisibility(View.GONE);
        act.stripHidden = true;
        if (act.stripScroll != null) act.stripScroll.setVisibility(View.GONE);
        act.findBar.setVisibility(View.GONE);
        act.divider.setVisibility(View.GONE);
        act.progress.setVisibility(View.GONE);
        act.videoFab.setVisibility(View.GONE);
        act.passwordsUi.hidePwBar();
        if (act.snackView != null) act.snackView.setVisibility(View.GONE);
        if (act.noticeView != null) act.noticeView.setVisibility(View.GONE);
    }

    void restorePip() {
        pipEntering = false;
        boolean fromPip = pipChromeSaved;
        if (act.current != null) {
            // Mode exit may precede Activity resume; keep the surface alive while expanding.
            if (activityVisible) ((LWebView) act.current.web).setPipVisible(false);
            act.current.web.evaluateJavascript(fromPip ? PIP_OFF_JS + FULLSCREEN_OFF_JS : PIP_OFF_JS, null);
        }
        if (pipChromeSaved) {
            act.resetBars(pipBarsHidden);
            act.stripHidden = pipStripHidden;
            act.findBar.setVisibility(pipFindVisible ? View.VISIBLE : View.GONE);
            pipChromeSaved = false;
        }
        act.refreshChrome();
        // Returning from PiP shows the normal page, never full screen.
        pipReturnFullscreen = false; pipHadFullscreen = false; pipFullscreenTab = null;
        if (fromPip && act.customView != null) act.hideCustomView();
        else if (act.customView != null) act.setFullscreenBars(true);
        act.placeFloating();
    }

    void stopBackgroundMedia() {
        if (act.current == null || act.current.web == null) return;
        act.current.mediaPlaying = false; act.current.mediaPipEligible = false; pipEntering = false;
        pipReturnFullscreen = false; pipHadFullscreen = false; pipFullscreenTab = null;
        ((LWebView) act.current.web).setPipVisible(false);
        // Turn off transition recovery before pausing; otherwise the pause handler resumes the video.
        try { act.current.web.evaluateJavascript(PIP_OFF_JS + PAUSE_JS, null); } catch (Exception e) { Log.d(act.TAG, "stop media", e); }
        updatePipParams();
    }

    boolean timersPaused;

    /** Called from Activity.onUserLeaveHint: arm or start picture-in-picture for playing video. */
    void onUserLeaveHint() {
        if (act.leavingByBack) return;
        if (!pipChromeSaved) { pipHadFullscreen = false; pipReturnFullscreen = false; }
        rememberPipFullscreen();
        boolean armed = pipArmed;
        pipEntering = pipRecent();
        if (!pipEntering) { updatePipParams(); return; }
        ((LWebView) act.current.web).setPipVisible(true);
        // Android 12+ performs auto-entry when it was armed; manual entry then would race that transition.
        // Prepare the page early so the shrinking window already shows only the video.
        if (Build.VERSION.SDK_INT >= 31 && armed) {
            updatePipParams();
            if (!act.isInPictureInPictureMode()) preparePip();
            return;
        }
        // Not armed (the player paused just before leaving, or params were stale): enter manually.
        if (Build.VERSION.SDK_INT >= 26 && !act.isInPictureInPictureMode()) {
            preparePip();
            try { if (!act.enterPictureInPictureMode(pipParams())) restorePip(); } catch (Exception e) { restorePip(); }
        }
    }

    void onModeChanged(boolean in) {
        if (in) {
            rememberPipFullscreen();
            pipReturnFullscreen = pipHadFullscreen;
            pipExited = false; pipClosed = false; pipEntering = false;
            if (timersPaused && act.current != null) { act.current.web.resumeTimers(); timersPaused = false; }
            if (act.current != null) act.current.web.onResume();
            preparePip();
            // The player can pause again after the system has resized its surface.
            for (int delay : new int[]{250, 750, 1500, 3000}) act.ui.postDelayed(() -> {
                if (act.isInPictureInPictureMode() && act.current != null)
                    act.current.web.evaluateJavascript(act.customView == null ? PIP_ON_JS : PIP_FS_JS, null);
            }, delay);
        }
        else {
            pipExited = true; pipClosed = !activityVisible; restorePip();
            // Expanding PiP resumes the Activity; closing it leaves the Activity stopped.
            act.ui.postDelayed(() -> { if (!activityVisible && !act.isInPictureInPictureMode()) stopBackgroundMedia(); }, 1000);
        }
        act.applyInsets();
    }
}
