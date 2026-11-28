package com.pees.agents;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText serverUrl;
    private TextView status;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private static final int FILE_REQUEST = 4107;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.pees.agents.R.layout.activity_main);
        database = AppDatabase.get(this);
        serverUrl = findViewById(com.pees.agents.R.id.serverUrl);
        status = findViewById(com.pees.agents.R.id.status);
        webView = findViewById(com.pees.agents.R.id.webView);
        Button open = findViewById(com.pees.agents.R.id.openButton);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, FILE_REQUEST); }
                catch (Exception e) { fileCallback = null; callback.onReceiveValue(null); }
                return true;
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setTitle("PESS الحلول الهندسية");
                request.setDescription("جارٍ تنزيل الملف");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName(contentDisposition, "PESS-export"));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, "بدأ تنزيل الملف إلى مجلد Downloads", Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(this, "تعذر تنزيل الملف", Toast.LENGTH_LONG).show(); }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
        });

        initializeFirebase();
        io.execute(() -> {
            AppSettings saved = database.settings().get();
            runOnUiThread(() -> { if (saved != null && !TextUtils.isEmpty(saved.serverUrl)) serverUrl.setText(saved.serverUrl); });
        });
        open.setOnClickListener(v -> openSystem());
    }

    private String safeFileName(String disposition, String fallback) {
        String name = fallback + ".json";
        if (disposition != null && disposition.contains("filename=")) name = disposition.substring(disposition.indexOf("filename=") + 9).replace("\"", "").trim();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }
        @JavascriptInterface public void saveTextFile(String name, String text, String mime) {
            io.execute(() -> {
                try {
                    String clean = (name == null || name.trim().isEmpty()) ? "PESS-export.txt" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String type = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, clean);
                        values.put(MediaStore.Downloads.MIME_TYPE, type);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new IllegalStateException("storage");
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("downloads");
                        try (FileOutputStream out = new FileOutputStream(new File(dir, clean))) { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                    }
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "تم حفظ الملف في Downloads", Toast.LENGTH_LONG).show());
                } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
            });
        }
    }

    private void initializeFirebase() {
        try {
            FirebaseApp app = FirebaseApp.initializeApp(this);
            if (app != null) { auth = FirebaseAuth.getInstance(app); firestore = FirebaseFirestore.getInstance(app); status.setText("Room محلي جاهز — Firebase متاح للمزامنة"); }
            else status.setText("Room محلي جاهز — أضف google-services.json لتفعيل Firebase");
        } catch (Exception ignored) { status.setText("Room محلي جاهز — إعداد Firebase غير مكتمل"); }
    }

    private void openSystem() {
        String url = serverUrl.getText().toString().trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) { status.setText("أدخل رابطًا صحيحًا يبدأ بـ https://"); return; }
        AppSettings settings = new AppSettings(); settings.serverUrl = url; settings.lastSync = System.currentTimeMillis();
        io.execute(() -> database.settings().save(settings)); syncSettingsToFirebase(settings);
        webView.setVisibility(android.view.View.VISIBLE); serverUrl.setVisibility(android.view.View.GONE);
        findViewById(com.pees.agents.R.id.openButton).setVisibility(android.view.View.GONE); status.setVisibility(android.view.View.GONE); webView.loadUrl(url);
    }

    private void syncSettingsToFirebase(AppSettings settings) {
        if (auth == null || firestore == null) return;
        if (auth.getCurrentUser() == null) auth.signInAnonymously().addOnSuccessListener(result -> writeSettings(settings)); else writeSettings(settings);
    }
    private void writeSettings(AppSettings settings) {
        if (auth.getCurrentUser() == null) return;
        Map<String, Object> data = new HashMap<>(); data.put("serverUrl", settings.serverUrl); data.put("lastSync", settings.lastSync);
        firestore.collection("user_settings").document(auth.getCurrentUser().getUid()).set(data);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(result); fileCallback = null;
        }
    }
    @Override protected void onDestroy() { if (fileCallback != null) fileCallback.onReceiveValue(null); io.shutdownNow(); if (webView != null) webView.destroy(); super.onDestroy(); }
}
