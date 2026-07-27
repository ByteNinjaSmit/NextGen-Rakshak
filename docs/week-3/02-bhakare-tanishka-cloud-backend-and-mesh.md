# Week 3 — Track 2: Cloud Backend & Mesh Reliability

**Project:** NextGen Rakshak: Smart Edge-Based Lost Child Recovery System for Mass Gatherings
**Member:** Bhakare Tanishka Sharad
**Roll No.:** 11
**Week of:** `____________ to ____________`
**Continues:** Week 2 Track 2 (Interaction Flow & Communication Protocols)
**Objective supported:** Objective 3 (FCM + offline Nearby Connections mesh),
Objective 2 (server-side embedding, §6)

---

## 1. Scope of this track

Week 2 specified both distribution paths — the online FCM push and the
offline mesh relay — as protocols on paper. This week implements and fixes
the parts of those protocols that the interaction-flow design depended on:
geofenced push targeting, alert-expiry consistency between the mesh and the
cloud, mesh TTL/hop-count routing, and a defect that had silently disabled
the offline path entirely.

This track also took the **server half of the face-recognition pipeline** (§6).
The model work was split four ways this week — Track 1 produced the weights and
the conversion scripts, Track 3 verified that the quantized model matched its
source, Track 4 measured recognition accuracy, and this track integrated the
model into the Cloud Function that computes an alert's embedding.

---

## 2. Cloud backend — online path

| Requirement | Work done |
|---|---|
| FR-03 — geofenced alerts | Push is now filtered by haversine distance to a 2 km radius (`functions/src/notify.ts`). Fails **open**: a volunteer with no known location is still notified, because a missed nearby helper costs a child while a spurious notification costs one buzz. Mobile side: `FirestoreVolunteerSource.updateLocation` writes a `lastLocation` GeoPoint, `VolunteerRepository.publishLocation()` publishes it, `HomeViewModel` calls it on load. Web side: the kiosk alert form captures the browser's GPS at submit time so `createAlert` writes `geoLocation` |
| FR-12 — alert expiry | Reconciled to 8 hours across the Cloud Function, the mesh, and the synopsis — the server previously had 2 h, which would have expired alerts server-side while the mesh still carried them |
| FR-14 — mesh routing | Added the TTL/hop-count the Week 2 protocol specified: packets carry a hop count decremented at each relay and dropped at zero, plus an expiry check so a packet tied to an already-expired alert is not relayed further |

---

## 3. Defect found: the offline mesh was silently carrying nothing

The most serious bug of the week was in this track's own protocol.

Alert timestamps were read from Firestore as epoch **seconds** while every
mesh consumer treated them as **milliseconds**. The resulting age of every
alert computed to roughly 55 years, so the expiry check rejected *everything*:
broadcasting bailed out immediately (`isExpired()` returned true before a
packet ever left the phone) and every received packet was dropped on arrival.
The offline path — the core contribution the Week 2 interaction-flow design
promised over connectivity-dependent systems like ReUnite — did nothing, with
no error to reveal it.

Fixed by pinning the unit on the model (`Alert.timestamp` documented and
converted consistently), and pinned by a regression test so the unit cannot
silently drift again.

This closes synopsis §5.1.4 and FR-14, both owned by this track's Week 2
mesh-routing protocol specification.

---

## 4. Server-side embedding — integrating the model into the Cloud Function

`onAlertCreated` must turn the officer's uploaded photograph into the 128-d
embedding that every volunteer device matches against. Track 1 supplied the
model; this track made the server run it.

### 4.1 Architecture change — load the SavedModel directly

The Week 2 design had the Cloud Function load a TensorFlow.js `GraphModel`
converted from the same weights. That was changed during implementation:
`tfjs-node` can load a TensorFlow SavedModel directly, so the server now runs
**the identical graph** that was quantized for the device, eliminating a second
conversion that could drift from the first.

The change also removed a hard blocker for the team. `tensorflowjs_converter`
cannot run on Windows at all — it imports `tensorflow_decision_forests`, which
has no Windows build — so the original design could not have been reproduced on
three of the four team machines. The SavedModel route sets up cleanly everywhere.

This supersedes the server-model row of the Week 2 Track 2 protocol table.

### 4.2 Defect: device and server framed faces differently

The server detects faces with **BlazeFace**; the device detects with **ML Kit**.
Both sides were squashing a raw detector box into the square 112×112 model
input, and the two detectors frame faces differently — so the same child
produced differently framed inputs on the two sides, and non-square boxes were
additionally distorted by an aspect-dependent amount. Measured against a fixed
threshold, this systematically depressed true matches: the server's stored
embedding and the device's live embedding were describing differently cropped
faces.

Both sides now crop a **square centred on the detector box with a 0.2 margin**.
The margin is a shared constant — `FACE_CROP_MARGIN` in `functions/src/embedding.ts`
and `Constants.FACE_CROP_MARGIN` on the device — and each is documented as
requiring the other to change with it. The device half of this fix is recorded in
Track 1 §4.

---

## 5. Deliverables

- [x] FR-03 geofence filter implemented server-side, fail-open, wired end to
      end from browser GPS (kiosk) and device GPS (volunteer) through to the
      push
- [x] FR-12 alert-expiry constant reconciled across mesh, Cloud Function, and
      synopsis
- [x] FR-14 mesh TTL/hop-count routing implemented per the Week 2 protocol spec
- [x] Critical timestamp-unit defect found and fixed, with a regression test
- [x] FR-02 server-side embedding: Cloud Function switched from a tfjs
      `GraphModel` to a direct SavedModel load, running the same graph as the
      device
- [x] Server-side crop geometry aligned with the device on a shared 0.2 margin

## 6. Remaining / handover

- **Server-side inference is not yet verified at runtime.** The `tfjs-node`
  native binding will not load on the development machine (local Node 22 vs the
  Node 20 that Cloud Functions pins). The code typechecks; the first deploy must
  confirm that `onAlertCreated` logs `dims: 128`. This is the single largest
  unverified claim in the project — until it runs, no alert has an embedding and
  nothing can match. Owned here because this track owns the Cloud Function.
- **Multi-device mesh trial not yet run.** TTL/hop-count and duplicate
  suppression are implemented and unit-tested but have not been exercised
  across three or more physical devices with no internet — the scenario the
  Week 2 protocol was designed for.
- **`onPayloadTransferUpdate` in `MeshNetworkManager` is currently a no-op** —
  a failed payload transfer between two devices is not retried or logged, so a
  relay can silently fail mid-hop. Follows directly from this track's mesh
  ownership.
- **The mesh carries `imageUrl`, not image bytes.** With no internet — the exact
  case the mesh exists for — a receiving device cannot fetch the child's photo,
  so the side-by-side match dialog has nothing to show. Synopsis §5.1.4 allows an
  optional low-resolution thumbnail in the payload; it is not implemented.
