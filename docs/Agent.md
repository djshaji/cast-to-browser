# 🤖 Agent Instructions: Local Media Caster

## 1. Project Mission
You are building an Android application that functions as a "Local Media Caster."
The app hosts an embedded web server. A user connects to the app's IP address from a PC browser on the same Wi-Fi network. The Android app acts as a master remote control, querying local photos and videos and "casting" them to the PC browser via bidirectional WebSocket commands.

## 2. Tech Stack & Environment
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Strictly NO XML layouts)
- **Architecture:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF)
- **Concurrency:** Kotlin Coroutines and StateFlow/SharedFlow (Strictly NO RxJava or Callbacks)
- **Server Engine:** Ktor Server (Netty) embedded within the Android app
- **Key Ktor Plugins:** WebSockets, PartialContent (for video streaming/seeking)
- **Target SDK:** Android 14 (API 34), with backward compatibility to API 26.

## 3. Strict Coding Guidelines
- **Modern Android:** Default to the latest recommended Android practices. Use `viewModelScope` for coroutines tied to the UI.
- **Background Execution:** The Ktor Server MUST run in a background thread (`Dispatchers.IO`).
- **State Management:** The UI must be reactive. Represent the playback state as a data class exposed via a `StateFlow` from the ViewModel.
- **No Boilerplate:** Do not generate unnecessary abstraction layers (e.g., UseCases) unless the logic becomes overly complex. A clean Repository pattern communicating with the ViewModel is sufficient.
- **Permissions:** Account for modern Android permission models (e.g., `READ_MEDIA_VIDEO` for Android 13+ vs `READ_EXTERNAL_STORAGE` for older versions).

---

## 4. Implementation Plan

Execute the following phases in order. Do not skip ahead.

### Phase 1: Foundation & Permissions
1. Add required Ktor Server, Ktor WebSockets, and Jetpack Compose dependencies to `build.gradle.kts`.
2. Add necessary Network and MediaStore permissions to `AndroidManifest.xml`.
3. Create a utility to retrieve the Android device's local Wi-Fi IP address.

### Phase 2: Media Repository
1. Create a `MediaRepository` that queries the Android `MediaStore`.
2. Fetch local video and image metadata (ID, Name, URI path, duration).
3. Expose this data as a `Flow<List<MediaItem>>` for the UI to consume.

### Phase 3: The Embedded Ktor Server
1. Create a `KtorServerManager` singleton or repository.
2. Initialize an embedded Netty server on port `8080`.
3. **Route `/`**: Serve an inline HTML/JS string. The JS must connect to `ws://[ip]:8080/ws`, listen for JSON playback commands, and emit HTML5 video `ontimeupdate` telemetry back to the server.
4. **Route `/media/{id}`**: Resolve the ID to a MediaStore URI and serve the raw file stream. You MUST install and use the Ktor `PartialContent` plugin here so the PC browser can seek through video files.
5. **Route `/ws`**: Maintain a synchronized set of active WebSocket sessions.

### Phase 4: Bi-directional Communication & ViewModel
1. Create a `MediaViewModel`.
2. Establish a `MutableSharedFlow` to collect incoming WebSocket telemetry (e.g., current playback time, duration).
3. Expose a `StateFlow<PlaybackState>` to the UI.
4. Create functions in the ViewModel to broadcast JSON commands (play, pause, seek, load media) to the `KtorServerManager`.

### Phase 5: The Jetpack Compose UI
1. **Gallery Screen:** Display the items fetched from `MediaRepository` in a grid. Tapping an item triggers the ViewModel to cast it to the PC.
2. **Remote Control Screen:** Display the connection IP URL. Build a UI with Play/Pause buttons and a `Slider` (seek bar).
3. The `Slider` value must be bound to the `StateFlow<PlaybackState>` so it moves automatically as the PC plays the video. Dragging the slider must send a `seek` command back to the PC.
4. **No Foreground Service:** Do not use a foreground service for the server. The server should start when the app is opened and stop when the app is closed.