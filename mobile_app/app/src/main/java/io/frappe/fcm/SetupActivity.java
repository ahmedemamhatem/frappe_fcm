/*
 * Frappe FCM - Setup Activity
 *
 * This activity allows users to configure their Frappe site URL.
 * Shows on first launch or when reconfiguration is needed.
 */

package io.frappe.fcm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SetupActivity extends AppCompatActivity {

    private static final String TAG = "FrappeFCM-Setup";

    private EditText siteUrlInput;
    private Button connectButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView instructionsText;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Check if already configured
        SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(Config.PREF_CONFIGURED, false)) {
            // Already configured, go to main activity
            startMainActivity();
            return;
        }

        // Initialize views
        siteUrlInput = findViewById(R.id.siteUrlInput);
        connectButton = findViewById(R.id.connectButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        instructionsText = findViewById(R.id.instructionsText);

        // Set default URL if configured
        if (!Config.DEFAULT_SITE_URL.isEmpty()) {
            siteUrlInput.setText(Config.DEFAULT_SITE_URL);
        }

        // Connect button click
        connectButton.setOnClickListener(v -> validateAndConnect());
    }

    private void validateAndConnect() {
        String siteUrl = siteUrlInput.getText().toString().trim();

        // Basic validation
        if (siteUrl.isEmpty()) {
            siteUrlInput.setError("Please enter your Frappe site URL");
            return;
        }

        // Ensure URL has protocol
        if (!siteUrl.startsWith("http://") && !siteUrl.startsWith("https://")) {
            siteUrl = "https://" + siteUrl;
            siteUrlInput.setText(siteUrl);
        }

        // Remove trailing slash
        if (siteUrl.endsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
            siteUrlInput.setText(siteUrl);
        }

        // Show progress
        setLoading(true);
        statusText.setText("Connecting to " + siteUrl + "...");

        // Validate connection
        final String finalUrl = siteUrl;
        executor.execute(() -> validateConnection(finalUrl));
    }

    private void validateConnection(String siteUrl) {
        try {
            // First try to access the site
            URL url = new URL(siteUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Site response code: " + responseCode);
            conn.disconnect();

            if (responseCode != 200 && responseCode != 302 && responseCode != 301) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("Cannot connect to site. Please check the URL.");
                });
                return;
            }

            // Now check if frappe_fcm is installed by calling validate API
            URL validateUrl = new URL(siteUrl + Config.VALIDATE_PATH);
            HttpURLConnection validateConn = (HttpURLConnection) validateUrl.openConnection();
            validateConn.setRequestMethod("GET");
            validateConn.setConnectTimeout(10000);
            validateConn.setReadTimeout(10000);

            int validateCode = validateConn.getResponseCode();
            Log.d(TAG, "Validate response code: " + validateCode);

            if (validateCode == 200) {
                // Read response
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(validateConn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.d(TAG, "Validate response: " + response.toString());
            }
            validateConn.disconnect();

            // Save configuration and proceed
            mainHandler.post(() -> {
                saveConfigAndProceed(siteUrl);
            });

        } catch (Exception e) {
            Log.e(TAG, "Connection error", e);
            mainHandler.post(() -> {
                setLoading(false);
                statusText.setText("Connection failed: " + e.getMessage());
            });
        }
    }

    private void saveConfigAndProceed(String siteUrl) {
        // Save to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(Config.PREF_SITE_URL, siteUrl)
                .putBoolean(Config.PREF_CONFIGURED, true)
                .apply();

        Log.d(TAG, "Configuration saved: " + siteUrl);
        statusText.setText("Connected! Opening app...");

        // Start main activity after short delay
        mainHandler.postDelayed(this::startMainActivity, 500);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        connectButton.setEnabled(!loading);
        siteUrlInput.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
