# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

from frappe import _


def get_data():
    return [
        {
            "module_name": "FCM",
            "color": "#FF9800",
            "icon": "octicon octicon-bell",
            "type": "module",
            "label": _("FCM"),
            "description": _("Firebase Cloud Messaging Push Notifications")
        }
    ]
