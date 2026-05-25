# Copilot instructions for Cast to Browser

## Build, test, and lint

- Build the debug app with `./gradlew :app:assembleDebug`
- Run all local unit tests with `./gradlew :app:testDebugUnitTest`
- Run a single local unit test with `./gradlew :app:testDebugUnitTest --tests "org.acoustixaudio.casttobrowser.ExampleUnitTest"`
- Run instrumentation tests on a connected device/emulator with `./gradlew :app:connectedDebugAndroidTest`
- Run a single instrumentation test with `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.acoustixaudio.casttobrowser.ExampleInstrumentedTest`
- Run lint with `./gradlew :app:lintDebug`

`lintDebug` currently fails on `CastServerService` because the app posts notifications without handling Android 13+ `POST_NOTIFICATIONS` permission. Do not assume lint is currently clean.

## High-level architecture

- This is a single-module Android app (`:app`) built with Jetpack Compose and an embedded Ktor server. The phone is both the media catalog and the HTTP/WebSocket server; the receiver is an HTML page served to another device's browser.
- `MainActivity` is the app entry point. It starts `CastServerService`, requests media permissions at startup, handles Android share intents, and renders the adaptive Compose UI.
- `CastServerService` owns server startup and connection metadata. It resolves the local IP, tries ports starting at `8080`, promotes itself to a foreground service, and publishes the active IP/port/error state through `ServerState`.
- `KtorServer` is the bridge to the browser receiver. It serves `/` with inline HTML/JS, streams selected media from MediaStore through `/media/{id}`, and keeps the browser and Android UI synchronized over the `/control` WebSocket.
- `ServerState` is the shared state hub between UI, service, and server code. Media selection, transport commands, server address, and browser telemetry all flow through `StateFlow`/`SharedFlow` values on this singleton.
- `MediaViewModel` is thin by design: it loads media from `MediaRepository`, exposes `ServerState` flows to the UI, and forwards play/pause/seek commands back into `ServerState`.
- `MediaRepository` queries both `MediaStore.Video` and `MediaStore.Images` and returns a merged `List<MediaItem>` sorted by each collection's `DATE_ADDED DESC`.
- The main UI flow is `MainAdaptiveEntry` -> `MediaListScreen` + `RemoteControlDashboard` using `ListDetailPaneScaffold`, so tablet and phone layouts share the same list/detail state model.

## Key conventions

- Keep UI code Compose-only. Existing guidance in `README.md` and `docs/Agent.md` is reflected in the codebase: no XML layouts, Material 3 components, and adaptive list/detail navigation.
- Prefer coroutines and flows over callbacks or Rx-style patterns. Cross-layer state is already modeled with `MutableStateFlow` and `MutableSharedFlow`; extend that model instead of adding another event system.
- Preserve the current control protocol shape when touching browser/server sync. Commands use uppercase `type` values such as `LOAD`, `PLAY`, `PAUSE`, `SEEK`, and browser telemetry comes back as a `TELEMETRY` message whose `data` field contains nested JSON.
- Treat `ServerState` as the integration point for playback/session state. The UI should not talk to `CastServerService` or `KtorServer` directly.
- Media routing is ID-based, not path-based. `KtorServer` resolves `/media/{id}` by probing both `MediaStore.Video` and `MediaStore.Images`, while `MediaItem` carries the original `content://` `Uri` for UI use.
- Server lifecycle changes need to account for both the Android side and the browser side: updating port/IP state, the foreground notification text, and the receiver page WebSocket endpoint together.
- Share support is part of the main flow. `MainActivity.handleIntent` can inject shared media directly into the current cast session, so changes to media selection should consider both gallery picks and incoming `ACTION_SEND` intents.
