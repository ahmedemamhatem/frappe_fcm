# Frappe FCM

Firebase Cloud Messaging (FCM) Push Notifications for Frappe/ERPNext applications.

A complete, production-ready solution for sending push notifications to mobile apps connected to your Frappe/ERPNext system.

## Features

- **FCM HTTP v1 API Support** (recommended by Google)
- **Legacy FCM API Support** (fallback)
- **Multi-device Support** - Users can register multiple devices
- **Automatic Token Management** - Invalid tokens are auto-disabled
- **Notification Logging** - Track all notification attempts
- **Ready-to-use Android App** - Generic app that works with any Frappe site
- **Simple Python API** - Send notifications with one line of code
- **Frappe Notification Integration** - Auto-sends FCM when Frappe Notifications trigger

## Complete Setup Guide

### Step 1: Install the Frappe App

```bash
# Get the app
bench get-app https://github.com/ahmedemamhatem/frappe_fcm

# Install on your site
bench --site your-site.localhost install-app frappe_fcm

# Run migrations
bench --site your-site.localhost migrate
```

### Step 2: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Click **"Add project"**
3. Enter a project name (e.g., "My Frappe App")
4. Follow the wizard (you can disable Google Analytics)
5. Click **"Create project"**

### Step 3: Get Service Account JSON (for Frappe Backend)

This allows your Frappe server to send push notifications.

1. In Firebase Console, click the **gear icon** (top left) → **Project Settings**
2. Go to **"Service Accounts"** tab
3. Click **"Generate New Private Key"**
4. Click **"Generate Key"** to download the JSON file
5. Open the downloaded JSON file in a text editor
6. **Copy all the contents**

### Step 4: Configure FCM Settings in Frappe

1. In your Frappe site, go to **FCM Settings** (search in awesomebar)
2. Check **"Enable FCM"**
3. Enter your **Firebase Project ID** (found in Firebase Console → Project Settings → General)
4. Paste the **Service Account JSON** contents into the "FCM Service Account JSON" field
5. Click **Save**
6. Click **"Test FCM Connection"** to verify

### Step 5: Add Android App to Firebase

This creates the configuration needed for the mobile app.

1. In Firebase Console → Project Settings, scroll to **"Your apps"**
2. Click **"Add app"** → Select **Android** icon
3. Enter these details:
   - **Android package name**: `io.frappe.fcm`
   - **App nickname**: Frappe FCM (optional)
4. Click **"Register app"**
5. Click **"Download google-services.json"**
6. **Save this file** - needed if you want to build your own APK

### Step 6: Install Mobile App

**Option A: Use Pre-built APK (Easiest)**

