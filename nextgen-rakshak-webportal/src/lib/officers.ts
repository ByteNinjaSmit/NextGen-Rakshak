import {
  doc,
  onSnapshot,
  serverTimestamp,
  updateDoc,
  type Unsubscribe,
} from "firebase/firestore";
import { db } from "@/lib/firebase";
import type { Officer, OfficerProfileInput } from "@/types";

/** `officers/{uid}` — the signed-in officer's directory record. */
function officerDoc(uid: string) {
  return doc(db, "officers", uid);
}

/**
 * Live subscription to an officer's record. Emits `null` while the document
 * does not exist yet — `claimOfficerRole` creates it moments after the first
 * sign-in, and the snapshot fires again once it lands.
 */
export function subscribeOfficer(
  uid: string,
  cb: (officer: Officer | null) => void,
  onError?: (err: Error) => void,
): Unsubscribe {
  return onSnapshot(
    officerDoc(uid),
    (snap) => cb(snap.exists() ? ({ ...snap.data(), uid: snap.id } as Officer) : null),
    (err) => onError?.(err),
  );
}

/**
 * Update the officer's own profile. Restricted to the editable fields — the
 * matching `hasOnly` guard in firestore.rules rejects anything else, so keep
 * this list and the rule in step.
 */
export async function updateOfficerProfile(
  uid: string,
  patch: OfficerProfileInput,
): Promise<void> {
  await updateDoc(officerDoc(uid), {
    displayName: patch.displayName.trim(),
    phone: patch.phone.trim(),
    station: patch.station.trim(),
    badgeNumber: patch.badgeNumber.trim(),
    updatedAt: serverTimestamp(),
  });
}

/**
 * Persist the browser's FCM token so the `onMatchCreated` Cloud Function can
 * push to this officer. Also restricted by firestore.rules' `hasOnly` guard —
 * keep the two lists in step.
 */
export async function saveOfficerFcmToken(uid: string, token: string): Promise<void> {
  await updateDoc(officerDoc(uid), {
    fcmToken: token,
    updatedAt: serverTimestamp(),
  });
}
