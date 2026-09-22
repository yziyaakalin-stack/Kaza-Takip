package com.ziya.kazatakip;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * İki hatırlatma yuvası: 1 = günlük kaza hedefi, 2 = vird.
 * Ayarlar SharedPreferences'ta tutulur, alarmlar her tetiklenişte ertesi güne kurulur.
 */
final class Reminder {
    static final String CHANNEL = "kaza_hatirlatma";
    static final String ACTION = "com.ziya.kazatakip.HATIRLAT";
    static final String EXTRA_SLOT = "slot";

    static final int SLOT_KAZA = 1;
    static final int SLOT_VIRD = 2;

    static final String K_ENABLED = "rem_enabled";
    static final String K_HOUR = "rem_hour";
    static final String K_MINUTE = "rem_minute";
    static final String K_UNITS = "target_units";
    static final String K_MET = "met_date";

    static final String K_V_ENABLED = "vird_enabled";
    static final String K_V_HOUR = "vird_hour";
    static final String K_V_MINUTE = "vird_minute";
    static final String K_V_MET = "vird_met_date";
    static final String K_V_TITLE = "vird_title";
    static final String K_V_TEXT = "vird_text";

    /** Namaz vakti bildirimleri: 10. yuvadan itibaren, en fazla 8 tane. */
    static final int SLOT_VAKIT = 10;
    static final int MAX_VAKIT = 8;
    static final String K_N_ENABLED = "vakit_enabled";
    static final String K_N_JSON = "vakit_json";

    /** Vird hatırlatması imsaka göre mi (dakika farkıyla) */
    static final String K_V_FECR = "vird_fecr";
    static final String K_V_FECR_DK = "vird_fecr_dk";

    /** Seçili şehrin konumu */
    static final String K_LAT = "konum_lat";
    static final String K_LNG = "konum_lng";

    /** Her gece vakitleri yeniden hesaplayan yuva */
    static final int SLOT_GECE = 30;

    private Reminder() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("kaza_prefs", Context.MODE_PRIVATE);
    }

    static boolean anyEnabled(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(K_ENABLED, false) || p.getBoolean(K_V_ENABLED, false)
                || p.getBoolean(K_N_ENABLED, false);
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Günlük hatırlatma", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Kaza hedefini ve virdi hatırlatır");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static PendingIntent pending(Context c, int slot) {
        Intent i = new Intent(c, ReminderReceiver.class)
                .setAction(ACTION)
                .putExtra(EXTRA_SLOT, slot);
        return PendingIntent.getBroadcast(c, slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Bütün yuvaları yeniden kurar. */
    static void schedule(Context c) {
        scheduleSlot(c, SLOT_KAZA);
        scheduleSlot(c, SLOT_VIRD);
        for (int i = 0; i < MAX_VAKIT; i++) scheduleSlot(c, SLOT_VAKIT + i);
        scheduleSlot(c, SLOT_GECE);
    }

    static double lat(Context c) { return Double.longBitsToDouble(prefs(c).getLong(K_LAT, Double.doubleToLongBits(41.0082))); }
    static double lng(Context c) { return Double.longBitsToDouble(prefs(c).getLong(K_LNG, Double.doubleToLongBits(28.9784))); }

    /** Vakit bildirimi ayarı: {"offset":30,"list":[{"v":2,"t":"Öğle vakti girdi","x":"metin"}]} */
    static org.json.JSONObject vakitAyari(Context c) {
        try {
            String raw = prefs(c).getString(K_N_JSON, "{}").trim();
            if (raw.startsWith("[")) return new org.json.JSONObject().put("list", new org.json.JSONArray(raw));
            return new org.json.JSONObject(raw);
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    static org.json.JSONArray vakitler(Context c) {
        org.json.JSONArray a = vakitAyari(c).optJSONArray("list");
        return a == null ? new org.json.JSONArray() : a;
    }

    static void scheduleSlot(Context c, int slot) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c, slot);
        am.cancel(pi);

        SharedPreferences p = prefs(c);
        int hour;
        int minute;
        long simdi = System.currentTimeMillis();
        if (slot == SLOT_GECE) {
            // Her gece 00:05: vakitleri yeniden hesaplayıp alarmları kur
            if (!anyEnabled(c)) return;
            hour = 0;
            minute = 5;
        } else if (slot >= SLOT_VAKIT) {
            if (!p.getBoolean(K_N_ENABLED, false)) return;
            org.json.JSONObject ayar = vakitAyari(c);
            org.json.JSONObject o = vakitler(c).optJSONObject(slot - SLOT_VAKIT);
            if (o == null) return;
            if (o.has("v")) {
                long zaman = Vakit.sonraki(o.optInt("v"), ayar.optInt("offset", 30), lat(c), lng(c), simdi);
                if (zaman > 0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zaman, pi);
                return;
            }
            hour = o.optInt("h", -1);
            minute = o.optInt("m", 0);
            if (hour < 0) return;
        } else if (slot == SLOT_VIRD && p.getBoolean(K_V_ENABLED, false) && p.getBoolean(K_V_FECR, false)) {
            long zaman = Vakit.sonraki(Vakit.IMSAK, p.getInt(K_V_FECR_DK, 10), lat(c), lng(c), simdi);
            if (zaman > 0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zaman, pi);
            return;
        } else {
            boolean on = p.getBoolean(slot == SLOT_VIRD ? K_V_ENABLED : K_ENABLED, false);
            if (!on) return;
            hour = p.getInt(slot == SLOT_VIRD ? K_V_HOUR : K_HOUR, slot == SLOT_VIRD ? 6 : 21);
            minute = p.getInt(slot == SLOT_VIRD ? K_V_MINUTE : K_MINUTE, 0);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= simdi + 5000) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
}
