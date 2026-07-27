# Status Audit — Everything Built, Everything Missing

**Audited:** 27 July 2026, against commit `312a868`
**Method:** every source file in all four components was read or listed, the
Firebase configuration was checked, the synopsis requirement tables (FR-01…14,
NFR-01…10) were extracted from the `.docx`, and every "Remaining / handover"
section in the Week 2 and Week 3 documents was carried forward.

Legend: ✅ done and evidenced · 🟡 done but unproven · ❌ not done

---

## 1. Police Kiosk Portal — `nextgen-rakshak-webportal/`

### Built

| Area | Files | Status |
|---|---|---|
| Next.js 14 App Router scaffold, Tailwind, path alias `@/*` | `src/app/layout.tsx`, `globals.css` | ✅ |
| shadcn/ui primitives (badge, button, card, input, label, select, table, textarea) | `src/components/ui/*` | ✅ |
| Google sign-in + session handling | `src/lib/auth.ts`, `components/auth-provider.tsx`, `login-screen.tsx` | ✅ |
| `police` custom-claim authorisation gate, re-checked on every auth state change | `components/auth-gate.tsx` | 🟡 code correct, never exercised — no officer allow-listed |
| Dashboard: active-alert count, match count, quick actions, sidebar nav | `app/page.tsx`, `components/stats-cards.tsx`, `sidebar-nav.tsx` | ✅ |
| New Alert form: photo upload, name, age, gender, clothing, parent contact, last seen, **browser GPS captured at submit** | `app/alerts/new/page.tsx`, `components/alert-form.tsx` | ✅ |
| Active alerts list with one-tap **Resolve** | `components/active-alerts-list.tsx` | ✅ |
| Live matches feed (realtime `onSnapshot`), confidence, volunteer, status badge, **Dispatch → Google Maps** | `app/matches/page.tsx`, `components/matches-list.tsx` | ✅ |
| Realtime hooks + Firestore access layer | `hooks/use-alerts.ts`, `lib/firestore.ts`, `lib/firebase.ts` | ✅ |
| TypeScript typecheck | `tsc --noEmit` clean | ✅ |

### Missing

- ❌ **Never deployed.** `firebase.json` has `firestore`, `storage` and
  `functions` blocks but **no `hosting` block**, and there is no Vercel config.
  The kiosk has only ever run on `npm run dev`. → task **DEP-04**
- ❌ **Dashboard map placeholder** for recent match locations, specified as MVP
  in synopsis §6.1.2. The dashboard shows counts only. → **GAP-04**
- ❌ **Map pin on match**, specified in synopsis §7 step 8 ("dropping a map pin").
  Currently only a Google Maps hyperlink. → **GAP-11**
- ❌ **No tests of any kind** — no unit tests, no smoke test, no lint in CI. → **QA-02**
- ❌ No error boundary / offline banner if Firestore is unreachable at the kiosk.
  → **GAP-13**

---

## 2. Cloud Functions — `functions/`

### Built

| Function | Purpose | Status |
|---|---|---|
| `onAlertCreated` | Computes the 128-d embedding server-side, writes it back, then broadcasts | 🟡 typechecks; **never executed** |
| `computeEmbedding` | BlazeFace detect → square crop, margin 0.2 → SavedModel via `tfjs-node` | 🟡 crop margin verified to match `Constants.FACE_CROP_MARGIN`; runtime unproven |
| `broadcastAlert` | FCM push, **haversine geofence to 2 km**, fails open for volunteers with no known location | 🟡 |
| `claimOfficerRole` | Grants the `police` custom claim only to emails in `allowedOfficers` | 🟡 |
| `onAlertResolved` | On `active → resolved`: deletes the child's photo from Storage and clears the embedding (NFR-08) | 🟡 |
| `expireAlerts` | Scheduled every 30 min; resolves alerts older than the 8 h TTL | 🟡 |
| `computeEmbeddingCallable` | Manual re-index fallback, auth required | 🟡 |
| Constants reconciled | `ALERT_TTL_MS` 8 h == `Constants.ALERT_EXPIRY_MILLIS` | ✅ |
| TypeScript typecheck | `tsc --noEmit` clean | ✅ |

### Missing

- ❌ **Never deployed.** This is the single largest blocker in the project —
  every 🟡 above collapses to ✅ or a bug the moment `firebase deploy` runs.
  → **DEP-01**
