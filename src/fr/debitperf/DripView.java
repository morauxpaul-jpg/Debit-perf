package fr.debitperf;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/** Toute l'interface de la montre, dessinée à la main pour un écran rond. */
public class DripView extends View {
    static final int MAIN = 0, SETTINGS = 1, MEASURE = 2;

    static final int BG = Color.BLACK;
    static final int PANEL = 0xFF121A20;
    static final int LINE = 0xFF22303A;
    static final int FG = 0xFFE6EEF2;
    static final int MUTED = 0xFF8BA0AB;
    static final int FLUID = 0xFF8FDCEC;
    static final int FLUID_DEEP = 0xFF3B8FA3;
    static final int OK = 0xFF6FD49A;
    static final int WARN = 0xFFF0B35A;
    static final int BAD = 0xFFF07A6A;

    static final String[] FEEDBACK = {"MUET", "VIBRE", "BIP", "V+BIP"};
    static final String[] ROWS = {"Poche", "Ajouts", "Durée", "Débit", "Perfuseur"};
    static final int NROWS = 5;
    static final float ROW_TOP = 0.18f, ROW_STEP = 0.105f, ROW_H = 0.095f;

    final Calc c = new Calc();
    final SharedPreferences prefs;
    final Handler handler = new Handler(Looper.getMainLooper());
    Vibrator vibrator;
    ToneGenerator tone;

    int screen = MAIN;
    boolean unitMlh = false;
    int feedback = 0;          // index dans FEEDBACK
    int sel = 0;               // ligne choisie dans les réglages

    // Animation
    long t0 = SystemClock.uptimeMillis();
    double period = 0;
    long lastIdx = -1;
    long splashAt = -100000;
    long flashAt = -100000;

    // Mesure au tap
    final long[] taps = new long[10];
    int nTaps = 0;

    // Géométrie
    float S, ox, oy;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF r = new RectF();
    final Path path = new Path();
    final Typeface numFace = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    final Typeface bodyFace = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface labelFace = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    // Appui long sur + / -
    int repeatDir = 0;
    final Runnable repeater = new Runnable() {
        @Override public void run() {
            if (repeatDir == 0) return;
            stepSelected(repeatDir);
            handler.postDelayed(this, 90);
        }
    };

