import { doc, getDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import type { Volunteer } from "@/types";

/**
 * One-shot fetch of a volunteer's account record. firestore.rules lets any
 * signed-in account (so any officer) read `volunteers/{uid}` — used to show
 * who a sighting actually came from: name, phone, email, and the device's
 * last known location, beyond what's denormalised onto the match itself.
 */
export async function fetchVolunteer(uid: string): Promise<Volunteer | null> {
  const snap = await getDoc(doc(db, "volunteers", uid));
  return snap.exists() ? ({ id: snap.id, ...snap.data() } as Volunteer) : null;
}
