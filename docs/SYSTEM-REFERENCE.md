# NextGen Rakshak — System Reference (as built)

**Single source of truth for what the code actually does.** Written by reading
every source file in `nextgen-rakshak-webportal/`, `nextgen-rakshak-mobile/`,
`functions/`, `scripts/` and the Firebase configuration.

**Documented state:** 12 September 2026 (HEAD `bb0f829`, plus the in-progress
mobile design-system work described in §5.7).

> Older documents in `docs/` are **dated snapshots** and are not corrected in
> place: `docs/plan/*` is the Week 4–8 plan as written on 27 July 2026, and
> `docs/week-2/`, `docs/week-3/` are weekly reports. Where they disagree with
> this file, this file is current.

---

## 1. What the system is

Hybrid edge-AI system for finding a missing child at a mass gathering during the
**golden hour** (first 60–90 minutes), when cell networks are congested.

Three tiers:

| Tier | Runs on | Responsibility |
|---|---|---|
| **Cloud** | Firebase (Firestore, Storage, Auth, FCM, Cloud Functions v2 on Node 22) | Case records, alert fan-out, fallback embedding, expiry + purge |
| **Edge** | Volunteer Android phones | Face detection, alignment, embedding, cosine matching — **all recognition** |
| **Mesh** | Nearby Connections between phones | Alert/match/resolve flooding with no internet at all |

**Privacy invariant:** no bystander image, video frame, or embedding leaves the
phone. The only image uploaded from the phone is the crop of the face the
volunteer explicitly confirmed as a sighting. The only parent-supplied image in
the cloud is the alert photo the officer files, and it is deleted when the case
closes (§6.4).

---

## 2. End-to-end flow

1. Officer signs into the kiosk with Google → `claimOfficerRole` grants the
   `police` custom claim and writes `officers/{uid}` (§4.1).
2. Officer files an alert: photo + details + the browser's GPS fix →
   `alert_images/…` in Storage, document in `alerts` (§3.1).
3. `onAlertCreated` computes a fallback embedding server-side and pushes the
   alert by FCM to volunteers within **2 km** (§6.1, §6.2).
4. Volunteer phones also learn the alert over the mesh, including a ≤8 KB face
   thumbnail so the photo renders with no internet (§7).
5. Volunteer opens Scan. The phone **re-embeds the alert photo on-device** and
   matches live camera faces against it (§5.4 — this is why geometry parity
   matters, §8.3).
6. Above threshold, the app shows the live crop **beside** the parent's photo and
   asks the volunteer. The system never declares a child found on its own.
7. On Confirm: GPS fix (≤6 s), sighting crop uploaded, `matches` document
   written; if the network is down it queues in Room **and** relays over the mesh
   for an online peer to upload (§5.6, §7.3).
8. `onMatchCreated` pushes the sighting to the filing officer's browser (§6.3).
9. Officer reviews side by side and **accepts**, **dismisses**, or **dispatches**.
10. Resolving the alert purges the embedding and deletes the photo everywhere
    (§6.4).

---

## 3. Shared data model (Firestore)

### 3.1 `alerts/{alertId}`

| Field | Type | Notes |
|---|---|---|
| `childName` | string | |
| `age` | number | |
| `gender` | `"Male" \| "Female" \| "Other"` | |
| `clothingDesc` | string | |
| `identifyingMarks` | string? | scars/marks/accessories; also carried on the mesh |
| `parentContact` | string | |
| `imageUrl` | string | Storage download URL; **blanked on resolve** |
| `embedding` | number[] | 128-d (shipping MobileFaceNet) or 512-d; **cleared on resolve** |
| `lastSeen` | string | free-text place |
| `lastSeenDate` | string? | `YYYY-MM-DD`; absent on older alerts |
| `lastSeenTime` | string? | `HH:MM`; absent on older alerts |
| `geoLocation` | GeoPoint? | kiosk's fix at filing; drives the geofence |
| `createdBy` | `{uid, name, station}`? | rules force `uid == request.auth.uid` |
| `status` | `active \| resolved` | |
| `timestamp` | Timestamp | server clock |

### 3.2 `matches/{matchId}`

