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
├── networking/        RakshakMessagingService (FCM), NotificationHelper, ConnectivityObserver, MatchSyncWorker
│   └── mesh/          MeshNetworkManager (Nearby P2P_CLUSTER), MeshPayloadCodec
├── di/                ServiceLocator (manual DI)
├── presentation/
│   ├── screen/        LoginScreen, HomeScreen, ScanScreen
│   ├── viewmodel/     LoginViewModel, HomeViewModel, ScanViewModel, ViewModelFactory
│   ├── navigation/    AppNavigation (Login→Home→Scan)
│   └── theme/         RakshakTheme, Color
└── utils/             Constants, LocationProvider
```

## Offline behaviour (Phase 2)
- **Mesh:** Nearby Connections `P2P_CLUSTER`. Alerts hop device-to-device (store-and-forward, deduped by id) with no internet. The app also re-broadcasts online alerts into the mesh and uploads mesh-relayed matches from whichever device has internet.
- **Offline matches:** if Firestore write fails, the match is queued in Room and relayed over the mesh; `MatchSyncWorker` (WorkManager) uploads the queue when connectivity returns.

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
