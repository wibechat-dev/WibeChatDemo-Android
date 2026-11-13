# WibeChat Android demo

A native Android (Kotlin) app showing WibeChat audio and video calls:
sign in, create rooms, call people one-to-one, ring their phone, and keep a call history.

The app comes in two versions with the same features and the same backend:
`:app` (Jetpack Compose) and `:app-xml` (XML layouts, Fragments, RecyclerView, Material Components).
Pick the UI toolkit your team uses; both share the `:core` and `:calls` modules.

It talks to the same backend as the Flutter demo, so Android and Flutter users can call each other.

## What's inside

| Module   | What it holds | Depends on |
|----------|---------------|------------|
| `:calls` | `CallSession` (one call: connect, mic, camera, flip, screen share, participant tiles as a `StateFlow`) and `VideoTrackView` (Compose video renderer). **No Firebase.** Copy this module into your own app. | WibeChat SDK only |
| `:core`  | `AppConfig` (build-time settings), Firebase Auth + Firestore repositories, clients for the two Cloud Functions. No UI. | Firebase, OkHttp |
| `:app`   | Compose screens, push handling, the incoming-call screen, Picture-in-Picture. | `:core`, `:calls` |
| `:app-xml` | The same app with XML layouts + ViewBinding: Fragments, RecyclerView adapters, Material Components, and `TextureViewRenderer` tiles for video. | `:core`, `:calls` |

```
app/src/main/java/com/wibechat/demo/
  feature/auth      Sign in, create account, Google sign-in, password reset
  feature/home      Home: Rooms / People / Recents tabs, account drawer
  feature/rooms     Room list with live presence, create/delete room
  feature/users     People list, voice and video call buttons
  feature/calllog   Call history
  feature/call      Call screen + CallViewModel (grid, 1:1 layout, controls, add to call)
  notifications     FCM service, ringing notification, full-screen incoming call, decline/timeout
  call              CallLauncher (single way to open a call), ActiveCall (in-app call signals)

app-xml/src/main/java/com/wibechat/xmldemo/
  MainActivity      Picks the screen (sign-in → workspace setup → home), opens calls, Picture-in-Picture
  feature/auth      LoginFragment, SignupFragment, Google sign-in
  feature/workspace WorkspaceSetupFragment
  feature/home      HomeFragment (tabs, search, account drawer) + RecyclerView adapters
  feature/call      CallFragment, VideoGridView, ParticipantTileView, quality and add-to-call sheets
  notifications     Same push handling as :app, with an XML full-screen incoming-call activity
app-xml/src/main/res/layout/   All screens as XML layouts
```

## How a call works

1. **Caller** taps Voice/Video call. The app creates (or reuses) the 1:1 room `dm-<uidA>-<uidB>` in Firestore,
   asks `sendNotification` to ring the other user, and opens the call screen.
2. The call screen asks for a room token from `wibechatToken`. The function checks the Firebase ID token and
   the room, and picks the identity itself. The app then connects with the WibeChat SDK.
3. **Callee's** phone gets a data-only push (`type = call | video_call`) and rings: a call notification with
   Accept / Decline, plus a full-screen screen over the lock screen. If nobody answers, it stops after 30 s.
4. Decline, timeout and hang-up send `type = end_call` back, so the other phone stops ringing or leaves the call.
   Each side writes its own entry to `users/{uid}/callLogs`.

No API secret or license key is in the app. Tokens are only issued by the server.

## Backend endpoints used (already deployed)

| Call | Endpoint | Auth |
|------|----------|------|
| Room token | `GET {base}/wibechatToken/api/wibechat-token?room=<roomId>` → `{ serverUrl, token }` | `Authorization: Bearer <Firebase ID token>` |
| Push | `POST {base}/sendNotification/api/send-notification` with `{ user_id, title, desc, data }` | same |

Firestore (named database, see `FIRESTORE_DATABASE_ID`): `users`, `users/{uid}/callLogs`, `rooms`, `rooms/{id}/participants`.

## Setup

1. **Android Studio** (Ladybug or newer) with JDK 17 or 21. Open this folder.
2. **Secrets.** Copy `secrets.defaults.properties` to `secrets.properties` and fill in:
   - `WIBECHAT_FUNCTIONS_BASE_URL`: base URL of the Cloud Functions
   - `FIRESTORE_DATABASE_ID`: the Firestore database name
   - `GOOGLE_WEB_CLIENT_ID`: optional, enables the Google button
3. **Firebase config.** In the Firebase console, add an Android app with package `com.wibechat.android`.
   Download its `google-services.json` into `app/` and `app-xml/` (see `app/google-services.json.example`).
   For Google sign-in, also add your debug/release SHA-1 fingerprint there.
4. Run the `app` (Compose) or `app-xml` (XML) configuration on a real device. Both use the application ID
   `com.wibechat.android`, so installing one replaces the other. Calls need a camera and microphone.
   Use two devices (or one device plus the Flutter app) to call.

`secrets.properties`, `app/google-services.json`, `app-xml/google-services.json` and `local.properties` are
git-ignored. Don't commit or share them.

## Using the calls module on its own

```kotlin
val session = CallSession(context)
session.connect(CallCredentials(serverUrl, token))   // token from YOUR server
session.setMicrophoneEnabled(true)
session.setCameraEnabled(true)

// Compose
val state by session.state.collectAsState()
state.remotes.forEach { tile ->
    tile.videoTrack?.let { VideoTrackView(session.room, it, Modifier.fillMaxSize()) }
}

session.release() // on hang-up
```

## Notes

- `applicationId` is `com.wibechat.android`. The Kotlin namespace is `com.wibechat.demo`, because the SDK already uses
  `com.wibechat.android` as its own namespace.
- The brand name ("WibeChat") lives in `app/src/main/res/values/strings.xml`.
- "Copy ID token" in the account drawer only appears in debug builds.
- The SDK runs the screen-share foreground service itself.
