# Future Work — Per-Alert Signing on the Mesh (paste-ready)

For the report's Future Scope / Limitations chapter. Records a deliberate,
reasoned descope, not an omission — the project's own planning backlog (task
GAP-02) states the requirement as "an HMAC over the payload verified on receipt
**or** a written descope with the reason."

---

## Delivered

Every mesh packet is authenticated with an **HMAC-SHA256** tag over its body
(the message id, type tag and all payload fields; only the mutable hop-count
byte is excluded so a relay need not re-sign). The key is a per-deployment
secret compiled into the application; the release build refuses to compile with
the placeholder key. On receipt, a packet whose tag does not verify is dropped
before it is parsed or relayed. This gives the mesh:

- **integrity** — a relaying device cannot alter alert content (name, last-seen
  location, embedding, thumbnail) without invalidating the tag;
- **origin authentication at the group level** — only a device holding the
  deployment key, i.e. running the official build, can put a packet on the mesh
  that other devices will accept.

## Not delivered — per-alert asymmetric signature

The synopsis (§5.1.4) also anticipated that each alert would be signed by the
police kiosk with a private key, so that a receiving phone could verify the
alert originated from the authority and not merely from *some* device holding
the shared key. This closes one residual threat the shared HMAC does not: a
modified copy of the official application (which would still hold the embedded
key) forging alerts into the mesh.

It was descoped for this iteration for three concrete reasons:

1. **The backend it depends on is not yet deployed.** The signature would be
   produced by the alert-embedding Cloud Function, which — as recorded in the
   Week 3 handover and the status audit — has never run in the Functions
   runtime (`tfjs-node` binding unverified). A signing step cannot be tested,
   and an untested canonical-serialisation contract between the TypeScript
   server and the Kotlin client (float encoding, field order, string
   normalisation) that is even slightly mismatched would cause **every** alert
   to fail verification — strictly worse than the shared-key scheme.

2. **Cost on the client.** Ed25519 verification in `java.security` requires
   API 33; the app's `minSdk` is 24, so a third-party crypto library
   (Tink or Bouncy Castle, ~1–2 MB) would have to be added for a check that,
   in the threat model of a supervised volunteer deployment, is marginal over
   the shared HMAC.

3. **Marginal value for the deployment model.** Volunteers are onboarded and
   the kiosk operator is present; the mesh is a fallback path used for minutes
   to hours at a single event, not an open network. The shared HMAC already
   defeats the practical attack (a bystander's phone injecting noise).

## Design sketch, if implemented later

1. Generate an Ed25519 key pair at deployment. Private key → Cloud Function
   secret (`firebase functions:secrets:set MESH_SIGNING_KEY`). Public key →
   `BuildConfig.MESH_SIGNING_PUBKEY`.
2. Define one canonical byte serialisation of the signable fields
   (`childName, age, gender, clothingDesc, lastSeen, identifyingMarks,
   embedding as IEEE-754 little-endian float32[], timestamp`) and implement it
   identically in `functions/src/` and `MeshPayloadCodec`, pinned by a
   cross-language test vector the way `FaceGeometry` ↔ `face_align.py` are.
3. `onAlertCreated` signs that serialisation and writes `signature` (base64) to
   the alert document, alongside the embedding it already writes.
4. `FirestoreAlertSource` reads `signature` into a new `Alert.signature` field;
   `MeshPayloadCodec` carries it; on mesh receipt the app verifies it and marks
   the alert `verified` / `unverified`.
5. **Graceful degradation is mandatory:** an alert with no signature, or received
   before the public key is configured, is still shown — flagged `unverified`,
   the same "trust it, but know where it came from" treatment the kiosk already
   gives a mesh-relayed match's `relayedBy` attribution. The mesh must never
   go dark because a key is missing.
