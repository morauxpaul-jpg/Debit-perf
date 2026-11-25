package fr.debitperf;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sur la montre : interface ronde dessinée à la main (DripView).
 * Sur le téléphone : interface téléphone (assets/index.html), affichée hors ligne.
 */
public class MainActivity extends Activity {
    private DripView view;   // montre
    private WebView web;     // téléphone

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // L'écran reste allumé pendant le réglage de la perfusion.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            view = new DripView(this);
            setContentView(view);
            view.requestFocus();
        } else {
            setupPhone();
        }
    }

    private void setupPhone() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);           // garde les réglages (localStorage)
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        String html;
        try {
            html = readAsset("index.html");
        } catch (IOException e) {
            html = "<p style='color:#fff'>Erreur de chargement : " + e.getMessage() + "</p>";
        }
        // Une adresse fixe donne une origine stable au stockage des réglages.
        web.loadDataWithBaseURL("https://debitperf.app/", html, "text/html", "UTF-8", null);
        setContentView(web);
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Pont appelé par la page : vibration native. */
    public class Bridge {
        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = getSystemService(Vibrator.class);
                if (v != null && v.hasVibrator())
                    v.vibrate(VibrationEffect.createOneShot(Math.max(5, Math.min(ms, 200)),
                            VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (RuntimeException ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.requestFocus();
        if (web != null) web.onResume();
    }

    @Override
    public void onBackPressed() {
        if (view != null) {
            if (!view.back()) super.onBackPressed();
            return;
        }
        if (web != null) {
            // La page gère d'abord le retour (fermer l'écran de mesure, etc.).
            web.evaluateJavascript("window.debitBack ? window.debitBack() : false", v -> {
                if (!"true".equals(v)) super.onBackPressed();
            });
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        // Lunette / couronne : transmise même si la vue a perdu le focus.
        if (view != null && view.onRotary(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    protected void onPause() {
        if (view != null) view.save();
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (view != null) view.release();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
