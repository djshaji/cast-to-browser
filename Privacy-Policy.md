# Privacy Policy — Cast to Browser

**Effective Date:** May 29, 2026
**Developer:** djshaji
**App Name:** Cast to Browser
**Package:** org.acoustixaudio.casttobrowser

---

## 1. Overview

Cast to Browser is a local media server app. It streams videos and images from your Android device to a web browser on the same Wi-Fi network. This privacy policy explains what data the app accesses, how it is used, and what it does **not** do.

---

## 2. Data We Access

### 2.1 Media Files (Photos & Videos)
- The app requests access to your device's local media files (videos and images stored on your device).
- This access is required solely to display a gallery and stream selected media to a browser on the same local network.
- **No media files are ever uploaded to any server, cloud service, or third party.**

### 2.2 Local Network (Wi-Fi)
- The app starts a local HTTP/WebSocket server on your device (port 8080 or nearby).
- This server is accessible only to devices on the **same Wi-Fi network**.
- The app reads your device's local IP address to display the connection URL to you.
- **No traffic is routed over the internet.**

### 2.3 Usage Data (Local Only)
- The app stores a small count of how many media items you have cast. This counter is stored locally on your device using Android DataStore.
- This is used only to determine whether to show the in-app upgrade prompt.
- **This data never leaves your device.**

---

## 3. Data We Do NOT Collect

- We do **not** collect any personal information (name, email, phone number, etc.).
- We do **not** collect location data.
- We do **not** collect device identifiers (IMEI, advertising ID, etc.).
- We do **not** send analytics, crash reports, or telemetry to any external server.
- We do **not** use third-party analytics SDKs (e.g., Firebase Analytics, Crashlytics).
- We do **not** track user behaviour across apps or websites.
- We do **not** share any data with third parties.

---

## 4. In-App Purchases

- Cast to Browser offers an optional one-time **Pro** upgrade (product id: `pro`) via Google Play Billing.
- Purchase transactions are handled entirely by **Google Play**. We do not process, store, or have access to your payment information.
- Google Play's own privacy policy applies to the purchase transaction:
  https://policies.google.com/privacy

---

## 5. Permissions Used

| Permission | Reason |
|---|---|
| `READ_MEDIA_VIDEO` (Android 13+) | Access local video files for streaming |
| `READ_MEDIA_IMAGES` (Android 13+) | Access local image files for streaming |
| `READ_EXTERNAL_STORAGE` (Android 12 and below) | Access local media files for streaming |
| `INTERNET` | Run the local HTTP/WebSocket server |
| `ACCESS_NETWORK_STATE` | Detect Wi-Fi and read local IP address |
| `FOREGROUND_SERVICE` | Keep the local server running while the app is in use |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required by Android 14+ for foreground services that manage connected devices |
| `com.android.vending.BILLING` | Enable Google Play in-app purchases |

---

## 6. Privacy

Cast to Browser does not collect any information. The app contains no advertising or social features.

---

## 7. Data Retention

- No user data is transmitted to or stored on any remote server at any time.
- Local preferences (such as the cast count and Pro status) remain on your device and are deleted when you uninstall the app.

---

## 8. Security

- All media streaming occurs over your local Wi-Fi network only.
- The embedded server does not implement authentication by design (local network trust model). Users should only use the app on trusted private Wi-Fi networks.
- No data is encrypted in transit because no data leaves the local network.

---

## 9. Changes to This Policy

We may update this privacy policy from time to time. Any changes will be reflected by updating the **Effective Date** at the top of this document. Continued use of the app after changes constitutes acceptance of the updated policy.

---

## 10. Contact

If you have any questions or concerns about this privacy policy, please contact:

**Developer:** djshaji
**GitHub:** https://github.com/djshaji/CasttoBrowser

---


