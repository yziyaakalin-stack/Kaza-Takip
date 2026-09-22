package com.ziya.kazatakip;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Uygulamadaki JavaScript hesabının birebir aynısı: Diyanet yöntemi
 * (imsak 18°, yatsı 17°, ikindi asr-ı evvel; temkin: güneş −7, öğle +5, ikindi +4, akşam +7 dk).
 * Sonuçlar gece yarısından itibaren dakika olarak döner.
 */
final class Vakit {
    static final int IMSAK = 0, GUNES = 1, OGLE = 2, IKINDI = 3, AKSAM = 4, YATSI = 5;

    private Vakit() {}

    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double tan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double asin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double acos(double x) { return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, x)))); }
    private static double atan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double acot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double A = Math.floor(y / 100.0), B = 2 - A + Math.floor(A / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    /** [sapma, zaman denklemi] */
    private static double[] sunPos(double jd) {
        double D = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * D, 360), q = fix(280.459 + 0.98564736 * D, 360);
        double L = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360), e = 23.439 - 0.00000036 * D;
        double RA = atan2(cos(e) * sin(L), cos(L)) / 15;
        return new double[]{asin(sin(e) * sin(L)), q / 15 - fix(RA, 24)};
    }

    /** Verilen günün vakitleri (gece yarısından itibaren dakika). */
    static int[] gun(Calendar gun, final double lat, final double lng) {
        int y = gun.get(Calendar.YEAR), m = gun.get(Calendar.MONTH) + 1, d = gun.get(Calendar.DAY_OF_MONTH);
        Calendar ogle = (Calendar) gun.clone();
        ogle.set(Calendar.HOUR_OF_DAY, 12); ogle.set(Calendar.MINUTE, 0); ogle.set(Calendar.SECOND, 0); ogle.set(Calendar.MILLISECOND, 0);
        TimeZone tzz = gun.getTimeZone();
        double tz = tzz.getOffset(ogle.getTimeInMillis()) / 3600000.0;
        final double jd = julian(y, m, d) - lng / (15 * 24);

        double[] h = {5, 6, 12, 13, 18, 18};
        for (int i = 0; i < 2; i++) {
            h = new double[]{
                angleTime(jd, lat, 18, h[0] / 24, true),
                angleTime(jd, lat, 0.833, h[1] / 24, true),
                mid(jd, h[2] / 24),
                asr(jd, lat, h[3] / 24),
                angleTime(jd, lat, 0.833, h[4] / 24, false),
                angleTime(jd, lat, 17, h[5] / 24, false)
            };
        }
        int[] adj = {0, -7, 5, 4, 7, 0};
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) out[i] = (int) Math.round((h[i] + tz - lng / 15) * 60 + adj[i]);
        return out;
    }

    private static double mid(double jd, double t) { return fix(12 - sunPos(jd + t)[1], 24); }

    private static double angleTime(double jd, double lat, double angle, double t, boolean ccw) {
        double decl = sunPos(jd + t)[0], noon = mid(jd, t);
        double T = acos((-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat))) / 15;
        return noon + (ccw ? -T : T);
    }

    private static double asr(double jd, double lat, double t) {
        double decl = sunPos(jd + t)[0];
        double a = -acot(1 + tan(Math.abs(lat - decl)));
        return angleTime(jd, lat, a, t, false);
    }

    /** Bugün ya da gerekiyorsa yarın, verilen vaktin "dakika" kadar sonrası (epoch ms). */
    static long sonraki(int vakit, int dakika, double lat, double lng, long simdi) {
        for (int gunEk = 0; gunEk < 3; gunEk++) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(simdi);
            c.add(Calendar.DAY_OF_YEAR, gunEk);
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            int dk = gun(c, lat, lng)[vakit] + dakika;
            c.add(Calendar.MINUTE, dk);
            if (c.getTimeInMillis() > simdi + 5000) return c.getTimeInMillis();
        }
        return -1;
    }
}
