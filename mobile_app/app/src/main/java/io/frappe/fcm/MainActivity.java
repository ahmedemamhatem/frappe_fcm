/*
 * Frappe FCM - Main Activity
 *
 * This activity displays your Frappe site in a WebView and handles
 * FCM token registration when users log in.
 */

package io.frappe.fcm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FrappeFCM";

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private View offlineView;

    private ValueCallback<Uri[]> fileUploadCallback;
    private String fcmToken;
    private String currentUser;
    private boolean tokenRegistered = false;

    // Site URL from SharedPreferences
    private String siteUrl;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // File chooser launcher
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (fileUploadCallback != null) {
                            Uri[] results = null;
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                String dataString = result.getData().getDataString();
                                if (dataString != null) {
                                    results = new Uri[]{Uri.parse(dataString)};
                                }
                            }
                            fileUploadCallback.onReceiveValue(results);
                            fileUploadCallback = null;
                        }
                    }
            );

    // Permission launcher
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            Log.d(TAG, "Notification permission granted");
                        } else {
                            Log.d(TAG, "Notification permission denied");
                            Toast.makeText(this,
                                    "Enable notifications in Settings to receive alerts",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if configured
        SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(Config.PREF_CONFIGURED, false)) {
            // Not configured, go to setup
            startSetupActivity();
            return;
        }

        // Get site URL from preferences
        siteUrl = prefs.getString(Config.PREF_SITE_URL, "");
        if (siteUrl.isEmpty()) {
            startSetupActivity();
            return;
        }

        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate - Frappe FCM App");
        Log.d(TAG, "Target URL: " + siteUrl);

        // Initialize views
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        offlineView = findViewById(R.id.offlineView);

        // Enable hardware acceleration for WebView
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Create notification channel
        createNotificationChannel();

        // Request notification permission (Android 13+)
        requestNotificationPermission();

        // Setup WebView
        setupWebView();

        // Setup SwipeRefresh
        setupSwipeRefresh();

        // Get FCM Token
        getFCMToken();

        // Load URL
        Log.d(TAG, "Loading URL: " + siteUrl);
        webView.loadUrl(siteUrl);

        // Handle deep link / notification click
        handleIntent(getIntent());
    }

    private void startSetupActivity() {
        Intent intent = new Intent(this, SetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // Handle deep link
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                webView.loadUrl(data.toString());
            }
        }

        // Handle notification click (URL from FCMService)
        if (intent.hasExtra("url")) {
            String url = intent.getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                webView.loadUrl(url);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            webView.reload();
            return true;
        } else if (id == R.id.action_settings) {
            showSettingsDialog();
            return true;
        } else if (id == R.id.action_change_site) {
            confirmChangeSite();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("App Settings")
                .setMessage("Current Site: " + siteUrl + "\n\nFCM Token: " + (fcmToken != null ? fcmToken.substring(0, 20) + "..." : "Not available"))
                .setPositiveButton("OK", null)
                .setNeutralButton("Change Site", (d, w) -> confirmChangeSite())
                .show();
    }

    private void confirmChangeSite() {
        new AlertDialog.Builder(this)
                .setTitle("Change Site")
                .setMessage("Are you sure you want to change the Frappe site? You will need to reconfigure the app.")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Clear preferences
                    SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
                    prefs.edit().clear().apply();

                    // Clear WebView data
                    webView.clearCache(true);
                    webView.clearHistory();
                    CookieManager.getInstance().removeAllCookies(null);

                    // Go to setup
                    startSetupActivity();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Enable JavaScript (required for Frappe)
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // DOM Storage
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // File access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Zoom
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Mixed content (allow HTTP on HTTPS for development)
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // User Agent
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " FrappeFCM-Android/1.0");

        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // WebView Client
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (Config.DEBUG) Log.d(TAG, "Page started: " + url);
                progressBar.setVisibility(View.VISIBLE);
                offlineView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (Config.DEBUG) Log.d(TAG, "Page finished: " + url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                // Inject FCM token to JavaScript (optional - for JS access)
                if (fcmToken != null) {
                    String js = "window.FCM_TOKEN = '" + fcmToken + "';";
                    webView.evaluateJavascript(js, null);
                }

                // Detect logged-in user and register token
                detectUserAndRegisterToken();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    Log.e(TAG, "WebView error: " + error.getDescription());
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    offlineView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                Log.e(TAG, "SSL Error: " + error.toString());
                // For production, handle SSL errors properly
                // For development with self-signed certs, you can proceed
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Open external links in browser
                if (!url.startsWith(siteUrl)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                return false;
            }
        });

        // WebChrome Client for file upload
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Cannot open file chooser", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    private int detectionAttempts = 0;
    private static final int MAX_DETECTION_ATTEMPTS = 10;

    private void detectUserAndRegisterToken() {
        // Stop if already registered or too many attempts
        if (tokenRegistered || detectionAttempts >= MAX_DETECTION_ATTEMPTS) {
            if (tokenRegistered) {
                Log.d(TAG, "Token already registered, stopping detection");
            } else {
                Log.d(TAG, "Max detection attempts reached");
            }
            return;
        }

        detectionAttempts++;

        // Get logged-in user from Frappe JavaScript
        String js = "(function() { " +
                "if (typeof frappe !== 'undefined' && frappe.session && frappe.session.user) { " +
                "  return frappe.session.user; " +
                "} " +
                "return ''; " +
                "})()";

        webView.evaluateJavascript(js, value -> {
            String user = value.replace("\"", "").trim();

            if (!user.isEmpty() && !user.equals("null") && !user.equals("Guest")) {
                Log.d(TAG, "Logged in user detected: " + user + " (attempt " + detectionAttempts + ")");

                // Register token if user changed or not yet registered
                if (!user.equals(currentUser) || !tokenRegistered) {
                    currentUser = user;

                    if (fcmToken != null) {
                        registerTokenForUser(fcmToken, user);
                    } else {
                        Log.d(TAG, "FCM token not yet available, will retry");
                    }
                }
            } else {
                Log.d(TAG, "User not logged in yet (attempt " + detectionAttempts + ")");
            }

            // Retry detection if not yet registered
            if (!tokenRegistered && detectionAttempts < MAX_DETECTION_ATTEMPTS) {
                new Handler(Looper.getMainLooper()).postDelayed(this::detectUserAndRegisterToken, 3000);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        // Disable swipe refresh when scrolled down
        webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                swipeRefresh.setEnabled(scrollY == 0));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Config.NOTIFICATION_CHANNEL_ID,
                    Config.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(Config.NOTIFICATION_CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "FCM token failed", task.getException());
                        return;
                    }

                    fcmToken = task.getResult();
                    Log.d(TAG, "FCM Token obtained: " + fcmToken.substring(0, 20) + "...");

                    // If user already logged in, register token
                    if (currentUser != null && !currentUser.isEmpty()) {
                        registerTokenForUser(fcmToken, currentUser);
                    }
                });
    }

    private void registerTokenForUser(String token, String user) {
        executor.execute(() -> {
            try {
                URL url = new URL(siteUrl + Config.TOKEN_REGISTER_PATH);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                // Send cookies for authentication - critical for Frappe session
                String cookies = CookieManager.getInstance().getCookie(siteUrl);
                if (cookies != null) {
                    conn.setRequestProperty("Cookie", cookies);
                    Log.d(TAG, "Sending cookies: " + cookies.substring(0, Math.min(50, cookies.length())) + "...");
                } else {
                    Log.w(TAG, "No cookies available for authentication!");
                }

                // Get device info
                String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
                String osVersion = "Android " + Build.VERSION.RELEASE;

                // Build form-urlencoded body (Frappe prefers this format)
                // Don't send user parameter - let server use session user
                String formBody = "token=" + java.net.URLEncoder.encode(token, "UTF-8") +
                        "&device_model=" + java.net.URLEncoder.encode(deviceModel, "UTF-8") +
                        "&os_version=" + java.net.URLEncoder.encode(osVersion, "UTF-8") +
                        "&app_version=1.0.0";

                Log.d(TAG, "Registering token for user: " + user);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(formBody.getBytes());
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Token registration response code: " + responseCode);

                // Read response
                BufferedReader reader;
                if (responseCode >= 200 && responseCode < 300) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Log.d(TAG, "Registration response: " + response.toString());

                final String responseStr = response.toString();
                mainHandler.post(() -> {
                    if (responseCode == 200 && responseStr.contains("\"success\"")) {
                        tokenRegistered = true;
                        Log.d(TAG, "FCM token registered successfully for user: " + user);
                        Toast.makeText(MainActivity.this, "Push notifications enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "Token registration failed: " + responseStr);
                    }
                });

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Token registration failed with exception", e);
            }
        });
    }

    public void retryConnection(View view) {
        offlineView.setVisibility(View.GONE);
        webView.reload();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
