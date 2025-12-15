# Copyright (c) 2025, Frappe FCM Contributors
# For license information, please see license.txt

"""
FCM HTTP v1 API Implementation (Modern Firebase Cloud Messaging)
This is the recommended way to send FCM notifications.

Also includes legacy API support for backward compatibility.
"""

import json
import frappe
from typing import Dict, Any, Optional
import requests


def get_fcm_settings():
    """
    Get FCM settings from FCM Settings doctype

    Returns:
        dict: FCM settings or None if not configured
    """
    try:
        settings = frappe.get_single("FCM Settings")
        if not settings.fcm_enabled:
            return None

        return {
            "project_id": settings.fcm_project_id,
            "service_account_json": settings.fcm_service_account_json,
            "server_key": None,  # Legacy API deprecated - use service account JSON instead
            "channel_id": settings.notification_channel_id or "frappe_fcm_notifications",
            "log_notifications": settings.log_notifications,
            "enabled": True
        }
    except Exception as e:
        frappe.log_error(f"Error getting FCM settings: {str(e)}", "FCM Settings Error")
        return None


def get_access_token() -> str:
    """
    Get OAuth2 access token for FCM HTTP v1 API using service account

    Returns:
        Access token string

    Raises:
        Exception: If authentication fails
    """
    settings = frappe.get_single("FCM Settings")

    service_account_json = settings.fcm_service_account_json
    if not service_account_json:
        raise Exception("FCM Service Account JSON not configured")

    try:
        from google.oauth2 import service_account
        from google.auth.transport.requests import Request

        service_account_info = json.loads(service_account_json)

        credentials = service_account.Credentials.from_service_account_info(
            service_account_info,
            scopes=['https://www.googleapis.com/auth/firebase.messaging']
        )

        credentials.refresh(Request())
        return credentials.token

    except json.JSONDecodeError:
        raise Exception("Invalid FCM Service Account JSON format")


def send_fcm_v1_message(
    fcm_token: str,
    title: Optional[str],
    body: Optional[str],
    data: Optional[Dict[str, str]] = None,
    image_url: Optional[str] = None
) -> Dict[str, Any]:
    """
    Send push notification using FCM HTTP v1 API (modern approach)

    Args:
        fcm_token: Device FCM token
        title: Notification title (None for data-only message)
        body: Notification body (None for data-only message)
        data: Additional data payload
        image_url: Image URL for notification

    Returns:
        Response dict with success status
    """
    settings = frappe.get_single("FCM Settings")
    project_id = settings.fcm_project_id

    if not project_id:
        return {"success": False, "error": "FCM Project ID not configured"}

    # Get access token
    try:
        access_token = get_access_token()
    except Exception as e:
        frappe.log_error(f"Failed to get FCM access token: {str(e)}", "FCM Auth Error")
        return {"success": False, "error": f"Authentication failed: {str(e)}"}

    # FCM HTTP v1 API endpoint
    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"

    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }

    # Build message payload (v1 format)
    message = {
        "token": fcm_token,
    }

    # Add notification payload (if not a silent data-only message)
    if title or body:
        message["notification"] = {}
        if title:
            message["notification"]["title"] = title
        if body:
            message["notification"]["body"] = body
        if image_url:
            message["notification"]["image"] = image_url

    # Android-specific settings
    channel_id = settings.notification_channel_id or "frappe_fcm_notifications"
    message["android"] = {
        "priority": "high",
        "notification": {
            "sound": "default",
            "channel_id": channel_id
        }
    }

    # Add data payload if provided
    if data:
        message["data"] = {k: str(v) for k, v in data.items()}  # FCM v1 requires string values

    payload = {"message": message}

    try:
        response = requests.post(url, headers=headers, json=payload, timeout=30)

        frappe.logger().info(f"FCM v1 HTTP Status: {response.status_code}")
        frappe.logger().debug(f"FCM v1 Response: {response.text[:500]}")

        if response.status_code == 200:
            result = response.json()
            return {
                "success": True,
                "message_name": result.get("name", ""),
                "response": result
            }
        else:
            # Parse error response
            try:
                error_data = response.json()
                error_msg = error_data.get("error", {}).get("message", "Unknown error")
                error_code = error_data.get("error", {}).get("code", "")
                error_status = error_data.get("error", {}).get("status", "")
            except:
                error_msg = response.text
                error_code = response.status_code
                error_status = ""

            # Check for UNREGISTERED error (invalid/expired token)
            fcm_error_code = None
            try:
                details = error_data.get("error", {}).get("details", [])
                if details:
                    fcm_error_code = details[0].get("errorCode", "")
            except:
                pass

            # Auto-disable invalid tokens
            if fcm_error_code == "UNREGISTERED" or "UNREGISTERED" in str(error_status):
                _disable_token(fcm_token)

            # Log error with helpful messages
            help_msg = ""
            if response.status_code == 404:
                if fcm_error_code == "UNREGISTERED":
                    help_msg = "\nToken is UNREGISTERED - device has been automatically disabled."
                else:
                    help_msg = "\nPossible cause: Firebase Cloud Messaging API not enabled. Enable it at: https://console.cloud.google.com/apis/library/fcm.googleapis.com"
            elif response.status_code == 403:
                help_msg = "\nPermission denied. Check service account has FCM permissions."

            frappe.log_error(
                f"FCM v1 Send Failed\nURL: {url}\nStatus: {response.status_code}\nToken: {fcm_token[:20]}...\nError: {error_msg}\nFCM Error Code: {fcm_error_code}{help_msg}",
                "FCM v1 Send Error"
            )

            return {
                "success": False,
                "error": f"{error_msg}{help_msg}",
                "error_code": fcm_error_code or error_code
            }

    except Exception as e:
        frappe.log_error(
            f"FCM v1 Exception: {str(e)}\nToken: {fcm_token[:20]}...",
            "FCM v1 Exception"
        )
        return {"success": False, "error": str(e)}


