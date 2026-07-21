import {
  collection,
  addDoc,
  updateDoc,
  doc,
  onSnapshot,
  query,
  where,
  orderBy,
  serverTimestamp,
  GeoPoint,
  type Unsubscribe,
} from "firebase/firestore";
import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
import { db, storage } from "@/lib/firebase";
import type { Alert, AlertInput, Match } from "@/types";

const alertsRef = collection(db, "alerts");
const matchesRef = collection(db, "matches");

/** Upload the child photo to Firebase Storage and return its download URL. */
export async function uploadChildPhoto(file: File): Promise<string> {
  const path = `alert_images/${Date.now()}_${file.name}`;
  const snapshot = await uploadBytes(ref(storage, path), file);
  return getDownloadURL(snapshot.ref);
}

/**
 * Create a missing-child alert.
 * The 128-d face embedding is computed server-side by a Cloud Function
 * that triggers on document creation, so we write an empty placeholder here.
 * When the kiosk's location is available it is stored as `geoLocation` so the
 * Cloud Function can geofence the push to nearby volunteers (FR-03).
 */
export async function createAlert(
  input: AlertInput,
  photo: File,
  origin?: { lat: number; lng: number },
): Promise<string> {
  const imageUrl = await uploadChildPhoto(photo);
  const docRef = await addDoc(alertsRef, {
    ...input,
    imageUrl,
    embedding: [],
    status: "active",
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

/** Subscribe to incoming matches, newest first. */
export function subscribeMatches(cb: (matches: Match[]) => void): Unsubscribe {
  const q = query(matchesRef, orderBy("timestamp", "desc"));
  return onSnapshot(q, (snap) => {
    cb(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as Match));
  });
}