1. Download APK from [GitHub Releases](https://github.com/ahmedemamhatem/frappe_fcm/releases)
2. Install on your Android device
3. Open the app
4. Enter your Frappe site URL (e.g., `https://your-site.frappe.cloud`)
5. Log in with your Frappe credentials
6. Done! The app will automatically register for push notifications

**Option B: Build Your Own APK**

If you want to customize the app or use your own Firebase project:

1. Clone this repository
2. Place `google-services.json` in `mobile_app/app/` folder
3. (Optional) Customize app name, colors, icons
4. Build:
   ```bash
   cd mobile_app
   ./gradlew assembleRelease
   ```
5. APK will be in `app/build/outputs/apk/release/`

## How It Works

### Automatic Notifications

When **Frappe Notification Integration** is enabled (default):
- When a Frappe Notification triggers (System or Email type)
- FCM push is automatically sent to the recipient
- Only users with registered mobile devices receive push notifications
- You can enable/disable FCM per-notification rule

### Manual Notifications

Send notifications from Python code:

```python
from frappe_fcm.fcm.notification_service import send_notification_to_user

# Send to a single user
send_notification_to_user(
    user="user@example.com",
    title="Hello!",
    body="This is a test notification"
)
```

## Usage Examples

### Python API

```python
from frappe_fcm.fcm.notification_service import (
    send_notification_to_user,
    send_notification_to_users,
    NotificationService
)

# Send to a single user
send_notification_to_user(
    user="user@example.com",
    title="Hello!",
    body="This is a test notification"
)

# Send to multiple users
send_notification_to_users(
    users=["user1@example.com", "user2@example.com"],
    title="Announcement",
    body="Important update!"
)

# Send with data payload (for deep linking)
send_notification_to_user(
    user="user@example.com",
    title="New Order",
    body="Order #1234 has been placed",
    data={
        "url": "/app/sales-order/SO-00001",
        "order_id": "SO-00001"
    }
)

# Send to all users with registered devices
NotificationService.notify_all(
    title="System Update",
    body="The system will be updated tonight"
)
```

### REST API

Register device token (from mobile app):

```http
POST /api/method/frappe_fcm.fcm.notification_service.register_user_fcm_token

{
    "token": "fcm_device_token_here",
    "device_model": "Samsung Galaxy S21",
    "os_version": "Android 13",
    "app_version": "1.0.0"
}
```

Send notification via API:

```http
POST /api/method/frappe_fcm.fcm.notification_service.send_push_to_user

{
    "user": "user@example.com",
    "title": "Hello",
    "body": "World",
    "data": "{\"url\": \"/app/sales-order/SO-001\"}"
}
```

## Android App Features

- **Configurable Site URL** - Users enter their site on first launch
- **WebView-based** - Displays your Frappe site as a native app
- **Automatic Token Registration** - Registers FCM token when user logs in
- **Push Notifications** - Receives and displays notifications
- **Deep Linking** - Tapping notification opens the relevant document
- **Offline Detection** - Shows retry button when offline
- **Pull-to-Refresh** - Swipe down to refresh
- **File Upload** - Supports file attachments
- **Settings Menu** - Change site URL anytime

## DocTypes

### FCM Settings (Single)

Configuration for FCM integration:
- Firebase Project ID
- Service Account JSON (recommended)
- Legacy Server Key (fallback)
- Frappe Notification Integration settings
- Notification defaults (channel ID, sound, icon)
- Advanced settings (async, retry, logging)

### FCM Device

Registered user devices:
- User (link to User)
- FCM Token
- Device info (model, OS, app version)
- Enabled status
- Usage statistics

### FCM Notification Log

Log of all notification attempts:
- Notification type
- Status (Sent/Failed)
- Recipient info
- Content (title, body, data)
- API response/error

## API Reference

### Main Functions

| Function | Description |
|----------|-------------|
| `send_notification_to_user(user, title, body, data)` | Send to single user |
| `send_notification_to_users(users, title, body, data)` | Send to multiple users |
| `send_fcm_message(fcm_token, title, body, data)` | Send to specific token |
| `NotificationService.notify_all(title, body, data)` | Send to all devices |

### Whitelisted Endpoints

| Endpoint | Auth | Description |
|----------|------|-------------|
| `validate_connection` | Guest | Check if FCM app is installed |
| `register_device_token` | Guest | Register device (deferred) |
| `register_user_fcm_token` | User | Register device token |
| `unregister_device` | User | Remove device |
| `get_my_devices` | User | List user's devices |
| `send_push_to_user` | User | Send notification |
| `test_fcm_connection` | User | Test FCM config |

## Troubleshooting

### "FCM not configured"
- Go to FCM Settings and ensure FCM is enabled
- Check that Service Account JSON is pasted correctly
- Verify Firebase Project ID is correct

### "Authentication failed"
- Verify Service Account JSON is valid and complete
- Make sure you copied the entire JSON file contents
- Check Firebase Cloud Messaging API is enabled in Google Cloud Console

### Notifications not received
- Check the device is registered in FCM Device list
- Verify the user is logged in on the mobile app
- Check FCM Notification Log for errors
- Ensure notification channel matches (default: `frappe_fcm_notifications`)

### Token registration fails
- Ensure user is logged in (not Guest)
- Check cookies are being sent with the request
- Verify site URL is correct in the mobile app

### Mobile app shows "Connection failed"
- Verify the site URL is correct and accessible
- Check that frappe_fcm app is installed on the site
- Ensure the site has valid SSL certificate (for HTTPS)

## License

MIT License - see [LICENSE](license.txt)
