# NextGen Rakshak — Cloud Functions

Firebase Cloud Functions v2 on **Node 22** (`firebase-functions` ^7.3.2,
`firebase-admin` ^12). Region `us-central1`, 1 GiB memory, 120 s timeout —
`tfjs-node` needs the headroom; a warm embedding takes ~1–3 s.

```
src/
├── index.ts      triggers + callables + the expiry sweep + the biometric purge
├── embedding.ts  BlazeFace detect → 3-point align → SavedModel embed
├── notify.ts     geofenced alert broadcast + match push to the filing officer
└── officers.ts   claimOfficerRole — grants the `police` claim, upserts officers/{uid}
model/
└── savedmodel/   the TF SavedModel (NOT committed — see model/README.md)
```

## Exports

| Export | Trigger | Does |
|---|---|---|
| `onAlertCreated` | create `alerts/{id}` | computes the fallback embedding, then broadcasts the alert (always, even if the embedding failed — humans can still look) |
| `onMatchCreated` | create `matches/{id}` | reads `alerts/{alertId}.createdBy.uid` and pushes the sighting to that officer |
| `onAlertResolved` | update `alerts/{id}`, `active → resolved` | purges the embedding and the photo, and blanks the copied `imageUrl` on every match for that alert |
| `expireAlerts` | schedule, every 30 min | flips `active` alerts older than `ALERT_TTL_MS` (8 h) to `resolved`, which routes into the purge above |
| `computeEmbeddingCallable` | callable | re-index one alert on demand — **officers only** |
| `claimOfficerRole` | callable | grants the `police` custom claim and creates/refreshes `officers/{uid}` |

## Embedding (`embedding.ts`)

Mirrors the Android pipeline exactly, or the scores are meaningless:

| | value |
|---|---|
| Input | `[1,112,112,3]` RGB, `(px − 127.5) / 127.5` |
| Output | 128-d (shipping MobileFaceNet) or 512-d (ArcFace) — read from the graph, never assumed |
| Alignment | 3-point similarity warp (left eye, right eye, nose) onto the ArcFace template — must equal `FaceGeometry.TEMPLATE_112` and `ARCFACE_TEMPLATE` in `scripts/face_align.py` |
| Fallback | centred square crop, `FACE_CROP_MARGIN = 0.2`, then the whole image |

BlazeFace picks the largest face. The model is loaded with
`tf.node.loadSavedModel` from `functions/model/savedmodel/` — the *same graph*
`scripts/convert_models.py` quantised into the Android `.tflite`, which is what
makes device and server embeddings comparable.

> **The device is the primary path.** Since the geometry-drift incident, the
> mobile app re-embeds alert photos on-device (`ScanViewModel.prepare`); this
> function's embedding is the fallback for a photo the phone cannot fetch.
> Redeploy after **any** change to `embedding.ts` so that fallback stays
> comparable, and see `scripts/README.md` §"Geometry must match on both sides".

**SSRF guard.** The image is never fetched from the caller-supplied URL. Only an
object path is parsed out of it, it must start with `alert_images/`, it must be
≤ 5 MB (matching `storage.rules`), and it is read through the Admin SDK from the
project's own bucket. A Functions runtime that fetches arbitrary URLs can be
pointed at `169.254.169.254` for service-account tokens — so there is no host to
redirect and nothing to forge.

## Notifications (`notify.ts`)

**Alert broadcast** — haversine filter at `GEOFENCE_RADIUS_KM = 2` over
`volunteers/*.fcmToken`. **Fail-open**: a volunteer is excluded only when their
`lastLocation` is valid *and* fresher than `STALE_LOCATION_MS` (6 h) *and* beyond
the radius; a missing, malformed or stale fix is notified anyway. Multicast in
batches of 500; permanently invalid tokens are blanked.

Messages are **data-only**. A payload with a `notification` block is rendered by
the OS when the app is backgrounded and `onMessageReceived` never runs — the
volunteer would get a default notification with no high-importance channel and no
deep link to the scan screen.

**Match push** — reads `officers/{uid}.fcmToken`, saved by the kiosk's
notification bell; absent means the officer has not opted in, and the push is a
silent no-op. The link is sent in `data.link` *and* `webpush.fcmOptions.link`,
because the kiosk's service worker displays the notification itself and
`fcmOptions.link` is therefore never applied.

## Officer roles (`officers.ts`)

`claimOfficerRole` is **self-service**: any authenticated Google account gets the
`police` claim. There is no allow-list (`allowedOfficers` is legacy and read by
nothing) and no approval step.

The one refusal: an account with a `volunteers/{uid}` document. Police and
volunteer are mutually exclusive because both apps share one Auth pool and claims
live on the *user*, not the app — so such an account is refused, and a claim
granted before this check existed is revoked along with its refresh tokens.
`firestore.rules` mirrors this from the other side by denying `volunteers` writes
from a `police` account. Keep both halves in step.

The client must call `getIdToken(true)` afterwards for the claim to take effect.

## Batching

Firestore caps a batch at 500 writes. Both the expiry sweep and the purge chunk
their updates: an unswept backlog (a busy event, or a paused schedule) would
otherwise overflow, throw, and leave *every* stale alert live instead of just the
excess.

## Develop and deploy

```bash
npm install
npm run build           # tsc
npm run serve           # build + functions emulator
npm run deploy          # firebase deploy --only functions
npm run logs
```

Deploy rules alongside the code when they change:

```bash
firebase deploy --only functions,firestore:rules,firestore:indexes,storage:rules
```

The model must be present at `model/savedmodel/` first — see
[`model/README.md`](model/README.md).

---

Full system documentation: [`../docs/SYSTEM-REFERENCE.md`](../docs/SYSTEM-REFERENCE.md).
