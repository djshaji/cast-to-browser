# Cast to Browser

**Cast to Browser** is a powerful Android application that transforms your smartphone into a versatile media server and remote control. By hosting a local Ktor server, the app allows you to cast local videos and images directly to any web browser on the same Wi-Fi network, providing a seamless "second screen" experience without the need for additional hardware like Chromecast.

## 🚀 Key Features

- **Embedded Ktor Media Server**: Hosts a local web server that serves a high-performance HTML5 player. It supports **Partial Content** streaming, enabling efficient seeking and low-latency playback of local media.
- **Real-time WebSocket Remote**: Establishes a bidirectional WebSocket bridge between the app and the browser. This allows for instant synchronization of playback state (Play, Pause, Seek) and real-time telemetry (progress, duration).
- **Media Discovery & Gallery**: Integrated MediaStore browsing allows you to easily find and select local photos and videos. The gallery features high-quality thumbnails powered by **Coil**.
- **Material 3 Adaptive UI**: A modern, expressive interface built with Jetpack Compose. The UI is fully adaptive, providing an optimized experience on both smartphones and tablets using the `ListDetailPaneScaffold`.
- **Share Support**: Cast media instantly from other applications (like Google Photos or your device gallery) by sharing them directly to the Cast to Browser app.
- **Dynamic Port Selection**: Robust networking logic that automatically finds an available port (starting from 8080) if the default port is already in use by another application.
- **Edge-to-Edge Experience**: Fully supports Android's edge-to-edge display with immersive system bar integration.

## 🛠 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material Design 3)
- **Networking/Server**: [Ktor Server](https://ktor.io/) (Netty, WebSockets, Content Negotiation)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/) (with video frame decoding support)
- **Asynchronous Logic**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
- **Navigation**: [Jetpack Navigation 3](https://developer.android.com/guide/navigation) & [Compose Material Adaptive](https://developer.android.com/jetpack/compose/adaptive)

## 🏁 Getting Started

### 1. Prerequisites
- An Android device running Android 10 (API 29) or higher.
- A PC or tablet with a modern web browser (Chrome, Firefox, Safari, Edge).
- Both devices must be connected to the **same Wi-Fi network**.

### 2. How to Use
1. **Launch the App**: Open Cast to Browser on your Android device.
2. **Grant Permissions**: Ensure you grant the required media access permissions when prompted.
3. **Connect to Browser**:
   - The app will display a URL in the top bar (e.g., `http://192.168.1.5:8080`).
   - Enter this exact URL into your PC's web browser.
4. **Cast Media**:
   - Tap any video or image in the app's gallery to start casting.
   - Use the **Remote Control Dashboard** in the app to play, pause, or seek through your videos.
   - You can also use the native controls directly within the browser player.

## 📱 User Interface
The app strictly follows Material 3 guidelines, featuring:
- **Expressive Layouts**: Large corner radii and spacious padding for a modern feel.
- **Dynamic Color**: Support for Material You (dynamic coloring based on your wallpaper) on Android 12+.
- **Adaptive Scaffolding**: Side-by-side list and remote dashboard on tablets; seamless transitions on phones.

## 📄 License
MIT License

---
Developed with ❤️ by djshaji
