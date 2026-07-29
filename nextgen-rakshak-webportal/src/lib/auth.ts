import { AuthErrorCodes, signInWithPopup, signOut, type User } from "firebase/auth";
import { FirebaseError } from "firebase/app";
import { httpsCallable } from "firebase/functions";
import { auth, functions, googleProvider } from "@/lib/firebase";

/** Thrown when the popup is closed/blocked before completing sign-in — not a real error. */
export class SignInCancelledError extends Error {}

/** Thrown when a signed-in account fails to get the police claim. */
export class NotAuthorisedError extends Error {}

/**
 * Grant the signed-in account the `police` custom claim that firestore.rules
 * requires for alert writes, and create/refresh its `officers/{uid}` record
 * (self-service: `claimOfficerRole` grants it to any authenticated account, no
 * approval step).
 *
 * The forced token refresh puts the new claim on this session and fires
 * onIdTokenChanged, which is what re-renders the app as authorised.
 */
export async function ensureOfficerRole(user: User): Promise<void> {
  try {
    await httpsCallable(functions, "claimOfficerRole")();
    await user.getIdToken(true);
  } catch (err) {
    throw new NotAuthorisedError(
      err instanceof Error && err.message
        ? err.message
        : "This account is not authorised for the police kiosk."
    );
  }
}

/** Sign an officer in with Google, then register them as police. */
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
    await ensureOfficerRole(result.user);
  } catch (err) {
    // Never leave a half-registered session behind: without the claim every
    // Firestore write would fail anyway.
    await signOut(auth);
    throw err;
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