- ❌ **`tfjs-node` native binding unverified.** It will not load on the dev
  machine (local Node 22 vs the Node 20 that Cloud Functions pins). Deployment is
  the only way to find out whether it loads in the Functions runtime.
  → **DEP-02**
- ❌ **SavedModel deployment path unverified.** `functions/model/savedmodel/` is
  gitignored and must be present in the deploy bundle. Nobody has confirmed the
  bundle size limit is respected or that the model is actually shipped.
  → **DEP-03**
- ❌ No function unit tests, no emulator run. → **QA-05**

---

## 3. Volunteer Android App — `nextgen-rakshak-mobile/`

### Built

| Layer | What exists | Status |
|---|---|---|
| **Architecture** | `data / domain / ml / networking / presentation`, manual DI via `ServiceLocator` | ✅ |
| **Auth** | Google (Credential Manager), email+password with human-readable error mapping, anonymous guest; role chosen before sign-in; profile written to `volunteers/{uid}` | 🟡 Google verified on device; **email/password and guest never run** |
| **Sign-out** | Race fixed — navigation reacts to profile becoming null | ✅ |
| **Stale session** | Stored profile discarded when the Firebase session is gone | ✅ |
| **Home** | Active alerts, "12 min ago" elapsed label refreshed every 30 s, Start Scanning gated on ≥1 alert | ✅ |
| **Scan** | CameraX, ML Kit detection, **yaw ≤30° / roll ≤40° frontal filter**, TFLite singleton (4 threads, serialised), cosine similarity vs all active alerts, "Scanning for …" overlay | 🟡 built; never run on physical hardware |
| **Match confirmation** | Side-by-side parent photo vs live capture, captioned; double-pulse haptic; Confirm / Not a match | 🟡 |
| **Match reporting** | Firestore write, **Room queue when offline + WorkManager sync**, mesh relay | 🟡 offline queue never exercised |
| **Mesh** | Nearby Connections P2P_CLUSTER, advertise+discover, alert and match packet types, **TTL 6 decremented per hop**, seen-ID duplicate suppression, expiry drop | 🟡 **never run across two devices** |
| **FCM** | Token provider, messaging service, notification channel | 🟡 |
| **Location** | `LocationProvider`, `publishLocation()` writes `lastLocation` GeoPoint for the geofence | 🟡 |
| **Permissions** | Rationale dialog before every system prompt, explaining on-device matching | ✅ |
| **Layout** | `safeDrawing` insets — heading clear of status bar and camera cutout | ✅ |
| **Crash resilience** | Firestore listener failure logs and emits empty instead of killing the process | ✅ |
| **Build** | Gradle wrapper committed, `assembleDebug` green, **13 unit tests pass** | ✅ |
| **16 KB page size** | LiteRT 2.1.6, datastore 1.1.7, CameraX 1.4.2; all 64-bit `.so` at `p_align 16384`, read from the APK | ✅ |
| **Toolchain** | Kotlin 2.3.21, AGP 8.13.2, KSP 2.3.10, compileSdk 35, Room 2.8.4 | ✅ |

### Missing

- ❌ **Never run on a physical phone.** One emulator profile only. Inference time
  (NFR-03 <200 ms) has no measurement. → **VER-01**, **VER-02**
- ❌ **`onPayloadTransferUpdate` is a no-op** (`MeshNetworkManager.kt:133`). A
  failed payload transfer between two devices is neither retried nor logged, so a
  relay can silently fail mid-hop — the same failure class as the timestamp bug
  found in Week 3. → **GAP-01**
- ❌ **Mesh payloads are not signed.** Synopsis §5.1.4 promises "alert payloads
  are additionally signed so a relaying device cannot tamper with alert content".
  `MeshPayloadCodec` writes plain fields with no MAC. Either implement or
  formally descope in the report. → **GAP-02**
- ❌ **Notification tap does not open the scan screen.** Synopsis §7 step 5 says
  "tapping it opens the app directly into the camera scan screen";
  `NotificationHelper` launches `MainActivity` with no deep-link extra.
  → **GAP-03**
- ❌ **No photo travels over the mesh.** The codec sends `imageUrl`, not image
  bytes. With no internet — the exact scenario the mesh exists for — the
  side-by-side match dialog cannot render the parent's photo, and FR-07 degrades
  to a name and a score. Synopsis §5.1.4 explicitly allows "an optional
  low-resolution thumbnail". → **GAP-06** (high impact on the demo)