| Field | Type | Notes |
|---|---|---|
| `alertId` | string | rules require the alert to exist |
| `childName` | string | denormalised |
| `imageUrl` | string | **the sighting crop**, not the alert photo (falls back to the alert photo if the upload fails); blanked when the alert resolves |
| `volunteerId` | string | |
| `volunteerName` | string? | blank when the profile carries no Google name |
| `volunteerRole` | string | |
| `relayedBy` | string? | set when a *different* device uploaded it off the mesh — unforgeable, unlike `volunteerId` |
| `location` | GeoPoint | `0,0` when there was no fix |
| `hasLocation` | bool | false ⇒ do not draw a pin; absent on old docs ⇒ treat as true |
| `confidence` | number | cosine, 0–1, clamped |
| `status` | `pending \| dispatched \| accepted \| dismissed` | only the kiosk may change it, and only this field |
| `timestamp` | Timestamp | must equal `request.time` — no back-dating |

### 3.3 `officers/{uid}` (kiosk directory)

`uid, email, photoURL, role:"police", displayName, phone, station, badgeNumber,
fcmToken?, createdAt, lastLoginAt, updatedAt`.

Written by `claimOfficerRole` with the Admin SDK. The browser may edit only
`displayName / phone / station / badgeNumber / fcmToken / updatedAt`.

### 3.4 `volunteers/{uid}` (mobile)

`phone, role, name, email, photoUrl, fcmToken, registeredAt, phoneUpdatedAt?,
updatedAt?, lastLocation (GeoPoint), locationUpdatedAt`.

### 3.5 `allowedOfficers/{email}` — **legacy, dead**

Officers self-register now. The collection is denied to every client and read by
nothing. Ignore any older document that tells you to seed it.

### 3.6 Indexes

One composite index: `alerts(status ASC, timestamp DESC)`.

---

## 4. Authentication and authorisation

### 4.1 One Auth pool, two mutually exclusive roles

Both apps share one Firebase Auth project. Authentication is **Google only**
(email/password and anonymous are gone from both UIs; `AuthService` still exposes
email helpers, but no screen calls them).

| Principal | Sign-in | Authorisation |
|---|---|---|
| Officer (kiosk) | Google → `claimOfficerRole` | `police` custom claim on the ID token |
| Volunteer (app) | Google | `volunteers/{uid}` document, no claim |

- `claimOfficerRole` (`functions/src/officers.ts`) is **self-service**: any
  authenticated Google account becomes police. There is no allow-list and no
  approval step.
- It **refuses** an account that has a `volunteers/{uid}` document, and *revokes*
  a `police` claim previously granted to such an account (plus its refresh
  tokens).
- `firestore.rules` mirrors that from the other side: `volunteers/{uid}` writes
  are denied to a `police` account. Both halves must stay in step.
- The kiosk's route guard (`useRequireOfficer`) is UX only — Firebase web auth
  lives in IndexedDB, so Next.js middleware cannot see it. The security boundary
  is the claim check inside the rules.
- The client must call `getIdToken(true)` after the claim is granted; the kiosk's
  `ensureOfficerRole` does exactly that.

### 4.2 Firestore rules summary (`firestore.rules`)

| Path | read | create | update | delete |
|---|---|---|---|---|
| `alerts/{id}` | any signed-in | `police` **and** `createdBy.uid == auth.uid` | `police` | never |
| `matches/{id}` | any signed-in | signed-in **and** `validMatch()` **and** `attributed()` | `police`, `status` only, from the 4-value enum | never |
| `volunteers/{uid}` | any signed-in | own uid **and not** `police` | same | same |
| `officers/{uid}` | `police` only | never (function only) | own uid, whitelisted fields only | never |
| `allowedOfficers/{email}` | never | never | never | never |

`validMatch()` pins the document shape (`hasAll` + `hasOnly`), the confidence
range, `location is latlng`, `hasLocation is bool`, `status == 'pending'`,
`timestamp == request.time`, and that the parent alert **exists**.
`attributed()` accepts either the reporter's own uid in `volunteerId` or a mesh
relay that stamps itself in `relayedBy`.

### 4.3 Storage rules (`storage.rules`)

| Path | read | write |
|---|---|---|
| `alert_images/{imageId}` | signed-in | `police` only, `< 5 MB`, `image/*` |
| `match_sightings/{imageId}` | signed-in | any signed-in, `< 5 MB`, `image/*` |

The single-segment match on `alert_images/{imageId}` is why the kiosk sanitises
filenames (`safeFileName`) — a slash in a filename silently escapes the rule.

---

## 5. Volunteer Android app (`nextgen-rakshak-mobile/`)

