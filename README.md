# Frappe FCM

Firebase Cloud Messaging (FCM) Push Notifications for Frappe/ERPNext applications.

A complete, production-ready solution for sending push notifications to mobile apps connected to your Frappe/ERPNext system.

## Features

- **FCM HTTP v1 API Support** (recommended by Google)
- **Legacy FCM API Support** (fallback)
- **Multi-device Support** - Users can register multiple devices
- **Automatic Token Management** - Invalid tokens are auto-disabled
- **Notification Logging** - Track all notification attempts
- **Ready-to-use Android App Template** - Just configure and build
- **Simple Python API** - Send notifications with one line of code

## Installation

### 1. Install the app

```bash
# Get the app
bench get-app https://github.com/frappe/frappe_fcm

# Install on your site
bench --site your-site.localhost install-app frappe_fcm

# Run migrations
bench --site your-site.localhost migrate
```

### 2. Configure Firebase

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create a new project or select existing
3. Go to **Project Settings** > **Service Accounts**
4. Click **Generate New Private Key**
5. Download the JSON file

### 3. Configure Frappe FCM

1. Go to **FCM Settings** in your Frappe site
2. Enable FCM
3. Enter your **Firebase Project ID**
4. Paste the **Service Account JSON** contents
5. Click **Test Connection** to verify

## Usage

### Python API

```python
from frappe_fcm import send_notification

# Send to a single user
send_notification(
    user="user@example.com",
    title="Hello!",
    body="This is a test notification"
)

# Send to multiple users
from frappe_fcm import send_notification_to_users

send_notification_to_users(
    users=["user1@example.com", "user2@example.com"],
    title="Announcement",
    body="Important update!"
)

# Send with data payload
send_notification(
    user="user@example.com",
    title="New Order",
    body="Order #1234 has been placed",
    data={
        "url": "/app/sales-order/SO-00001",
        "order_id": "SO-00001"
    }
)

# Send to all users with registered devices
from frappe_fcm.fcm.notification_service import NotificationService

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

## Mobile App Setup

The `mobile_app` folder contains a ready-to-use Android app template.

### Quick Start

1. **Copy the mobile_app folder** to your development machine

2. **Configure your site URL** in `app/src/main/java/io/frappe/fcm/Config.java`:
   ```java
   public static final String BASE_URL = "https://your-site.frappe.cloud";
   ```

3. **Add Firebase to your Android app**:
   - Go to Firebase Console > Project Settings > Add App > Android
   - Enter your package name (default: `io.frappe.fcm`)
   - Download `google-services.json`
   - Place it in `mobile_app/app/` folder

4. **Customize the app** (optional):
   - Change package name in `app/build.gradle`
   - Update app name in `res/values/strings.xml`
   - Change colors in `res/values/colors.xml`
   - Replace app icon in `res/mipmap-*/` folders

5. **Build the APK**:
   ```bash
   cd mobile_app
   ./gradlew assembleRelease
   ```
   APK will be in `app/build/outputs/apk/release/`

### Android App Features

- WebView-based app displaying your Frappe site
- Automatic FCM token registration on user login
- Push notification handling with deep linking
- Offline detection with retry
- Pull-to-refresh
- File upload support
- External link handling

## DocTypes

### FCM Settings (Single)

Configuration for FCM integration:
- Firebase Project ID
- Service Account JSON (recommended)
- Legacy Server Key (fallback)
- Notification defaults
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
| `send_notification(user, title, body, data)` | Send to single user |
| `send_notification_to_users(users, title, body, data)` | Send to multiple users |
| `send_fcm_message(fcm_token, title, body, data)` | Send to specific token |
| `NotificationService.notify_all(title, body, data)` | Send to all devices |

### Whitelisted Endpoints

| Endpoint | Auth | Description |
|----------|------|-------------|
| `register_device_token` | Guest | Register device (deferred) |
| `register_user_fcm_token` | User | Register device token |
| `unregister_device` | User | Remove device |
| `get_my_devices` | User | List user's devices |
| `send_push_to_user` | User | Send notification |
| `test_fcm_connection` | User | Test FCM config |

## Troubleshooting

### "FCM not configured"
- Check FCM Settings has Service Account JSON or Server Key
- Ensure FCM is enabled in settings

### "Authentication failed"
- Verify Service Account JSON is valid
- Check Firebase Cloud Messaging API is enabled in Google Cloud Console

### Notifications not received
- Check device token is valid (use "Check Token Status" button)
- Verify notification channel matches (default: `frappe_fcm_notifications`)
- Check Error Log for FCM errors

### Token registration fails
- Ensure user is logged in (not Guest)
- Check cookies are being sent with the request
- Verify API endpoint URL is correct

## License

MIT License - see [LICENSE](license.txt)

## Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## Support

- Issues: [GitHub Issues](https://github.com/frappe/frappe_fcm/issues)
- Discussions: [GitHub Discussions](https://github.com/frappe/frappe_fcm/discussions)
