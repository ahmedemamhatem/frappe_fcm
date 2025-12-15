"""
Installation hooks for Frappe FCM
"""

import frappe


def after_install():
    """Run after app installation"""
    # Create default FCM Settings if not exists
    if not frappe.db.exists("FCM Settings", "FCM Settings"):
        frappe.get_doc({
            "doctype": "FCM Settings",
            "fcm_enabled": 0,
            "auto_send_on_notification": 1,
            "log_notifications": 1,
            "send_async": 1,
            "retry_failed": 1,
            "max_retries": 3
        }).insert(ignore_permissions=True)
        frappe.db.commit()
        print("FCM Settings created successfully")

    # Create custom fields for Notification DocType
    try:
        from frappe_fcm.fcm.fixtures.notification_custom_fields import create_notification_custom_fields
        create_notification_custom_fields()
    except Exception as e:
        print(f"Warning: Could not create Notification custom fields: {e}")

    print("\n" + "=" * 60)
    print("Frappe FCM installed successfully!")
    print("=" * 60)
    print("\nFrappe Integration:")
    print("- FCM will automatically send push notifications when")
    print("  Frappe Notifications (System/Email) are triggered")
    print("- Configure per-notification FCM in each Notification rule")
    print("\nNext Steps:")
    print("1. Go to FCM Settings and configure your Firebase credentials")
    print("2. Download the Android app template from the mobile_app folder")
    print("3. Configure your Firebase project and get google-services.json")
    print("4. Build and distribute your Android app")
    print("\nDocumentation: https://github.com/frappe/frappe_fcm")
    print("=" * 60 + "\n")


def after_migrate():
    """Run after migrations - ensure custom fields exist"""
    try:
        from frappe_fcm.fcm.fixtures.notification_custom_fields import create_notification_custom_fields
        create_notification_custom_fields()
    except Exception as e:
        print(f"Warning: Could not create Notification custom fields: {e}")