Kotlin + Jetpack Compose, `minSdk 24`, `compileSdk 35`, JDK 17, manual DI via
`ServiceLocator`. (The synopsis's "Android 5.0+ / API 21" is drift — the project
is API 24+.)

### 5.1 Screens and navigation

`AppNavigation` routes: `login → home → scan`, plus `matches`, `profile`, `mesh`.
Bottom navigation bar over **Home / Matches / Profile**; those three are
back-stack roots and require a double back press to exit. A tapped alert
notification deep-links to Scan once sign-in has settled on Home.

| Screen | What it does |
|---|---|
| **Login** | One action: Continue with Google (Credential Manager + `googleid`). Rejects an account carrying the `police` claim. |
| **Home** | Live active-alert list (Firestore ∪ mesh), alert detail with photo, share, and publishes the volunteer's GPS on load for the geofence. |
| **Scan** | CameraX preview, live face boxes, torch toggle, front/back camera switch (front preview mirrored), readiness header, match-confirm dialog, queued-match counter, haptic buzz on a hit. |
| **Matches** | The volunteer's own reports with status (pending / dispatched / accepted / dismissed), confidence, coordinates or "no location", and an explicit **still queued on this device** state. Summary counts on top. |
| **Profile** | Google identity card (avatar re-requested at the rendered size via `AvatarUrl`), SIM-based phone number with dual-SIM picker and manual override, cloud-sync state, mesh entry point, sign-out. |
| **Mesh debug** | Live peer count, packet log, gateway state, and a warning + settings shortcut when the OS **Location** toggle is off (Nearby discovery needs it even when the permission is granted). |

### 5.2 Package layout

```
com.rakshak.app/
├── data/
│   ├── auth/          AuthService + FirebaseAuthService, GoogleSignInClient, AuthFailure
│   ├── model/         Alert, MatchReport + MatchStatus + MatchStatusReport, Volunteer
│   ├── datasource/    AlertDataSource · FirestoreAlertSource · MeshAlertSource · MeshStore
│   │                  MatchDataSource · FirestoreMatchSource · FirestoreVolunteerSource
│   │                  SightingPhotoUploader
│   ├── local/         AppDatabase (Room v4), PendingMatchDao/Entity, MeshDao, MeshEntities,
│   │                  VolunteerStore (DataStore)
│   └── repository/    AlertRepository (Firestore ∪ mesh) · MatchRepository (Firestore →
│                      Room queue + mesh relay) · VolunteerRepository
├── domain/
│   ├── matching/      FaceMatcher, AlertIndex, TrackRegistry, EmbeddingAggregator,
│   │                  EmbeddingComparator + CosineEmbeddingComparator, FaceMatch
│   └── usecase/       ReportMatchUseCase
├── ml/                FaceDetector + MlKitFaceDetector, FaceGeometry, FacePreprocessor,
│                      ImageQuality, EmbeddingExtractor + TFLiteEmbeddingExtractor
├── networking/        RakshakMessagingService (FCM), NotificationHelper, FcmTokenProvider,
│   │                  ConnectivityMonitor, MatchSyncWorker
│   └── mesh/          MeshNetworkManager, MeshPayloadCodec, MeshCrypto, MeshRouter,
│                      MeshSeenCache, MeshThumbnail, MeshService (foreground)
├── di/                ServiceLocator
├── presentation/
│   ├── screen/        Login, Home, Scan, Matches, Profile, MeshDebug, PermissionRationaleDialog
│   ├── viewmodel/     Login, Home, Scan, Matches, ViewModelFactory
│   ├── navigation/    AppNavigation (Routes)
│   └── theme/         Theme, Color, ExtendedColors, Type, Shape, Spacing, WindowInfo
└── utils/             Constants, LocationProvider, LocationSettings, Haptics, BitmapExt,
                       ElapsedTime, AvatarUrl, SimPhoneProvider
```

### 5.3 Face pipeline (mirrored on all three sides)

Order, per frame:

1. **ML Kit detect** + landmarks + tracking id (`MlKitFaceDetector`).
2. **Frontality gate** — yaw ≤ 40°, roll ≤ 35° (`Constants`).
3. **3-point similarity alignment** — left eye, right eye, nose warped onto the
   ArcFace 112×112 template (`FaceGeometry`). Fallback when landmarks are
   missing: square crop with `FACE_CROP_MARGIN = 0.2`.
4. **Quality gate** (Android only) — face ≥ 48 px, mean luma 25–240,
   variance-of-Laplacian ≥ 12 (`ImageQuality`).
