import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

/**
 * Allow-list of officer email addresses, one document per address:
 *   allowedOfficers/{email}
 *
 * Client access is denied outright in firestore.rules — only the Admin SDK
 * (i.e. this function) may read it, so a signed-in user cannot enumerate or
 * extend the list. Add officers from the Firebase console.
 */
const ALLOWLIST = "allowedOfficers";

/**
 * Grant the caller the `police` custom claim if their verified email is
 * allow-listed.
 *
 * Authentication alone is not authorisation: any Google account can sign in, so
 * without this check anyone on the internet could reach the kiosk and file or
 * resolve missing-child alerts. The claim rides inside the ID token, so
 * firestore.rules can gate writes on it with no extra read.
 *
 * The client must refresh its ID token (`getIdToken(true)`) after this returns
 * for the new claim to take effect.
 */
export const claimOfficerRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in first.");

  const email = auth.token.email?.toLowerCase();
  // Require a Google-verified address; otherwise the allow-list means nothing.
  if (!email || auth.token.email_verified !== true) {
    throw new HttpsError("permission-denied", "A verified email address is required.");
  }

  const entry = await getFirestore().collection(ALLOWLIST).doc(email).get();
  if (!entry.exists) {
    logger.warn("Kiosk access denied", { uid: auth.uid, email });
    throw new HttpsError(
      "permission-denied",
      "This account is not authorised for the police kiosk."
    );
  }

  await getAuth().setCustomUserClaims(auth.uid, { role: "police" });
  logger.info("Granted police claim", { uid: auth.uid, email });
  return { role: "police" };
});
