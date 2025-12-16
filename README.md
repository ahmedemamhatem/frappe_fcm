<p align="center">
  <img src="https://img.shields.io/badge/Frappe-FCM-blue?style=for-the-badge" alt="Frappe FCM"/>
  <img src="https://img.shields.io/badge/Firebase-Cloud%20Messaging-orange?style=for-the-badge&logo=firebase" alt="Firebase"/>
  <img src="https://img.shields.io/badge/Android-Ready-green?style=for-the-badge&logo=android" alt="Android"/>
</p>

<h1 align="center">Frappe FCM</h1>

<p align="center">
  <strong>Enterprise-grade Firebase Cloud Messaging (FCM) Push Notifications for Frappe/ERPNext</strong>
</p>

<p align="center">
  A complete, production-ready solution for sending real-time push notifications to mobile apps connected to your Frappe/ERPNext system.
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#installation">Installation</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#usage">Usage</a> •
  <a href="#api-reference">API Reference</a> •
  <a href="#mobile-app">Mobile App</a>
</p>

---

## Features

### Core Capabilities

| Feature | Description |
|---------|-------------|
| **FCM HTTP v1 API** | Latest Google-recommended API with OAuth2 authentication |
| **Legacy API Fallback** | Automatic fallback to legacy API if needed |
| **Multi-Device Support** | Users can register unlimited devices |
| **Smart Token Management** | Automatic cleanup of invalid/expired tokens |
| **Comprehensive Logging** | Full audit trail of all notification attempts |
| **Frappe Integration** | Seamless integration with Frappe Notification system |

### Android App Features

| Feature | Description |
|---------|-------------|
| **Universal Connectivity** | Connect to any Frappe/ERPNext site |
| **Secure Authentication** | Login with Frappe credentials |
| **Deep Linking** | Tap notifications to open specific documents |
| **Background Notifications** | Receive notifications even when app is closed |
| **Material Design** | Modern, clean UI following Material Design 3 |
| **Offline Handling** | Graceful handling of connectivity issues |

### Developer Experience

| Feature | Description |
|---------|-------------|
| **Simple Python API** | Send notifications with one line of code |
| **REST API** | Full REST API for external integrations |
| **Webhook Support** | Trigger notifications from external systems |
| **Batch Sending** | Send to multiple users efficiently |
| **Topic Messaging** | Broadcast to subscribed topics |

---

## Quick Start

### 3-Step Setup (No Firebase Configuration Required!)

The mobile app uses a **shared Firebase project** - you don't need to create your own!

```bash
# Step 1: Install the app
bench get-app https://github.com/ahmedemamhatem/frappe_fcm
bench --site your-site install-app frappe_fcm

# Step 2: Configure in Frappe
# 1. Go to FCM Settings
# 2. Enable FCM checkbox
# 3. Click "Fetch Shared Credentials" button (auto-fills everything!)
# 4. Save

# Step 3: Install mobile app and login
# Download APK from releases, enter your site URL, login with Frappe credentials
```

That's it! No Firebase console setup needed.

### Send Your First Notification

```bash
bench --site your-site execute frappe_fcm.fcm.notification_service.send_notification_to_user \
  --args '["user@example.com", "Hello!", "Your first push notification"]'
```

---

## Installation

### Prerequisites

- Frappe/ERPNext v14 or higher
- Python 3.10+
- Firebase Project with Cloud Messaging enabled

### Step 1: Install the Frappe App

```bash
# Navigate to your bench directory
cd ~/frappe-bench

# Get the app from GitHub
bench get-app https://github.com/ahmedemamhatem/frappe_fcm

# Install on your site
bench --site your-site.localhost install-app frappe_fcm

# Run migrations
bench --site your-site.localhost migrate

# Clear cache
bench --site your-site.localhost clear-cache
```

### Step 2: Configure FCM Settings (Easy Way)

1. Go to your Frappe site
2. Search for **"FCM Settings"** in the awesomebar
3. Check **"Enable FCM"**
4. Click **"Fetch Shared Credentials"** button - this auto-fills the Firebase credentials
5. Click **Save**
6. Click **"Test FCM Connection"** button to verify