5. **Embed** — `(px − 127.5) / 127.5`, `[1,112,112,3]` → L2-normalised vector.
6. **Multi-frame fusion** — up to 3 embeddings averaged per ML Kit tracking id,
   gated by a coherence floor of 0.5 so a recycled id cannot blend two people
   (`EmbeddingAggregator`, `TrackRegistry`, idle eviction at 3 s).
7. **Cosine** against every prepared alert (`AlertIndex`,
   `CosineEmbeddingComparator`).

Must stay identical across `FacePreprocessor`/`FaceGeometry` (Android),
`functions/src/embedding.ts` (server) and `scripts/face_align.py` (evaluation).
Embedding width is read from the model's output tensor at load time — nothing
hard-codes 128 or 512.

### 5.4 Alert embeddings are recomputed on-device

`ScanViewModel.prepare` downloads each alert photo, detects and embeds it with
the **phone's** model, and caches the result by alert id + photo URL. The
server's `embedding` array is only a fallback for a photo that cannot be
fetched. Reason: mixing geometries scores an identical face at 0.38–0.49, below
the 0.55 threshold (§8.3) — running both sides of the comparison through one
implementation removes that whole failure class.

Readiness is surfaced in the scan header: alerts total, alerts ready, preparing,
**model mismatch** (width disagreement) and **model unavailable**.

### 5.5 Match decision

| Rule | Value |
|---|---|
| Candidate threshold | cosine > `SIMILARITY_THRESHOLD` = **0.55** |
| Instant single-frame match | cosine ≥ `STRONG_MATCH_THRESHOLD` = **0.72** |
| Mid-band confirmation | `MATCH_CONFIRM_FRAMES` = **2** consecutive frames of one track |
| Fusion depth | `EMBEDDING_FUSION_FRAMES` = **3** |
| Track coherence floor | `TRACK_COHERENCE_MIN` = **0.5** |
| Track idle eviction | 3 s |
| Analysis resolution | 1280×720, single-flight (detector latency = frame rate) |

The TFLite interpreter runs on **one dedicated thread** (XNNPACK, 4 threads —
LiteRT 2.x bundles no NNAPI/GPU delegate); `onFrame` is gated by an
`AtomicBoolean` so two frames can never enter the interpreter at once. A second
face that also crosses the threshold in the same frame is held in a backlog queue
instead of being dropped while the dialog is up.

### 5.6 Confirm → report

`ReportMatchUseCase` on `Dispatchers.IO`:

1. GPS fix with a **6 s** bound (`hasLocation = false` beats a 30 s hang).
2. Sighting crop → `match_sightings/{alertId}_{ts}.jpg`, JPEG q85, explicit
   `image/jpeg` metadata (the rules reject `application/octet-stream`), **6 s**
   bound, falls back to the alert photo.
3. `MatchRepository.report` → Firestore; on failure or timeout the report is
   written to the Room queue **and** relayed over the mesh. `MatchSyncWorker`
   (WorkManager, connectivity-gated) drains the queue later.

### 5.7 Design system (in progress in a parallel session)

`presentation/theme/` now carries a real system rather than ad-hoc values: full
Material 3 light + dark `ColorScheme`s, `RakshakExtendedColors`
(success/warning with container pairs, via `RakshakExtras.current`), the complete
M3 type scale on the system font, a five-step `RakshakShapes` + `PillShape`, a
`Spacing` scale (4 dp base, `xxs…xxl`) and `Elevation` scale, and
`rememberWindowInfo()` for orientation + COMPACT/MEDIUM/EXPANDED width classes.
Screens are being migrated onto it; that work is **uncommitted** at the time of
writing.

### 5.8 Permissions (`AndroidManifest.xml`)

`INTERNET`, `CAMERA`, `ACCESS_FINE/COARSE_LOCATION`, `POST_NOTIFICATIONS`,
`VIBRATE`, `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_CONNECTED_DEVICE`, Nearby's legacy Bluetooth/Wi-Fi set
(`maxSdkVersion=30`) and the API 31+ set (`BLUETOOTH_ADVERTISE/CONNECT/SCAN`,
`NEARBY_WIFI_DEVICES`).

`allowBackup="false"` — the Room queue holds unsent sightings and DataStore holds
the volunteer's identity; auto-backup would copy both to Google Drive and
contradict the on-device guarantee.

### 5.9 Tests (`./gradlew :app:testDebugUnitTest`)

