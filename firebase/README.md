# Firebase Configuration

This directory contains the shared Firebase service account credentials for the Frappe FCM universal mobile app.

## For App Developer (Ahmed Emam)

To set up the shared Firebase project:

1. Create a Firebase project at https://console.firebase.google.com
2. Enable Firebase Cloud Messaging
3. Add an Android app with package name: `io.frappe.fcm`
4. Download `google-services.json` and place it in `mobile_app/app/`
5. Generate Service Account JSON:
   - Go to Project Settings → Service Accounts
   - Click "Generate New Private Key"
   - Save as `service-account.json` in this directory
6. Commit and push

## For Users

Users don't need to do anything with this directory. The credentials are automatically fetched when you click "Fetch Shared Credentials" in FCM Settings.

## Security Note

The service account JSON in this repository is specifically for sending push notifications to the universal mobile app. It only has permission to send FCM messages and does not provide access to any user data or other Firebase services.