> **Note:** The app uses a shared Firebase project maintained by the developer. No Firebase console setup required!

### Step 3: Install the Mobile App

1. Download the APK from [GitHub Releases](https://github.com/ahmedemamhatem/frappe_fcm/releases)
2. Install on your Android device
3. Enter your Frappe site URL
4. Login with your Frappe credentials
5. Done! You'll now receive push notifications

---

## Advanced Setup (Own Firebase Project)

If you prefer to use your own Firebase project:

<details>
<summary>Click to expand custom Firebase setup instructions</summary>

### Create Firebase Project

1. Navigate to [Firebase Console](https://console.firebase.google.com)
2. Click **"Add project"** or select an existing project
3. Enter a project name (e.g., "My ERP Notifications")
4. Complete the setup wizard

### Generate Service Account Key

1. In Firebase Console, click **Settings (gear icon)** → **Project Settings**
2. Navigate to **"Service Accounts"** tab
3. Click **"Generate New Private Key"**
4. Download and securely store the JSON file
5. Copy the entire JSON content

### Add Android App in Firebase

1. In Firebase Console, go to **Project Settings** → **Your apps**
2. Click **Add app** → **Android**
3. Package name: `io.frappe.fcm`
4. Download `google-services.json`

### Build Custom APK

```bash
cd frappe_fcm/mobile_app
cp /path/to/google-services.json app/
./gradlew assembleRelease
```

### Configure Frappe

1. Go to **FCM Settings**
2. Enable FCM
3. Enter your Firebase Project ID
4. Paste the Service Account JSON
5. Save and test connection

</details>

---

## Configuration

### FCM Settings Options

#### Firebase Configuration
| Setting | Description | Required |
|---------|-------------|----------|
| Enable FCM | Master switch for FCM functionality | Yes |
| Firebase Project ID | Your Firebase project identifier | Yes |
| Service Account JSON | OAuth2 credentials for FCM v1 API | Yes |

#### Frappe Integration
| Setting | Description | Default |
|---------|-------------|---------|
| Auto Send on Notification | Send FCM when Frappe Notification triggers | Enabled |
| Send for System Notifications | Include system notifications | Enabled |
| Send for Email Notifications | Include email notifications | Enabled |

#### Notification Defaults
| Setting | Description | Default |
|---------|-------------|---------|
| Channel ID | Android notification channel | `frappe_fcm_notifications` |
| Default Title | Fallback notification title | `Notification` |
| Default Sound | Notification sound | `default` |
| Default Icon | Android drawable resource | (app icon) |

#### Advanced Settings
| Setting | Description | Default |
|---------|-------------|---------|
| Send Asynchronously | Background processing | Enabled |
| Retry Failed | Auto-retry failed notifications | Enabled |
| Max Retries | Maximum retry attempts | 3 |
| Log Notifications | Store notification logs | Enabled |

---

## Usage

### Python API

#### Basic Usage

```python
from frappe_fcm.fcm.notification_service import (
    send_notification_to_user,
    send_notification_to_users,
    NotificationService
)

# Send to a single user
send_notification_to_user(
    user="user@example.com",
    title="New Message",
    body="You have a new message waiting"
)
```

#### Advanced Usage

```python
# Send to multiple users
send_notification_to_users(
    users=["user1@example.com", "user2@example.com", "user3@example.com"],
    title="Team Announcement",
    body="Meeting at 3 PM today"
)

# Send with custom data payload (for deep linking)
send_notification_to_user(
    user="user@example.com",
    title="Order Approved",
    body="Sales Order SO-00123 has been approved",
    data={
        "doctype": "Sales Order",
        "name": "SO-00123",
        "url": "https://your-site.com/app/sales-order/SO-00123"
    }
)

# Broadcast to all registered users
NotificationService.notify_all(
    title="System Maintenance",
    body="Scheduled maintenance tonight at 11 PM",
    exclude_users=["admin@example.com"]  # Optional exclusion
)

# Send to a topic (requires topic subscription)
NotificationService.send_to_topic(
    topic="sales_team",
    title="New Lead",
    body="A new lead has been assigned to sales team"
)
```

#### Document-based Notifications

```python
# Automatically generates URL from doctype and name
send_notification_to_user(
    user="user@example.com",
    title="Invoice Ready",
    body="Invoice INV-00456 is ready for review",
    reference_doctype="Sales Invoice",
    reference_name="INV-00456"
)
```

### REST API

#### Register Device Token

```http
POST /api/method/frappe_fcm.fcm.notification_service.register_user_fcm_token
Content-Type: application/json
Cookie: sid=your-session-cookie

{
    "token": "fcm_device_token_from_firebase",
    "device_model": "Samsung Galaxy S23",
    "os_version": "Android 14",
    "app_version": "1.0.0"
}
```

**Response:**
```json
{
    "message": {
        "success": true,
        "message": "Device registered",
        "device": "FCM-DEV-00001"
    }
}
```

#### Send Notification

```http
POST /api/method/frappe_fcm.fcm.notification_service.send_push_to_user
Content-Type: application/json
Authorization: token api_key:api_secret

{
    "user": "user@example.com",
    "title": "Hello from API",
    "body": "This notification was sent via REST API",
    "data": "{\"url\": \"/app/todo\"}"
}
```

#### Validate Connection

```http
GET /api/method/frappe_fcm.fcm.notification_service.validate_connection
```

**Response:**
```json
{
    "message": {
        "success": true,
        "message": "Frappe FCM is installed",
        "fcm_enabled": true,
        "site": "your-site.com",
        "version": "1.0.0"
    }
}
```

#### Get My Devices

```http
GET /api/method/frappe_fcm.fcm.notification_service.get_my_devices
Cookie: sid=your-session-cookie
```

---

## API Reference

### Functions

| Function | Parameters | Description |
|----------|------------|-------------|
| `send_notification_to_user` | `user, title, body, data, reference_doctype, reference_name` | Send notification to a single user |
| `send_notification_to_users` | `users, title, body, data, reference_doctype, reference_name` | Send to multiple users |
| `send_fcm_message` | `fcm_token, title, body, data, image_url` | Send to specific device token |
| `get_user_fcm_tokens` | `user` | Get all tokens for a user |
| `NotificationService.notify` | `users, title, body, data, ...` | Class method for notifications |
| `NotificationService.notify_all` | `title, body, data, exclude_users` | Broadcast to all users |
| `NotificationService.send_to_topic` | `topic, title, body, data` | Send to FCM topic |

### Whitelisted API Endpoints

| Endpoint | Auth Level | Description |
|----------|------------|-------------|
| `validate_connection` | Guest | Check if FCM is installed and configured |
| `register_device_token` | Guest | Register device (queued for auth) |
| `register_user_fcm_token` | Authenticated | Register FCM token for user |
| `unregister_device` | Authenticated | Remove device registration |
| `get_my_devices` | Authenticated | List current user's devices |
| `send_push_to_user` | Authenticated | Send push to specific user |
| `test_fcm_connection` | System Manager | Test FCM configuration |

---

## Mobile App

### Download

| Build | Download | Description |
|-------|----------|-------------|
| **Release** | [frappe-fcm-release.apk](https://github.com/ahmedemamhatem/frappe_fcm/raw/main/releases/frappe-fcm-release.apk) | Production build (recommended) |
| **Debug** | [frappe-fcm-debug.apk](https://github.com/ahmedemamhatem/frappe_fcm/raw/main/releases/frappe-fcm-debug.apk) | Development build with logging |

### App Screenshots

The app provides a simple, clean interface:

1. **Login Screen** - Enter your site URL and credentials
2. **Connected Screen** - Shows connection status and site info
3. **Notifications** - Receive and tap to open documents

### Building from Source

```bash
# Clone the repository
git clone https://github.com/ahmedemamhatem/frappe_fcm.git
cd frappe_fcm/mobile_app

# Add your google-services.json
cp /path/to/google-services.json app/

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# APKs will be in app/build/outputs/apk/
```

### Customization

To customize the app for your organization:

1. Update `app/src/main/res/values/strings.xml` for app name
2. Replace icons in `app/src/main/res/mipmap-*/`
3. Modify colors in `app/src/main/res/values/colors.xml`
4. Update `app/build.gradle` for package name (requires new Firebase app)

---

## DocTypes

### FCM Settings (Single DocType)

Central configuration for FCM integration.

| Field | Type | Description |
|-------|------|-------------|
| fcm_enabled | Check | Master enable/disable switch |
| fcm_project_id | Data | Firebase Project ID |
| fcm_service_account_json | Code | Service account credentials |
| auto_send_on_notification | Check | Integration with Frappe Notifications |
| notification_channel_id | Data | Android notification channel |
| send_async | Check | Background processing |
| log_notifications | Check | Enable notification logging |

### FCM Device

Registered user devices.

| Field | Type | Description |
|-------|------|-------------|
| user | Link (User) | Device owner |
| fcm_token | Data | Firebase device token |
| device_id | Data | Unique device identifier |
| device_model | Data | Device model info |
| os_version | Data | Operating system version |
| app_version | Data | App version |
| enabled | Check | Token validity status |
| last_used | Datetime | Last notification sent |
| notification_count | Int | Total notifications sent |

### FCM Notification Log

Audit trail for all notifications.

| Field | Type | Description |
|-------|------|-------------|
| notification_type | Data | Type identifier |
| status | Select | Sent / Failed |
| recipient_user | Link (User) | Target user |
| title | Data | Notification title |
| body | Text | Notification body |
| data_payload | Code | JSON data payload |
| response | Code | FCM API response |
| error_message | Text | Error details if failed |
| sent_at | Datetime | Timestamp |

---

## Troubleshooting

### Common Issues

<details>
<summary><strong>"FCM not configured" error</strong></summary>

1. Go to **FCM Settings**
2. Ensure **Enable FCM** is checked
3. Verify **Firebase Project ID** matches your Firebase project
4. Check that **Service Account JSON** is complete and valid
5. Click **Test FCM Connection** to verify
</details>

<details>
<summary><strong>"Authentication failed" when sending notifications</strong></summary>

1. Regenerate Service Account JSON from Firebase Console
2. Ensure you copied the **entire** JSON content
3. Check Firebase Cloud Messaging API is enabled in Google Cloud Console
4. Verify the service account has necessary permissions
</details>

<details>
<summary><strong>Notifications not being received</strong></summary>

1. Check device is registered in **FCM Device** list
2. Verify the device token is **Enabled**
3. Check **FCM Notification Log** for errors
4. Ensure app is not in battery optimization mode
5. Verify notification channel matches (`frappe_fcm_notifications`)
</details>

<details>
<summary><strong>Mobile app "Connection failed"</strong></summary>

1. Verify site URL is correct and accessible
2. Check site has valid SSL certificate (HTTPS)
3. Ensure `frappe_fcm` app is installed on the site
4. Test the URL in a browser first
</details>

<details>
<summary><strong>Token registration fails</strong></summary>

1. Ensure user is logged in (not Guest)
2. Check session cookies are being sent
3. Verify site URL is correct in mobile app
4. Check server logs for errors
</details>

### Debug Mode

Enable debug logging in the mobile app:
1. Use the **Debug APK** instead of Release
2. Connect device to Android Studio
3. Filter logcat by tag `FrappeFCM`

---

## Changelog

### Version 1.0.0
- Initial release
- FCM HTTP v1 API support
- Android app with login flow
- Frappe Notification integration
- Comprehensive logging

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## Support

- **Issues**: [GitHub Issues](https://github.com/ahmedemamhatem/frappe_fcm/issues)
- **Discussions**: [GitHub Discussions](https://github.com/ahmedemamhatem/frappe_fcm/discussions)

---

## Author

**Ahmed Emam**

- Email: ahmedemamhatem@gmail.com
- GitHub: [@ahmedemamhatem](https://github.com/ahmedemamhatem)

---

## License

This project is licensed under the MIT License - see the [LICENSE](license.txt) file for details.

---

<p align="center">
  Made with ❤️ for the Frappe/ERPNext community
</p>

