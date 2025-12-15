/*
 * Frappe FCM - Simple Push Notification App
 *
 * This app connects to any Frappe site and receives push notifications.
 * Users enter site URL, username, and password to authenticate.
 */

package io.frappe.fcm;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

        // Get FCM Token
        getFCMToken();

        // Check if already connected
        checkExistingConnection();
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
        statusText.setText("Connecting...");

        // Login and register token
        executor.execute(() -> {
            try {
                // Step 1: Login to Frappe
                String loginResult = login(siteUrl, username, password);

                if (loginResult == null) {
                    mainHandler.post(() -> {
                        showError("Login failed. Check credentials.");
                        connectButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                    });
                    return;
                }

                mainHandler.post(() -> statusText.setText("Registering device..."));

                // Step 2: Register FCM token
                if (fcmToken != null) {
                    boolean registered = registerToken(siteUrl, fcmToken);

                    if (registered) {
                        // Save connection info
                        SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
                        prefs.edit()
                                .putBoolean("is_connected", true)
                                .putBoolean(Config.PREF_CONFIGURED, true)
                                .putString(Config.PREF_SITE_URL, siteUrl)
                                .putString("connected_user", username)
                                .putString("session_cookie", sessionCookie)
                                .apply();

                        mainHandler.post(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Connected! You will receive notifications.", Toast.LENGTH_LONG).show();
                            showConnectedState(siteUrl, username);
                        });
                    } else {
                        mainHandler.post(() -> {
                            showError("Failed to register device");
                            connectButton.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        showError("FCM token not available. Try again.");
                        connectButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                    });
                }

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

    private String login(String siteUrl, String username, String password) {
        try {
            URL url = new URL(siteUrl + "/api/method/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String body = "usr=" + java.net.URLEncoder.encode(username, "UTF-8") +
                    "&pwd=" + java.net.URLEncoder.encode(password, "UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Login response code: " + responseCode);

            if (responseCode == 200) {
                // Get session cookie
                String cookies = conn.getHeaderField("Set-Cookie");
                if (cookies != null) {
                    sessionCookie = cookies;
                    Log.d(TAG, "Got session cookie");
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

            // Send session cookie
            if (sessionCookie != null) {
                conn.setRequestProperty("Cookie", sessionCookie);
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

            return responseCode == 200 && response.toString().contains("success");

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
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "FCM token failed", task.getException());
                        return;
                    }
                    fcmToken = task.getResult();
                    Log.d(TAG, "FCM Token: " + fcmToken.substring(0, 20) + "...");
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
