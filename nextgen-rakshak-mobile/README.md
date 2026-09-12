# NextGen Rakshak — Volunteer Android App

Kotlin + Jetpack Compose app. Volunteers receive a missing-child alert, open the
camera, and the phone scans the crowd on-device (ML Kit face detection + 3-point
alignment + MobileFaceNet TFLite/LiteRT embeddings + cosine similarity). **No
biometric data leaves the device** — the only image ever uploaded is the crop of
the face the volunteer explicitly confirmed as a sighting.

`minSdk 24` · `compileSdk 35` · JDK 17 · manual DI via `ServiceLocator`.

## Screens

`AppNavigation` routes: `login → home → scan`, plus `matches`, `profile`, `mesh`.
Home / Matches / Profile are the bottom-bar roots (double back press to exit); a
tapped alert notification deep-links into Scan once sign-in settles on Home.

| Screen | What it does |
|---|---|
| **Login** | One route: Continue with Google (Credential Manager + `googleid`). Email/password and anonymous guest are both gone. An account carrying the kiosk's `police` claim is refused — one account, one role. |
| **Home** | Live active alerts (Firestore ∪ mesh), alert detail with photo, share, and publishes the volunteer's GPS so the server can geofence pushes. Landscape (a rotated phone, or a tablet) gets a real master-detail split — the alert list stays visible in one pane while the detail fills the other — instead of the detail screen replacing the list. |
| **Scan** | CameraX preview, live face boxes, torch, front/back switch (front preview mirrored), readiness header, side-by-side confirm dialog, queued-match counter, haptic buzz on a hit. Controls are a bottom bar in portrait and a side rail in landscape (a bottom bar there ate a disproportionate share of the shorter preview height); the confirm dialog splits into two columns (evidence / decision) in landscape instead of one column that could push Confirm/Reject off-screen. Confirm/Reject disable and show a spinner while the report is in flight, so a second tap cannot fire a duplicate mesh relay. |
| **Matches** | The volunteer's own reports: status (pending / dispatched / accepted / dismissed), the confidence they acted on, coordinates or "no location", and an explicit *still queued on this device* state. Summary counts on top. |
| **Profile** | Google identity card (avatar re-requested at the rendered size via `AvatarUrl`), SIM phone number with a dual-SIM picker and manual override, cloud-sync state, mesh entry point, sign-out. |
| **Mesh network** | Rewritten from a bare log dump into a live view: a radar animation while discovery is active, a hero card (peer count, self-online/gateway state), a stats block (packets sent/received/relayed), a peer list by device name with connect time, and a filterable colour-coded activity log — plus the Location-off warning + settings shortcut (Nearby discovery needs it even with the permission granted). |

## Architecture (clean, SOLID)

```
com.rakshak.app/
├── data/
│   ├── auth/          AuthService + FirebaseAuthService, GoogleSignInClient, AuthFailure
│   ├── model/         Alert, MatchReport + MatchStatus + MatchStatusReport, Volunteer
│   ├── datasource/    AlertDataSource + FirestoreAlertSource + MeshAlertSource + MeshStore
│   │                  MatchDataSource + FirestoreMatchSource, FirestoreVolunteerSource,
│   │                  SightingPhotoUploader
│   ├── local/         Room AppDatabase (v4): PendingMatchDao/Entity, MeshDao, MeshEntities
│   │                  VolunteerStore (DataStore)
│   └── repository/    AlertRepository (Firestore ∪ mesh) · MatchRepository (Firestore →
│                      Room queue + mesh relay + sync) · VolunteerRepository
├── domain/
│   ├── matching/      FaceMatcher, AlertIndex, TrackRegistry, EmbeddingAggregator,
│   │                  EmbeddingComparator + CosineEmbeddingComparator, FaceMatch
│   └── usecase/       ReportMatchUseCase
├── ml/                FaceDetector + MlKitFaceDetector, FaceGeometry, FacePreprocessor,
│                      ImageQuality, EmbeddingExtractor + TFLiteEmbeddingExtractor
├── networking/        RakshakMessagingService (FCM), NotificationHelper, FcmTokenProvider,
│   │                  ConnectivityMonitor, MatchSyncWorker
│   └── mesh/          MeshNetworkManager, MeshPayloadCodec, MeshCrypto (HMAC), MeshRouter,
│                      MeshSeenCache, MeshThumbnail, MeshService (foreground)
├── di/                ServiceLocator (manual DI)
├── presentation/
│   ├── screen/        LoginScreen, HomeScreen, ScanScreen, MatchesScreen, ProfileScreen,
│   │                  MeshNetworkScreen, PermissionRationaleDialog
│   ├── viewmodel/     LoginViewModel, HomeViewModel, ScanViewModel, MatchesViewModel,
│   │                  ViewModelFactory
│   ├── navigation/    AppNavigation (Routes)
│   └── theme/         Theme, Color, ExtendedColors, Type, Shape, Spacing, WindowInfo
└── utils/             Constants, LocationProvider, LocationSettings, Haptics, BitmapExt,
                       ElapsedTime, AvatarUrl, SimPhoneProvider
```

