package com.ziya.kazatakip;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String START_URL =
            "https://appassets.androidplatform.net/assets/index.html";
    private static final int REQ_SAVE = 11;
    private static final int REQ_OPEN = 12;
    private static final int REQ_NOTIF = 13;
    private static final int MAX_BACKUP_BYTES = 5 * 1024 * 1024;

    private WebView webView;
    private String pendingSave;

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
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"application/json", "text/plain", "application/octet-stream"});
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
            Reminder.prefs(this).edit().putBoolean(Reminder.K_ENABLED, false).apply();
        }
        Reminder.schedule(this);
        reportReminder();
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
