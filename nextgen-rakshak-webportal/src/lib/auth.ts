import { signInWithPopup, signOut, type User } from "firebase/auth";
import { auth, googleProvider } from "@/lib/firebase";

/** Launch the Google sign-in popup. Resolves with the signed-in user. */
export async function signInWithGoogle(): Promise<User> {
  const result = await signInWithPopup(auth, googleProvider);
  return result.user;
}

/** Sign the current officer out. */
export async function signOutUser(): Promise<void> {
  await signOut(auth);
}
