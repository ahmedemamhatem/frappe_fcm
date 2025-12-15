# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

import frappe
from frappe import _
from frappe.model.document import Document
from frappe.utils import now_datetime


class FCMDevice(Document):
    def before_insert(self):
        """Set creation timestamps"""
        self.created_on = now_datetime()
        self.last_used = now_datetime()

    def before_save(self):
        """Update last_used timestamp"""
        if not self.is_new():
            self.last_used = now_datetime()


@frappe.whitelist()
def send_test_notification_to_device(device_name):
    """
    Send a test notification to a specific device

    Args:
        device_name: Name of the FCM Device document

    Returns:
        dict: Success status and message
    """
    from frappe_fcm.fcm.notification_service import send_fcm_message
    from frappe.utils import format_datetime

    device = frappe.get_doc("FCM Device", device_name)

    if not device.enabled:
        return {
            "success": False,
            "error": _("Device is disabled")
        }

    test_time = format_datetime(now_datetime(), "dd/MM/yyyy hh:mm a")

    result = send_fcm_message(
        fcm_token=device.fcm_token,
        title="Test Notification",
        body=f"This is a test notification sent at {test_time}",
        data={
            "type": "test",
            "device_id": device.device_id or "",
            "timestamp": str(now_datetime())
        }
    )

    if result.get("success"):
        # Update device statistics
        frappe.db.set_value("FCM Device", device_name, {
            "last_used": now_datetime(),
            "notification_count": device.notification_count + 1
        }, update_modified=False)
        frappe.db.commit()

        return {
            "success": True,
            "message": _("Test notification sent successfully")
        }
    else:
        return {
            "success": False,
            "error": result.get("error", _("Unknown error"))
        }


@frappe.whitelist()
def check_token_validity(device_name):
    """
    Check if a device token is still valid

    Args:
        device_name: Name of the FCM Device document

    Returns:
        dict: Validity status
    """
    from frappe_fcm.fcm.notification_service import send_fcm_message

    device = frappe.get_doc("FCM Device", device_name)

    # Try to send a data-only (silent) message
    result = send_fcm_message(
        fcm_token=device.fcm_token,
        title=None,
        body=None,
        data={"type": "ping", "silent": "true"}
    )

    if result.get("success"):
        frappe.db.set_value("FCM Device", device_name, "last_used", now_datetime(), update_modified=False)
        frappe.db.commit()
        return {"valid": True, "message": _("Token is valid")}
    else:
        error = result.get("error", "")
        # Check for invalid token errors
        if "UNREGISTERED" in str(error) or "InvalidRegistration" in str(error) or "NotRegistered" in str(error):
            frappe.db.set_value("FCM Device", device_name, "enabled", 0, update_modified=False)
            frappe.db.commit()
            return {"valid": False, "message": _("Token is invalid/expired - device disabled")}

        return {"valid": False, "message": error}
