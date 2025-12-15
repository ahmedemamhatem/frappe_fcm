# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

"""
Frappe FCM Notification Service
===============================

A comprehensive notification service for sending FCM push notifications.

Usage:
    from frappe_fcm.fcm.notification_service import (
        send_notification_to_user,
        send_notification_to_users,
        send_fcm_message,
        NotificationService
    )

    # Simple usage
    send_notification_to_user(
        user="user@example.com",
        title="Hello",
        body="World"
    )

    # Advanced usage with NotificationService class
    NotificationService.notify(
        users=["user1@example.com", "user2@example.com"],
        title="Announcement",
        body="Important update!",
        data={"url": "/app/announcement/1"}
    )
"""

import json
import frappe
from frappe import _
from frappe.utils import now_datetime
from typing import Optional, Dict, Any, List

from frappe_fcm.fcm.fcm_sender import (
    get_fcm_settings,
    send_fcm_v1_message,
    send_fcm_legacy_message,
    send_fcm_to_topic
)


def send_fcm_message(
    fcm_token: str,
    title: Optional[str],
    body: Optional[str],
    data: Optional[Dict[str, str]] = None,
    image_url: Optional[str] = None
) -> Dict[str, Any]:
    """
    Send a push notification via Firebase Cloud Messaging

    Automatically uses v1 API if service account is configured,
    otherwise falls back to legacy API.

    Args:
        fcm_token: Device FCM token
        title: Notification title (None for data-only message)
        body: Notification body text (None for data-only message)
        data: Additional data payload (optional)
        image_url: Image URL for rich notification (optional)

    Returns:
        API response dict with 'success' boolean
    """
    settings = get_fcm_settings()
    if not settings:
        return {"success": False, "error": "FCM not configured"}

    # Use v1 API if service account JSON is configured (recommended)
    if settings.get("service_account_json"):
        result = send_fcm_v1_message(fcm_token, title, body, data, image_url)
    # Fallback to legacy API
    elif settings.get("server_key"):
        result = send_fcm_legacy_message(fcm_token, title, body, data, image_url)
    else:
        return {"success": False, "error": "No FCM credentials configured"}

    # Log notification if enabled
    if settings.get("log_notifications"):
        _log_notification(
            title=title,
            body=body,
            data=data,
            fcm_token=fcm_token,
            result=result
        )

    return result


def get_user_fcm_tokens(user: str) -> List[str]:
    """
    Get all enabled FCM tokens for a specific user

    Args:
        user: Frappe user ID (email)

    Returns:
        List of FCM tokens
    """
    tokens = frappe.get_all(
        "FCM Device",
        filters={"user": user, "enabled": 1},
        pluck="fcm_token"
    )
    return tokens


def send_notification_to_user(
    user: str,
    title: str,
    body: str,
    data: Optional[Dict[str, str]] = None,
    reference_doctype: Optional[str] = None,
    reference_name: Optional[str] = None
) -> Dict[str, Any]:
    """
    Send push notification to a specific user (all their devices)

    Args:
        user: Frappe user ID (email)
        title: Notification title
        body: Notification body
        data: Additional data payload
        reference_doctype: Related DocType (auto-generates URL)
        reference_name: Related document name

    Returns:
        Dict with success count and failed count
    """
    tokens = get_user_fcm_tokens(user)

    if not tokens:
        return {"success": 0, "failed": 0, "message": "No FCM tokens found for user"}

    # Build data payload
    if data is None:
        data = {}

    # Add reference info to data
    if reference_doctype:
        data["doctype"] = reference_doctype
    if reference_name:
        data["name"] = reference_name
    if reference_doctype and reference_name and "url" not in data:
        data["url"] = f"{frappe.utils.get_url()}/app/{frappe.scrub(reference_doctype)}/{reference_name}"

    success_count = 0
    failed_count = 0

    for token in tokens:
        result = send_fcm_message(
            fcm_token=token,
            title=title,
            body=body,
            data=data
        )
        if result.get("success"):
            success_count += 1
            # Update device notification count
            frappe.db.sql("""
                UPDATE `tabFCM Device`
                SET notification_count = notification_count + 1,
                    last_used = %s
                WHERE fcm_token = %s
            """, (now_datetime(), token))
        else:
            failed_count += 1

    frappe.db.commit()
    return {"success": success_count, "failed": failed_count}


