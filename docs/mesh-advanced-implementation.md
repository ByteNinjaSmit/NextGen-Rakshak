# Offline Mesh — Advanced Implementation

Closes the gaps between report §4.3.2 and the code, and hardens the mesh to the
level the "core contribution over connectivity-dependent systems" claim needs.
All work is in `nextgen-rakshak-mobile/app/src/main/java/com/rakshak/app/networking/mesh/`.

## What §4.3.2 claimed vs what now exists

| §4.3.2 claim | Before | Now |
|---|---|---|
| "unique message ID (UUID)" per packet | dedup keyed on the Firestore alert id | real per-packet `UUID` (`MeshPayloadCodec.newMessageId()`), on every packet type; seen-set keyed on it |
| "short-lived seen-IDs set" | `Set` that never shrank | `MeshSeenCache` — time-windowed, evicts after the 8 h alert lifetime |
| "96×96 pixel thumbnail (≈2–3 KB) … full side-by-side visual validation on a completely offline phone" | **not implemented** — only `imageUrl` on the wire | `MeshThumbnail` encodes a 96×96 JPEG (q40); it rides in the alert packet and the match dialog renders `alert.thumbnail ?: alert.imageUrl` |
| "TTL … caps how far a packet can travel" | implemented | unchanged (TTL 6, dropped at 1), now in a testable `MeshRouter.shouldRelay` |
| "512-byte MobileFaceNet embedding (128×4)" | implemented | unchanged; width still read from the wire so the 512-d model also works |
| packets "expire … once the parent alert's lifetime elapses" | implemented | unchanged |
| cosine similarity, 0–1, brightness-robust | implemented | unchanged |

## Beyond the paragraph — hardening

| Area | Change |
|---|---|
| **Packet authentication** (synopsis §5.1.4) | `MeshCrypto` — HMAC-SHA256 trailer over the packet body (TTL byte excluded so a relay need not re-sign). Key from `BuildConfig.MESH_HMAC_KEY` (override in `local.properties`). A packet whose MAC fails is dropped and logged. *Limitation:* a shared app-embedded key detects corruption and blocks non-official builds, but not a modified copy of the official app — per-alert asymmetric signing by the kiosk is future work. |
| **Runs in the field** | `MeshService` — a `connectedDevice` foreground service owns the mesh, so it keeps relaying when the app is backgrounded or the screen is locked. Low-priority notification shows the live peer count + a Stop action. Started by `MainActivity` once transport permissions are granted; deliberately survives the activity finishing. |
| **Failed transfers** (was GAP-01) | `onPayloadTransferUpdate` now logs a `FAILURE` with the endpoint and retries the payload once to a still-connected peer. |
| **Gateway-aware match routing** | Peers exchange a `HELLO` carrying an "I have internet" bit. `MeshRouter.matchTargets` sends a match report to online peers first, floods only if none is connected. The online device uploads the match and `ackMatch()`s back along the mesh. |
| **Restart resilience** | `MeshStore` (Room, `mesh_alerts` + `mesh_seen`) persists learned alert packets and processed ids; `MeshNetworkManager.start()` reloads and re-verifies them, so closing the app mid-event does not lose mesh alerts. |
| **Measurability** (for VER-08) | Every receive / relay / drop / retry is logged with a timestamp to `MeshNetworkManager.log`; **Profile → Mesh Network** (`MeshDebugScreen`) shows the live peer count and the rolling packet log — the multi-device trial reads hop timings straight off it. |

## Wire format (`MeshPayloadCodec`)

```
byte[0]        TTL / hop-count            (mutable — decremented at each relay, outside the MAC)
byte[1]        type tag                   (alert=1 match=2 resolve=3 hello=4 ack=5)
byte[2..]      message id (UUID, writeUTF)
...            type-specific fields       (alert: id, name, imageUrl, age, gender,
                                           clothing, lastSeen, embedding[n], timestamp,
                                           thumbnailLen, thumbnail[])
last 32 bytes  HMAC-SHA256(key, bytes[1 .. len-32])
```

`parentContact` is still deliberately absent from the wire.

## Tests

`./gradlew :app:testDebugUnitTest` — 48 unit tests, including:
`MeshPayloadCodecTest` (round-trips incl. thumbnail, stable UUID, tamper + truncation
rejection, HELLO/ACK), `MeshCryptoTest`, `MeshSeenCacheTest` (eviction, restore),
`MeshRouterTest` (relay cut-off, gateway preference).

## Self-review fixes (second pass)

- **Duplicate match documents.** Every relaying device used to emit the sighting to
  its own uploader, so each offline hop between the volunteer and a gateway queued
  it in Room and produced another match doc on reconnect. Now a device emits only
  when it is itself online; middle relays just forward. Origin device's Room queue
  + `MatchSyncWorker` remain the safety net.
- **`startForeground` crash loop.** If the Bluetooth runtime permission is revoked
  while `MeshService` is stopped, `startForeground` throws on a `START_STICKY`
  restart. Now caught → `stopSelf()` instead of a crash loop.
- **Unbounded outbound de-dup sets.** `broadcastedAlertIds` / `broadcastedResolveIds`
  never shrank. Replaced with a time-windowed `MeshSeenCache`.
- **`pendingSends` leak.** A peer that vanishes never reports SUCCESS/FAILURE for
  its in-flight payloads. `dropEndpoint()` now clears them on disconnect / lost.

## Third pass — remaining gap fills

| Was open | Now |
|---|---|
| No ACK retry timer | `MeshNetworkManager.scheduleMatchAckRetry` — the origin re-sends the match packet every 15 s (same message id) to whatever gateway peers are connected, up to 3 attempts. Cleared on an ACK, or when the origin itself comes online (it can upload from Room then). ACK packets now carry their own seen-id dedup so a receipt cannot loop. |
| Location Services (not permission) | `LocationServices.enabled()` — the mesh debug screen shows a red banner with a "Turn on" shortcut to `ACTION_LOCATION_SOURCE_SETTINGS` when the system toggle is off. |
| `logLine()` non-atomic write | now `_log.update { … }`. |
| `MatchReport` lat/long `0.0` on no fix | `MatchReport.hasLocation` flows through the mesh wire, Room (`AppDatabase` v3→v4), and Firestore. The kiosk (`matches-list.tsx`) renders **"Dispatch (no location)"** without a Maps link when it is false, instead of a pin on 0,0. |

## Still open (formal descope + hardware)

- **Per-alert asymmetric signing by the kiosk** — *descoped, documented as future
  work* (plan GAP-02 explicitly permits "implement HMAC **or** a written
  descope"). Doing it properly needs an Ed25519 keypair generated at deploy time,
  the private half in a Cloud Function that has never been deployed, the public
  half pinned in the app, a canonical serialization shared by
  `functions/src/embedding.ts` and `MeshPayloadCodec`, and a crypto dependency
  (`java.security` Ed25519 is API 33+, below `minSdk 24`, so Tink or
  BouncyCastle). The shipped HMAC already blocks packets from any build that does
  not hold the key and catches tampering/corruption on a relay; the residual gap
  — a modified copy of the *official* app — is the one only per-alert asymmetric
  signing closes, and it is not reachable without the backend deploy landing
  first.
- **≥3-device field trial (VER-08)** — needs hardware. The debug screen's
  timestamped packet log exists to make hop timing measurable when it runs.
