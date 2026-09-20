package com.ziya.kazatakip;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Kurulan saatte çalışır: o gün tamamlanmadıysa bildirim gösterir, ertesi günü kurar. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null || !Reminder.ACTION.equals(intent.getAction())) return;
        int slot = intent.getIntExtra(Reminder.EXTRA_SLOT, Reminder.SLOT_KAZA);
        Reminder.scheduleSlot(c, slot);

        SharedPreferences p = Reminder.prefs(c);
        boolean vird = slot == Reminder.SLOT_VIRD;
        boolean vakit = slot >= Reminder.SLOT_VAKIT;
        if (vakit) {
            if (!p.getBoolean(Reminder.K_N_ENABLED, false)) return;
        } else if (!p.getBoolean(vird ? Reminder.K_V_ENABLED : Reminder.K_ENABLED, false)) return;
        // Günlük kaza hedefi tamamsa vakit bildirimi de gelmez
        String metKey = vird ? Reminder.K_V_MET : Reminder.K_MET;
        if (Reminder.today().equals(p.getString(metKey, ""))) return;
        if (!Reminder.canNotify(c)) return;

        String title;
        String text;
        if (vakit) {
            org.json.JSONObject o = Reminder.vakitler(c).optJSONObject(slot - Reminder.SLOT_VAKIT);
            if (o == null) return;
            title = o.optString("t", "Kaza vakti");
            text = o.optString("x", "Bir kaza namazı kılmaya ne dersin?");
        } else if (vird) {
            title = p.getString(Reminder.K_V_TITLE, "Vird vakti");
            text = p.getString(Reminder.K_V_TEXT, "Bugünkü virdini unutma.");
        } else {
            int units = p.getInt(Reminder.K_UNITS, 0);
            title = "Kaza vakti";
            text = units > 0
                    ? "Bugünkü hedefin " + units + " vakit. Kerahat vakti dışında kılabilirsin."
                    : "Bugünkü kazanı kılmayı unutma.";
        }

        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 100 + slot, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, Reminder.CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(slot, n);
    }
}
