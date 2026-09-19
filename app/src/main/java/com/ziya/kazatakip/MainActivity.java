package com.ziya.kazatakip;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String START_URL =
            "https://appassets.androidplatform.net/assets/index.html";
    private static final int REQ_SAVE = 11;
    private static final int REQ_OPEN = 12;
    private static final int REQ_NOTIF = 13;
    private static final int MAX_BACKUP_BYTES = 5 * 1024 * 1024;
    private static final int MAX_APK_BYTES = 60 * 1024 * 1024;
    /** Güncelleme yalnızca bu adresten indirilir. */
    private static final String UPDATE_PREFIX = "https://github.com/yziyaakalin-stack/Kaza-Takip/releases/";

    private WebView webView;
    private String pendingSave;
    private volatile boolean volumeCounting = false;
    private volatile boolean downloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Reminder.createChannel(this);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage: kayıtlar burada tutulur
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                return !"appassets.androidplatform.net".equals(u.getHost());
            }
        });
        webView.addJavascriptInterface(new Bridge(), "KazaNative");

        setContentView(webView);
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    /* ---------- JS'e geri çağrı ---------- */
    private void callJs(final String fn, final String args) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (webView == null) return;
                webView.evaluateJavascript(
                        "window." + fn + "&&window." + fn + "(" + args + ")", null);
            }
        });
    }

    private void reportReminder() {
        callJs("kazaOnReminder", JSONObject.quote(reminderJson()));
    }

    private String reminderJson() {
        SharedPreferences p = Reminder.prefs(this);
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", p.getBoolean(Reminder.K_ENABLED, false));
            o.put("hour", p.getInt(Reminder.K_HOUR, 21));
            o.put("minute", p.getInt(Reminder.K_MINUTE, 0));
            o.put("permission", Reminder.canNotify(this) ? "granted" : "denied");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean needsNotifPermission() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    /* ---------- Sayfanın çağırdığı yerel işlevler ---------- */
    private class Bridge {
        @JavascriptInterface
        public String getReminder() {
            return reminderJson();
        }

        @JavascriptInterface
        public void setReminder(final boolean enabled, final int hour, final int minute) {
            final int h = Math.max(0, Math.min(23, hour));
            final int m = Math.max(0, Math.min(59, minute));
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Reminder.prefs(MainActivity.this).edit()
                            .putInt(Reminder.K_HOUR, h)
                            .putInt(Reminder.K_MINUTE, m)
                            .putBoolean(Reminder.K_ENABLED, enabled)
                            .apply();
                    if (enabled && needsNotifPermission()) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                        return;
                    }
                    Reminder.schedule(MainActivity.this);
                    reportReminder();
                }
            });
        }

        /** Kurulu sürümün numarası ve adı. */
        @JavascriptInterface
        public String appVersion() {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                JSONObject o = new JSONObject();
                o.put("code", pi.versionCode);
                o.put("name", pi.versionName);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        /** Yeni sürümü indirir ve Android'in kurulum ekranını açar. */
        @JavascriptInterface
        public void downloadUpdate(final String url) {
            if (url == null || !url.startsWith(UPDATE_PREFIX)) {
                updateState("hata", 0, "Güncelleme adresi tanınmadı");
                return;
            }
            if (downloading) return;
            downloading = true;
            new Thread(new Runnable() {
                @Override public void run() {
                    HttpURLConnection conn = null;
                    try {
                        File dir = new File(getCacheDir(), "guncelleme");
                        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("klasör yok");
                        File apk = new File(dir, "KazaTakibi.apk");

                        String next = url;
                        for (int hop = 0; hop < 5; hop++) {
                            conn = (HttpURLConnection) new URL(next).openConnection();
                            conn.setInstanceFollowRedirects(false);
                            conn.setConnectTimeout(20000);
                            conn.setReadTimeout(30000);
                            int code = conn.getResponseCode();
                            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                                String loc = conn.getHeaderField("Location");
                                conn.disconnect();
                                if (loc == null) throw new IllegalStateException("yönlendirme yok");
                                next = new URL(new URL(next), loc).toString();
                                continue;
                            }
                            if (code != 200) throw new IllegalStateException("sunucu " + code);
                            break;
                        }

                        int total = conn.getContentLength();
                        InputStream in = conn.getInputStream();
                        FileOutputStream out = new FileOutputStream(apk);
                        byte[] buf = new byte[16384];
                        int n, got = 0, lastPct = -1;
                        while ((n = in.read(buf)) != -1) {
                            got += n;
                            if (got > MAX_APK_BYTES) throw new IllegalStateException("dosya çok büyük");
                            out.write(buf, 0, n);
                            int pct = total > 0 ? (int) (got * 100L / total) : 0;
                            if (pct != lastPct) {
                                lastPct = pct;
                                updateState("indiriliyor", pct, "");
                            }
                        }
                        out.flush();
                        out.close();
                        in.close();
                        installApk(apk);
                    } catch (Exception e) {
                        updateState("hata", 0, "İndirme başarısız, internetini kontrol et");
                    } finally {
                        if (conn != null) conn.disconnect();
                        downloading = false;
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void setVirdReminder(final boolean enabled, final int hour, final int minute,
                                    final String title, final String text) {
            final int h = Math.max(0, Math.min(23, hour));
            final int m = Math.max(0, Math.min(59, minute));
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Reminder.prefs(MainActivity.this).edit()
                            .putInt(Reminder.K_V_HOUR, h)
                            .putInt(Reminder.K_V_MINUTE, m)
                            .putString(Reminder.K_V_TITLE, title == null || title.isEmpty() ? "Vird vakti" : title)
                            .putString(Reminder.K_V_TEXT, text == null || text.isEmpty() ? "Bugünkü virdini unutma." : text)
                            .putBoolean(Reminder.K_V_ENABLED, enabled)
                            .apply();
                    if (enabled && needsNotifPermission()) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                        return;
                    }
                    Reminder.scheduleSlot(MainActivity.this, Reminder.SLOT_VIRD);
                }
            });
        }

        @JavascriptInterface
        public void setVirdMet(String dateIso) {
            Reminder.prefs(MainActivity.this).edit()
                    .putString(Reminder.K_V_MET, dateIso == null ? "" : dateIso).apply();
        }

        @JavascriptInterface
        public void setTargetUnits(int units) {
            Reminder.prefs(MainActivity.this).edit().putInt(Reminder.K_UNITS, units).apply();
        }

        @JavascriptInterface
        public void setTargetMet(String dateIso) {
            Reminder.prefs(MainActivity.this).edit()
                    .putString(Reminder.K_MET, dateIso == null ? "" : dateIso).apply();
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void setVolumeCounting(boolean on) {
            volumeCounting = on;
        }

        @JavascriptInterface
        public void saveFile(final String name, final String text) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    pendingSave = text;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, name);
                    try {
                        startActivityForResult(i, REQ_SAVE);
                    } catch (ActivityNotFoundException e) {
                        pendingSave = null;
                        callJs("kazaOnSaved", "false," + JSONObject.quote("Dosya seçici açılamadı, Paylaş'ı dene"));
                    }
                }
            });
        }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT, "Kaza takibi yedeği");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    try {
                        startActivity(Intent.createChooser(send, "Yedeği gönder"));
                    } catch (ActivityNotFoundException e) {
                        callJs("kazaOnFileError", JSONObject.quote("Paylaşacak uygulama bulunamadı"));
                    }
                }
            });
        }

        @JavascriptInterface
        public void openFile() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    // Tür filtresi yok: bazı telefonlar yedek dosyasına başka bir tür atadığı için
                    // filtre koyunca dosya soluk görünüp seçilemiyordu.
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    try {
                        startActivityForResult(i, REQ_OPEN);
                    } catch (ActivityNotFoundException e) {
                        callJs("kazaOnFileError", JSONObject.quote("Dosya seçici açılamadı"));
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = (data != null) ? data.getData() : null;

        if (requestCode == REQ_SAVE) {
            String text = pendingSave;
            pendingSave = null;
            if (resultCode != RESULT_OK || uri == null || text == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("akış yok");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                callJs("kazaOnSaved", "true,null");
            } catch (Exception e) {
                callJs("kazaOnSaved", "false," + JSONObject.quote("Yedek dosyaya yazılamadı"));
            }
        } else if (requestCode == REQ_OPEN) {
            if (resultCode != RESULT_OK || uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("akış yok");
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n, total = 0;
                while ((n = in.read(chunk)) != -1) {
                    total += n;
                    if (total > MAX_BACKUP_BYTES) throw new IllegalStateException("çok büyük");
                    buf.write(chunk, 0, n);
                }
                String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                callJs("kazaOnFile", JSONObject.quote(text));
            } catch (Exception e) {
                callJs("kazaOnFileError", JSONObject.quote("Dosya okunamadı"));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIF) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Reminder.prefs(this).edit()
                    .putBoolean(Reminder.K_ENABLED, false)
                    .putBoolean(Reminder.K_V_ENABLED, false)
                    .apply();
        }
        Reminder.schedule(this);
        reportReminder();
    }

    private void updateState(String durum, int yuzde, String mesaj) {
        callJs("kazaOnUpdate", JSONObject.quote(durum) + "," + yuzde + "," + JSONObject.quote(mesaj));
    }

    /** İndirilen APK için Android'in kurulum ekranını açar. */
    private void installApk(final File apk) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                        updateState("izin", 100, "Kurulum için izin ver, sonra tekrar dene");
                        Intent perm = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(perm);
                        return;
                    }
                    Uri u = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".dosya", apk);
                    Intent i = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(u, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    updateState("kuruluyor", 100, "");
                } catch (Exception e) {
                    updateState("hata", 0, "Kurulum ekranı açılamadı");
                }
            }
        });
    }

    /** Zikirmatik açıkken ses tuşları sayaç olarak çalışır. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (volumeCounting && (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                callJs("kazaZikirKey", "");
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onPause() {
        volumeCounting = false;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("window.kazaBack?window.kazaBack():false", value -> {
            if (!"true".equals(value)) super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
