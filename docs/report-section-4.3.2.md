# Report §4.3.2 — updated to the delivered system

Paste this over the current §4.3.2 in the report. It reflects commits `8a3dc4a`
and `6aed293` on `main`. See `docs/mesh-advanced-implementation.md` for the
implementation detail behind each claim.

---

## 4.3.2 Peer-to-Peer Offline Communication

The Google Nearby Connections API [11] is an actively maintained peer-to-peer
networking API that lets applications discover, connect to, and exchange data
with nearby devices without any internet access. It uses Bluetooth Low Energy
(BLE) for low-power discovery and switches to Wi-Fi Direct for high-speed data
transfer. Nearby Connections establishes direct links between pairs of nearby
devices — it is not itself a multi-hop mesh routing protocol. Reaching a
volunteer several hops away from the point at which an alert enters the mesh
therefore requires an application-level store-and-forward routing layer built on
top of it.

NextGen Rakshak implements this layer as a dedicated component. Every packet
carries a unique message identifier (a Version-4 UUID), a hop-count /
time-to-live (TTL) field initialised to six and decremented at each relay, and
each device keeps a short-lived "seen-IDs" set so that a given packet is relayed
at most once. The seen-IDs set is time-windowed — entries are evicted once they
pass the alert lifetime (eight hours) — so it cannot grow unbounded over a
multi-day event. Packets are additionally dropped, and no longer relayed, once
their parent alert expires. Learned alerts and processed message identifiers are
persisted to a local database, so that closing and reopening the application
during an event does not discard alerts that the flood may not repeat.

Each packet is authenticated with an HMAC-SHA256 tag computed over its contents
(excluding only the mutable TTL byte, so that a relay need not re-sign). A packet
whose tag does not verify against the key embedded in the build is silently
dropped; this rejects packets from any device not running the official
application and detects any corruption introduced on a relay.

An alert packet contains the message UUID, the alert's short text fields (child
name, age, gender, clothing description, identifying marks and last-seen
location — the parent's contact number is deliberately excluded, as the mesh
reaches any nearby device), a 512-byte MobileFaceNet embedding vector (128
four-byte Float32 values; the vector length is written on the wire, so a
512-dimension model also works without a format change), and a highly compressed
96 × 96-pixel JPEG thumbnail of the child's face (approximately 2–3 KB, where the
photograph was available when the alert entered the mesh). The thumbnail is what
allows a fully offline phone to render the parent's photograph in the side-by-side
confirmation dialog, since the image URL carried for the online path cannot be
fetched without internet. The complete packet remains well under the 32 KB Nearby
Connections payload limit.

So that the mesh keeps operating while a volunteer walks the crowd with the phone
in a pocket, it is hosted in an Android foreground service rather than tied to the
screen being on. Confirmed-sighting reports are routed preferentially toward peers
that have advertised internet access (exchanged in a short capability handshake on
connection); the device that succeeds in uploading a sighting returns an
acknowledgement along the mesh, and the originating device re-sends the report a
few times until that acknowledgement arrives, falling back to its own upload queue
if none does.

Cosine similarity is used to compare face embeddings because it measures the angle
between two vectors rather than their magnitude, making it robust to brightness
variation. It yields an interpretable score that, for face embeddings, falls
between 0 (completely dissimilar) and 1 (identical).

---

## What changed from the earlier draft

| Earlier draft said | Status | Now |
|---|---|---|
| "unique message ID (UUID)" | was keyed on the Firestore alert id | real per-packet UUID |
| "short-lived seen-IDs set" | set never shrank | time-windowed, evicts at 8 h |
| "96 × 96 thumbnail … full side-by-side visual validation on a completely offline phone" | **not implemented** | JPEG thumbnail now travels in the alert packet |
| TTL / embedding / expiry / cosine | implemented | unchanged, wording kept |
| — (not mentioned) | new | HMAC-SHA256 authentication on every packet |
| — | new | foreground service keeps the mesh alive when backgrounded |
| — | new | gateway-preferred match routing + delivery acknowledgement + bounded retry |
| — | new | learned alerts / seen-IDs persisted across an app restart |
| — | new | `identifyingMarks` on the alert wire and `volunteerName` on the match wire (both were being dropped) |

Still described as future work in the report's limitations / future-scope
section, not in §4.3.2: per-alert asymmetric (public-key) signing of alerts by
the kiosk, and a field trial across three or more physical devices.
