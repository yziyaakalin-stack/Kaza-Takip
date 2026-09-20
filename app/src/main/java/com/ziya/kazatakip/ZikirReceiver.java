package com.ziya.kazatakip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Vibrator;

/** Bildirimdeki ve ana ekran aracındaki "+1" dokunuşlarını işler. */
public class ZikirReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String act = intent == null ? null : intent.getAction();
        if (act == null) return;

        SharedPreferences p = Zikir.prefs(c);
        if (Zikir.ACTION_TICK.equals(act)) {
            p.edit().putInt(Zikir.K_TICKS, p.getInt(Zikir.K_TICKS, 0) + 1).apply();
            try {
                Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(15);
            } catch (Exception ignored) {
            }
            Zikir.showNotification(c);
            KazaWidget.refresh(c);
        } else if (Zikir.ACTION_STOP.equals(act)) {
            p.edit().putBoolean(Zikir.K_BILDIRIM, false).apply();
            Zikir.hideNotification(c);
        }
    }
}
