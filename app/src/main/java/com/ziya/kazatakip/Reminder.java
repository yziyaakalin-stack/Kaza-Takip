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

/** Günlük hatırlatmanın ayarları ve zamanlaması. */
final class Reminder {
    static final String CHANNEL = "kaza_hatirlatma";
    static final String ACTION = "com.ziya.kazatakip.HATIRLAT";
    static final String K_ENABLED = "rem_enabled";
    static final String K_HOUR = "rem_hour";
    static final String K_MINUTE = "rem_minute";
    static final String K_UNITS = "target_units";
    static final String K_MET = "met_date";

    private Reminder() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("kaza_prefs", Context.MODE_PRIVATE);
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Günlük hatırlatma", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Günlük kaza hedefini hatırlatır");
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

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Hatırlatma açıksa bir sonraki saati kurar, kapalıysa iptal eder. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c);
        am.cancel(pi);
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_ENABLED, false)) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, p.getInt(K_HOUR, 21));
        cal.set(Calendar.MINUTE, p.getInt(K_MINUTE, 0));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 5000) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
}