- ❌ No instrumentation/Compose UI tests. → **QA-03**
- ❌ No release build config — no signing config, no R8 shrink, no release APK.
  → **QA-08**
- ❌ Only three test classes (`CosineEmbeddingComparatorTest`,
  `MeshPayloadCodecTest`, `ElapsedTimeTest`). Crop geometry, TTL/relay logic and
  the alert-merge path are untested. → **QA-04**

---

## 4. Face Recognition Pipeline — `scripts/`

Built in Week 3, split across all four tracks: conversion scripts and weights
(Smitraj 09), server-side integration (Tanishka 11), parity verification
(Vedant 34), accuracy measurement and the threshold (Atharva 94).

### Built ✅

- `freeze_to_savedmodel.py` → `convert_models.py` → `verify_parity.py`,
  reproducible from the Apache-2.0 `sirius-ai/MobileFaceNet_TF` weights.
- Quantisation 5.9 MB → **1.5 MB**; parity against the source SavedModel
  **cosine 0.99967**.
- Device and server crop geometry unified (square crop, margin 0.2).
- **Measured accuracy:** 9 photos, 4 people, 36 pairs — same-person
  0.7142–0.9899, different-person 0.0864–0.3551, **gap 0.3591**.
- **Threshold revised on evidence 0.75 → 0.55**; 0 false matches / 21,
  0 missed / 15.

### Missing

- ❌ **Measured on adult faces from a public sample, not children, and not under
  festival lighting.** NFR-02 (≥90% recognition accuracy on frontal faces under
  festival lighting) is the project's headline KPI and is currently **unproven**.
  This is the most academically exposed gap in the project. → **VER-05**
- ❌ NFR-01 (detection accuracy ≥95% on frontal faces) has no measurement at all.
  → **VER-06**
- ❌ No measurement of degradation with distance, motion blur, or low light —
  all of which a festival guarantees. → **VER-07**

---

## 5. Firebase Configuration — repo root

| Item | Status |
|---|---|
| `firestore.rules` — alert writes gated on the `police` claim; `allowedOfficers` unreadable by any client; no client delete | 🟡 written, **never deployed, never tested** |
| `storage.rules` | 🟡 same |
| `firestore.indexes.json` | 🟡 same |
| `firebase.json` — firestore, storage, functions | 🟡 **no hosting block** |
| `allowedOfficers/{email}` document | ❌ **does not exist — the kiosk is currently unusable by anyone** |
| Auth providers: Email/Password, Anonymous | ❌ not enabled in console |
| Release SHA-1 registered | ❌ debug only |

---

## 6. Raspberry Pi Node — `nextgen-rakshak-raspberry/`

**Status: ❌ README placeholder only. Zero code.**

FR-13 is marked *Low (Optional)* in the synopsis and the build order puts it
last. A decision is required in Week 4 — see **GAP-07**. Recommendation:
**descope it formally and document why**, rather than ship a half-node in
Week 7. It buys one bullet point and costs the week that the report needs.

---

## 7. Requirement Coverage

### Functional requirements

| ID | Requirement | Code | Verified | Gap |
|---|---|---|---|---|
| FR-01 | Officer creates alert with photo + details | ✅ | ❌ | needs deploy |
| FR-02 | System computes and stores face embedding | ✅ | ❌ | **DEP-01/02** |
| FR-03 | Geofenced push to volunteers | ✅ | ❌ | **VER-04** |
| FR-04 | Real-time face detection on device | ✅ | 🟡 emulator | **VER-01** |
| FR-05 | On-device embedding extraction (TFLite) | ✅ | 🟡 | **VER-02** |
| FR-06 | Cosine comparison vs all active alerts | ✅ | ✅ (36 pairs) | children untested |
| FR-07 | Side-by-side match display | ✅ | 🟡 | **GAP-06** offline photo |
| FR-08 | Volunteer confirms or dismisses | ✅ | 🟡 | device test |
| FR-09 | Confirmed match to kiosk with GPS | ✅ | ❌ | **VER-03** |
| FR-10 | Multi-hop mesh relay when offline | ✅ | ❌ | **VER-08** ← core contribution |
| FR-11 | Multiple simultaneous alerts, no degradation | ✅ | ❌ | **VER-09** |
| FR-12 | Auto-expiry after 8 h | ✅ | ❌ | **VER-11** |
| FR-13 | Raspberry Pi gate node | ❌ | ❌ | **GAP-07** decide |
| FR-14 | Message ID + TTL, relay at most once | ✅ | 🟡 unit-tested only | **VER-08** |