`CosineEmbeddingComparatorTest`, `AlertIndexTest`, `EmbeddingAggregatorTest`,
`TrackRegistryTest`, `FaceGeometryTest`, `MeshCryptoTest`,
`MeshPayloadCodecTest`, `MeshRouterTest`, `MeshSeenCacheTest`,
`MeshAlertSourceTest`, `ElapsedTimeTest`.

---

## 6. Cloud Functions (`functions/`)

Node **22**, `firebase-functions` v7, region `us-central1`, 1 GiB, 120 s timeout
(tfjs-node needs headroom; a warm embedding is ~1–3 s).

| Export | Trigger | Does |
|---|---|---|
| `onAlertCreated` | create `alerts/{id}` | fallback embedding + geofenced FCM broadcast |
| `onMatchCreated` | create `matches/{id}` | push the sighting to the filing officer |
| `onAlertResolved` | update `alerts/{id}`, `active → resolved` | purge embedding, photo, and copied match photo URLs |
| `expireAlerts` | schedule, every 30 min | flip alerts older than `ALERT_TTL_MS` (8 h) to `resolved` |
| `computeEmbeddingCallable` | callable | re-index one alert — **officers only** |
| `claimOfficerRole` | callable | grant the `police` claim + upsert `officers/{uid}` |

### 6.1 Embedding (`embedding.ts`)

BlazeFace finds the largest face; the eye+nose landmarks warp it onto the same
ArcFace template the phone uses; the tile is normalised `(px − 127.5)/127.5` and
run through the **TensorFlow SavedModel** at `functions/model/savedmodel/` via
`tf.node.loadSavedModel` — the same graph `scripts/convert_models.py` quantised
into the Android `.tflite`. Fallbacks: centred square crop, then the whole image.

**SSRF guard:** the photo is never `fetch`ed from the caller's URL. Only the
object path is taken from it, it must start with `alert_images/`, it must be
≤ 5 MB, and it is read through the Admin SDK from the project's own bucket. A
Functions runtime that fetches arbitrary URLs can be pointed at
`169.254.169.254` for service-account tokens.

### 6.2 Alert broadcast (`notify.ts`)

Haversine filter at `GEOFENCE_RADIUS_KM = 2`, over `volunteers/*.fcmToken`.
**Fail-open**: a volunteer is skipped only when their fix is valid *and* fresher
than `STALE_LOCATION_MS` (6 h) *and* out of range. Multicast in batches of 500;
permanently invalid tokens are blanked.

Messages are **data-only** (no `notification` block): an OS-displayed
notification would bypass `onMessageReceived`, losing the high-importance channel
and the deep link to the scan screen.

### 6.3 Match push (`notify.ts`)

Reads `officers/{uid}.fcmToken` (saved by the kiosk's notification bell; absent ⇒
silent no-op). Sends `notification` + `data.link = "/matches"` +
`webpush.fcmOptions.link`, because the kiosk's service worker displays the
notification itself and reads `data.link` on click.

### 6.4 Purge on resolve

Deletes the Storage object, sets `embedding: []` and `imageUrl: ""`, and blanks
the copied `imageUrl` on **every match for that alert** (otherwise the kiosk
shows a permanently broken image). Writes are chunked at the 500-per-batch
Firestore limit — `expireAlerts` uses the same chunking so a backlog cannot throw
and leave every stale alert live.

---

## 7. Offline mesh (mobile only)

Nearby Connections `P2P_CLUSTER` (BLE discovery + Wi-Fi Direct transfer) with an
application-level store-and-forward layer on top — Nearby links pairs of devices;
multi-hop reach is this layer re-broadcasting each packet minus its sender.

### 7.1 Wire format (`MeshPayloadCodec`)

```
byte[0]        TTL / remaining hop-count   (mutable, outside the MAC)
byte[1]        type tag: alert | match | resolve | hello | ack
byte[2..]      message id (UUID)
...            type-specific fields
last 32 bytes  HMAC-SHA256 over byte[1..end-32]
```

Alert packet ≈ 700 B for a 128-float embedding, ≈ 2.7 KB for 512, plus a ≤ 8 KB
thumbnail — under Nearby's 32 KB `BytesPayload` limit. Embedding length and
thumbnail length are written on the wire, so either model width works unchanged.

### 7.2 Controls

