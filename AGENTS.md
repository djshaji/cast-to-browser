# AGENTS.md: Cast to Browser

**Mission:** Transform an Android device into a local media server that casts videos/images to any browser on the same Wi-Fi network via an embedded Ktor server and bidirectional WebSocket synchronization.

## Quick Start: Essential Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run all tests
./gradlew :app:testDebugUnitTest

# Run instrumentation tests (connected device/emulator required)
./gradlew :app:connectedDebugAndroidTest

# Run lint (currently fails on CastServerService POST_NOTIFICATIONS; expected)
./gradlew :app:lintDebug
```

## Architecture: Three Operational Boundaries

### 1. **Android UI Layer** (`ui/`)
- **Jetpack Compose** (Material 3) with **no XML layouts whatsoever**
- Entry point: `MainActivity` → `MainAdaptiveEntry` (handles `ListDetailPaneScaffold` for adaptive tablet/phone layouts)
- Screens: `MediaListScreen` (gallery grid) + `RemoteControlDashboard` (playback control)
- Responsive: side-by-side on tablets, stacked on phones, controlled by `ListDetailPaneScaffoldNavigator`

### 2. **Embedded Ktor Server** (`server/`)
- Runs in foreground service (`CastServerService`)
- **Three HTTP routes:**
  - `GET /` → inline HTML/JS receiver page with `<video>` or `<img>` elements
  - `GET /media/{type}/{id}` → streams raw media files with HTTP 206 Partial Content support for seeking
  - `WS /control` → bidirectional WebSocket for commands and telemetry
- **Dynamic port selection:** tries port 8080, then increments up to 10 attempts if port is busy
- **Late-joiner problem solved:** before adding new WebSocket clients to broadcast pool, server sends cached current media state

### 3. **Browser Receiver** (client-side)
- Connects via `ws://[ip]:[port]/control` on page load
- Listens for JSON commands (`LOAD`, `PLAY`, `PAUSE`, `SEEK`)
- Sends telemetry every ~250ms: `{type: "TELEMETRY", data: {...currentTime, duration, isPlaying}}`
- Auto-reconnects every 3 seconds if disconnected

## State Management: ServerState Singleton

**Central integration point** (`server/ServerState.kt`) for cross-layer communication:

```kotlin
object ServerState {
    val currentMedia: StateFlow<MediaItem?>        // Current selected file
    val mediaLoadEvent: SharedFlow<MediaItem>      // Event when new media is selected
    val serverIp: StateFlow<String?>               // Device's local IP
    val serverPort: StateFlow<Int>                 // Current server port
    val serverError: StateFlow<String?>            // Error messages
    val telemetry: StateFlow<TelemetryData?>       // Browser playback progress
    val commandFlow: SharedFlow<ControlMessage>    // Commands to send to browser
}
```

**Key principle:** UI reads flows, ViewModel updates flows, Server consumes flows. No direct service/server calls from UI.

## Data Flow: User Action → Browser Playback

```
User taps "Play" in RemoteControlDashboard
    ↓
viewModel.togglePlayPause()
    ↓
ServerState.sendCommand(ControlMessage("PLAY"))
    ↓
KtorServer broadcasts to all WebSocket clients
    ↓
Browser's JS executes player.play()
    ↓
Browser telemetry loop detects isPlaying: true
    ↓
Browser sends TELEMETRY message back via WebSocket
    ↓
ServerState.updateTelemetry(...) → StateFlow update
    ↓
RemoteControlDashboard recomposes with new UI state
```

## WebSocket Protocol: Command & Telemetry

**App → Browser (Commands):**
```json
{"type": "LOAD", "data": null}         // Page reload to load new media
{"type": "PLAY", "data": null}
{"type": "PAUSE", "data": null}
{"type": "SEEK", "data": "45.5"}       // Seconds (float)
```

**Browser → App (Telemetry):**
```json
{"type": "TELEMETRY", "data": "{\"currentTime\": 45.5, \"duration\": 300, \"isPlaying\": true}"}
```

Data is nested JSON string (not object) to match `ControlMessage` serialization contract.

## Media Routing: ID-Based, Not Path-Based

| Source | Collection | URI Pattern |
|--------|-----------|------------|
| Videos | `MediaStore.Video.Media` | `/media/video/{id}` |
| Images | `MediaStore.Images.Media` | `/media/image/{id}` |

**Server resolution:** `KtorServer` probes both tables when a browser requests `/media/{type}/{id}`. Original `content://` `Uri` stored in `MediaItem` for UI display (thumbnails via Coil).

## Repository & ViewModels

### `MediaRepository`
- **Single responsibility:** queries MediaStore, returns merged list of videos + images sorted by `DATE_ADDED DESC`
- Uses standard Android `ContentResolver.query()` on coroutine `Dispatchers.IO`
- Both collections fetched in one pass, not lazy-loaded

### `MediaViewModel` 
- **Thin by design:** no business logic; only loads repository data and forwards UI intents to `ServerState`
- Exposes `StateFlow`s: `mediaItems`, `isLoading`, `selectedFilter`, `selectedSort`, plus `ServerState` flows
- Key methods:
  - `selectMedia(MediaItem)` → `ServerState.setCurrentMedia()` 
  - `togglePlayPause()` → `ServerState.sendCommand(ControlMessage("PLAY"/"PAUSE"))`
  - `seekTo(Float)` → `ServerState.sendCommand(ControlMessage("SEEK", position))`