### Non-functional requirements

| ID | Target | Status |
|---|---|---|
| NFR-01 | Detection accuracy ≥95% frontal | ❌ never measured |
| NFR-02 | Recognition accuracy ≥90% under festival lighting | ❌ measured on adults, good lighting only |
| NFR-03 | Inference <200 ms per face | ❌ never measured on hardware |
| NFR-04 | Alert delivery <5 s (internet) | ❌ never measured |
| NFR-05 | Alert delivery <30 s (mesh) | ❌ never measured |
| NFR-06 | 200+ faces/hour/volunteer | ❌ never measured |
| NFR-07 | 50 concurrent alerts, no loss | ❌ never measured |
| NFR-08 | Zero biometric upload, on-device matching | 🟡 code enforces it; needs an evidence write-up |
| NFR-09 | Battery: camera only during an alert | 🟡 behaviour implemented; no measurement |
| NFR-10 | Android 5.0+ (API 21) | ⚠️ **project sets `minSdk 24` — synopsis is wrong** |

**Seven of ten NFRs have no number behind them.** For a final-year project this
is the difference between "we built it" and "we validated it", and Objective 8 is
explicitly about validation. Week 5 exists to fix this.

---

## 8. Synopsis Corrections Required

These are places where the delivered system deliberately and correctly differs
from the synopsis. Each must be corrected in the final report with the reason —
an unexplained mismatch reads as a defect at the viva; an explained one reads as
engineering judgement.

| # | Synopsis says | Reality | Why |
|---|---|---|---|
| 1 | Match threshold **0.75** | **0.55** | Measured: 0.75 missed 5 of 15 genuine pairs. Week 3 §2 |
| 2 | Android **5.0+ / API 21** | `minSdk 24` | CameraX + LiteRT + Credential Manager baseline |
| 3 | Kotlin 1.9, `tensorflow-lite:2.14` | Kotlin 2.3.21, LiteRT 2.1.6 | Android 15 16 KB page-size compliance |
| 4 | Server loads a **tfjs `GraphModel`** | Loads the **SavedModel** directly | One conversion instead of two; `tensorflowjs_converter` cannot run on Windows |
| 5 | **Kiosk device broadcasts** the alert over Nearby Connections (§7 step 4) | A browser cannot use Nearby Connections. Alerts enter the mesh from the first volunteer phone that receives them online, then flood | Platform constraint — must be stated explicitly, it changes the offline story |
| 6 | Volunteer registers with **phone number + OTP** | Google / email+password / guest | Anonymous sign-in made every sighting untraceable |
| 7 | Mesh payloads are **signed** | Not implemented | Decide in Week 6: implement HMAC or descope |
| 8 | Low-resolution **thumbnail** may travel the mesh | Only `imageUrl` travels | See **GAP-06** |

---

## 9. Documentation Status

| Item | Status |
|---|---|
| Synopsis (`.docx`) | ✅ complete, 2 figures, needs the §8 corrections above |
| Week 1 report | ❌ **absent from `docs/`** — research exists inside the synopsis §4 only |
| Week 2 (4 tracks + README) | ✅ complete — **date range still blank** |
| Week 3 (4 tracks + README + 18 screenshots) | ✅ complete — **date range blank; 4 member files are untracked in git** |
| Weeks 4–8 reports | ❌ not started (5 weeks × 5 files = 25 documents) |
| Final project report | ❌ not started |
| Test plan + test case document | ❌ not started |
| Results and analysis chapter | ❌ not started |
| IEEE-format paper | ❌ not started (check whether your college requires it) |
| User manual / deployment guide | ❌ partially covered by the root README |
| Demo video | ❌ not started |
| Final presentation slides | ❌ not started |

---

## 10. Engineering Hygiene

| Item | Status |
|---|---|
| CI (build + test on push) | ❌ none |
| Branching workflow | ⚠️ all commits straight to `main` |
| Secrets hygiene | ✅ `.env*`, `google-services.json`, keystores, model weights all gitignored |
| Uncommitted work | ⚠️ 4 Week-3 member documents untracked |
| Crash reporting (Crashlytics) | ❌ none |
| Dependency licences recorded | 🟡 MobileFaceNet Apache-2.0 noted; no full list |
