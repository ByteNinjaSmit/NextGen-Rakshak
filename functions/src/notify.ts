import { getFirestore, GeoPoint } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";

/** Geofence radius around the reporting kiosk (synopsis §2.1). */
const GEOFENCE_RADIUS_KM = 2;

/**
 * A volunteer's `lastLocation` older than this is treated as unknown, so an
 * out-of-range check on a stale fix cannot silently exclude a helper who has
 * since travelled to the event. Volunteers refresh their position on opening
 * Home and on starting a scan; six hours is comfortably longer than that gap
 * for anyone actually near an alert.
 */
const STALE_LOCATION_MS = 6 * 60 * 60 * 1000;

/** Great-circle distance between two lat/lng points, in kilometres. */
function haversineKm(a: GeoPoint, b: GeoPoint): number {
  const R = 6371;
  const dLat = ((b.latitude - a.latitude) * Math.PI) / 180;
  const dLng = ((b.longitude - a.longitude) * Math.PI) / 180;
  const lat1 = (a.latitude * Math.PI) / 180;
  const lat2 = (b.latitude * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * Push a "child missing" alert to registered volunteers.
 * Tokens live on `volunteers/{uid}.fcmToken`; each volunteer's last known
 * position on `volunteers/{uid}.lastLocation` (GeoPoint).
 *
 * When `origin` is provided (the alert's geo-location), only volunteers within
 * GEOFENCE_RADIUS_KM are notified (FR-03). Fail-open: a volunteer with no
 * recorded location is still notified — better to over-notify than miss a nearby
 * helper. When `origin` is absent (alert has no coordinates), everyone is
 * notified, preserving the previous behaviour. Invalid tokens are pruned.
 */
export async function broadcastAlert(
  alertId: string,
  childName: string,
  origin?: GeoPoint,
): Promise<void> {
  const db = getFirestore();
  const snap = await db.collection("volunteers").get();

  const tokens: string[] = [];
  const tokenToRef = new Map<string, FirebaseFirestore.DocumentReference>();
  let skippedOutOfRange = 0;

  snap.forEach((doc) => {
    const token = doc.get("fcmToken");
    if (typeof token !== "string" || token.length === 0) return;

    if (origin) {
      const loc = doc.get("lastLocation");
      const updatedAt = doc.get("locationUpdatedAt");
      const fixMillis =
        updatedAt && typeof updatedAt.toMillis === "function" ? updatedAt.toMillis() : 0;
      const fresh = fixMillis > 0 && Date.now() - fixMillis < STALE_LOCATION_MS;
      // Only exclude when we KNOW the volunteer is currently out of range: a
      // valid AND recent fix that is beyond the radius. A missing, malformed, or
      // stale location falls through and is notified (fail-open).
      if (loc instanceof GeoPoint && fresh && haversineKm(origin, loc) > GEOFENCE_RADIUS_KM) {
        skippedOutOfRange++;
        return;
      }
    }

    tokens.push(token);
    tokenToRef.set(token, doc.ref);
  });

  if (tokens.length === 0) {
    logger.info("No volunteer tokens to notify", { alertId, skippedOutOfRange });
    return;
  }

  // FCM multicast caps at 500 tokens per request.
  //
  // Data-only (no `notification` block): a message with a `notification` payload
  // is displayed by the OS itself when the app is backgrounded and
  // `onMessageReceived` never runs, so the volunteer would get a plain default
  // notification instead of the app's high-importance channel + deep link to the
  // scan screen. Data-only guarantees RakshakMessagingService handles every
  // message and builds the notification the same way in every app state.
  const messaging = getMessaging();
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const res = await messaging.sendEachForMulticast({
      tokens: batch,
      data: {
        type: "alert",
        alertId,
        childName,
        title: "Child Missing — Tap to Scan",
        body: `Searching for ${childName}. Tap to help.`,
      },
      android: { priority: "high" },
    });

    // Remove tokens FCM reports as permanently invalid.
    res.responses.forEach((r, idx) => {
      const code = r.error?.code;
      if (
        code === "messaging/invalid-registration-token" ||
        code === "messaging/registration-token-not-registered"
      ) {
        tokenToRef.get(batch[idx])?.update({ fcmToken: "" }).catch(() => undefined);
      }
    });

    logger.info("Alert push sent", {
      alertId,
      sent: res.successCount,
      failed: res.failureCount,
      geofenced: Boolean(origin),
      skippedOutOfRange,
    });
  }
}

/**
 * Push a new volunteer sighting to the officer who filed the matching alert.
 * The token lives on `officers/{uid}.fcmToken`, saved by the kiosk's
 * notification bell — absent until that officer opts in, in which case this
 * is a silent no-op (there is no one to notify yet).
 */
export async function notifyOfficerOfMatch(
  officerUid: string,
  childName: string,
  confidence: number,
): Promise<void> {
  const db = getFirestore();
  const officerRef = db.collection("officers").doc(officerUid);
  const officerDoc = await officerRef.get();
  const token = officerDoc.get("fcmToken");
  if (typeof token !== "string" || token.length === 0) return;

  const messaging = getMessaging();
  try {
    await messaging.send({
      token,
      notification: {
        title: "New Match Sighting",
        body: `${childName} — ${Math.round(confidence * 100)}% confidence match reported.`,
      },
      // `link` is carried in data as well as fcmOptions: the kiosk service
      // worker defines its own onBackgroundMessage handler, so it displays the
      // notification itself and fcmOptions.link is never applied — the SW reads
      // data.link in its notificationclick handler instead.
      data: { childName, link: "/matches" },
      webpush: { fcmOptions: { link: "/matches" } },
    });
  } catch (err) {
    const code = (err as { code?: string }).code;
    if (
      code === "messaging/invalid-registration-token" ||
      code === "messaging/registration-token-not-registered"
    ) {
      await officerRef.update({ fcmToken: "" }).catch(() => undefined);
      return;
    }
    logger.error("Match push failed", { officerUid, err: String(err) });
  }
}
