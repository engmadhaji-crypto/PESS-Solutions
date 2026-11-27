package com.pees.agents;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText serverUrl;
    private TextView status;
    private WebView webView;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @SuppressLint("SetJavaScriptEnabled")
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
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
        });

        initializeFirebase();
        io.execute(() -> {
            AppSettings saved = database.settings().get();
            runOnUiThread(() -> {
                if (saved != null && !TextUtils.isEmpty(saved.serverUrl)) serverUrl.setText(saved.serverUrl);
            });
        });
        open.setOnClickListener(v -> openSystem());
    }

    private void initializeFirebase() {
        try {
            FirebaseApp app = FirebaseApp.initializeApp(this);
            if (app != null) {
                auth = FirebaseAuth.getInstance(app);
                firestore = FirebaseFirestore.getInstance(app);
                status.setText("Room محلي جاهز — Firebase متاح للمزامنة");
            } else {
                status.setText("Room محلي جاهز — أضف google-services.json لتفعيل Firebase");
            }
        } catch (Exception ignored) {
            status.setText("Room محلي جاهز — إعداد Firebase غير مكتمل");
        }
    }

    private void openSystem() {
        String url = serverUrl.getText().toString().trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            status.setText("أدخل رابطًا صحيحًا يبدأ بـ https://");
            return;
        }
        AppSettings settings = new AppSettings();
        settings.serverUrl = url;
        settings.lastSync = System.currentTimeMillis();
        io.execute(() -> database.settings().save(settings));
        syncSettingsToFirebase(settings);
        webView.setVisibility(View.VISIBLE);
        serverUrl.setVisibility(View.GONE);
        findViewById(com.pees.agents.R.id.openButton).setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        webView.loadUrl(url);
    }

    private void syncSettingsToFirebase(AppSettings settings) {
        if (auth == null || firestore == null) return;
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously().addOnSuccessListener(result -> writeSettings(settings));
        } else {
            writeSettings(settings);
        }
    }

    private void writeSettings(AppSettings settings) {
        if (auth.getCurrentUser() == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("serverUrl", settings.serverUrl);
        data.put("lastSync", settings.lastSync);
        firestore.collection("user_settings").document(auth.getCurrentUser().getUid()).set(data);
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
