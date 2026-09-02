# NextGen Rakshak — Volunteer Android App

Kotlin + Jetpack Compose app. Volunteers receive a missing-child alert, open the
camera, and the phone scans the crowd on-device (ML Kit face detection +
MobileFaceNet TFLite embeddings + cosine similarity). No biometric data leaves
the device.

## Architecture (clean, SOLID)
```
com.rakshak.app/
├── data/
│   ├── model/         Alert, MatchReport, Volunteer
│   ├── datasource/    AlertDataSource + FirestoreAlertSource + MeshAlertSource; MatchDataSource + FirestoreMatchSource
│   ├── local/         Room (AppDatabase, PendingMatchDao, PendingMatchEntity), VolunteerStore (DataStore)
│   └── repository/    AlertRepository (combines Firestore∪Mesh) · MatchRepository (Firestore→Room fallback + sync)
├── domain/
│   ├── matching/      FaceMatcher, EmbeddingComparator + CosineEmbeddingComparator, FaceMatch
│   └── usecase/       ReportMatchUseCase
├── ml/                FaceDetector + MlKitFaceDetector, EmbeddingExtractor + TFLiteEmbeddingExtractor, FacePreprocessor
├── networking/        RakshakMessagingService (FCM), NotificationHelper, ConnectivityMonitor, MatchSyncWorker
│   └── mesh/          MeshNetworkManager, MeshPayloadCodec, MeshCrypto (HMAC), MeshThumbnail,
│                      MeshSeenCache, MeshRouter, MeshService (foreground)
├── di/                ServiceLocator (manual DI)
├── presentation/
│   ├── screen/        LoginScreen, HomeScreen, ScanScreen
│   ├── viewmodel/     LoginViewModel, HomeViewModel, ScanViewModel, ViewModelFactory
│   ├── navigation/    AppNavigation (Login→Home→Scan)
│   └── theme/         RakshakTheme, Color
└── utils/             Constants, LocationProvider
```

## Offline behaviour (Phase 2)
- **Mesh:** Nearby Connections `P2P_CLUSTER` + an application-level store-and-forward
  layer (`MeshNetworkManager`). Nearby only links pairs of devices; multi-hop reach
  is this layer re-broadcasting each received packet minus its sender.
  - Every packet: a per-packet **UUID message id**, a **TTL** (6, decremented per hop,
    dropped at 1), and an **HMAC-SHA256** trailer (`MeshCrypto`, key from
    `BuildConfig.MESH_HMAC_KEY`) — a packet whose MAC fails to verify is dropped.
  - Flood control: a **time-windowed seen-id cache** (`MeshSeenCache`, evicts after
    the 8 h alert lifetime — genuinely short-lived), a resolved-id set, and an
    expiry check.
  - Alert packet also carries a **96×96 ≈2–3 KB JPEG thumbnail** (`MeshThumbnail`) so
    an offline phone renders the parent's photo in the match dialog (FR-07).
  - **Gateway-aware match routing:** peers exchange a HELLO with an "I have internet"
    bit; match reports are sent to online peers first, flooded otherwise. The
    online device uploads the match and sends an ACK back along the mesh; the
    origin re-sends every 15 s (≤3 tries) until the ACK arrives or it comes online.
  - `MatchReport.hasLocation` rides the wire — the kiosk shows "no location" rather
    than a pin on 0,0 when the volunteer had no GPS fix.
  - Mesh debug screen warns (with a settings shortcut) when the device's **Location**
    toggle is off — Nearby discovery needs it even with the permission granted.
  - **Foreground service** (`MeshService`, `connectedDevice` type) keeps the mesh
    relaying when the app is backgrounded / screen locked; a low-priority
    notification shows the live peer count and a Stop action.
  - Learned alerts + seen ids are **persisted to Room** (`MeshStore`) so a restart
    mid-event does not drop them.
  - Live packet log + peer count: **Profile → Mesh Network** (`MeshDebugScreen`).
- **Offline matches:** if Firestore write fails, the match is queued in Room and
  relayed over the mesh; `MatchSyncWorker` (WorkManager) uploads the queue when
  connectivity returns.

## Setup
1. Open this folder in **Android Studio** (it generates the Gradle wrapper jar on
   first sync).
2. `app/google-services.json` (Firebase, project `nextgen-rakshak`) is already in
   place and the `com.google.gms.google-services` plugin is enabled. It is
   gitignored — never commit it.
3. Add the model at `app/src/main/assets/mobilefacenet.tflite` (see assets README).
4. Run on a device/emulator (min SDK 24).

> Secrets: `google-services.json` and `local.properties` are gitignored. No keys
> are hardcoded in source.

## Tests
`./gradlew test` — includes cosine-similarity unit tests.