| Mechanism | Where |
|---|---|
| Per-packet UUID message id | `MeshPayloadCodec` |
| TTL 6, decremented per relay, dropped at 1 | `MESH_INITIAL_TTL` |
| HMAC-SHA256 trailer, key from `BuildConfig.MESH_HMAC_KEY` | `MeshCrypto` |
| Release build refuses to compile with the dev key | `app/build.gradle.kts` |
| Time-windowed seen-id set, evicted at the 8 h alert lifetime | `MeshSeenCache` |
| Resolved-id set + expiry check before relay | `MeshNetworkManager` |
| 96×96 q40 JPEG thumbnail (≤ 8 KB) for offline FR-07 | `MeshThumbnail` |
| Learned alerts + seen ids persisted across restart | `MeshStore`, Room v4 |
| Foreground service (`connectedDevice`) with peer count + Stop | `MeshService` |

### 7.3 Gateway-aware match routing

Peers exchange a `HELLO` carrying an "I have internet" bit. A match report goes to
online peers first and is flooded otherwise. The online device uploads it
(stamping `relayedBy`) and sends an `ACK` back; the origin re-sends every **15 s**
up to **3 tries** until the ACK arrives or it comes online itself.

A `RESOLVE` packet floods like an alert, because an offline phone has no other way
to learn a case closed — the alert just vanishes from a Firestore query, and
absence does not reach a peer with no internet.

---

## 8. Face model and measurement (`scripts/`)

### 8.1 Artifacts (never committed)

| Artifact | Consumer | Path |
|---|---|---|
| `mobilefacenet.tflite` | Android | `nextgen-rakshak-mobile/app/src/main/assets/` |
| `savedmodel/` | Cloud Function | `functions/model/` |

Both must come from **one** source model in **one** run. Shipping: sirius-ai
`MobileFaceNet_TF` / `MobileFaceNet_9925_9680`, dynamic-range quantised → 1.5 MB,
128-d. `verify_parity.py` measures cosine **0.99967** between the `.tflite` and
the SavedModel.

Scripts: `freeze_to_savedmodel.py`, `onnx_to_savedmodel.py`, `convert_models.py`
(`--precision float16|int8|dynamic|fp32`), `verify_parity.py`, `face_align.py`,
`evaluate_model.py`, plus `build_brand_assets.py` and the pptx builders.

### 8.2 Threshold provenance

Measured, not taken from literature. Original MobileFaceNet, unaligned crop, 36
real pairs: same-person **0.7142–0.9899**, different-person **0.0864–0.3551** →
empty band 0.3551–0.7142, threshold **0.55** near its middle. The synopsis's 0.75
sat inside the genuine range and missed 5/15 true pairs.

**Re-measure with `evaluate_model.py` after any change to the model, the
alignment, or the precision.** An ArcFace model shifts the genuine band down
(~0.4–0.7).

A 512-d ArcFace device model was trialled and **reverted** — the function still
served 128-d, so no comparison ever ran.

### 8.3 Geometry parity is load-bearing (measured)

| Pairing, same-person photo | cosine |
|---|---|
| centred crop vs centred crop | 0.8855 |
| 3-point align vs 3-point align | 0.8467 |
| **centred crop vs 3-point align, same photo** | **0.38 – 0.49** |

Mixing geometries scores an identical face *below* threshold: a mismatch does not
degrade matching, it silently disables it. This is what broke matching when
`ef3998f` aligned on-device while the deployed function still cropped, and it is
why §5.4 re-embeds alert photos on the phone.

A **width** mismatch is equally silent: every alert falls out on a length check
and the scanner never matches anyone. `AlertIndex` detects and reports it, and the
scan header says "face model mismatch" instead of "no match".

---

## 9. Police kiosk (`nextgen-rakshak-webportal/`)

Next.js 14 App Router, React 18, TypeScript, Tailwind + shadcn/ui (Radix),
`lucide-react`, Firebase JS SDK 10. Path alias `@/*` → `src/*`. Realtime
everywhere via `onSnapshot`.

### 9.1 Routes

| Route | Page |
|---|---|
| `/login` | Google sign-in, `?next=` return path, unauthorised message |
| `/` | Dashboard — 6 clickable stat tiles, active alerts, live match preview, match-status breakdown chart, top-reporting-volunteers leaderboard, live pulse indicator |
| `/alerts/new` | File an alert |
| `/alerts/history` | Every alert, active + resolved — status filter, child-name search, pagination |
| `/matches` | Sighting feed + review dialog — status filter, child-name search, pagination with rows-per-page |
| `/profile` | Officer identity card + editable profile |

