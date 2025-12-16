/*
 * Frappe FCM - Universal Push Notification App
 *
 * This app connects to any Frappe site and receives push notifications.
 * Firebase is initialized dynamically based on the site's FCM Settings.
 * No google-services.json needed - each site provides its own Firebase config!
 */

package io.frappe.fcm;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FrappeFCM";
    private static final int MAX_TOKEN_WAIT_ATTEMPTS = 10;

    // UI Elements
    private LinearLayout loginLayout;
    private LinearLayout connectedLayout;
    private EditText urlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private Button connectButton;
    private Button disconnectButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView connectedUrlText;
    private TextView connectedUserText;

    // State
    private String fcmToken;
    private String sessionCookie;
    private boolean tokenFetching = false;
    private boolean firebaseInitialized = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Permission launcher
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            Log.d(TAG, "Notification permission granted");
                        } else {
                            Toast.makeText(this, "Enable notifications in Settings", Toast.LENGTH_LONG).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        initViews();

        // Create notification channel
        createNotificationChannel();

        // Request notification permission (Android 13+)
        requestNotificationPermission();

        // Check if already connected (and reinitialize Firebase if needed)
        checkExistingConnection();

        // Handle notification click - open URL if present
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle notification click when app is already running
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("url")) {
            String url = intent.getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                Log.d(TAG, "Opening URL from notification: " + url);
                openUrlInBrowser(url);
                // Clear the extra to prevent re-opening on rotation
                intent.removeExtra("url");
            }
        }
    }

    private void openUrlInBrowser(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open URL: " + url, e);
            Toast.makeText(this, "Could not open URL", Toast.LENGTH_SHORT).show();
        }
    }

    private void initViews() {
        loginLayout = findViewById(R.id.loginLayout);
        connectedLayout = findViewById(R.id.connectedLayout);
        urlInput = findViewById(R.id.urlInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        connectedUrlText = findViewById(R.id.connectedUrlText);
        connectedUserText = findViewById(R.id.connectedUserText);

        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> confirmDisconnect());
    }

    private void checkExistingConnection() {
        SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
        boolean isConnected = prefs.getBoolean("is_connected", false);

        if (isConnected) {
            String url = prefs.getString(Config.PREF_SITE_URL, "");
            String user = prefs.getString("connected_user", "");

            // Try to reinitialize Firebase with saved config
            String projectId = prefs.getString("firebase_project_id", "");
            String senderId = prefs.getString("firebase_sender_id", "");
            String apiKey = prefs.getString("firebase_api_key", "");
            String appId = prefs.getString("firebase_app_id", "");

            if (!projectId.isEmpty() && !senderId.isEmpty() && !apiKey.isEmpty() && !appId.isEmpty()) {
                initializeFirebase(projectId, senderId, apiKey, appId);
            }

            showConnectedState(url, user);
        } else {
            showLoginState();
        }
    }

    private void showLoginState() {
        loginLayout.setVisibility(View.VISIBLE);
        connectedLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        statusText.setText("");
        statusText.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
    }

    private void showConnectedState(String url, String user) {
        loginLayout.setVisibility(View.GONE);
        connectedLayout.setVisibility(View.VISIBLE);
        connectedUrlText.setText(url);
        connectedUserText.setText(user);
    }

    private void connect() {
        String url = urlInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Validate inputs
        if (url.isEmpty()) {
            urlInput.setError("Enter site URL");
            return;
        }
        if (username.isEmpty()) {
            usernameInput.setError("Enter username");
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Enter password");
            return;
        }

        // Add https if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        final String siteUrl = url;

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        connectButton.setEnabled(false);
        statusText.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
        statusText.setText("Connecting...");

        // If token not ready, wait for it
        if (fcmToken == null) {
            statusText.setText("Getting device token...");
            waitForTokenAndConnect(siteUrl, username, password, 0);
        } else {
            performConnection(siteUrl, username, password);
        }
    }

    private void waitForTokenAndConnect(String siteUrl, String username, String password, int attempt) {
        if (fcmToken != null) {
            // Token is ready, proceed
            performConnection(siteUrl, username, password);
            return;
        }

        if (attempt >= MAX_TOKEN_WAIT_ATTEMPTS) {
            // Timeout - show dialog to proceed anyway or cancel
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("FCM Token Not Available")
                        .setMessage("Could not get Firebase token. This usually means:\n\n" +
                                "1. Google Play Services is not installed\n" +
                                "2. The app needs a valid google-services.json\n\n" +
                                "Would you like to test the login anyway? (You won't receive notifications)")
                        .setPositiveButton("Test Login", (dialog, which) -> {
                            fcmToken = "TEST_TOKEN_" + System.currentTimeMillis();
                            performConnection(siteUrl, username, password);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            connectButton.setEnabled(true);
                        })
                        .show();
            });
            getFCMToken(); // Try again for next time
            return;
        }

        // Wait 500ms and check again
        mainHandler.postDelayed(() -> waitForTokenAndConnect(siteUrl, username, password, attempt + 1), 500);
    }

    private void performConnection(String siteUrl, String username, String password) {
        executor.execute(() -> {
            try {
                // Step 1: Fetch Firebase config from site
                mainHandler.post(() -> statusText.setText("Fetching Firebase config..."));
                JSONObject firebaseConfig = fetchFirebaseConfig(siteUrl);

                if (firebaseConfig == null) {
                    mainHandler.post(() -> {
                        showError("Site not configured for FCM. Admin needs to set up google-services.json in FCM Settings.");
                        connectButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                    });
                    return;
                }

                // Step 2: Initialize Firebase with fetched config
                mainHandler.post(() -> statusText.setText("Initializing Firebase..."));
                String projectId = firebaseConfig.optString("project_id");
                String senderId = firebaseConfig.optString("sender_id");
                String apiKey = firebaseConfig.optString("api_key");
                String appId = firebaseConfig.optString("app_id");

                mainHandler.post(() -> {
                    initializeFirebase(projectId, senderId, apiKey, appId);
                });

                // Wait for Firebase to initialize
                Thread.sleep(500);

                // Step 3: Login to Frappe
                mainHandler.post(() -> statusText.setText("Logging in..."));
                String loginResult = login(siteUrl, username, password);

                if (loginResult == null) {
                    mainHandler.post(() -> {
                        showError("Login failed. Check credentials.");
                        connectButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                    });
                    return;
                }

                // Step 4: Get FCM token (now that Firebase is initialized)
                mainHandler.post(() -> statusText.setText("Getting device token..."));
                waitForFCMToken(siteUrl, username, projectId, senderId, apiKey, appId);

            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                mainHandler.post(() -> {
                    showError("Connection failed: " + e.getMessage());
                    connectButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void waitForFCMToken(String siteUrl, String username, String projectId, String senderId, String apiKey, String appId) {
        getFCMToken();

        // Wait for token with timeout
        mainHandler.postDelayed(new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                attempts++;
                if (fcmToken != null) {
                    // Token ready, continue with registration
                    completeRegistration(siteUrl, username, projectId, senderId, apiKey, appId);
                } else if (attempts < MAX_TOKEN_WAIT_ATTEMPTS) {
                    mainHandler.postDelayed(this, 500);
                } else {
                    // Timeout - proceed with test token
                    fcmToken = "TEST_TOKEN_" + System.currentTimeMillis();
                    completeRegistration(siteUrl, username, projectId, senderId, apiKey, appId);
                }
            }
        }, 500);
    }

    private void completeRegistration(String siteUrl, String username, String projectId, String senderId, String apiKey, String appId) {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> statusText.setText("Registering device..."));

                boolean registered = registerToken(siteUrl, fcmToken);

                if (registered) {
                    // Save connection info including Firebase config
                    SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putBoolean("is_connected", true)
                            .putBoolean(Config.PREF_CONFIGURED, true)
                            .putString(Config.PREF_SITE_URL, siteUrl)
                            .putString("connected_user", username)
                            .putString("session_cookie", sessionCookie)
                            .putString("firebase_project_id", projectId)
                            .putString("firebase_sender_id", senderId)
                            .putString("firebase_api_key", apiKey)
                            .putString("firebase_app_id", appId)
                            .apply();

                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "Connected! You will receive notifications.", Toast.LENGTH_LONG).show();
                        showConnectedState(siteUrl, username);
                    });
                } else {
                    mainHandler.post(() -> {
                        showError("Failed to register device. Check if frappe_fcm is installed.");
                        connectButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Registration failed", e);
                mainHandler.post(() -> {
                    showError("Registration failed: " + e.getMessage());
                    connectButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private JSONObject fetchFirebaseConfig(String siteUrl) {
        try {
            URL url = new URL(siteUrl + "/api/method/frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.get_firebase_config");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Firebase config response code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONObject message = json.optJSONObject("message");

                if (message != null && message.optBoolean("success", false)) {
                    return message.optJSONObject("config");
                }

                Log.e(TAG, "Firebase config error: " + (message != null ? message.optString("message") : "Unknown"));
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch Firebase config", e);
        }
        return null;
    }

    private void initializeFirebase(String projectId, String senderId, String apiKey, String appId) {
        if (firebaseInitialized) {
            Log.d(TAG, "Firebase already initialized");
            return;
        }

        try {
            // Check if default app already exists
            try {
                FirebaseApp existingApp = FirebaseApp.getInstance();
                if (existingApp != null) {
                    Log.d(TAG, "Firebase app already exists, deleting...");
                    existingApp.delete();
                }
            } catch (IllegalStateException e) {
                // No existing app, continue
            }

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setGcmSenderId(senderId)
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .build();

            FirebaseApp.initializeApp(this, options);
            firebaseInitialized = true;
            Log.d(TAG, "Firebase initialized with project: " + projectId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase", e);
        }
    }

    private String login(String siteUrl, String username, String password) {
        try {
            URL url = new URL(siteUrl + "/api/method/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            String body = "usr=" + java.net.URLEncoder.encode(username, "UTF-8") +
                    "&pwd=" + java.net.URLEncoder.encode(password, "UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Login response code: " + responseCode);

            if (responseCode == 200) {
                // Get ALL session cookies (Frappe sends multiple Set-Cookie headers)
                StringBuilder cookieBuilder = new StringBuilder();
                java.util.Map<String, java.util.List<String>> headers = conn.getHeaderFields();
                java.util.List<String> cookieHeaders = headers.get("Set-Cookie");
                if (cookieHeaders != null) {
                    for (String cookie : cookieHeaders) {
                        // Extract just the cookie name=value part (before ;)
                        String cookiePart = cookie.split(";")[0];
                        if (cookieBuilder.length() > 0) {
                            cookieBuilder.append("; ");
                        }
                        cookieBuilder.append(cookiePart);
                    }
                    sessionCookie = cookieBuilder.toString();
                    Log.d(TAG, "Got session cookies: " + sessionCookie);
                }

                // Read response
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Log.d(TAG, "Login response: " + response.toString());
                conn.disconnect();
                return response.toString();
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Login error", e);
        }
        return null;
    }

    private boolean registerToken(String siteUrl, String token) {
        try {
            URL url = new URL(siteUrl + Config.TOKEN_REGISTER_PATH);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            // Send session cookie
            if (sessionCookie != null) {
                conn.setRequestProperty("Cookie", sessionCookie);
                Log.d(TAG, "Sending cookies: " + sessionCookie);
            } else {
                Log.w(TAG, "No session cookie available!");
            }

            // Get device info
            String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
            String osVersion = "Android " + Build.VERSION.RELEASE;

            String body = "token=" + java.net.URLEncoder.encode(token, "UTF-8") +
                    "&device_model=" + java.net.URLEncoder.encode(deviceModel, "UTF-8") +
                    "&os_version=" + java.net.URLEncoder.encode(osVersion, "UTF-8") +
                    "&app_version=1.0.0";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Register token response: " + responseCode);

            // Read response
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseCode >= 200 && responseCode < 300 ?
                            conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            Log.d(TAG, "Register response: " + response.toString());

            conn.disconnect();

            // Success if HTTP 200 and no exception in response
            String resp = response.toString();
            Log.d(TAG, "Register HTTP code: " + responseCode + ", response: " + resp);

            // Success if 200 and doesn't contain "exception"
            if (responseCode == 200 && !resp.contains("exception")) {
                return true;
            }

            // Also accept if success:true is in response
            if (resp.toLowerCase().contains("success\":true") ||
                resp.toLowerCase().contains("success\": true")) {
                return true;
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Register token error", e);
        }
        return false;
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect")
                .setMessage("You will stop receiving notifications. Continue?")
                .setPositiveButton("Yes", (dialog, which) -> disconnect())
                .setNegativeButton("No", null)
                .show();
    }

    private void disconnect() {
        // Clear saved data
        SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();

        // Clear inputs
        urlInput.setText("");
        usernameInput.setText("");
        passwordInput.setText("");

        // Show login
        showLoginState();

        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        statusText.setText(message);
        statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
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
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void getFCMToken() {
        if (tokenFetching) return;
        tokenFetching = true;

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    tokenFetching = false;
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "FCM token failed", task.getException());
                        return;
                    }
                    fcmToken = task.getResult();
                    Log.d(TAG, "FCM Token obtained: " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
