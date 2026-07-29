import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

/**
 * Grant the caller the `police` custom claim on first sign-in — self-registration,
 * no allow-list/approval step. Any authenticated Google account becomes police.
 *
 * The officer's directory record lives in `officers/{uid}`. It is written here
 * with the Admin SDK rather than from the kiosk so identity fields (email, role,
 * createdAt) cannot be forged by the client; firestore.rules leaves only the
 * editable profile fields writable from the browser.
 *
 * The client must refresh its ID token (`getIdToken(true)`) after this returns
 * for the new claim to take effect.
 */
export const claimOfficerRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in first.");

  const db = getFirestore();

  // Police and volunteer are mutually exclusive: the two apps share one Auth
  // pool, so without this a volunteer's account would pick up the `police`
  // claim by opening the kiosk once — and keep it inside the mobile app,
  // because claims live on the user, not the app.
  const volunteerDoc = await db.collection("volunteers").doc(auth.uid).get();
  if (volunteerDoc.exists) {
    // Repair accounts that were granted the claim before this check existed.
    if (auth.token.role === "police") {
      await getAuth().setCustomUserClaims(auth.uid, null);
      await getAuth().revokeRefreshTokens(auth.uid);
      logger.warn("Revoked police claim from a volunteer account", { uid: auth.uid });
    }
    throw new HttpsError(
      "failed-precondition",
      "This Google account is registered as a volunteer device. " +
        "Sign in to the kiosk with a separate police account.",
    );
  }

  const user = await getAuth().getUser(auth.uid);
  await getAuth().setCustomUserClaims(auth.uid, { role: "police" });

  const docRef = db.collection("officers").doc(auth.uid);
  const existing = await docRef.get();
  const isNewOfficer = !existing.exists;

  // Google is the only provider, so email/displayName come from the verified
  // account. `?? null` because Firestore rejects undefined values.
  await docRef.set(
    {
      uid: auth.uid,
      email: user.email ?? null,
      photoURL: user.photoURL ?? null,
      role: "police",
      lastLoginAt: FieldValue.serverTimestamp(),
      ...(isNewOfficer
        ? {
            // Seeded once; afterwards the officer owns these via the profile page.
            displayName: user.displayName ?? user.email ?? "Officer",
            phone: user.phoneNumber ?? "",
            station: "",
            badgeNumber: "",
            createdAt: FieldValue.serverTimestamp(),
          }
        : {}),
    },
    { merge: true },
  );

  logger.info("Granted police claim", {
    uid: auth.uid,
    email: user.email,
    isNewOfficer,
  });
  return { role: "police", isNewOfficer };
});
