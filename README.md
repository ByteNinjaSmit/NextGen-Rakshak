# NextGen Rakshak: Smart Edge-Based Lost Child Recovery System for Mass Gatherings

Hybrid edge-AI system for rapidly locating missing children at mass gatherings
(festivals, fairs, religious congregations) during the **"golden hour"** — the
first 60–90 minutes after a child goes missing, when cellular networks are most
congested and existing government portals are least useful.

**Privacy by design:** all face matching happens on-device. No image, video
frame, or biometric fingerprint of a bystander ever leaves the phone.

> Final-year capstone project · Group ID 6

**Full technical documentation of the system as built:**
[`docs/SYSTEM-REFERENCE.md`](docs/SYSTEM-REFERENCE.md).

---

## Repository layout

```
/
├── nextgen-rakshak-webportal/   # Next.js 14 police kiosk portal
├── nextgen-rakshak-mobile/      # Kotlin + Compose volunteer Android app
├── nextgen-rakshak-raspberry/   # Optional Pi fixed-camera node (placeholder)
├── functions/                   # Firebase Cloud Functions (embedding, FCM, expiry, roles)
├── scripts/                     # Model conversion, alignment, parity, evaluation, brand assets
├── docs/                        # System reference, synopsis, weekly reports, plan, progress decks
├── brand/ · Logo/ · UI_Images/  # Brand source + screenshots
├── firebase.json                # Firebase CLI config — must stay at repo root
├── .firebaserc                  #   ”
├── firestore.rules              #   ”
├── firestore.indexes.json       #   ”
└── storage.rules                #   ”
```

## Architecture

Three layers:

- **Cloud (Firebase)** — case management, alert distribution, a server-side
  embedding fallback, scheduled expiry, and biometric purge on resolve.
- **Edge (volunteer phones / Pi)** — ML Kit face detection + 3-point alignment +
  MobileFaceNet (TFLite/LiteRT) embeddings + cosine similarity. All recognition
  happens here.
- **Mesh (Nearby Connections)** — BLE discovery + Wi-Fi Direct transfer, with a
  custom multi-hop store-and-forward routing layer (UUID message id, TTL/hop
  count, HMAC authentication, duplicate suppression, gateway-aware match routing)
  so alerts keep flowing with zero internet.

### Flow

