"""
Frappe FCM - Firebase Cloud Messaging for Frappe
=================================================

A comprehensive push notification solution for Frappe applications
using Firebase Cloud Messaging (FCM).

Features:
- FCM HTTP v1 API support (recommended)
- Legacy FCM API support (fallback)
- Multi-device support per user
- Automatic token management
- Notification logging
- Ready-to-use Android app template

Usage:
    from frappe_fcm import send_notification

    # Send to a specific user
    send_notification(
        user="user@example.com",
        title="Hello",
        body="This is a test notification"
    )

    # Send to multiple users
    from frappe_fcm import send_notification_to_users
    send_notification_to_users(
        users=["user1@example.com", "user2@example.com"],
        title="Announcement",
        body="Important update!"
    )
"""

__version__ = "1.0.0"

# Convenience imports
from frappe_fcm.fcm.notification_service import (
    send_notification_to_user as send_notification,
    send_notification_to_users,
    send_fcm_message,
    NotificationService,
)
