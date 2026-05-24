Architecture Design Document: Local Media Caster
1. Executive Summary
   The Local Media Caster is an Android application that transforms a user's mobile device into both a local media server and a smart remote control. It allows users to select on-device photos and videos and instantly "cast" them to any web browser on the same local Wi-Fi network. The app relies on an embedded Ktor web server and bidirectional WebSocket communication to synchronize playback state in real time.

2. Technology Stack
   Component	Technology	Purpose
   UI Framework	Jetpack Compose	Declarative, reactive UI for the gallery and remote control.
   Architecture	MVVM	Clean separation of UI logic, state management, and server execution.
   Concurrency	Coroutines & Flow	Background thread management and real-time state emission.
   Server Engine	Ktor Server (Netty)	Embedded HTTP server, Partial Content streaming, and WebSockets.
   Media Access	Android MediaStore API	Querying and resolving URIs for local device storage.
   Web Client	HTML5 / JavaScript	Lightweight, zero-install receiver running on the PC browser.
3. High-Level System Architecture
   The system is divided into three distinct operational boundaries, two of which live entirely inside the Android device.

A. The Android Client (Frontend)
Role: The user interface (Gallery & Remote Control).

Behavior: Queries the MediaStore to display available files. Sends user intents (e.g., "Play Video", "Seek to 01:20") to the ViewModel. Observes real-time telemetry from the server to update the UI (e.g., moving the seek bar).

B. The Embedded Server (Backend)
Role: The bridge between the phone and the PC.

Behavior: Runs locally on the Android device (e.g., http://192.168.1.15:8080). Serves the HTML receiver page. Streams raw media files using HTTP 206 Partial Content. Maintains a pool of active WebSocket connections.

C. The Web Receiver (PC Client)
Role: The playback screen.

Behavior: Connects to the Android device via a standard web browser. Renders an HTML5 <video> or <img> tag. Listens for WebSocket commands to control the player and fires JavaScript ontimeupdate events back to the server to report playback progress.

4. Data Flow & State Management
   The application relies on a Unidirectional Data Flow (UDF) bridged across a network layer.

User Action: User taps "Seek" on the Compose UI.

Intent: The UI calls viewModel.seekTo(45f).

Command: The ViewModel formats a JSON command and passes it to the KtorServerManager.

Broadcast: The server broadcasts {"action": "seek", "value": 45.0} to all connected WebSocket clients.

Execution: The PC browser's JavaScript catches the JSON and updates player.currentTime = 45.0.

Telemetry: The PC browser's video element fires an event, sending {"type": "sync", "currentTime": 45.1} back through the WebSocket.

State Update: The KtorServerManager pushes this JSON to a MutableSharedFlow.

UI Recomposition: The ViewModel updates its StateFlow<PlaybackState>, causing Jetpack Compose to redraw the seek bar at the new position.

5. WebSocket Communication Protocol
   All real-time communication between the Android app and the Web Client is formatted as lightweight JSON payloads.

App to PC (Commands)
Sent by the Android app to control the web browser.

Load Media: {"type": "video", "url": "/media/video_123.mp4"}

Play/Pause: {"action": "play"} or {"action": "pause"}

Seek: {"action": "seek", "value": 125.5} (value in seconds)

Volume: {"action": "volume", "value": 0.8} (value between 0.0 and 1.0)

PC to App (Telemetry)
Sent by the web browser roughly every 250ms to keep the Android UI in sync.

Sync State: ```json
{
"type": "sync",
"currentTime": 125.5,
"duration": 300.0,
"isPlaying": true
}

Media Ended: {"type": "ended"} (triggers the Android app to auto-play the next file)

6. Security & Permissions
   Because this app exposes a web server, strict permission management and scope limitation are required.

Required Permissions
android.permission.INTERNET (To bind to localhost/network ports).

android.permission.ACCESS_WIFI_STATE (To retrieve the local IP address for the UI).

android.permission.READ_MEDIA_VIDEO / READ_MEDIA_IMAGES (Android 13+).

android.permission.READ_EXTERNAL_STORAGE (Android 12 and below).

Security Note: The Ktor server must strictly validate incoming HTTP requests to the /media/{id} route. It must only serve files that exist within the specific MediaStore URIs authorized by the user. Directory traversal attacks (e.g., requesting /media/../../../etc/passwd) must be blocked by the routing logic.

7. Edge Cases & Architectural Considerations
   The "Late Joiner" Problem
   If a second PC connects to the server while a video is already playing, it will miss the initial Load Media WebSocket broadcast.

Resolution: The KtorServerManager must maintain a currentMediaState variable in memory. The moment a new WebSocket session connects, the server must immediately emit the cached state to that specific session before joining it to the active broadcast pool.

App Backgrounding & Doze Mode
If the user switches away from the Android app to check a text message, the Android OS may suspend the Ktor Coroutine, instantly freezing the video playback on the PC.

Resolution: The Ktor server must not be hosted inside a Foreground Service. Do not use a background service either. Instead, the server should be designed to start when the app is opened and stop when the app is closed. This way, if the app is backgrounded, the server will stop, and the user will understand that they need to return to the app to resume casting.

Dynamic IP Reassignment
Mobile devices frequently disconnect and reconnect to Wi-Fi, potentially changing their local IP address.

Resolution: The app must utilize the Android ConnectivityManager to monitor network changes. If the IP changes, the server must be restarted on the new interface, and the UI must update to show the new connection URL to the user.