def send_fcm_legacy_message(
    fcm_token: str,
    title: Optional[str],
    body: Optional[str],
    data: Optional[Dict[str, str]] = None,
    image_url: Optional[str] = None
) -> Dict[str, Any]:
    """
    Send push notification using FCM Legacy HTTP API (deprecated but still functional)

    Args:
        fcm_token: Device FCM token
        title: Notification title
        body: Notification body
        data: Additional data payload
        image_url: Image URL for notification

    Returns:
        Response dict with success status
    """
    settings = get_fcm_settings()
    if not settings or not settings.get("server_key"):
        return {"success": False, "error": "FCM Legacy Server Key not configured"}

    url = "https://fcm.googleapis.com/fcm/send"

    headers = {
        "Authorization": f"key={settings['server_key']}",
        "Content-Type": "application/json"
    }

    # Build notification payload
    payload = {
        "to": fcm_token,
        "priority": "high"
    }

    # Add notification if title/body provided
    if title or body:
        notification = {
            "sound": "default",
            "badge": 1
        }
        if title:
            notification["title"] = title
        if body:
            notification["body"] = body
        if image_url:
            notification["image"] = image_url
        payload["notification"] = notification

    # Add data payload if provided
    if data:
        payload["data"] = data

    try:
        response = requests.post(url, headers=headers, json=payload, timeout=30)

        frappe.logger().info(f"FCM Legacy HTTP Status: {response.status_code}")
        frappe.logger().debug(f"FCM Legacy Response: {response.text[:500]}")

        if not response.text or response.text.strip() == "":
            frappe.log_error(
                f"FCM Legacy Empty Response\nHTTP Status: {response.status_code}\nToken: {fcm_token[:20]}...",
                "FCM Legacy Empty Response"
            )
            return {"success": False, "error": "Empty response from FCM"}

        try:
            result = response.json()
        except ValueError as json_err:
            frappe.log_error(
                f"FCM Legacy Invalid JSON\nHTTP Status: {response.status_code}\nResponse: {response.text[:500]}",
                "FCM Legacy Invalid JSON"
            )
            return {"success": False, "error": f"Invalid JSON response: {str(json_err)}"}

        if result.get("success") == 1:
            return {
                "success": True,
                "message_id": result.get("results", [{}])[0].get("message_id"),
                "response": result
            }
        else:
            error = result.get("results", [{}])[0].get("error", "Unknown error")

            # Auto-disable invalid tokens
            if error in ["InvalidRegistration", "NotRegistered"]:
                _disable_token(fcm_token)

            frappe.log_error(
                f"FCM Legacy Send Failed\nToken: {fcm_token[:20]}...\nError: {error}\nFull Response: {result}",
                "FCM Legacy Send Error"
            )
            return {"success": False, "error": error, "response": result}

    except requests.exceptions.RequestException as req_err:
        frappe.log_error(
            f"FCM Legacy Request Exception: {str(req_err)}\nToken: {fcm_token[:20]}...",
            "FCM Legacy Request Error"
        )
        return {"success": False, "error": f"Request failed: {str(req_err)}"}
    except Exception as e:
        frappe.log_error(
            f"FCM Legacy Exception: {str(e)}\nToken: {fcm_token[:20]}...",
            "FCM Legacy Exception"
        )
        return {"success": False, "error": str(e)}


