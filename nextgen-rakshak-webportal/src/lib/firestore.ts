import {
  collection,
  addDoc,
  updateDoc,
  doc,
  onSnapshot,
  query,
  where,
  orderBy,
  limit,
  getCountFromServer,
  serverTimestamp,
  GeoPoint,
  type Unsubscribe,
} from "firebase/firestore";
import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
import { db, storage } from "@/lib/firebase";
import type { Alert, AlertAuthor, AlertInput, Match } from "@/types";

const alertsRef = collection(db, "alerts");
const matchesRef = collection(db, "matches");

/**
 * Reduce a browser-supplied filename to one safe Storage path segment.
 *
 * storage.rules matches `alert_images/{imageId}`, which is a *single* segment,
 * so a name containing a slash silently lands outside the rule and the upload
 * is rejected as unauthorised — an officer filing an alert would see a
 * permission error with no hint that their filename caused it.
 */
function safeFileName(name: string): string {
  const cleaned = name.replace(/[^A-Za-z0-9._-]/g, "_").slice(-64);
  return cleaned.replace(/^\.+/, "") || "photo.jpg";
}

/** Upload the child photo to Firebase Storage and return its download URL. */
export async function uploadChildPhoto(file: File): Promise<string> {
  const path = `alert_images/${Date.now()}_${safeFileName(file.name)}`;
  const snapshot = await uploadBytes(ref(storage, path), file);
  return getDownloadURL(snapshot.ref);
}

/**
 * Create a missing-child alert.
 * The 128-d face embedding is computed server-side by a Cloud Function
 * that triggers on document creation, so we write an empty placeholder here.
 * When the kiosk's location is available it is stored as `geoLocation` so the
 * Cloud Function can geofence the push to nearby volunteers (FR-03).
 *
 * `author.uid` must be the caller's own uid — firestore.rules rejects the write
 * otherwise, so an alert always traces back to a real officer account.
 */
export async function createAlert(
  input: AlertInput,
  photo: File,
  author: AlertAuthor,
  origin?: { lat: number; lng: number },
): Promise<string> {
  const imageUrl = await uploadChildPhoto(photo);
  const docRef = await addDoc(alertsRef, {
    ...input,
    imageUrl,
    embedding: [],
    status: "active",
    createdBy: author,
    timestamp: serverTimestamp(),
    ...(origin ? { geoLocation: new GeoPoint(origin.lat, origin.lng) } : {}),
  });
  return docRef.id;
}

/** Mark an alert resolved (child found / case closed). */
export async function resolveAlert(alertId: string): Promise<void> {
  await updateDoc(doc(db, "alerts", alertId), { status: "resolved" });
}

/** Mark a match as dispatched once police are en route. */
export async function dispatchMatch(matchId: string): Promise<void> {
  await updateDoc(doc(db, "matches", matchId), { status: "dispatched" });
}

/** Subscribe to all active alerts, newest first. */
export function subscribeActiveAlerts(cb: (alerts: Alert[]) => void): Unsubscribe {
  const q = query(alertsRef, where("status", "==", "active"), orderBy("timestamp", "desc"));
  return onSnapshot(q, (snap) => {
    cb(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as Alert));
  });
}

/**
 * Newest N matches the kiosk keeps live. Sightings are only actionable while
 * the case is open, so the dashboard streams a recent window rather than the
 * whole history — which otherwise grows without bound across every event.
 */
const MATCH_FEED_LIMIT = 100;

/** Subscribe to the most recent incoming matches, newest first. */
export function subscribeMatches(cb: (matches: Match[]) => void): Unsubscribe {
  const q = query(matchesRef, orderBy("timestamp", "desc"), limit(MATCH_FEED_LIMIT));
  return onSnapshot(q, (snap) => {
    cb(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as Match));
  });
}

/**
 * True totals for the dashboard tiles.
 *
 * Counting the live feed would silently cap at MATCH_FEED_LIMIT and report a
 * frozen number as fact, so the counts come from server-side aggregations —
 * one read each, no documents transferred.
 */
export async function fetchMatchCounts(): Promise<{ total: number; pending: number }> {
  const [total, pending] = await Promise.all([
    getCountFromServer(matchesRef),
    getCountFromServer(query(matchesRef, where("status", "==", "pending"))),
  ]);
  return { total: total.data().count, pending: pending.data().count };
}