def send_notification_to_users(
    users: List[str],
    title: str,
    body: str,
    data: Optional[Dict[str, str]] = None,
    reference_doctype: Optional[str] = None,
    reference_name: Optional[str] = None
) -> Dict[str, Any]:
    """
    Send push notification to multiple users

    Args:
        users: List of Frappe user IDs
        title: Notification title
        body: Notification body
        data: Additional data payload
        reference_doctype: Related DocType
        reference_name: Related document name

    Returns:
        Dict with results per user
    """
    results = {
        "total_success": 0,
        "total_failed": 0,
        "by_user": {}
    }

    for user in users:
        user_result = send_notification_to_user(
            user=user,
            title=title,
            body=body,
            data=data,
            reference_doctype=reference_doctype,
            reference_name=reference_name
        )
        results["by_user"][user] = user_result
        results["total_success"] += user_result.get("success", 0)
        results["total_failed"] += user_result.get("failed", 0)

    return results


class NotificationService:
    """
    Unified notification service for sending FCM notifications
    """

    @classmethod
    def notify(
        cls,
        users: List[str],
        title: str,
        body: str,
        data: Optional[Dict[str, str]] = None,
        reference_doctype: Optional[str] = None,
        reference_name: Optional[str] = None,
        notification_type: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Send notification to specified users

        Args:
            users: List of user IDs
            title: Notification title
            body: Notification body
            data: Additional data payload
            reference_doctype: Related DocType
            reference_name: Related document name
            notification_type: Type identifier for logging

        Returns:
            Results dict
        """
        if data is None:
            data = {}

        if notification_type:
            data["notification_type"] = notification_type

        return send_notification_to_users(
            users=users,
            title=title,
            body=body,
            data=data,
            reference_doctype=reference_doctype,
            reference_name=reference_name
        )

    @classmethod
    def notify_all(
        cls,
        title: str,
        body: str,
        data: Optional[Dict[str, str]] = None,
        exclude_users: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """
        Send notification to all users with registered devices

        Args:
            title: Notification title
            body: Notification body
            data: Additional data payload
            exclude_users: Users to exclude

        Returns:
            Results dict
        """
        # Get all users with enabled FCM devices
        users = frappe.get_all(
            "FCM Device",
            filters={"enabled": 1},
            pluck="user",
            distinct=True
        )

        if exclude_users:
            users = [u for u in users if u not in exclude_users]

        if not users:
            return {"total_success": 0, "total_failed": 0, "message": "No users with FCM devices"}

        return cls.notify(
            users=users,
            title=title,
            body=body,
            data=data
        )

    @classmethod
    def send_to_topic(
        cls,
        topic: str,
        title: str,
        body: str,
        data: Optional[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        Send notification to a topic

        Args:
            topic: Topic name
            title: Notification title
            body: Notification body
            data: Additional data payload

        Returns:
            API response dict
        """
        return send_fcm_to_topic(topic, title, body, data)


def _log_notification(
    title: Optional[str],
    body: Optional[str],
    data: Optional[Dict],
    fcm_token: str,
    result: Dict[str, Any],
    recipient_user: Optional[str] = None,
    notification_type: Optional[str] = None,
    reference_doctype: Optional[str] = None,
    reference_name: Optional[str] = None
):
    """
    Log notification to FCM Notification Log
    """
    try:
        # Get user from device if not provided
        if not recipient_user:
            device = frappe.db.get_value(
                "FCM Device",
                {"fcm_token": fcm_token},
                ["user", "device_id"],
                as_dict=True
            )
            if device:
                recipient_user = device.user
                device_id = device.device_id
            else:
                device_id = None
        else:
            device_id = None

        log = frappe.new_doc("FCM Notification Log")
        log.notification_type = notification_type or data.get("notification_type") if data else None
        log.status = "Sent" if result.get("success") else "Failed"
        log.recipient_user = recipient_user
        log.recipient_name = frappe.db.get_value("User", recipient_user, "full_name") if recipient_user else None
        log.device_id = device_id
        log.fcm_token_preview = f"{fcm_token[:20]}..." if fcm_token else None
        log.title = title
        log.body = body[:500] if body else None
        log.data_payload = json.dumps(data, ensure_ascii=False) if data else None
        log.response = json.dumps(result, ensure_ascii=False)
        log.error_message = result.get("error") if not result.get("success") else None
        log.reference_doctype = reference_doctype or (data.get("doctype") if data else None)
        log.reference_name = reference_name or (data.get("name") if data else None)
        log.sent_at = now_datetime()

        log.insert(ignore_permissions=True)
        frappe.db.commit()

    except Exception as e:
        frappe.log_error(f"Failed to log notification: {str(e)}", "FCM Log Error")


# ============================================================
# WHITELISTED API METHODS (for mobile apps)
# ============================================================

@frappe.whitelist(allow_guest=True)
def register_device_token(
    token: str,
    device_id: Optional[str] = None,
    device_name: Optional[str] = None,
    device_model: Optional[str] = None,
    os_version: Optional[str] = None,
    app_version: Optional[str] = None
):
    """
    Register FCM device token (public endpoint for mobile app)

    Args:
        token: FCM device token
        device_id: Unique device identifier
        device_name: Human-readable device name
        device_model: Device model (e.g., "Samsung Galaxy S21")
        os_version: OS version (e.g., "Android 13")
        app_version: App version (e.g., "1.0.0")

    Returns:
        dict: Success status
    """
    if not token:
        return {"success": False, "message": "Token is required"}

    # Get current user
    current_user = frappe.session.user if frappe.session else None

    if not current_user or current_user == "Guest":
        return {"success": True, "message": "Token will be registered on next authenticated request"}

    return register_user_fcm_token(
        token=token,
        user=current_user,
        device_id=device_id,
        device_name=device_name,
        device_model=device_model,
        os_version=os_version,
        app_version=app_version
    )


@frappe.whitelist(allow_guest=True)
def register_user_fcm_token(
    token: str,
    user: Optional[str] = None,
    device_id: Optional[str] = None,
    device_name: Optional[str] = None,
    device_model: Optional[str] = None,
    os_version: Optional[str] = None,
    app_version: Optional[str] = None
):
    """
    Register FCM token for a specific Frappe user

    Args:
        token: FCM device token
        user: Frappe user ID (uses session user if not provided)
        device_id: Unique device identifier
        device_name: Human-readable device name
        device_model: Device model
        os_version: OS version
        app_version: App version

    Returns:
        dict: Success status
    """
    if not token:
        frappe.throw("Token is required")

    target_user = user or frappe.session.user
    if target_user == "Guest":
        frappe.throw("Authentication required")

    if not frappe.db.exists("User", target_user):
        frappe.throw(f"User {target_user} not found")

    # Generate device ID from token if not provided
    if not device_id:
        device_id = token[:16]

    try:
        # Check if this exact token already exists for this user
        existing = frappe.db.exists("FCM Device", {
            "user": target_user,
            "fcm_token": token
        })

        if existing:
            # Update last_used and device info
            frappe.db.set_value("FCM Device", existing, {
                "last_used": now_datetime(),
                "device_model": device_model or frappe.db.get_value("FCM Device", existing, "device_model"),
                "os_version": os_version or frappe.db.get_value("FCM Device", existing, "os_version"),
                "app_version": app_version or frappe.db.get_value("FCM Device", existing, "app_version"),
                "enabled": 1
            })
            frappe.db.commit()
            return {"success": True, "message": "Token updated", "updated": True, "device": existing}

        # Check if this device_id exists for user (same device, new token - token refreshed)
        existing_device = frappe.db.exists("FCM Device", {
            "user": target_user,
            "device_id": device_id
        })

        if existing_device:
            # Update token for existing device
            frappe.db.set_value("FCM Device", existing_device, {
                "fcm_token": token,
                "last_used": now_datetime(),
                "device_model": device_model or frappe.db.get_value("FCM Device", existing_device, "device_model"),
                "os_version": os_version or frappe.db.get_value("FCM Device", existing_device, "os_version"),
                "app_version": app_version or frappe.db.get_value("FCM Device", existing_device, "app_version"),
                "enabled": 1
            })
            frappe.db.commit()
            return {"success": True, "message": "Token refreshed for device", "updated": True, "device": existing_device}

        # Create new device record
        doc = frappe.new_doc("FCM Device")
        doc.user = target_user
        doc.fcm_token = token
        doc.device_id = device_id
        doc.device_name = device_name
        doc.device_model = device_model
        doc.os_version = os_version
        doc.app_version = app_version
        doc.enabled = 1
        doc.insert(ignore_permissions=True)
        frappe.db.commit()

        return {"success": True, "message": "Device registered", "created": True, "device": doc.name}

    except Exception as e:
        frappe.log_error(f"FCM device registration error: {str(e)}", "FCM Registration Error")
        return {"success": False, "message": str(e)}


@frappe.whitelist()
def unregister_device(device_id: Optional[str] = None, token: Optional[str] = None):
    """
    Unregister/remove a device

    Args:
        device_id: Device ID to remove
        token: FCM token to remove

    Returns:
        dict: Success status
    """
    current_user = frappe.session.user

    filters = {"user": current_user}
    if device_id:
        filters["device_id"] = device_id
    elif token:
        filters["fcm_token"] = token
    else:
        return {"success": False, "message": "device_id or token required"}

    try:
        devices = frappe.get_all("FCM Device", filters=filters, pluck="name")

        for device_name in devices:
            frappe.delete_doc("FCM Device", device_name, ignore_permissions=True)

        frappe.db.commit()
        return {"success": True, "message": f"Removed {len(devices)} device(s)"}

    except Exception as e:
        frappe.log_error(f"FCM device unregister error: {str(e)}", "FCM Unregister Error")
        return {"success": False, "message": str(e)}


@frappe.whitelist()
def get_my_devices():
    """
    Get all devices registered for the current user

    Returns:
        list: List of device documents
    """
    return frappe.get_all(
        "FCM Device",
        filters={"user": frappe.session.user},
        fields=[
            "name", "device_id", "device_name", "device_model",
            "os_version", "app_version", "enabled", "last_used",
            "created_on", "notification_count"
        ],
        order_by="last_used desc"
    )


@frappe.whitelist()
def send_push_to_user(user: str, title: str, body: str, data: Optional[str] = None):
    """
    API to send push notification to a specific user

    Args:
        user: Frappe user ID
        title: Notification title
        body: Notification body
        data: JSON string of additional data

    Returns:
        dict: Send results
    """
    data_dict = json.loads(data) if data else None
    return send_notification_to_user(user, title, body, data_dict)


@frappe.whitelist()
def test_fcm_connection():
    """
    Test FCM connection (delegates to FCM Settings)
    """
    from frappe_fcm.fcm.doctype.fcm_settings.fcm_settings import test_fcm_connection as _test
    return _test()


@frappe.whitelist(allow_guest=True)
def validate_connection():
    """
    Validate that this Frappe site has frappe_fcm installed.
    Used by mobile app during setup to verify the site URL.

    Returns:
        dict: Connection validation info
    """
    settings = get_fcm_settings()
    fcm_enabled = settings.get("fcm_enabled") if settings else False

    return {
        "success": True,
        "message": "Frappe FCM is installed",
        "fcm_enabled": fcm_enabled,
        "site": frappe.local.site,
        "version": "1.0.0"
    }