## Face matching pipeline

Per camera frame:

1. **ML Kit detect** + landmarks + tracking id.
2. **Frontality gate** — yaw ≤ 40°, roll ≤ 35°. A profile view does not match even
   the right child, and can weakly match the wrong one.
3. **3-point similarity alignment** — eyes + nose warped onto the ArcFace 112×112
   template (`FaceGeometry`). Landmark-less fallback: square crop,
   `FACE_CROP_MARGIN = 0.2`.
4. **Quality gate** — face ≥ 48 px, mean luma 25–240, variance-of-Laplacian ≥ 12
   (`ImageQuality`).
5. **Embed** — `(px − 127.5)/127.5`, `[1,112,112,3]`, L2-normalised output.
6. **Multi-frame fusion** — up to 3 embeddings averaged per tracking id, gated by
   a 0.5 coherence floor so a recycled ML Kit id cannot blend two identities;
   tracks idle for 3 s are evicted.
7. **Cosine** against every prepared alert.

| Decision | Value |
|---|---|
| Candidate | cosine > `SIMILARITY_THRESHOLD` = 0.55 |
| Instant single-frame match | cosine ≥ `STRONG_MATCH_THRESHOLD` = 0.72 |
| Mid-band confirmation | 2 consecutive frames of one track |
| Analysis resolution | 1280×720, single-flight |

**Alert photos are re-embedded on this device** (`ScanViewModel.prepare`, cached
by alert id + photo URL); the server's `embedding` is only a fallback for a photo
that cannot be fetched. Mixing the server's geometry with the phone's scores an
identical face at 0.38–0.49 — below threshold — so one implementation on both
sides of the comparison removes that failure class entirely.

`TFLiteEmbeddingExtractor` runs every interpreter call on **one dedicated thread**
(XNNPACK, 4 threads — LiteRT 2.x bundles no NNAPI/GPU delegate), and `onFrame` is
gated by an `AtomicBoolean` so two frames can never enter the interpreter at once.
Embedding width is read from the model's output tensor at load time; nothing
hard-codes 128 or 512. `AlertIndex` detects a width mismatch and the scan header
says so, because that failure is otherwise completely silent.

## Reporting a sighting

`ReportMatchUseCase` (on `Dispatchers.IO`): GPS fix bounded at 6 s
(`hasLocation = false` beats a 30 s hang) → sighting crop uploaded to
`match_sightings/` as `image/jpeg` q85, bounded at 6 s, falling back to the alert
photo → `MatchRepository.report`. If Firestore fails or times out, the report is
written to the Room queue **and** relayed over the mesh; `MatchSyncWorker`
(WorkManager) drains the queue when connectivity returns.

## Offline behaviour (mesh)

Nearby Connections `P2P_CLUSTER` + an application-level store-and-forward layer
(`MeshNetworkManager`). Nearby only links pairs of devices; multi-hop reach is
this layer re-broadcasting each received packet minus its sender.

- Every packet: a per-packet **UUID message id**, a **TTL** (6, decremented per
  hop, dropped at 1), and an **HMAC-SHA256** trailer (`MeshCrypto`, key from
  `BuildConfig.MESH_HMAC_KEY`) — a packet whose MAC fails to verify is dropped.
  A release build refuses to compile with the default dev key.
- Packet types: `alert`, `match`, `resolve`, `hello`, `ack`. `resolve` floods like
  an alert, because an offline phone has no other way to learn a case closed.
- Flood control: a **time-windowed seen-id cache** (`MeshSeenCache`, evicted after
  the 8 h alert lifetime), a resolved-id set, and an expiry check.
- Alert packets carry a **96×96 ≈2–3 KB JPEG thumbnail** (`MeshThumbnail`) so an
  offline phone renders the parent's photo in the match dialog (FR-07).
- **Gateway-aware match routing:** peers exchange a HELLO with an "I have
  internet" bit; match reports go to online peers first and are flooded otherwise.
  The online device uploads the match (stamping `relayedBy`) and sends an ACK back
  along the mesh; the origin re-sends every 15 s (≤3 tries) until the ACK arrives
  or it comes online.
- `MatchReport.hasLocation` and `identifyingMarks` / `volunteerName` ride the wire,
  so the kiosk shows "no location" rather than a pin on 0,0.
- **Foreground service** (`MeshService`, `connectedDevice` type) keeps relaying
  while the app is backgrounded or the screen is locked; a low-priority
  notification shows the live peer count and a Stop action.
- Learned alerts + seen ids are **persisted to Room** (`MeshStore`) so a restart
  mid-event does not drop them.
