/*
 * Frappe FCM - Firebase Cloud Messaging Service
 *
 * This service handles incoming FCM messages and displays notifications.
 * Customize this file to match your app's notification requirements.
 */

package io.frappe.fcm;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FrappeFCM";
    private static final String CHANNEL_ID = "frappe_fcm_notifications";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token received: " + token.substring(0, Math.min(20, token.length())) + "...");

        // Token registration is handled by MainActivity when user logs in
        // The token will be registered the next time the user opens the app
        Log.d(TAG, "Token will be registered when user opens app");
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        String title = null;
        String body = null;
        Map<String, String> data = remoteMessage.getData();

        // Check notification payload (sent from server with notification key)
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // Check data payload (sent from server with data key)
        if (data.size() > 0) {
            // Data payload can override or supplement notification
            if (data.containsKey("title")) {
                title = data.get("title");
            }
            if (data.containsKey("body")) {
                body = data.get("body");
            }
        }

        // Show notification if we have content
        if (title != null || body != null) {
            showNotification(title, body, data);
        }
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        // Create intent for notification click
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Add data to intent for handling when app opens
        if (data != null) {
            // Check for URL in data payload
            String url = data.get("url");
            if (url != null && !url.isEmpty()) {
                intent.putExtra("url", url);
            }

            // Check for doctype/name to build URL
            String doctype = data.get("doctype");
            String name = data.get("name");
            if (doctype != null && name != null && url == null) {
                // Get base URL from SharedPreferences
                SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME, MODE_PRIVATE);
                String baseUrl = prefs.getString(Config.PREF_SITE_URL, "");
                if (!baseUrl.isEmpty()) {
                    // Build document URL
                    String docUrl = baseUrl + "/app/" +
                            doctype.toLowerCase().replace(" ", "-") + "/" + name;
                    intent.putExtra("url", docUrl);
                }
            }

            // Add notification type for handling
            String notificationType = data.get("notification_type");
            if (notificationType != null) {
                intent.putExtra("notification_type", notificationType);
            }
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // Default notification sound
        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title != null ? title : getString(R.string.app_name))
                .setContentText(body != null ? body : "")
                .setAutoCancel(true)
                .setSound(defaultSound)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        // Expandable notification for long text
        if (body != null && body.length() > 50) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }

        // Show notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            // Use unique ID for each notification
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, builder.build());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