Dashboard stat tiles deep-link into Matches / Alert History with `?status=…`,
which both routes read on load to preselect their status filter (wrapped in
`<Suspense>` per-page, since `useSearchParams` requires it).

`app/(kiosk)/layout.tsx` is the guarded shell: sidebar + notification bell, and a
full-screen loader until the `police` claim is confirmed, so no protected content
flashes. `error.tsx`, `global-error.tsx`, `not-found.tsx` cover failures.

### 9.2 Components

`alert-form`, `active-alerts-list`, `alert-history-list`, `alert-detail-dialog`,
`matches-list`, `pending-matches-preview`, `match-review-dialog`, `stats-cards`,
`match-status-chart`, `top-volunteers`, `sidebar-nav`, `notification-bell`,
`officer-identity-card`, `officer-profile-form`, `auth-provider`,
`login-screen`, `brand-logo`, `confirm-dialog`, `full-screen-loader`, and
`ui/` (badge, button, card, dialog, input, label, select, table, textarea).

### 9.3 Behaviour worth knowing

- **Alert form** requires a photo, name, age, gender, clothing, contact, and
  last-seen place/date/time; `identifyingMarks` is optional. It captures
  `navigator.geolocation` at submit (undefined if blocked — the alert is filed
  anyway, just un-geofenced) and sanitises the upload filename.
- **Match review** shows the sighting crop beside the alert photo, flags
  `relayedBy`, suppresses the map link when `hasLocation` is false, and offers
  **Accept** / **Dismiss** (with a confirm step) / **Dispatch**. A reviewed match
  locks.
- **Notification bell** requests browser permission, registers
  `public/firebase-messaging-sw.js`, saves the token to `officers/{uid}.fcmToken`,
  and shows in-page toasts via `onMessage`.
- **Data API** (`lib/firestore.ts`): `uploadChildPhoto`, `createAlert`,
  `resolveAlert`, `dispatchMatch`, `acceptMatch`, `dismissMatch`,
  `subscribeActiveAlerts`, `subscribeAllAlerts`, `fetchAlert`,
  `subscribeMatches`, `fetchMatchCounts`. Officer API (`lib/officers.ts`):
  `subscribeOfficer`, `updateOfficerProfile`, `saveOfficerFcmToken`.
  `fetchMatchCounts()` runs 5 parallel `getCountFromServer` aggregates
  (total/pending/dispatched/accepted/dismissed) so the dashboard tiles and
  `match-status-chart` are exact counts, not scoped to `subscribeMatches`'s
  100-doc live window.
