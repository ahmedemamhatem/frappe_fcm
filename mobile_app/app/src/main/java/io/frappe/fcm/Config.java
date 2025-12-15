/*
 * Frappe FCM - Configuration
 *
 * This config holds default values. The actual site URL
 * is configured by the user in the app's setup screen.
 */

package io.frappe.fcm;

public class Config {
    /**
     * Default Frappe site URL (can be changed by user in setup screen)
     * Leave empty to force user to configure on first launch
     */
    public static final String DEFAULT_SITE_URL = "";

    /**
     * API endpoint path for registering FCM tokens
     */
    public static final String TOKEN_REGISTER_PATH =
            "/api/method/frappe_fcm.fcm.notification_service.register_user_fcm_token";

    /**
     * API endpoint path for validating site connection
     */
    public static final String VALIDATE_PATH =
            "/api/method/frappe_fcm.fcm.notification_service.validate_connection";

    /**
     * Notification channel ID (must match FCM Settings in Frappe)
     */
    public static final String NOTIFICATION_CHANNEL_ID = "frappe_fcm_notifications";

    /**
     * Notification channel name (shown in Android settings)
     */
    public static final String NOTIFICATION_CHANNEL_NAME = "Notifications";

    /**
     * Notification channel description
     */
    public static final String NOTIFICATION_CHANNEL_DESCRIPTION = "Push notifications from your Frappe app";

    /**
     * Enable debug logging
     */
    public static final boolean DEBUG = true;

    /**
     * SharedPreferences keys
     */
    public static final String PREF_NAME = "frappe_fcm_prefs";
    public static final String PREF_SITE_URL = "site_url";
    public static final String PREF_CONFIGURED = "is_configured";
}
