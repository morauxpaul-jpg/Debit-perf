package fr.debitperf;

import java.util.Locale;

/** Calculs de débit, sans dépendance Android (testable sur un PC). */
public final class Calc {
    public static final int[] FACTORS = {20, 60, 15, 10};
    public static final int[] BAGS = {50, 100, 250, 500, 1000};

    public double bag = 500;      // volume de la poche (ml)
    public double add = 0;        // ml ajoutés dans la poche
    public double durMin = 240;   // minutes
    public int factor = 20;       // gouttes par ml

    /** Volume total à passer : poche + ajouts. */
    public double volume() {
        return bag + add;
    }

    public double mlh() {
        double v = volume();
        return (v > 0 && durMin > 0) ? v * 60.0 / durMin : 0;
    }

    public double gtt() {
        return mlh() * factor / 60.0;
    }

    /** Secondes entre deux gouttes (0 si rien à afficher). */
    public double periodSec() {
        double g = gtt();
        return g > 0 ? 60.0 / g : 0;
    }

    /** Fixe le débit en ml/h : la durée est recalculée. */
    public void setMlh(double r) {
        double v = volume();
        if (r > 0 && v > 0) durMin = v * 60.0 / r;
    }

    static double snap(double v, double step, int dir) {
        return dir > 0 ? Math.floor(v / step + 1e-9) * step + step
                       : Math.ceil(v / step - 1e-9) * step - step;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double stepVolume(double v, int dir) {
        double step = (dir > 0 ? v < 100 : v <= 100) ? 10 : (dir > 0 ? v < 1000 : v <= 1000) ? 50 : 100;
        return clamp(snap(v, step, dir), 10, 5000);
    }

    public static double stepDuration(double d, int dir) {
        double step = (dir > 0 ? d < 60 : d <= 60) ? 5 : (dir > 0 ? d < 360 : d <= 360) ? 15 : 30;
        return clamp(snap(d, step, dir), 5, 2880);
    }

    public static double stepRate(double r, int dir) {
        double step = (dir > 0 ? r < 50 : r <= 50) ? 1 : (dir > 0 ? r < 300 : r <= 300) ? 5 : 10;
        return clamp(snap(r, step, dir), 1, 999);
    }

    public static double stepAdd(double a, int dir) {
        double step = (dir > 0 ? a < 50 : a <= 50) ? 5 : 10;
        return clamp(snap(a, step, dir), 0, 1000);
    }

    /** Gouttes par ml personnalisé, de 1 en 1. */
    public static int stepFactor(int f, int dir) {
        return (int) clamp(f + dir, 1, 100);
    }

    /** Taille de poche classique suivante (après la dernière, revient à la première). */
    public static double nextBag(double b) {
        for (int v : BAGS) if (v > b + 1e-9) return v;
        return BAGS[0];
    }

    public static int nextFactor(int f, int dir) {
        int i = 0;
        for (int k = 0; k < FACTORS.length; k++) if (FACTORS[k] == f) i = k;
        i = (i + dir + FACTORS.length) % FACTORS.length;
        return FACTORS[i];
    }

    /** 0 bon (±5 %), 1 trop rapide, 2 bien trop rapide (>15 %), -1 trop lent, -2 bien trop lent. */
    public static int verdict(double measured, double target) {
        if (!(target > 0) || !(measured > 0)) return 0;
        double d = (measured - target) / target * 100;
        if (Math.abs(d) <= 5) return 0;
        if (d > 0) return d > 15 ? 2 : 1;
        return d < -15 ? -2 : -1;
    }

    public static double diffPct(double measured, double target) {
        return (measured - target) / target * 100;
    }

    /** Gouttes par minute d'après les instants des touches (ms). NaN si moins de 3 touches. */
    public static double measuredRate(long[] taps, int n) {
        if (n < 3) return Double.NaN;
        double avg = (taps[n - 1] - taps[0]) / (double) (n - 1);
        return avg > 0 ? 60000.0 / avg : Double.NaN;
    }

    public static String num(double x) {
        if (!(x > 0) || Double.isInfinite(x)) return "--";
        if (x < 10) return String.format(Locale.FRANCE, "%.1f", x);
        return String.format(Locale.FRANCE, "%d", Math.round(x));
    }

    public static String duration(double min) {
        long m = Math.round(min);
        long h = m / 60, r = m % 60;
        if (h == 0) return r + " min";
        return String.format(Locale.FRANCE, "%d h %02d", h, r);
    }
}
