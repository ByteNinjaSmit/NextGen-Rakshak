# NextGen Rakshak: Smart Edge-Based Lost Child Recovery System for Mass Gatherings

Hybrid edge-AI system for rapidly locating missing children at mass gatherings
(festivals, fairs, religious congregations) during the **"golden hour"** — the
first 60–90 minutes after a child goes missing, when cellular networks are most
congested and existing government portals are least useful.

**Privacy by design:** all face matching happens on-device. No image, video
frame, or biometric fingerprint of a bystander ever leaves the phone.

> Final-year capstone project · Group ID 6

---

## Repository layout

```
/
├── nextgen-rakshak-webportal/   # Next.js 14 police kiosk portal
├── nextgen-rakshak-mobile/      # Kotlin + Compose volunteer Android app
├── nextgen-rakshak-raspberry/   # Optional Pi fixed-camera node (placeholder)
├── functions/                   # Firebase Cloud Functions (embedding, FCM, expiry)
├── scripts/                     # MobileFaceNet model conversion + parity check
├── docs/                        # Project synopsis
├── firebase.json                # Firebase CLI config — must stay at repo root
├── .firebaserc                  #   ”
├── firestore.rules              #   ”
├── firestore.indexes.json       #   ”
└── storage.rules                #   ”
```

## Architecture

Three layers:

- **Cloud (Firebase)** — case management, alert distribution, serverless
  embedding computation, scheduled expiry.
- **Edge (volunteer phones / Pi)** — ML Kit face detection + MobileFaceNet
  (TFLite) embeddings + cosine similarity. All recognition happens here.
- **Mesh (Nearby Connections)** — BLE discovery + Wi-Fi Direct transfer, with a
  custom multi-hop store-and-forward routing layer (message ID, TTL/hop-count,
  duplicate suppression) so alerts keep flowing with zero internet.

### Flow

1. Officer files an alert at the kiosk (photo + details).
2. A Cloud Function computes the child's 128-d face embedding.
3. Alert pushes to volunteers via FCM (geofenced to ~2 km) **and** floods the
   offline mesh.
4. Volunteers scan the crowd; matching runs entirely on-device.
5. On a score above the threshold, the volunteer **visually confirms** — the
   system never autonomously declares a child found.
6. Confirmed match (GPS + timestamp) reaches the kiosk; officer dispatches.

## Setup

### Prerequisites
Node 18+, JDK 17, Android Studio, a Firebase project, and Python 3.10–3.12 (for
the model scripts only).

### 1. Supply the ML model — required

Face matching does nothing until MobileFaceNet weights are present. Weights are
**not committed** (size + licensing). Both artifacts must come from the *same*
weights or embeddings won't line up:

```bash
pip install "tensorflow>=2.14,<2.16" tensorflowjs
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
```

See [`scripts/README.md`](scripts/README.md) for model sources and the I/O contract.

### 2. Web portal

```bash
cd nextgen-rakshak-webportal
cp .env.local.example .env.local   # fill in your Firebase web config
npm install && npm run dev
```

### 3. Cloud Functions

```bash
cd functions && npm install
firebase deploy --only functions,firestore:rules,storage:rules
```

### 4. Android app

Drop your `google-services.json` into `nextgen-rakshak-mobile/app/`, then either
open the project in Android Studio, or build from the command line with the
bundled Gradle wrapper (Gradle 8.14, requires **JDK 17**):

> **Enable "Continue with Google" first.** Volunteer sign-in uses Google via
> Credential Manager, which needs an OAuth client that only exists once your
> app's signing fingerprint is registered:
>
> 1. Get the debug SHA-1: `cd nextgen-rakshak-mobile && ./gradlew signingReport`
> 2. Firebase console → Project settings → Your apps → Android → **Add fingerprint**
> 3. Enable **Authentication → Sign-in method → Google**
> 4. Re-download `google-services.json` and replace the existing one
>
> Until then the button reports that sign-in is not configured, and the demo
> (anonymous) fallback on the login screen still works. Repeat step 1–2 with the
> release SHA-1 before distributing a release build.

```bash
cd nextgen-rakshak-mobile
./gradlew :app:testDebugUnitTest   # compile + run unit tests
./gradlew :app:assembleDebug       # build the APK
```

## Configuration

| Setting | Value | Where |
|---------|-------|-------|
| Face-match threshold | cosine > 0.55 (measured — see [`scripts/README.md`](scripts/README.md)) | `Constants.SIMILARITY_THRESHOLD` |
| Embedding size | 128-d | `Constants.EMBEDDING_SIZE` |
| Face input | 112×112 RGB, `(px-127.5)/127.5` | app + `functions/src/embedding.ts` |
| Alert expiry | 8 hours | `Constants.ALERT_EXPIRY_MILLIS`, `functions/src/index.ts` |
| Geofence radius | 2 km | `functions/src/notify.ts` |
| Mesh initial TTL | 6 hops | `Constants.MESH_INITIAL_TTL` |

## Security

Never commit: `.env*`, `google-services.json`, `local.properties`, keystores, or
service-account JSON. All are gitignored — verify with `git status` before your
first push.

## Team

| Roll No. | Name |
|----------|------|
| 09 | Bankar Smitraj Dinkar |
| 11 | Bhakare Tanishka Sharad |
| 34 | Dhadge Vedant Sanjay |
| 94 | Narkhede Atharva Anantkumar |

Guide: Dr. A. B. Pawar · Coordinator: Dr. S. R. Deshmukh · HOD: Dr. M. A. Jawale
