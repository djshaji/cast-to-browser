# Project Plan

Build an Android app that hosts a local Ktor web server to cast media to a browser. Use Jetpack Compose for the UI, MVVM architecture, and WebSockets for real-time control.

## Project Brief

# Project Brief: Cast to Browser

**Cast to Browser** is
 an Android application that transforms your smartphone into a media server and remote control. By hosting a local Ktor server, the app allows
 users to cast local videos and images directly to any PC web browser on the same Wi-Fi network, providing a seamless "
second screen" experience without the need for external hardware like Chromecast.

### Features
*   **Embedded Ktor Media Server:**
 Hosts a local web server that serves a custom HTML5 media player and streams local device media using Partial Content support for efficient
 seeking.
*   **Real-time WebSocket Remote:** Establishes a bidirectional WebSocket connection to send playback commands (Play,
 Pause, Seek, Load) and receive live telemetry (current time, duration) from the browser.
*   **
Media Discovery & Casting:** Integrated MediaStore browsing to select and instantly cast local photos and videos to the connected browser.
*   **
Dynamic Remote Dashboard:** A modern Material 3 playback interface featuring a real-time synchronized seek bar and responsive playback controls.

###
 High-Level Technical Stack
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Material Design
 3)
*   **Navigation:** Jetpack Navigation 3 (State-driven)
*   **Adaptive Strategy:**
 Compose Material Adaptive (for optimized layouts across different screen sizes)
*   **Asynchronous Logic:** Kotlin Coroutines & Flow
 for state management and server operations
*   **Server Engine:** Ktor Server (Netty, WebSockets, Content
 Negotiation)
*   **Media Handling:** Android MediaStore API for local file indexing

## Implementation Steps

### Task_1_Core_Server_Setup: Initialize the project architecture and setup the Ktor server. Implement routes for serving static HTML/JS assets and streaming media files from MediaStore with partial content support.
- **Status:** COMPLETED
- **Updates:** Task 1 completed:
- **Acceptance Criteria:**
  - Ktor server starts and stops with app lifecycle
  - Server can stream a sample local file via HTTP
  - WebSocket endpoint is reachable

### Task_2_Media_Browsing_Implementation: Implement MediaStore integration to fetch local images and videos. Build a Jetpack Compose UI to display these media items in a list or grid with thumbnails using Material 3.
- **Status:** COMPLETED
- **Updates:** Task 2 completed:
- **Acceptance Criteria:**
  - Local media files are correctly listed in the app
  - Thumbnails are displayed using Coil
  - Selecting a media item updates the server's current stream

### Task_3_Web_Player_WebSocket_Bridge: Develop the HTML5 media player (HTML/JS/CSS) to be hosted by Ktor. Implement the WebSocket communication layer to sync playback state (Play, Pause, Seek, Load) between the Android app and the browser.
- **Status:** COMPLETED
- **Updates:** Task 3 completed:
- **Acceptance Criteria:**
  - Web player is accessible from a PC browser on the same network
  - Browser receives play/pause commands via WebSockets
  - Browser sends playback progress back to the Android app

### Task_4_Remote_Control_UI: Build the Material 3 playback dashboard UI. Include playback controls, a real-time seek bar synchronized with the browser via WebSockets, and support for adaptive layouts using Compose Material Adaptive.
- **Status:** COMPLETED
- **Updates:** Task 4 completed:
- **Acceptance Criteria:**
  - Material 3 UI with play/pause/seek controls
  - Seek bar updates in real-time as the browser plays
  - Layout adapts to different screen sizes

### Task_5_Final_Polish_Verification: Finalize the application with Material 3 themes (Light/Dark) using Material Color Utilities, an adaptive app icon, and full edge-to-edge display. Perform a final verification of all features.
- **Status:** COMPLETED
- **Updates:** Task 5 completed:
- Fixed Foreground Service crash by adding CHANGE_WIFI_STATE permission.
- Verified app stability on Android 14+ (SDK 36).
- Edge-to-edge display and Material 3 themes finalized.
- Adaptive app icon implemented.
- Critic agent verified all core features: server startup, media browsing, and remote control sync.
- Final build is stable and meets all acceptance criteria.
- **Acceptance Criteria:**
  - App icon is adaptive and matches core function
  - Edge-to-edge display is implemented correctly
  - Final build passes and app does not crash during end-to-end usage
  - Critic agent verifies stability and UI alignment
- **Duration:** N/A