def send_fcm_to_topic(
    topic: str,
    title: str,
    body: str,
    data: Optional[Dict[str, str]] = None
) -> Dict[str, Any]:
    """
    Send a push notification to a topic (all subscribed devices)

    Args:
        topic: Topic name (e.g., "all_users", "admins")
        title: Notification title
        body: Notification body text
        data: Additional data payload (optional)

    Returns:
        API response dict
    """
    settings = get_fcm_settings()
    if not settings:
        return {"success": False, "error": "FCM not configured"}

    # Use v1 API if service account is configured
    if settings.get("service_account_json"):
        try:
            access_token = get_access_token()
            project_id = settings.get("project_id")

            url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
            headers = {
                "Authorization": f"Bearer {access_token}",
                "Content-Type": "application/json"
            }

            message = {
                "topic": topic,
                "notification": {
                    "title": title,
                    "body": body
                },
                "android": {
                    "priority": "high"
                }
            }

            if data:
                message["data"] = {k: str(v) for k, v in data.items()}

            response = requests.post(url, headers=headers, json={"message": message}, timeout=30)
            result = response.json()

            if response.status_code == 200:
                return {"success": True, "response": result}
            else:
                return {"success": False, "error": result.get("error", {}).get("message", "Unknown error")}

        except Exception as e:
            frappe.log_error(f"FCM Topic Send Error: {str(e)}", "FCM Topic Error")
            return {"success": False, "error": str(e)}

    # Fallback to legacy API
    if not settings.get("server_key"):
        return {"success": False, "error": "FCM not properly configured"}

    url = "https://fcm.googleapis.com/fcm/send"
    headers = {
        "Authorization": f"key={settings['server_key']}",
        "Content-Type": "application/json"
    }

    payload = {
        "to": f"/topics/{topic}",
        "notification": {
            "title": title,
            "body": body,
            "sound": "default"
        },
        "priority": "high"
    }

    if data:
        payload["data"] = data

    try:
        response = requests.post(url, headers=headers, json=payload, timeout=30)
        result = response.json()
        return {"success": True, "response": result}
    except Exception as e:
        return {"success": False, "error": str(e)}


def _disable_token(fcm_token: str):
    """
    Disable an invalid FCM token

    Args:
        fcm_token: The FCM token to disable
    """
    try:
        existing_devices = frappe.get_all(
            "FCM Device",
            filters={"fcm_token": fcm_token},
            pluck="name"
        )
        for device_name in existing_devices:
            frappe.db.set_value("FCM Device", device_name, "enabled", 0)

        frappe.db.commit()
        frappe.logger().info(f"Disabled unregistered token: {fcm_token[:20]}...")
    except Exception as e:
        frappe.log_error(f"Error disabling token: {str(e)}", "FCM Token Disable Error")