- **Pagination/filtering** (`matches-list`, `alert-history-list`,
  `active-alerts-list`): client-side over the already-subscribed live data —
  status filter, child-name search (search box only renders past a threshold
  count so it doesn't clutter short lists), and Prev/Next pagination. Live
  Matches additionally has a rows-per-page select (10/15/25/50).
- **Kiosk shell scroll**: `app/(kiosk)/layout.tsx` uses `h-dvh` +`min-h-0`
  through the flex chain so only `<main>` scrolls; `globals.css` sets
  `html, body { height:100%; overflow:hidden }` as a backstop so the document
  itself can never scroll (a missing `min-h-0` on a flex item is a classic way
  to lose this and get the whole page — sidebar included — scrolling instead).
- PWA: `public/manifest.webmanifest`, icons under `public/icons`,
  `src/app/icon.svg` + `apple-icon.png`, theme colour `#0E2A66`.

---

## 10. Configuration — mirrored values

Change these **together** or the two sides drift apart:

| Setting | Value | Lives in |
|---|---|---|
| Cosine threshold | 0.55 | `Constants.SIMILARITY_THRESHOLD` (device-side only) |
| Strong single-frame match | 0.72 | `Constants.STRONG_MATCH_THRESHOLD` |
| Confirm frames / fusion frames | 2 / 3 | `Constants` |
| Face input | 112×112 RGB, `(px−127.5)/127.5` | `FacePreprocessor` ↔ `embedding.ts` ↔ `face_align.py` |
| Alignment template | ArcFace 3-point | `FaceGeometry.TEMPLATE_112` ↔ `TEMPLATE` ↔ `ARCFACE_TEMPLATE` |
| Crop margin (fallback) | 0.2 | `Constants.FACE_CROP_MARGIN` ↔ `FACE_CROP_MARGIN` |
| Embedding width | read at runtime (128 or 512) | model output tensor; `SUPPORTED_EMBEDDING_SIZES` is a sanity bound |
| Alert expiry | 8 h | `Constants.ALERT_EXPIRY_MILLIS` ↔ `ALERT_TTL_MS` |
| Geofence radius | 2 km | `notify.ts` only |
| Stale-fix window | 6 h | `notify.ts` |
| Mesh TTL | 6 hops | `Constants.MESH_INITIAL_TTL` (mobile only) |
| Mesh seen-id TTL | = alert expiry | `Constants.MESH_SEEN_TTL_MILLIS` |
| Thumbnail | 96 px, q40, ≤ 8 KB | `Constants.MESH_THUMBNAIL_*` |
| Match ACK retry | 15 s × 3 | `MeshNetworkManager` |
| Max alert photo | 5 MB | `storage.rules` ↔ `MAX_IMAGE_BYTES` |

---

## 11. Requirement traceability (12 September 2026)

| ID | Requirement | Built | Verified |
|---|---|---|---|
| FR-01 | Officer files alert with photo + details | ✅ | ✅ real device + kiosk |
| FR-02 | Face embedding computed and stored | ✅ | ✅ (server fallback + on-device primary) |
| FR-03 | Geofenced push to volunteers | ✅ | ✅ end-to-end |
| FR-04 | Real-time on-device detection + frontality gate | ✅ | ✅ real device |
| FR-05 | On-device embedding (TFLite / LiteRT) | ✅ | ✅ |
| FR-06 | Cosine comparison vs all active alerts | ✅ | ✅ live match observed ~77% |
| FR-07 | Side-by-side confirmation (incl. offline thumbnail) | ✅ | ✅ |
| FR-08 | Volunteer confirms or dismisses | ✅ | ✅ |
| FR-09 | Confirmed match to kiosk with GPS | ✅ | ✅ |
| FR-10 | Multi-hop mesh relay when offline | ✅ | 🟡 unit-tested + 2 devices; **≥3-device field trial open** |
| FR-11 | Multiple simultaneous alerts | ✅ | 🟡 not load-tested at 50 |
| FR-12 | Auto-expiry after 8 h | ✅ | 🟡 sweep deployed, not timed end-to-end |
| FR-13 | Raspberry Pi gate node | ❌ placeholder | — (optional; descoped unless time allows) |
| FR-14 | Message id + TTL, relay at most once | ✅ | ✅ unit-tested |

| ID | NFR | State |
|---|---|---|
| NFR-01 | Detection accuracy ≥ 95% frontal | ❌ not measured |
| NFR-02 | Recognition ≥ 90% under festival lighting | 🟡 100% on 36 adult pairs; children / low light unproven |
| NFR-03 | Inference < 200 ms per face | ❌ not formally timed |
| NFR-04 | Alert delivery < 5 s (internet) | 🟡 observed fast, not measured |
| NFR-05 | Alert delivery < 30 s (mesh) | ❌ needs the ≥3-device trial |
| NFR-06 | 200+ faces/hour/volunteer | ❌ not measured |
| NFR-07 | 50 concurrent alerts | ❌ not measured |
| NFR-08 | Zero biometric upload | ✅ enforced in code; 🟡 no capture-based evidence write-up |
| NFR-09 | Camera only during an alert | ✅ implemented; ❌ battery not measured |
| NFR-10 | Android 5.0+ | ⚠️ project is `minSdk 24` — the synopsis is wrong |

### Open work

1. **≥3-device mesh field trial** (FR-10 / NFR-05) — the last functional gap.
2. **Measurement pass** for the NFRs above (`docs/plan/measurements.md` is the log).
3. **Raspberry Pi node** — optional, still a placeholder README.
4. Finish migrating mobile screens onto the new design system (§5.7).

---

## 12. Build and deploy

```bash
# Web kiosk
cd nextgen-rakshak-webportal && cp .env.local.example .env.local && npm install && npm run dev

# Cloud Functions + rules
cd functions && npm install
firebase deploy --only functions,firestore:rules,firestore:indexes,storage:rules

# Android (JDK 17, Gradle wrapper)
cd nextgen-rakshak-mobile
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Model first — matching does nothing without it (§8.1, `scripts/README.md`).
`google-services.json`, `.env*`, `local.properties` and keystores are gitignored
and must never be committed. A release APK needs `MESH_HMAC_KEY` in
`local.properties` (the build fails otherwise) and the release SHA-1 registered
in Firebase.
