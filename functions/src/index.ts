// Runtime: nodejs22 (see firebase.json). Bumped from nodejs20 ahead of its
// 2026-10-30 decommission.
import { initializeApp } from "firebase-admin/app";
import { getFirestore, GeoPoint, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2/options";
import * as logger from "firebase-functions/logger";
import { computeEmbedding } from "./embedding";
import { broadcastAlert, notifyOfficerOfMatch } from "./notify";

export { claimOfficerRole } from "./officers";

initializeApp();

// Alerts auto-expire this long after creation (synopsis FR-12 default; matches
// the mobile mesh's Constants.ALERT_EXPIRY_MILLIS so both drop alerts in lockstep).
const ALERT_TTL_MS = 8 * 60 * 60 * 1000; // 8 hours

// tfjs-node needs headroom; embedding runs in ~1-3s after a warm start.
setGlobalOptions({ region: "us-central1", memory: "1GiB", timeoutSeconds: 120 });

/** Refuse anything larger than storage.rules already allows to be uploaded. */
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/**
 * Read a child's photo out of our own Storage bucket.
 *
 * Deliberately *not* `fetch(imageUrl)`. Fetching a caller-supplied URL from
 * inside the Functions runtime is a server-side request forgery primitive: the
 * runtime sits on an internal network and would happily retrieve
 * `http://169.254.169.254/…` (the GCP metadata server, which serves service
 * account tokens) or any other host reachable from there, and hand the result
 * to a model that returns 128 floats — but a failure message alone is enough to
 * probe what exists. Going through the Admin SDK means the only thing taken
 * from the URL is an object path inside a bucket we already own, so there is no
 * host to redirect and nothing to forge.
 */
async function fetchAlertImage(imageUrl: string): Promise<Buffer> {
  const path = storagePathFromUrl(imageUrl);
  // Alert photos live under this prefix and nowhere else; anything outside it
  // is not an alert photo regardless of who asked for it.
  if (!path || !path.startsWith("alert_images/")) {
    throw new Error("imageUrl does not point at an alert photo in this project's bucket.");
  }

  const file = getStorage().bucket().file(path);
  const [metadata] = await file.getMetadata();
  const size = Number(metadata.size ?? 0);
  if (size > MAX_IMAGE_BYTES) {
    throw new Error(`Alert photo is ${size} bytes; the limit is ${MAX_IMAGE_BYTES}.`);
  }

  const [buffer] = await file.download();
  return buffer;
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
      const embedding = await computeEmbedding(await fetchAlertImage(imageUrl));
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
 * Firestore trigger: when a volunteer reports a sighting, push it to the
 * officer who filed the matching alert. Reads `alerts/{alertId}.createdBy.uid`
 * to find who to notify — firestore.rules' `validMatch()` already guarantees
 * that alert exists before the match can be created.
 */
export const onMatchCreated = onDocumentCreated("matches/{matchId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const { alertId, childName, confidence } = snap.data();
  try {
    const alert = await getFirestore().collection("alerts").doc(alertId).get();
    const officerUid: string | undefined = alert.get("createdBy")?.uid;
    if (!officerUid) {
      logger.warn("Match's alert has no createdBy.uid; skipping push", { matchId: event.params.matchId, alertId });
      return;
    }
    await notifyOfficerOfMatch(officerUid, childName, confidence);
  } catch (err) {
    logger.error("Match push failed", { matchId: event.params.matchId, alertId, err: String(err) });
  }
});

/**
 * Derive the Cloud Storage object path from a Firebase download URL.
 * URLs look like `https://.../o/alert_images%2F123_a.jpg?alt=media&token=...`,
 * so the path is the URL-decoded segment between `/o/` and the query string.
 */
function storagePathFromUrl(imageUrl: string): string | null {
  const match = /\/o\/([^?]+)/.exec(imageUrl);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Purge the biometric material for a resolved alert: clear the embedding and
 * delete the child's photo from Cloud Storage. This is what makes the
 * "data is ephemeral and auto-expires" guarantee real rather than a policy
 * statement — the photo does not outlive the case.
 */
async function purgeAlertData(
  ref: FirebaseFirestore.DocumentReference,
  data: FirebaseFirestore.DocumentData,
  alertId: string,
): Promise<void> {
  const imageUrl: string | undefined = data.imageUrl;
  if (imageUrl) {
    const path = storagePathFromUrl(imageUrl);
    if (path) {
      try {
        await getStorage().bucket().file(path).delete({ ignoreNotFound: true });
        logger.info("Alert photo deleted", { alertId, path });
      } catch (err) {
        logger.error("Photo deletion failed", { alertId, path, err: String(err) });
      }
    } else {
      logger.warn("Could not parse storage path from imageUrl", { alertId });
    }
  }

  const updates: Record<string, unknown> = {};
  if (Array.isArray(data.embedding) && data.embedding.length > 0) updates.embedding = [];
  if (imageUrl) updates.imageUrl = "";
  if (Object.keys(updates).length > 0) await ref.update(updates);

  // Each match carries its own copy of the photo URL. Left alone it outlives the
  // deleted object and renders as a broken image in the kiosk forever, so the
  // denormalised copies are cleared alongside the original.
  if (imageUrl) await clearMatchImages(alertId);
}

/** A Firestore batch caps at 500 writes; anything unbounded has to be chunked. */
const BATCH_LIMIT = 500;

/** Apply the same field update to every document, in batches of [BATCH_LIMIT]. */
async function updateAll(
  docs: FirebaseFirestore.QueryDocumentSnapshot[],
  update: Record<string, unknown>,
): Promise<void> {
  const db = getFirestore();
  for (let i = 0; i < docs.length; i += BATCH_LIMIT) {
    const batch = db.batch();
    docs.slice(i, i + BATCH_LIMIT).forEach((doc) => batch.update(doc.ref, update));
    await batch.commit();
  }
}

/** Blank the copied `imageUrl` on every match reported against this alert. */
async function clearMatchImages(alertId: string): Promise<void> {
  const db = getFirestore();
  const matches = await db.collection("matches").where("alertId", "==", alertId).get();
  if (matches.empty) return;

  await updateAll(matches.docs, { imageUrl: "" });
  logger.info("Cleared match photo URLs", { alertId, count: matches.size });
}

/**
 * When an alert is marked resolved (by an officer or by the expiry sweep),
 * purge its embedding and photo. Firing on the status transition keeps a single
 * cleanup path for both routes, and the guard stops the function's own write
 * from re-triggering it.
 */
export const onAlertResolved = onDocumentUpdated("alerts/{alertId}", async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after) return;
  if (before.status !== "active" || after.status !== "resolved") return;

  await purgeAlertData(event.data!.after.ref, after, event.params.alertId);
});