- **Supports filtering/sorting by:** all vs. images vs. videos, and by modified time / size / alphabetical

## Permission Handling: API-Level Branching

```kotlin
// MainActivity.kt
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // Android 13+: Runtime permissions for READ_MEDIA_VIDEO and READ_MEDIA_IMAGES
    requestPermissions(Manifest.permission.READ_MEDIA_VIDEO, READ_MEDIA_IMAGES)
} else {
    // Android 12 and below: READ_EXTERNAL_STORAGE
    requestPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
}
```

**Permission state gates:** `MainAdaptiveEntry` only rendered after `allPermissionsGranted` is true. Share intent handling also checks URI access.

## Async Patterns: Coroutines & Flow Only

- **No callbacks, Rx, or event buses.** Use `viewModelScope.launch` for all background work tied to UI lifecycle
- **Data streams modeled as `Flow`:** `MediaRepository.fetchMedia()` uses `withContext(Dispatchers.IO)`
- **State updates use `MutableStateFlow` / `MutableSharedFlow`.** Collected in Compose via `collectAsState()`
- **Error handling:** catch exceptions in `try/catch` within `launch`, update error `StateFlow`

## Share Intent Support: Injecting Media Into Cast Session

```kotlin
// MainActivity.kt: handleIntent()
if (intent.action == Intent.ACTION_SEND) {
    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    // Convert URI + MIME type to MediaItem and call viewModel.selectMedia()
}
```

Allows "Share to Cast to Browser" from Google Photos etc. Shared media streams immediately to connected browser.

## Layout Adaptivity: ListDetailPaneScaffold

```kotlin
// MainAdaptiveEntry.kt
ListDetailPaneScaffold(
    directive = navigator.scaffoldDirective,  // Auto-adjusts for screen size
    listPane = { MediaListScreen(...) },      // Gallery always visible
    detailPane = { RemoteControlDashboard(...) }, // Visible iff media selected
)
```

**Behavior:**
- **Tablets (large screens):** both panes side-by-side
- **Phones (compact):** tapping a media item transitions from list → detail pane
- `ListDetailPaneScaffoldNavigator` manages state machine and back navigation

## Image Loading: Coil with Video Frame Support

```kotlin
// MainActivity.kt
val imageLoader = ImageLoader.Builder(context)
    .components { add(VideoFrameDecoder.Factory()) }
    .build()
CompositionLocalProvider(LocalImageLoader provides imageLoader) { ... }
```

Enables `Coil` to extract first frame from video files for gallery thumbnails. Standard `Image(model = mediaItem.uri)` then auto-decodes video thumbnails.

## Key Files & Examples

| Path | Purpose | Key Types |
|------|---------|-----------|
| `server/ServerState.kt` | Central singleton hub | `StateFlow`, `SharedFlow`, `TelemetryData`, `ControlMessage` |
| `server/CastServerService.kt` | Foreground service + port negotiation | `Service`, `start()`, `tryStartServer()` |
| `server/KtorServer.kt` | Ktor server config + routes | `embeddedServer()`, WebSocket handler, HTML page |
| `data/MediaRepository.kt` | MediaStore queries | `ContentResolver.query()`, sorting |
| `ui/viewmodel/MediaViewModel.kt` | UI orchestration | `AndroidViewModel`, `StateFlow`, delegation to `ServerState` |
| `ui/MainAdaptiveEntry.kt` | Adaptive scaffold entry | `ListDetailPaneScaffold(Navigator)` |
| `MainActivity.kt` | Permission gating + share handling | `rememberMultiplePermissionsState`, `handleIntent()` |

## Cross-Component Communication Rules

1. **UI → ViewModel:** call `viewModel.selectMedia()`, `togglePlayPause()`, `seekTo()`, etc.
2. **ViewModel → ServerState:** call `ServerState.setCurrentMedia()`, `sendCommand()`
3. **ServerState → Server:** `KtorServer` collects `commandFlow`, `mediaLoadEvent`, `currentMedia` StateFlows
4. **Server → Browser:** WebSocket messages (JSON serialized `ControlMessage`)
5. **Browser → Server:** WebSocket telemetry (JSON string in `ControlMessage.data`)
6. **Server → State:** `ServerState.updateTelemetry()`, `setServerIp()`, `setServerPort()`
7. **UI ← ServerState:** `collectAsState()` on `StateFlow`s

**Never:** call `CastServerService` or `KtorServer` directly from UI. Everything flows through `ServerState`.

## Foreground Service Notification

`CastServerService` broadcasts connection URL in foreground notification:
- Channel: `"cast_server_channel"`; importance: `IMPORTANCE_LOW`
- Content: `"Connect to http://192.168.1.15:8080"`
- Updated dynamically if port changes

Target Android 13+: requires `FOREGROUND_SERVICE_CONNECTED_DEVICE` declaration and `ServiceInfo` type.

## Known Lint Issues

`lintDebug` fails because `CastServerService` posts notifications without checking `POST_NOTIFICATIONS` permission (Android 13+). This is expected; **do not assume lint is clean.** To fix, add `requestPostNotificationPermission()` call in service startup if needed for production.

## Testing Strategy

- **Unit tests:** place in `app/src/test/` (local JVM, fast)
- **Instrumentation tests:** place in `app/src/androidTest/` (device/emulator, slow)
- Example: `ExampleUnitTest`, `ExampleInstrumentedTest` (structure provided in `src/` tree)

---

**Last Updated:** 2026-05-25 | **Agent Version:** 1.0