1. Officer files an alert at the kiosk (photo + details + the browser's GPS fix).
2. A Cloud Function computes a fallback face embedding and pushes the alert by
   FCM to volunteers within 2 km; the same alert also floods the offline mesh
   with a ≤8 KB face thumbnail attached.
3. The volunteer's phone re-embeds the alert photo **on-device** so both sides of
   every comparison come from one implementation, then scans the crowd.
4. Above threshold the app shows the live face beside the parent's photo and the
   volunteer **visually confirms** — the system never autonomously declares a
   child found.
5. The confirmed sighting (face crop + GPS + timestamp) reaches the kiosk, over
   the internet if there is any and over the mesh if there is not.
6. The officer reviews the two photos side by side and accepts, dismisses, or
   dispatches. Resolving the case deletes the photo and clears the embedding.

## Setup

### Prerequisites
Node 18+ (Functions run on Node 22), JDK 17, Android Studio, a Firebase project,
and Python 3.10–3.12 (for the model scripts only).

### 1. Supply the ML model — required

Face matching does nothing until MobileFaceNet weights are present. Weights are
**not committed** (size + licensing). Both artifacts must come from the *same*
weights in the *same* run, or embeddings won't line up:

```bash
pip install "tensorflow>=2.14,<2.16"
python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel --precision dynamic
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
```

See [`scripts/README.md`](scripts/README.md) for model sources, the I/O contract,
and how to re-measure the threshold.

### 2. Web portal

```bash
cd nextgen-rakshak-webportal
cp .env.local.example .env.local   # fill in your Firebase web config
npm install && npm run dev
```

See [`nextgen-rakshak-webportal/README.md`](nextgen-rakshak-webportal/README.md).

### 3. Cloud Functions

```bash
cd functions && npm install
firebase deploy --only functions,firestore:rules,firestore:indexes,storage:rules
```

#### Officer authorisation — how it works now

Signing in with Google proves identity, not authority. Alert writes are gated on
a `police` custom claim that the `claimOfficerRole` Cloud Function grants.

Registration is **self-service**: the first Google sign-in on the kiosk grants
the claim and creates the officer's `officers/{uid}` record. There is no
allow-list and no approval step. The one refusal is an account that already has a
`volunteers/{uid}` document — one account, one role; such an account is refused
and any previously granted claim is revoked.

> The old `allowedOfficers` collection is **legacy and unused**. It is denied to
> every client and read by nothing. Do not seed it.

### 4. Android app

Drop your `google-services.json` into `nextgen-rakshak-mobile/app/`, then either
open the project in Android Studio, or build from the command line with the
bundled Gradle wrapper (requires **JDK 17**):

> **Enable Google sign-in first.** It is the app's only sign-in route
> (email/password and anonymous guest were both removed). Firebase console →
> **Authentication → Sign-in method → Google**, plus an OAuth client, which only
> exists once your signing fingerprint is registered:
>
> 1. Get the debug SHA-1: `cd nextgen-rakshak-mobile && ./gradlew signingReport`
> 2. Firebase console → Project settings → Your apps → Android → **Add fingerprint**
> 3. Enable **Authentication → Sign-in method → Google**
> 4. Re-download `google-services.json` and replace the existing one
>
> Repeat steps 1–2 with the release SHA-1 before distributing a release build.

```bash
cd nextgen-rakshak-mobile
./gradlew :app:testDebugUnitTest   # compile + run unit tests
./gradlew :app:assembleDebug       # build the APK
```

A **release** build additionally requires `MESH_HMAC_KEY` in `local.properties` —
the build fails rather than ship the well-known dev key, which would make mesh
packet authentication worthless.

See [`nextgen-rakshak-mobile/README.md`](nextgen-rakshak-mobile/README.md).

## Configuration

| Setting | Value | Where |
|---------|-------|-------|
| Face-match threshold | cosine > 0.55 (measured — see [`scripts/README.md`](scripts/README.md)) | `Constants.SIMILARITY_THRESHOLD` |
| Strong single-frame match | cosine ≥ 0.72 | `Constants.STRONG_MATCH_THRESHOLD` |
| Mid-band confirmation | 2 consecutive frames of one track | `Constants.MATCH_CONFIRM_FRAMES` |
| Multi-frame fusion | 3 embeddings averaged per track | `Constants.EMBEDDING_FUSION_FRAMES` |
| Embedding size | read from the model at runtime (128-d ships, 512-d supported) | model output tensor |
| Face input | 112×112 RGB, `(px-127.5)/127.5`, 3-point ArcFace alignment | app + `functions/src/embedding.ts` + `scripts/face_align.py` |
| Alert expiry | 8 hours | `Constants.ALERT_EXPIRY_MILLIS`, `ALERT_TTL_MS` in `functions/src/index.ts` |
| Geofence radius | 2 km (fail-open, 6 h stale-fix window) | `functions/src/notify.ts` |
| Mesh initial TTL | 6 hops | `Constants.MESH_INITIAL_TTL` |

## Security

Never commit: `.env*`, `google-services.json`, `local.properties`, keystores, or
service-account JSON. All are gitignored — verify with `git status` before your
first push.

**Authorisation model.** Authentication and authorisation are separate:

| Principal | How they sign in | What they may write |
|-----------|------------------|---------------------|
| Officer (kiosk) | Google → `claimOfficerRole` grants the `police` claim | Create/resolve alerts; update match status only |
| Volunteer (app) | Google | Their own `volunteers/{uid}` doc; create matches |
| Anyone signed in | — | Nothing else; no client may delete anything |

Firestore rules enforce this server-side on the `police` custom claim, so the
kiosk UI check is a convenience, not the boundary. A match document must name its
author (its own uid, or a mesh relay stamping `relayedBy`), must reference an
alert that exists, and carries the server's own timestamp.

## Team

| Roll No. | Name |
|----------|------|
| 09 | Bankar Smitraj Dinkar |
| 11 | Bhakare Tanishka Sharad |
| 34 | Dhadge Vedant Sanjay |
| 94 | Narkhede Atharva Anantkumar |

Guide: Dr. A. B. Pawar · Coordinator: Dr. S. R. Deshmukh · HOD: Dr. M. A. Jawale
