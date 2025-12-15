/*
 * Frappe FCM - Configuration
 *
 * ========================================
 * IMPORTANT: Configure these values before building!
 * ========================================
 */

package io.frappe.fcm;

public class Config {
    /*
     * ========================================
     * REQUIRED CONFIGURATION
     * ========================================
     */

    /**
     * Your Frappe/ERPNext site URL (without trailing slash)
     *
     * Examples:
     * - "https://your-site.frappe.cloud"
     * - "https://erp.yourcompany.com"
     * - "http://localhost:8000" (for development)
     */
    public static final String BASE_URL = "https://your-site.frappe.cloud";

    /**
     * API endpoint for registering FCM tokens
     * This should work with the default frappe_fcm installation.
     * Only change if you've customized the API.
     */
    public static final String TOKEN_REGISTER_API =
            BASE_URL + "/api/method/frappe_fcm.fcm.notification_service.register_user_fcm_token";

    /*
     * ========================================
     * OPTIONAL CONFIGURATION
     * ========================================
     */

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
    public static final boolean DEBUG = false;
}
