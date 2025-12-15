# Frappe FCM Android App Template

A ready-to-use Android app template for receiving push notifications from your Frappe/ERPNext system.

## Quick Start

### 1. Prerequisites

- Android Studio (Arctic Fox or newer)
- Java 11+
- Firebase project with your Frappe site

### 2. Setup

1. **Open in Android Studio**
   ```bash
   # Open the mobile_app folder in Android Studio
   ```

2. **Configure your site URL**

   Edit `app/src/main/java/io/frappe/fcm/Config.java`:
   ```java
   public static final String BASE_URL = "https://your-site.frappe.cloud";
   ```

3. **Add Firebase configuration**

   - Go to [Firebase Console](https://console.firebase.google.com)
   - Select your project
   - Go to Project Settings > Add App > Android
   - Enter package name: `io.frappe.fcm` (or your custom package)
   - Download `google-services.json`
   - Place it in the `app/` folder

4. **Customize (Optional)**

   **Change Package Name** (if needed):
   - Update `applicationId` in `app/build.gradle`
   - Update `namespace` in `app/build.gradle`
   - Rename the Java package folder
   - Update package declarations in all Java files
   - Update `AndroidManifest.xml` references

   **Change App Name**:
   - Edit `app/src/main/res/values/strings.xml`

   **Change Colors**:
   - Edit `app/src/main/res/values/colors.xml`

   **Change App Icon**:
   - Replace icons in `res/mipmap-*/` folders
   - Use Android Studio's Image Asset tool

### 3. Build

**Debug build:**
```bash
./gradlew assembleDebug
```

**Release build:**
```bash
./gradlew assembleRelease
```

APK location: `app/build/outputs/apk/`

### 4. Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Configuration Reference

### Config.java

| Constant | Description | Default |
|----------|-------------|---------|
| `BASE_URL` | Your Frappe site URL | `https://your-site.frappe.cloud` |
| `TOKEN_REGISTER_API` | FCM token registration endpoint | Auto-generated |
| `NOTIFICATION_CHANNEL_ID` | Android notification channel ID | `frappe_fcm_notifications` |
| `DEBUG` | Enable debug logging | `false` |

### Deep Linking

Update `AndroidManifest.xml` to enable deep linking to your domain:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https"
        android:host="your-site.frappe.cloud" />
</intent-filter>
```

## Features

### Included

- **WebView** - Displays your Frappe site
- **FCM Integration** - Receives push notifications
- **Auto Token Registration** - Registers device when user logs in
- **Deep Linking** - Open documents from notifications
- **Offline Detection** - Shows offline screen when no connection
- **Pull to Refresh** - Swipe down to reload
- **File Upload** - Select files from device
- **External Links** - Opens external URLs in browser

### Not Included (Customize as needed)

- Biometric authentication
- Offline data caching
- Custom native screens
- Background sync

## How It Works

1. **App Launch**: WebView loads your Frappe site
2. **User Login**: JavaScript detects `frappe.session.user`
3. **Token Registration**: App sends FCM token to server via API
4. **Notification Received**: FCMService handles incoming messages
5. **User Taps Notification**: Opens app with document URL

## Notification Data Format

Notifications from Frappe FCM include these data fields:

```json
{
  "title": "Notification Title",
  "body": "Notification body text",
  "url": "/app/doctype/docname",
  "doctype": "Sales Order",
  "name": "SO-00001",
  "notification_type": "new_order"
}
```

## Signing for Release

### Option 1: GitHub Actions (CI/CD)

Set these secrets in your repository:
- `KEYSTORE_BASE64` - Base64 encoded keystore file
- `KEYSTORE_PASSWORD` - Keystore password
- `KEY_ALIAS` - Key alias
- `KEY_PASSWORD` - Key password

### Option 2: Local Signing

1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias mykey -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Update `app/build.gradle`:
   ```groovy
   signingConfigs {
       release {
           storeFile file("../release.keystore")
           storePassword "your-store-password"
           keyAlias "mykey"
           keyPassword "your-key-password"
       }
   }
   ```

## Troubleshooting

### Build fails: "google-services.json not found"
- Download from Firebase Console and place in `app/` folder

### Notifications not received
- Check FCM is configured in Frappe FCM Settings
- Verify device is registered (check FCM Device list)
- Check notification permissions on device

### WebView shows blank/error
- Check BASE_URL is correct
- Verify site is accessible from device network
- Check network_security_config.xml for HTTPS issues

### Token not registering
- Ensure user is logged in (not Guest)
- Check browser cookies are being sent
- Look at Android logs: `adb logcat | grep FrappeFCM`

## License

MIT License
