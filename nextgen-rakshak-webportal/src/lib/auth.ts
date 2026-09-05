import {
  AuthErrorCodes,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  type User,
} from "firebase/auth";
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

/** Sign an officer in with Google, with redirect fallback if popup fails. */
export async function signInWithGoogle(): Promise<User | null> {
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
    // Fallback to full-page redirect if popup sign-in fails or throws a Google 500/COOP error
    console.warn("Popup sign-in failed, attempting redirect sign-in...", err);
    await signInWithRedirect(auth, googleProvider);
    return null;
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

/** Process redirect sign-in result after returning from Google OAuth page. */
export async function handleRedirectResult(): Promise<User | null> {
  try {
    const result = await getRedirectResult(auth);
    if (result?.user) {
      await ensureOfficerRole(result.user);
      return result.user;
    }
  } catch (err) {
    console.error("Redirect sign-in error:", err);
  }
  return null;
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

