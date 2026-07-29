import { getAuth } from "firebase-admin/auth";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

/**
 * Grant the caller the `police` custom claim on first sign-in — self-registration,
 * no allow-list/approval step. Any authenticated Google account becomes police.
 *
 * The client must refresh its ID token (`getIdToken(true)`) after this returns
 * for the new claim to take effect.
 */
export const claimOfficerRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in first.");

  await getAuth().setCustomUserClaims(auth.uid, { role: "police" });
  logger.info("Granted police claim", { uid: auth.uid, email: auth.token.email });
  return { role: "police" };
});