/**
 * Scheduled cleanup: mark active alerts older than the TTL as resolved.
 * The resulting status transition triggers [onAlertResolved], which deletes the
 * embedding and the photo. Runs every 30 minutes.
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

  // Chunked: an unswept backlog (a busy event, or the schedule having been
  // paused) can exceed the 500-write batch limit, and a batch that overflows
  // throws — leaving every stale alert live instead of just the excess.
  await updateAll(stale.docs, { status: "resolved" });
  logger.info("Expired alerts", { count: stale.size });
});

/**
 * Callable fallback: recompute an alert's embedding on demand.
 *
 * Officers only. Re-indexing an alert is a kiosk operation, and every volunteer
 * device — including an anonymous one — is in the same Auth pool, so
 * "authenticated" on its own would have left this open to anybody who installed
 * the app. It is also the one entry point where the image path comes straight
 * from the caller, which is why [fetchAlertImage] resolves it inside our own
 * bucket rather than fetching it.
 */
export const computeEmbeddingCallable = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (request.auth.token.role !== "police") {
    throw new HttpsError("permission-denied", "Officers only.");
  }
  const imageUrl = request.data?.imageUrl;
  if (typeof imageUrl !== "string") {
    throw new HttpsError("invalid-argument", "imageUrl (string) is required.");
  }
  const embedding = await computeEmbedding(await fetchAlertImage(imageUrl));
  return { embedding };
});