- Live radar, stats, peer list and activity log: **Profile → Mesh Network**
  (`MeshNetworkScreen`).
- **Reconnect stability**, found and fixed on real hardware (3-device field
  test): `NEARBY_WIFI_DEVICES` was declared in the manifest but never actually
  requested at runtime, so on Android 13+ any device but the one that happened
  to have it pre-granted could accept incoming connections but never run its
  *own* discovery — permanently, with only a log line. `MeshNetworkManager` now
  retries `startAdvertising`/`startDiscovery` with backoff on failure, and a
  15 s watchdog re-arms both whenever a running device has zero peers. Separately,
  `onEndpointLost` (a *discovery* callback — "the BLE beacon is no longer seen")
  was wrongly treated as a disconnect: Nearby throttles advertising once paired,
  so it fires routinely for a link whose `KEEP_ALIVE` traffic is still succeeding
  both ways, and only a real `onDisconnected` should drop a peer now. With both
  fixes, three physical phones held a stable mesh (a star topology through
  whichever device happened to link to both others) and a relayed alert was
  confirmed hopping two links deep (`ttl` decrementing 6 → 5 across two phones
  that were never directly paired) — VER-08's remaining gap is measuring
  hop-relay timing, not whether the mesh holds together.

## Theme / design system

`presentation/theme/` holds a real system, not per-screen values: full Material 3
light + dark schemes (a Signal Red + Graphite palette — the one color a
volunteer scanning a crowd in daylight needs to spot instantly is used sparingly
so it keeps that meaning, and reject/danger is a separate darker red so "not the
child" never reads as the brand color), `RakshakExtendedColors` (success/warning
with container/on-container pairs, read via `RakshakExtras.current`), the
complete M3 type scale on the system font, five-step `RakshakShapes` +
`PillShape`, a `Spacing` scale (4 dp base, `xxs…xxl`), an `Elevation` scale, and
`rememberWindowInfo()` for orientation + COMPACT/MEDIUM/EXPANDED width classes.

Every screen is migrated onto it — no screen references a raw `Color(0x…)` or a
one-off `dp` value anymore. Each screen also has a real landscape layout, not
just "doesn't clip": Home's master-detail split, Scan's control rail and
two-column match dialog, and a scrollable Profile (see the Screens table above)
were all found to overflow or misplace content in landscape before this pass —
in Home/Profile's case, content (including the Start Scanning button and
Logout) could be pushed off-screen with no way to scroll to it at all, which
the fix treats as a correctness bug, not a polish item.

## Permissions

Camera, fine/coarse location, notifications, vibrate, `READ_PHONE_STATE` +
`READ_PHONE_NUMBERS` (auto-fill the volunteer's own number instead of asking),
foreground service (`connectedDevice`), and Nearby's transport permissions — the
legacy Bluetooth/Wi-Fi set capped at `maxSdkVersion=30` plus
`BLUETOOTH_ADVERTISE/CONNECT/SCAN` and `NEARBY_WIFI_DEVICES` for API 31+.

`allowBackup="false"`: the Room queue holds unsent sightings (a child's name, a
confidence score, a GPS fix) and DataStore holds the volunteer's identity.
Auto-backup would copy both to Google Drive, contradicting the on-device
guarantee the project is built on.

## Setup

1. Open this folder in **Android Studio** (it generates the Gradle wrapper jar on
   first sync).
2. `app/google-services.json` (Firebase project `nextgen-rakshak`) must be in
   place; the `com.google.gms.google-services` plugin is enabled. It is
   gitignored — never commit it.
3. Enable **Authentication → Sign-in method → Google** in the Firebase console and
   register your signing SHA-1 (`./gradlew signingReport`), then re-download
   `google-services.json`. Google is the app's only sign-in route.
4. Add the model at `app/src/main/assets/mobilefacenet.tflite` — see
   [the assets README](app/src/main/assets/README.md) and
   [`scripts/README.md`](../scripts/README.md).
5. Run on a device or emulator (min SDK 24).

```bash
./gradlew :app:testDebugUnitTest   # compile + unit tests
./gradlew :app:assembleDebug
```

**Release builds** require `MESH_HMAC_KEY` (and the `RELEASE_*` signing entries)
in `local.properties`; the build fails rather than ship the well-known dev key,
which would make mesh packet authentication worthless.

> Secrets: `google-services.json` and `local.properties` are gitignored. No keys
> are hardcoded in source.

## Tests

`./gradlew :app:testDebugUnitTest` — cosine similarity, `AlertIndex`,
`EmbeddingAggregator`, `TrackRegistry`, `FaceGeometry`, `MeshCrypto`,
`MeshPayloadCodec`, `MeshRouter`, `MeshSeenCache`, `MeshAlertSource`,
`ElapsedTime`.

---

Full system documentation: [`../docs/SYSTEM-REFERENCE.md`](../docs/SYSTEM-REFERENCE.md).
