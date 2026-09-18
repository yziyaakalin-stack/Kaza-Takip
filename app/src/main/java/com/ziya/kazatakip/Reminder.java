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

    private Reminder() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("kaza_prefs", Context.MODE_PRIVATE);
    }

    static boolean anyEnabled(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(K_ENABLED, false) || p.getBoolean(K_V_ENABLED, false);
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

    /** Her iki yuvayı da yeniden kurar. */
    static void schedule(Context c) {
        scheduleSlot(c, SLOT_KAZA);
        scheduleSlot(c, SLOT_VIRD);
    }

    static void scheduleSlot(Context c, int slot) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c, slot);
        am.cancel(pi);

        SharedPreferences p = prefs(c);
        boolean on = p.getBoolean(slot == SLOT_VIRD ? K_V_ENABLED : K_ENABLED, false);
        if (!on) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, p.getInt(slot == SLOT_VIRD ? K_V_HOUR : K_HOUR, slot == SLOT_VIRD ? 6 : 21));
        cal.set(Calendar.MINUTE, p.getInt(slot == SLOT_VIRD ? K_V_MINUTE : K_MINUTE, 0));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 5000) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
}
