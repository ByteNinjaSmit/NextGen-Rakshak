import { signInWithPopup, signOut, type User } from "firebase/auth";
import { httpsCallable } from "firebase/functions";
import { auth, functions, googleProvider } from "@/lib/firebase";

/** Thrown when a real Google account signs in but isn't an authorised officer. */
export class NotAuthorisedError extends Error {}

/**
 * Sign an officer in with Google and confirm they are authorised for the kiosk.
 *
 * Signing in proves who someone is, not that they may file missing-child
 * alerts — any Google account can authenticate. `claimOfficerRole` checks the
 * account against the server-side allow-list and grants the `police` custom
 * claim that firestore.rules requires for alert writes. An unauthorised account
 * is signed straight back out, so the kiosk never renders for them.
 */
export async function signInWithGoogle(): Promise<User> {
  const result = await signInWithPopup(auth, googleProvider);
  try {
    await httpsCallable(functions, "claimOfficerRole")();
    // Force a token refresh so the new claim is present on this session.
    await result.user.getIdToken(true);
  } catch (err) {
    await signOut(auth);
    throw new NotAuthorisedError(
      err instanceof Error && err.message
        ? err.message
        : "This account is not authorised for the police kiosk."
    );
  }
  return result.user;
}

/** True if the signed-in user carries the `police` claim. */
export async function hasOfficerRole(user: User): Promise<boolean> {
  const token = await user.getIdTokenResult();
  return token.claims.role === "police";
}

/** Sign the current officer out. */
export async function signOutUser(): Promise<void> {
  await signOut(auth);
}
