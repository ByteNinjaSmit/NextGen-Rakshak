import { AuthErrorCodes, signInWithPopup, signOut, type User } from "firebase/auth";
import { FirebaseError } from "firebase/app";
import { httpsCallable } from "firebase/functions";
import { auth, functions, googleProvider } from "@/lib/firebase";

/** Thrown when the popup is closed/blocked before completing sign-in — not a real error. */
export class SignInCancelledError extends Error {}

/** Thrown when a signed-in account fails to get the police claim. */
export class NotAuthorisedError extends Error {}

/**
 * Sign an officer in with Google, then grant the `police` custom claim that
 * firestore.rules requires for alert writes (self-service: `claimOfficerRole`
 * grants it to any authenticated account, no approval step).
 */
export async function signInWithGoogle(): Promise<User> {
  let result;
  try {
    result = await signInWithPopup(auth, googleProvider);
  } catch (err) {
    if (
      err instanceof FirebaseError &&
      (err.code === AuthErrorCodes.POPUP_CLOSED_BY_USER || err.code === AuthErrorCodes.POPUP_BLOCKED)
    ) {
      throw new SignInCancelledError("Sign-in was cancelled.");
    }
    throw err;
  }

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
