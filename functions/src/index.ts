import { initializeApp } from "firebase-admin/app";
import { getFirestore, GeoPoint, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2/options";
import * as logger from "firebase-functions/logger";
import { computeEmbedding } from "./embedding";
import { broadcastAlert } from "./notify";

initializeApp();

// Alerts auto-expire this long after creation (synopsis FR-12 default; matches
// the mobile mesh's Constants.ALERT_EXPIRY_MILLIS so both drop alerts in lockstep).
const ALERT_TTL_MS = 8 * 60 * 60 * 1000; // 8 hours

// tfjs-node needs headroom; embedding runs in ~1-3s after a warm start.
setGlobalOptions({ region: "us-central1", memory: "1GiB", timeoutSeconds: 120 });

async function fetchImage(imageUrl: string): Promise<Buffer> {
  const res = await fetch(imageUrl);
  if (!res.ok) throw new Error(`Image download failed: ${res.status}`);
  return Buffer.from(await res.arrayBuffer());
}

/**
 * Firestore trigger: when an alert is created, compute the child's face
 * embedding server-side and write it back so volunteer devices can match.
 */
export const onAlertCreated = onDocumentCreated("alerts/{alertId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const data = snap.data();
  const imageUrl: string | undefined = data.imageUrl;
  const existing: unknown = data.embedding;

  const alertId = event.params.alertId;
  const childName: string = data.childName ?? "a child";

  // Compute the face embedding (best-effort) unless one already exists.
  if (imageUrl && !(Array.isArray(existing) && existing.length > 0)) {
    try {
      const embedding = await computeEmbedding(await fetchImage(imageUrl));
      await snap.ref.update({ embedding });
      logger.info("Embedding written", { alertId, dims: embedding.length });
    } catch (err) {
      logger.error("Embedding failed", { alertId, err: String(err) });
    }
  } else if (!imageUrl) {
    logger.warn("Alert has no imageUrl; skipping embedding", { alertId });
  }

  // Always notify volunteers, even if embedding failed — humans can still look.
  // Geofence to volunteers near the alert's location when coordinates are present.
  const origin = data.geoLocation instanceof GeoPoint ? data.geoLocation : undefined;
  try {
    await broadcastAlert(alertId, childName, origin);
  } catch (err) {
    logger.error("Alert broadcast failed", { alertId, err: String(err) });
  }
});

/**
 * Scheduled cleanup: mark active alerts older than the TTL as resolved and drop
 * their embeddings (privacy). Runs every 30 minutes.
 */
export const expireAlerts = onSchedule("every 30 minutes", async () => {
  const db = getFirestore();
  const cutoff = Timestamp.fromMillis(Date.now() - ALERT_TTL_MS);

  const stale = await db
    .collection("alerts")
    .where("status", "==", "active")
    .where("timestamp", "<", cutoff)
    .get();

  if (stale.empty) return;

  const batch = db.batch();
  stale.forEach((doc) => batch.update(doc.ref, { status: "resolved", embedding: [] }));
  await batch.commit();
  logger.info("Expired alerts", { count: stale.size });
});

/**
 * Callable fallback: compute an embedding for an image URL on demand
 * (e.g. to re-index an alert). Requires an authenticated caller.
 */
export const computeEmbeddingCallable = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");
  const imageUrl = request.data?.imageUrl;
  if (typeof imageUrl !== "string") {
    throw new HttpsError("invalid-argument", "imageUrl (string) is required.");
  }
  const embedding = await computeEmbedding(await fetchImage(imageUrl));
  return { embedding };
});