    public DripView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("debit", Context.MODE_PRIVATE);
        c.bag = prefs.getFloat("bag", prefs.getFloat("volume", 500f));
        c.add = prefs.getFloat("add", 0f);
        c.durMin = prefs.getFloat("durMin", 240f);
        c.factor = prefs.getInt("factor", 20);
        unitMlh = prefs.getBoolean("unitMlh", false);
        feedback = prefs.getInt("feedback", 0);
        vibrator = ctx.getSystemService(Vibrator.class);
        setFocusable(true);
        setFocusableInTouchMode(true);
        updatePeriod();
    }

    void save() {
        prefs.edit()
                .putFloat("bag", (float) c.bag)
                .putFloat("add", (float) c.add)
                .putFloat("durMin", (float) c.durMin)
                .putInt("factor", c.factor)
                .putBoolean("unitMlh", unitMlh)
                .putInt("feedback", feedback)
                .apply();
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        if (tone != null) { tone.release(); tone = null; }
    }

    /** Retour : vrai si on est revenu à l'écran principal. */
    boolean back() {
        if (screen == MAIN) return false;
        screen = MAIN;
        lastIdx = -1;
        save();
        invalidate();
        return true;
    }

    float X(float f) { return ox + f * S; }
    float Y(float f) { return oy + f * S; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        S = Math.min(w, h);
        ox = (w - S) / 2f;
        oy = (h - S) / 2f;
    }

    void updatePeriod() {
        double np = c.periodSec();
        if (np == period) return;
        long now = SystemClock.uptimeMillis();
        double frac = period > 0 ? (((now - t0) / 1000.0) % period) / period : 0;
        period = np;
        t0 = now - (long) (frac * period * 1000);
        lastIdx = -1;
    }

    // ------------------------------------------------------------ retours

    void onDrop(long now) {
        splashAt = now;
        flashAt = now;
        if (feedback == 1 || feedback == 3) vibrate(28);
        if (feedback == 2 || feedback == 3) beep();
    }

    void vibrate(int ms) {
        try {
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException ignored) { }
    }

    void beep() {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 45);
        } catch (RuntimeException ignored) { }
    }

    // ------------------------------------------------------------ dessin

    @Override
    protected void onDraw(Canvas cv) {
        cv.drawColor(BG);
        long now = SystemClock.uptimeMillis();
        if (screen == MAIN) drawMain(cv, now);
        else if (screen == SETTINGS) drawSettings(cv, now);
        else drawMeasure(cv, now);
        postInvalidateOnAnimation();
    }

    void ring(Canvas cv, long now, double phase, boolean showPhase) {
        float inset = 0.02f * S;
        r.set(X(0) + inset, Y(0) + inset, X(1) - inset, Y(1) - inset);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.018f * S);
        p.setStrokeCap(Paint.Cap.ROUND);
        float k = (now - flashAt) / 350f;
        if (k < 1) {
            p.setColor(blend(Color.WHITE, LINE, k));
            p.setStrokeWidth(0.018f * S * (1.6f - 0.6f * k));
        } else {
            p.setColor(LINE);
        }
        cv.drawArc(r, 0, 360, false, p);
        if (showPhase && phase > 0) {
            p.setStrokeWidth(0.018f * S);
            p.setColor(FLUID);
            cv.drawArc(r, -90, (float) (phase * 360), false, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    void drawMain(Canvas cv, long now) {
        double phase = 0, t = 0;
        if (period > 0) {
            double el = (now - t0) / 1000.0;
            long idx = (long) Math.floor(el / period);
            t = el - idx * period;
            if (lastIdx >= 0 && idx != lastIdx) onDrop(now);
            lastIdx = idx;
            phase = t / period;
        }
        ring(cv, now, phase, true);
        drawChamber(cv, now, t);

        // Chiffre principal
        double g = c.gtt(), m = c.mlh();
        text(cv, Calc.num(unitMlh ? m : g), 0.5f, 0.625f, 0.17f, FG, numFace, Paint.Align.CENTER);
        text(cv, unitMlh ? "ML / H" : "GOUTTES / MIN", 0.5f, 0.685f, 0.042f, FLUID, labelFace, Paint.Align.CENTER);
        String sub = (unitMlh ? Calc.num(g) + " gtt/min" : Calc.num(m) + " ml/h")
                + (period > 0 ? "  ·  1 / " + String.format(Locale.FRANCE, "%.1f", period) + " s" : "");
        text(cv, sub, 0.5f, 0.74f, 0.042f, FG, bodyFace, Paint.Align.CENTER);
        text(cv, volText() + " · " + Calc.duration(c.durMin) + " · fin " + endTime(),
                0.5f, 0.787f, 0.036f, MUTED, bodyFace, Paint.Align.CENTER);

        button(cv, 0.30f, 0.875f, FEEDBACK[feedback], feedback != 0);
        button(cv, 0.50f, 0.875f, "RÉGLER", false);
        button(cv, 0.70f, 0.875f, "MESURE", false);
    }

    void drawChamber(Canvas cv, long now, double t) {
        // tubulure du haut et du bas
        p.setColor(0xFF2B3943);
        r.set(X(0.475f), Y(0.04f), X(0.525f), Y(0.11f));
        cv.drawRoundRect(r, 0.008f * S, 0.008f * S, p);
        r.set(X(0.48f), Y(0.46f), X(0.52f), Y(0.53f));
        cv.drawRoundRect(r, 0.008f * S, 0.008f * S, p);

        // chambre
        RectF ch = new RectF(X(0.39f), Y(0.10f), X(0.61f), Y(0.465f));
        float rad = 0.05f * S;
        p.setColor(0xFF0E161B);
        cv.drawRoundRect(ch, rad, rad, p);

        cv.save();
        path.reset();
        path.addRoundRect(ch, rad, rad, Path.Direction.CW);
        cv.clipPath(path);

        float surf = 0.385f;
        float s = (now - splashAt) / 1000f;
        float amp = 0;
        if (s < 0.6f) amp = 0.012f * (1 - s / 0.6f) * (float) Math.sin(s * 28);
        path.reset();
        path.moveTo(X(0.39f), Y(surf));
        path.quadTo(X(0.445f), Y(surf - amp), X(0.5f), Y(surf));
        path.quadTo(X(0.555f), Y(surf + amp), X(0.61f), Y(surf));
        path.lineTo(X(0.61f), Y(0.47f));
        path.lineTo(X(0.39f), Y(0.47f));
        path.close();
        p.setColor(FLUID_DEEP);
        cv.drawPath(path, p);

        // éclaboussure
        if (s < 0.6f) {
            float k = s / 0.6f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.005f * S);
            p.setColor(withAlpha(FLUID, 1 - k));
            float rx = 0.02f + 0.07f * k, ry = 0.004f + 0.008f * k;
            r.set(X(0.5f - rx), Y(surf - ry), X(0.5f + rx), Y(surf + ry));
            cv.drawOval(r, p);
            p.setStyle(Paint.Style.FILL);
        }

        // goutte
        if (period > 0) {
            float y0 = 0.135f, cy, rx, ry;
            double fall = Math.min(0.42, period * 0.5), grow = period - fall;
            if (t < grow) {
                float q = (float) Math.sqrt(t / grow);
                rx = 0.022f * q; ry = rx * 1.12f; cy = y0 + rx * 0.9f;
            } else {
                float q = (float) ((t - grow) / fall);
                rx = 0.02f; ry = 0.02f + 0.013f * q;
                cy = y0 + 0.02f + (surf - y0 - 0.02f) * q * q;
            }
            p.setColor(FLUID);
            r.set(X(0.5f - rx), Y(cy - ry), X(0.5f + rx), Y(cy + ry));
            cv.drawOval(r, p);
        }
        cv.restore();

        // embout et verre
        p.setColor(0xFF4A5D69);
        r.set(X(0.485f), Y(0.10f), X(0.515f), Y(0.128f));
        cv.drawRect(r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.007f * S);
        cv.drawRoundRect(ch, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    String volText() {
        return c.add > 0 ? Math.round(c.bag) + "+" + Math.round(c.add) + " ml" : Math.round(c.bag) + " ml";
    }

    /** En-tête « ‹ TITRE » : touché, il ramène à l'écran principal. */
    void header(Canvas cv, String title) {
        r.set(X(0.30f), Y(0.075f), X(0.70f), Y(0.155f));
        p.setColor(PANEL);
        cv.drawRoundRect(r, 0.04f * S, 0.04f * S, p);
        text(cv, "‹  " + title, 0.5f, 0.128f, 0.04f, FLUID, labelFace, Paint.Align.CENTER);
    }

    boolean hitHeader(float x, float y) {
        return y < Y(0.17f) && x > X(0.25f) && x < X(0.75f);
    }

    void drawSettings(Canvas cv, long now) {
        ring(cv, now, 0, false);
        header(cv, "RÉGLAGES");
        String[] vals = {
                Math.round(c.bag) + " ml",
                (c.add > 0 ? "+" : "") + Math.round(c.add) + " ml",
                Calc.duration(c.durMin),
                Calc.num(c.mlh()) + " ml/h",
                c.factor + " gtt/ml"
        };
        for (int i = 0; i < NROWS; i++) {
            float top = ROW_TOP + i * ROW_STEP;
            r.set(X(0.12f), Y(top), X(0.88f), Y(top + ROW_H));
            p.setColor(i == sel ? 0xFF0E1A20 : PANEL);
            cv.drawRoundRect(r, 0.028f * S, 0.028f * S, p);
            if (i == sel) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.006f * S);
                p.setColor(FLUID);
                cv.drawRoundRect(r, 0.028f * S, 0.028f * S, p);
                p.setStyle(Paint.Style.FILL);
            }
            float base = top + 0.063f;
            text(cv, ROWS[i], 0.16f, base, 0.037f, i == sel ? FLUID : MUTED, bodyFace, Paint.Align.LEFT);
            text(cv, vals[i], 0.84f, base + 0.003f, 0.05f, FG, numFace, Paint.Align.RIGHT);
        }
        String hint = sel == 0 ? "touchez Poche : taille suivante" : sel == 4 ? "touchez : 20 · 60 · 15 · 10" : "";
        text(cv, "= " + Calc.num(c.gtt()) + " gtt/min · " + Math.round(c.volume()) + " ml", 0.5f, 0.74f, 0.042f, FLUID, numFace, Paint.Align.CENTER);
        if (!hint.isEmpty()) text(cv, hint, 0.5f, 0.775f, 0.03f, MUTED, bodyFace, Paint.Align.CENTER);
        button(cv, 0.29f, 0.845f, "−", false);
        button(cv, 0.50f, 0.895f, "OK", true);
        button(cv, 0.71f, 0.845f, "+", false);
    }

    void drawMeasure(Canvas cv, long now) {
        ring(cv, now, 0, false);
        header(cv, "MESURE");
        double target = c.gtt();
        double m = Calc.measuredRate(taps, nTaps);
        if (Double.isNaN(m)) {
            text(cv, "Touchez l'écran", 0.5f, 0.40f, 0.06f, FG, bodyFace, Paint.Align.CENTER);
            text(cv, "à chaque goutte", 0.5f, 0.47f, 0.06f, FG, bodyFace, Paint.Align.CENTER);
            text(cv, nTaps + " / 3", 0.5f, 0.575f, 0.07f, FLUID, numFace, Paint.Align.CENTER);
            text(cv, "cible " + Calc.num(target) + " gtt/min", 0.5f, 0.66f, 0.042f, MUTED, bodyFace, Paint.Align.CENTER);
        } else {
            text(cv, Calc.num(m), 0.5f, 0.42f, 0.15f, FG, numFace, Paint.Align.CENTER);
            text(cv, "gtt/min mesurées · cible " + Calc.num(target), 0.5f, 0.485f, 0.038f, MUTED, bodyFace, Paint.Align.CENTER);
            int v = Calc.verdict(m, target);
            String head, advice;
            int col;
            if (v == 0) { head = "BON RÉGLAGE"; advice = "≈ " + Calc.num(m * 60 / c.factor) + " ml/h"; col = OK; }
            else {
                long d = Math.round(Calc.diffPct(m, target));
                head = (v > 0 ? "TROP RAPIDE +" : "TROP LENT ") + d + " %";
                advice = v > 0 ? "Fermez un peu la molette" : "Ouvrez un peu la molette";
                col = Math.abs(v) == 2 ? BAD : WARN;
            }
            text(cv, head, 0.5f, 0.585f, 0.056f, col, numFace, Paint.Align.CENTER);
            text(cv, advice, 0.5f, 0.65f, 0.042f, FG, bodyFace, Paint.Align.CENTER);
        }
        button(cv, 0.38f, 0.86f, "ZÉRO", false);
        button(cv, 0.62f, 0.86f, "FIN", true);
    }

    void button(Canvas cv, float fx, float fy, String label, boolean active) {
        float rad = 0.068f * S;
        p.setColor(active ? 0xFF13252D : PANEL);
        cv.drawCircle(X(fx), Y(fy), rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.005f * S);
        p.setColor(active ? FLUID : LINE);
        cv.drawCircle(X(fx), Y(fy), rad, p);
        p.setStyle(Paint.Style.FILL);
        float size = label.length() <= 2 ? 0.07f : 0.028f;
        text(cv, label, fx, fy + size * 0.36f, size, active ? FLUID : FG, labelFace, Paint.Align.CENTER);
    }

    void text(Canvas cv, String s, float fx, float fy, float size, int color, Typeface tf, Paint.Align al) {
        txt.setTypeface(tf);
        txt.setTextSize(size * S);
        txt.setColor(color);
        txt.setTextAlign(al);
        cv.drawText(s, X(fx), Y(fy), txt);
    }

    String endTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, (int) Math.round(c.durMin * 60));
        return String.format(Locale.FRANCE, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    static int withAlpha(int color, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (color & 0xFFFFFF);
    }

    static int blend(int a, int b, float k) {
        int ra = (a >> 16) & 255, ga = (a >> 8) & 255, ba = a & 255;
        int rb = (b >> 16) & 255, gb = (b >> 8) & 255, bb = b & 255;
        return Color.rgb(Math.round(ra + (rb - ra) * k), Math.round(ga + (gb - ga) * k), Math.round(ba + (bb - ba) * k));
    }

    // ------------------------------------------------------------ saisie

    boolean hit(float x, float y, float fx, float fy, float frad) {
        float dx = x - X(fx), dy = y - Y(fy), rr = frad * S;
        return dx * dx + dy * dy <= rr * rr;
    }

    void stepSelected(int dir) {
        switch (sel) {
            case 0: c.bag = Calc.stepVolume(c.bag, dir); break;
            case 1: c.add = Calc.stepAdd(c.add, dir); break;
            case 2: c.durMin = Calc.stepDuration(c.durMin, dir); break;
            case 3: c.setMlh(Calc.stepRate(c.mlh(), dir)); break;
            default: c.factor = Calc.stepFactor(c.factor, dir); break;
        }
        updatePeriod();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            repeatDir = 0;
            handler.removeCallbacks(repeater);
        }
        if (screen == MEASURE) {
            if (a == MotionEvent.ACTION_DOWN) {
                if (hitHeader(x, y) || hit(x, y, 0.62f, 0.86f, 0.09f)) { back(); return true; }
                if (hit(x, y, 0.38f, 0.86f, 0.09f)) { nTaps = 0; return true; }
                tap(SystemClock.uptimeMillis());
            }
            return true;
        }
        if (screen == SETTINGS) {
            if (a == MotionEvent.ACTION_DOWN) {
                int dir = hit(x, y, 0.29f, 0.845f, 0.09f) ? -1 : hit(x, y, 0.71f, 0.845f, 0.09f) ? 1 : 0;
                if (dir != 0) {
                    stepSelected(dir);
                    repeatDir = dir;
                    handler.postDelayed(repeater, 420);
                }
            } else if (a == MotionEvent.ACTION_UP) {
                if (hitHeader(x, y) || hit(x, y, 0.50f, 0.895f, 0.09f)) { back(); return true; }
                for (int i = 0; i < NROWS; i++) {
                    float top = ROW_TOP + i * ROW_STEP;
                    if (y >= Y(top) && y <= Y(top + ROW_H) && x >= X(0.1f) && x <= X(0.9f)) {
                        // ligne déjà choisie : un tap passe à la valeur classique suivante
                        if (sel == i && i == 0) { c.bag = Calc.nextBag(c.bag); updatePeriod(); }
                        if (sel == i && i == 4) { c.factor = Calc.nextFactor(c.factor, 1); updatePeriod(); }
                        sel = i;
                        invalidate();
                    }
                }
            }
            return true;
        }
        // écran principal
        if (a == MotionEvent.ACTION_UP) {
            if (hit(x, y, 0.30f, 0.875f, 0.09f)) {
                feedback = (feedback + 1) % FEEDBACK.length;
                if (feedback == 1 || feedback == 3) vibrate(40);
                if (feedback == 2 || feedback == 3) beep();
                save();
            } else if (hit(x, y, 0.50f, 0.875f, 0.09f)) {
                screen = SETTINGS;
            } else if (hit(x, y, 0.70f, 0.875f, 0.09f)) {
                screen = MEASURE;
                nTaps = 0;
            } else if (y > Y(0.48f) && y < Y(0.80f)) {
                unitMlh = !unitMlh;
                save();
            }
            invalidate();
        }
        return true;
    }

    void tap(long now) {
        if (nTaps > 0 && now - taps[nTaps - 1] > 12000) nTaps = 0;
        if (nTaps == taps.length) {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            nTaps--;
        }
        taps[nTaps++] = now;
        flashAt = now;
        vibrate(12);
        invalidate();
    }

    /** Lunette rotative (ou tactile) : règle la ligne choisie dans les réglages. */
    boolean onRotary(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_SCROLL
                || !ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) return false;
        if (screen != SETTINGS) return true;
        float v = -ev.getAxisValue(MotionEvent.AXIS_SCROLL);
        if (v != 0) stepSelected(v > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        return onRotary(ev) || super.onGenericMotionEvent(ev);
    }
}
