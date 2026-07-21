import { getFirestore, GeoPoint } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";

/** Geofence radius around the reporting kiosk (synopsis §2.1). */
const GEOFENCE_RADIUS_KM = 2;

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
      // Only exclude when we KNOW the volunteer is out of range; missing/invalid
      // location falls through and is notified (fail-open).
      if (loc instanceof GeoPoint && haversineKm(origin, loc) > GEOFENCE_RADIUS_KM) {
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
  const messaging = getMessaging();
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const res = await messaging.sendEachForMulticast({
      tokens: batch,
      notification: {
        title: "Child Missing — Tap to Scan",
        body: `Searching for ${childName}. Tap to help.`,
      },
      data: { alertId, childName },
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
