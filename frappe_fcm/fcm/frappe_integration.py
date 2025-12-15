# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

"""
Frappe Integration for FCM

This module provides integration with Frappe's built-in Notification system.
When enabled, FCM push notifications are automatically sent alongside
System and Email notifications.

How it works:
1. When a Frappe Notification rule triggers, it creates a Notification Log
2. This hook catches the Notification Log after_insert event
3. If FCM is enabled and the notification type is allowed, FCM push is sent
4. Recipients are determined from the Notification Log

Configuration:
- Enable in FCM Settings: "Auto Send on Frappe Notification"
- Per-notification control: "Send FCM Push" checkbox in each Notification rule
"""

import re
import frappe
from frappe import _
from frappe.utils import get_url


def on_notification_log_insert(doc, method=None):
    """
    Hook for Frappe Notification Log after_insert

    Sends FCM push notification when a Frappe Notification is triggered.

    Args:
        doc: Notification Log document
        method: Hook method (unused)
    """
    try:
        # Check if FCM is enabled globally
        settings = frappe.get_single("FCM Settings")
        if not settings.fcm_enabled:
            return

        # Check if auto-send is enabled
        if not settings.auto_send_on_notification:
            return

        # Check notification type settings
        notification_type = doc.type if hasattr(doc, 'type') else None

        # Determine if this is a system or email notification
        # Notification Log doesn't always have clear type, so we check based on context
        is_system_notification = doc.for_user and not doc.email_content
        is_email_notification = bool(doc.email_content)

        if is_system_notification and not settings.send_for_system_notification:
            return

        if is_email_notification and not settings.send_for_email_notification:
            return

        # Check per-notification FCM setting (custom field on Notification DocType)
        if doc.document_type and doc.document_name:
            # Try to get the source Notification rule
            notification_name = _get_notification_rule(doc)
            if notification_name:
                send_fcm = frappe.db.get_value("Notification", notification_name, "send_fcm_push")
                # If send_fcm_push field exists and is explicitly set to 0, skip
                if send_fcm is not None and not send_fcm:
                    frappe.logger().debug(f"FCM disabled for notification: {notification_name}")
                    return

        # Get recipient
        for_user = doc.for_user
        if not for_user:
            return

        # Check if user has FCM devices
        user_devices = frappe.get_all(
            "FCM Device",
            filters={"user": for_user, "enabled": 1},
            pluck="name"
        )

        if not user_devices:
            return

        # Build notification content
        title = doc.subject or settings.default_notification_title or "Notification"
        body = _clean_html(doc.email_content or doc.subject or "")
        body = body[:200]  # Limit length for push notification

        # Build data payload
        data = {
            "notification_log": doc.name,
            "notification_type": "frappe_notification",
            "type": notification_type or "notification"
        }

        # Add document reference if available
        if doc.document_type and doc.document_name:
            data["doctype"] = doc.document_type
            data["name"] = doc.document_name
            data["url"] = f"{get_url()}/app/{frappe.scrub(doc.document_type)}/{doc.document_name}"

        # Send FCM notification
        from frappe_fcm.fcm.notification_service import send_notification_to_user

        result = send_notification_to_user(
            user=for_user,
            title=title,
            body=body,
            data=data
        )

        if result.get("success", 0) > 0:
            frappe.logger().info(
                f"FCM sent for Notification Log: {doc.name} to {for_user} "
                f"(success: {result.get('success', 0)}, failed: {result.get('failed', 0)})"
            )
        else:
            frappe.logger().warning(
                f"FCM failed for Notification Log: {doc.name} to {for_user}"
            )

    except Exception as e:
        # Don't fail the notification if FCM fails
        frappe.log_error(
            f"FCM Frappe Integration Error: {str(e)}\n"
            f"Notification Log: {doc.name if doc else 'N/A'}",
            "FCM Integration Error"
        )


def _get_notification_rule(notification_log):
    """
    Try to get the Notification rule name from a Notification Log

    Args:
        notification_log: Notification Log document

    Returns:
        str: Notification rule name or None
    """
    try:
        # Notification Log may have reference to the Notification rule
        if hasattr(notification_log, 'notification_name') and notification_log.notification_name:
            return notification_log.notification_name

        # Try to find by document type and subject pattern
        if notification_log.document_type:
            notification = frappe.db.get_value(
                "Notification",
                {
                    "document_type": notification_log.document_type,
                    "enabled": 1
                },
                "name"
            )
            return notification

        return None
    except Exception:
        return None


def _clean_html(html_content):
    """
    Remove HTML tags and clean content for push notification

    Args:
        html_content: HTML string

    Returns:
        str: Clean text
    """
    if not html_content:
        return ""

    # Remove HTML tags
    clean = re.sub('<[^<]+?>', '', html_content)
    # Remove multiple spaces
    clean = re.sub(r'\s+', ' ', clean)
    # Remove leading/trailing whitespace
    clean = clean.strip()

    return clean


def send_fcm_for_notification_log(notification_log_name: str):
    """
    Manually trigger FCM for an existing Notification Log

    Args:
        notification_log_name: Name of the Notification Log document

    Returns:
        dict: Result of FCM send
    """
    doc = frappe.get_doc("Notification Log", notification_log_name)
    on_notification_log_insert(doc)
    return {"success": True, "message": f"FCM triggered for {notification_log_name}"}


@frappe.whitelist()
def test_frappe_integration(user: str = None):
    """
    Test FCM integration with Frappe notifications

    Args:
        user: Optional user to send test to (defaults to current user)

    Returns:
        dict: Test result
    """
    from frappe_fcm.fcm.notification_service import send_notification_to_user

    target_user = user or frappe.session.user
    if target_user == "Guest":
        return {"success": False, "message": "Please login to test"}

    # Check if user has devices
    devices = frappe.get_all(
        "FCM Device",
        filters={"user": target_user, "enabled": 1},
        pluck="name"
    )

    if not devices:
        return {
            "success": False,
            "message": f"User {target_user} has no registered FCM devices"
        }

    result = send_notification_to_user(
        user=target_user,
        title="Test Frappe Integration",
        body="This is a test notification from Frappe FCM integration.",
        data={
            "notification_type": "test",
            "url": f"{get_url()}/app"
        }
    )

    return {
        "success": result.get("success", 0) > 0,
        "sent": result.get("success", 0),
        "failed": result.get("failed", 0),
        "devices": len(devices)
    }


@frappe.whitelist()
def get_fcm_status_for_user(user: str = None):
    """
    Get FCM status for a user

    Args:
        user: User to check (defaults to current user)

    Returns:
        dict: FCM status info
    """
    target_user = user or frappe.session.user

    devices = frappe.get_all(
        "FCM Device",
        filters={"user": target_user},
        fields=["name", "device_name", "device_model", "enabled", "last_used"]
    )

    settings = frappe.get_single("FCM Settings")

    return {
        "user": target_user,
        "fcm_enabled": settings.fcm_enabled,
        "auto_send_enabled": settings.auto_send_on_notification,
        "devices": devices,
        "active_devices": len([d for d in devices if d.enabled]),
        "total_devices": len(devices)
    }
