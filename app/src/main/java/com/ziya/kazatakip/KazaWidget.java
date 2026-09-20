package com.ziya.kazatakip;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Ana ekran aracı: kalan kaza sayısı ve tek dokunuşla zikir sayacı. */
public class KazaWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, views(c));
    }

    /** Sayılar değiştiğinde aracı yeniler. */
    static void refresh(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, KazaWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews rv = views(c);
        for (int id : ids) mgr.updateAppWidget(id, rv);
    }

    private static RemoteViews views(Context c) {
        SharedPreferences p = Zikir.prefs(c);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_kaza);

        int kalan = p.getInt(Zikir.K_W_KALAN, -1);
        int bugun = p.getInt(Zikir.K_W_BUGUN, 0);
        int hedef = p.getInt(Zikir.K_W_HEDEF, 0);
        rv.setTextViewText(R.id.widget_kalan, kalan < 0 ? "—" : String.valueOf(kalan));
        rv.setTextViewText(R.id.widget_alt,
                hedef > 0 ? "vakit kaldı · bugün " + bugun + "/" + hedef : "vakit kaldı");

        int say = p.getInt(Zikir.K_SAY, 0) + p.getInt(Zikir.K_TICKS, 0);
        int zhedef = p.getInt(Zikir.K_HEDEF, 0);
        rv.setTextViewText(R.id.widget_zikir_ad, p.getString(Zikir.K_AD, "Zikir"));
        rv.setTextViewText(R.id.widget_zikir_say,
                zhedef > 0 ? (say % zhedef) + " / " + zhedef : String.valueOf(say));

        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        rv.setOnClickPendingIntent(R.id.widget_kaza_alan, PendingIntent.getActivity(c, 8, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent tick = new Intent(c, ZikirReceiver.class).setAction(Zikir.ACTION_TICK);
        rv.setOnClickPendingIntent(R.id.widget_zikir_say, PendingIntent.getBroadcast(c, 9, tick,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        return rv;
    }
}
