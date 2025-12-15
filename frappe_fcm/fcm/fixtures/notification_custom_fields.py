# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

"""
Custom Fields for Frappe Notification DocType

Adds FCM-related fields to the Notification DocType to allow
per-notification control of FCM push notifications.
"""

import frappe


def create_notification_custom_fields():
    """
    Create custom fields for Notification DocType to enable FCM settings
    """
    custom_fields = [
        {
            "doctype": "Custom Field",
            "dt": "Notification",
            "module": "FCM",
            "fieldname": "fcm_section",
            "fieldtype": "Section Break",
            "label": "Push Notification (FCM)",
            "insert_after": "send_system_notification",
            "collapsible": 1
        },
        {
            "doctype": "Custom Field",
            "dt": "Notification",
            "module": "FCM",
            "fieldname": "send_fcm_push",
            "fieldtype": "Check",
            "label": "Send FCM Push Notification",
            "insert_after": "fcm_section",
            "default": "1",
            "description": "Send push notification to mobile devices when this notification triggers"
        },
        {
            "doctype": "Custom Field",
            "dt": "Notification",
            "module": "FCM",
            "fieldname": "fcm_title_template",
            "fieldtype": "Data",
            "label": "FCM Title (Optional)",
            "insert_after": "send_fcm_push",
            "depends_on": "send_fcm_push",
            "description": "Custom title for push notification. Leave blank to use notification subject. Supports Jinja: {{ doc.name }}"
        },
        {
            "doctype": "Custom Field",
            "dt": "Notification",
            "module": "FCM",
            "fieldname": "fcm_body_template",
            "fieldtype": "Small Text",
            "label": "FCM Body (Optional)",
            "insert_after": "fcm_title_template",
            "depends_on": "send_fcm_push",
            "description": "Custom body for push notification. Leave blank to use notification message. Supports Jinja: {{ doc.customer_name }}"
        },
        {
            "doctype": "Custom Field",
            "dt": "Notification",
            "module": "FCM",
            "fieldname": "fcm_info_html",
            "fieldtype": "HTML",
            "label": "FCM Info",
            "insert_after": "fcm_body_template",
            "depends_on": "send_fcm_push",
            "options": """<div style="background: #e3f2fd; padding: 10px; border-radius: 5px; margin-top: 10px;">
<p style="color: #1565c0; margin: 0;"><strong>FCM Push Notification</strong></p>
<ul style="color: #333; margin: 5px 0; font-size: 12px;">
<li>Push notifications will be sent to users with registered mobile devices</li>
<li>Recipients are determined by the notification's "Recipients" settings</li>
<li>Ensure FCM is configured in <strong>FCM Settings</strong></li>
</ul>
</div>"""
        }
    ]

    for field in custom_fields:
        field_name = f"{field['dt']}-{field['fieldname']}"

        if not frappe.db.exists("Custom Field", field_name):
            doc = frappe.get_doc(field)
            doc.insert(ignore_permissions=True)
            print(f"Created custom field: {field_name}")
        else:
            # Update existing field
            existing = frappe.get_doc("Custom Field", field_name)
            for key, value in field.items():
                if key not in ["doctype", "dt", "fieldname"]:
                    setattr(existing, key, value)
            existing.save(ignore_permissions=True)
            print(f"Updated custom field: {field_name}")

    frappe.db.commit()
    print("Notification custom fields created/updated successfully!")


def remove_notification_custom_fields():
    """
    Remove custom fields from Notification DocType
    """
    fields_to_remove = [
        "Notification-fcm_section",
        "Notification-send_fcm_push",
        "Notification-fcm_title_template",
        "Notification-fcm_body_template",
        "Notification-fcm_info_html"
    ]

    for field_name in fields_to_remove:
        if frappe.db.exists("Custom Field", field_name):
            frappe.delete_doc("Custom Field", field_name, ignore_permissions=True)
            print(f"Removed custom field: {field_name}")

    frappe.db.commit()
    print("Notification custom fields removed!")
