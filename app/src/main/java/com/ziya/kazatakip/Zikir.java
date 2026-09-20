package com.ziya.kazatakip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Ekran kapalıyken ya da ana ekrandan zikir çekmek için ortak yardımcı.
 * Sayılan her dokunuş prefs'te birikir, uygulama açılınca JS tarafı bunları alır.
 */
final class Zikir {
    static final String CHANNEL = "zikir_sayac_v2";
    private static final String ESKI_CHANNEL = "zikir_sayac";
    static final String ACTION_TICK = "com.ziya.kazatakip.ZIKIR_ARTIR";
    static final String ACTION_STOP = "com.ziya.kazatakip.ZIKIR_BITIR";

    static final String K_ID = "zikir_id";
    static final String K_AD = "zikir_ad";
    static final String K_SAY = "zikir_say";
    static final String K_HEDEF = "zikir_hedef";
    static final String K_TICKS = "zikir_ticks";
    static final String K_BILDIRIM = "zikir_bildirim";

    static final String K_W_KALAN = "w_kalan";
    static final String K_W_BUGUN = "w_bugun";
    static final String K_W_HEDEF = "w_hedef";

    static final int NOTIF_ID = 42;

    private Zikir() {}

    static SharedPreferences prefs(Context c) {
        return Reminder.prefs(c);
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        // Sessiz kanal bazı telefonlarda kilit ekranında gizleniyor; sesi ve titreşimi
        // kapalı ama normal öncelikli bir kanal kullanıyoruz.
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Zikir sayacı", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Ekran kapalıyken zikir çekmek için kilit ekranında duran sayaç");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.enableLights(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        try {
            nm.deleteNotificationChannel(ESKI_CHANNEL);
        } catch (Exception ignored) {
        }
    }

    private static PendingIntent action(Context c, String act, int kod) {
        Intent i = new Intent(c, ZikirReceiver.class).setAction(act);
        return PendingIntent.getBroadcast(c, kod, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sabit bildirimi gösterir ya da günceller. */
    static void showNotification(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_BILDIRIM, false)) return;
        if (!Reminder.canNotify(c)) return;

        String ad = p.getString(K_AD, "Zikir");
        int say = p.getInt(K_SAY, 0) + p.getInt(K_TICKS, 0);
        int hedef = p.getInt(K_HEDEF, 0);
        String metin = hedef > 0 ? (say % hedef) + " / " + hedef + (say >= hedef ? "  ·  " + (say / hedef) + ". tur" : "") : say + " çekildi";

        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(ad)
                .setContentText(metin)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notif),
                        "+1", action(c, ACTION_TICK, 71)).build())
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notif),
                        "Bitir", action(c, ACTION_STOP, 72)).build());
        b.setVisibility(Notification.VISIBILITY_PUBLIC);
        b.setCategory(Notification.CATEGORY_PROGRESS);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT);

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, b.build());
    }

    static void hideNotification(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
    }
}
