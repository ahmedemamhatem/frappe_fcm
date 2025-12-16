# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

import frappe
from frappe import _
from frappe.model.document import Document
import json


class FCMSettings(Document):
    pass


@frappe.whitelist()
def fetch_shared_credentials():
    """
    Fetch the shared Firebase service account credentials from the official repository.
    This allows all users to use the same Firebase project for the universal mobile app.
    """
    import requests

    url = "https://raw.githubusercontent.com/ahmedemamhatem/frappe_fcm/main/firebase/service-account.json"

    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()

        # Validate it's valid JSON
        credentials = response.json()

        # Extract project_id
        project_id = credentials.get("project_id", "")

        return {
            "success": True,
            "credentials": json.dumps(credentials, indent=2),
            "project_id": project_id,
            "message": _("Credentials fetched successfully! Click Save to apply.")
        }
    except requests.exceptions.RequestException as e:
        return {
            "success": False,
            "message": _("Failed to fetch credentials: {0}").format(str(e))
        }
    except json.JSONDecodeError:
        return {
            "success": False,
            "message": _("Invalid JSON received from server")
        }


@frappe.whitelist()
def test_fcm_connection():
    """
    Test FCM connection by validating credentials
    """
    settings = frappe.get_single("FCM Settings")

    if not settings.fcm_enabled:
        return {
            "success": False,
            "message": _("FCM is not enabled")
        }

    # Check if service account JSON is provided (recommended)
    service_account_json = settings.fcm_service_account_json
    if service_account_json:
        try:
            import json
            from google.oauth2 import service_account
            from google.auth.transport.requests import Request

            # Parse and validate JSON
            service_account_info = json.loads(service_account_json)

            # Create credentials
            credentials = service_account.Credentials.from_service_account_info(
                service_account_info,
                scopes=['https://www.googleapis.com/auth/firebase.messaging']
            )

            # Try to get access token
            credentials.refresh(Request())

            if credentials.token:
                return {
                    "success": True,
                    "message": _("FCM connection successful! Service Account authenticated."),
                    "project_id": settings.fcm_project_id,
                    "api_type": "v1"
                }
        except json.JSONDecodeError:
            return {
                "success": False,
                "message": _("Invalid Service Account JSON format")
            }
        except Exception as e:
            return {
                "success": False,
                "message": _("Service Account authentication failed: {0}").format(str(e))
            }

    return {
        "success": False,
        "message": _("No FCM credentials configured. Please add Service Account JSON.")
    }


@frappe.whitelist()
def parse_google_services_json():
    """
    Parse google-services.json and extract FCM configuration values.
    These values are needed for the mobile app to initialize Firebase dynamically.
    """
    settings = frappe.get_single("FCM Settings")

    if not settings.google_services_json:
        return {
            "success": False,
            "message": _("Please paste google-services.json content first")
        }

    try:
        data = json.loads(settings.google_services_json)

        # Extract project_info
        project_info = data.get("project_info", {})
        project_number = project_info.get("project_number", "")
        project_id = project_info.get("project_id", "")

        # Extract client info (first Android client)
        clients = data.get("client", [])
        if not clients:
            return {
                "success": False,
                "message": _("No client configuration found in google-services.json")
            }

        client = clients[0]
        client_info = client.get("client_info", {})
        app_id = client_info.get("mobilesdk_app_id", "")

        # Extract API key
        api_keys = client.get("api_key", [])
        api_key = api_keys[0].get("current_key", "") if api_keys else ""

        # Update settings
        frappe.db.set_value("FCM Settings", "FCM Settings", {
            "fcm_sender_id": project_number,
            "fcm_api_key": api_key,
            "fcm_app_id": app_id,
            "fcm_project_id": project_id
        })
        frappe.db.commit()

        return {
            "success": True,
            "message": _("Configuration extracted successfully!"),
            "sender_id": project_number,
            "api_key": api_key,
            "app_id": app_id,
            "project_id": project_id
        }

    except json.JSONDecodeError:
        return {
            "success": False,
            "message": _("Invalid JSON format in google-services.json")
        }
    except Exception as e:
        return {
            "success": False,
            "message": _("Error parsing google-services.json: {0}").format(str(e))
        }


@frappe.whitelist(allow_guest=True)
def get_firebase_config():
    """
    API endpoint for mobile app to fetch Firebase configuration.
    This allows the universal mobile app to initialize Firebase dynamically
    based on each site's configuration.

    Returns the values needed to create a FirebaseOptions object in the app.
    """
    settings = frappe.get_single("FCM Settings")

    if not settings.fcm_enabled:
        return {
            "success": False,
            "message": "FCM is not enabled on this site"
        }

    # Check if mobile app config is available
    if not settings.fcm_sender_id or not settings.fcm_api_key or not settings.fcm_app_id:
        return {
            "success": False,
            "message": "Mobile app configuration not set. Admin needs to configure google-services.json in FCM Settings."
        }

    return {
        "success": True,
        "config": {
            "project_id": settings.fcm_project_id,
            "sender_id": settings.fcm_sender_id,
            "api_key": settings.fcm_api_key,
            "app_id": settings.fcm_app_id,
            "storage_bucket": f"{settings.fcm_project_id}.appspot.com"
        }
    }


@frappe.whitelist()
def send_test_notification():
    """
    Send a test notification to all registered devices
    """
    from frappe_fcm.fcm.notification_service import send_fcm_message
    from frappe.utils import now_datetime

    # Get all enabled device tokens
    devices = frappe.get_all(
        "FCM Device",
        filters={"enabled": 1},
        fields=["name", "user", "fcm_token"]
    )

    if not devices:
        frappe.throw(_("No registered devices found"))

    success_count = 0
    failed = []

    test_time = now_datetime().strftime("%Y-%m-%d %H:%M:%S")

    for device in devices:
        result = send_fcm_message(
            fcm_token=device.fcm_token,
            title="Test Notification",
            body=f"FCM is working! Sent at {test_time}",
            data={"type": "test", "timestamp": test_time}
        )

        if result.get("success"):
            success_count += 1
        else:
            failed.append(device.user)

    return {
        "success": success_count,
        "failed": len(failed),
        "failed_users": failed
    }
