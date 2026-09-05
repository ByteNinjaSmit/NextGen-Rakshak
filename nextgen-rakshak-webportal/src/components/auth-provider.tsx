"use client";

import { createContext, useContext, useEffect, useRef, useState } from "react";
import { onIdTokenChanged, type User } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { ensureOfficerRole, hasOfficerRole, handleRedirectResult } from "@/lib/auth";
import { subscribeOfficer } from "@/lib/officers";
import type { Officer } from "@/types";

interface AuthState {
  user: User | null;
  /** True only when the signed-in user carries the `police` custom claim. */
  isOfficer: boolean;
  /** Live `officers/{uid}` record; null until the Cloud Function writes it. */
  officer: Officer | null;
  /** Set when the officer record could not be read or created. */
  officerError: string | null;
  /** True until the session *and* its claim have been resolved. */
  loading: boolean;
}

const AuthContext = createContext<AuthState>({
  user: null,
  isOfficer: false,
  officer: null,
  officerError: null,
  loading: true,
});

type Session = Pick<AuthState, "user" | "isOfficer" | "loading">;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session>({
    user: null,
    isOfficer: false,
    loading: true,
  });
  const [officer, setOfficer] = useState<Officer | null>(null);
  const [officerError, setOfficerError] = useState<string | null>(null);
  // uid whose missing record we already tried to backfill — one attempt only.
  const backfilled = useRef<string | null>(null);
  // Monotonic id of the most recent auth event; see the guard in the effect.
  const latestEvent = useRef(0);

  useEffect(() => {
    handleRedirectResult().catch(() => {});

    // A session is restored on reload without going through signInWithGoogle,
    // so the claim is re-checked here rather than trusted from sign-in alone.
    // onIdTokenChanged (not onAuthStateChanged) so that the forced token
    // refresh after claimOfficerRole re-runs the claim check immediately.
    return onIdTokenChanged(auth, async (user) => {
      // The claim check is async, and sign-in immediately followed by the
      // forced refresh fires two events in quick succession. Without this
      // generation guard the slower one can land last and write a stale
      // isOfficer — locking an officer out of their own session.
      const generation = ++latestEvent.current;
      const isCurrent = () => generation === latestEvent.current;

      if (!user) {
        if (isCurrent()) setSession({ user: null, isOfficer: false, loading: false });
        return;
      }
      let isOfficer = false;
      try {
        isOfficer = await hasOfficerRole(user);
      } catch {
        isOfficer = false;
      }
      if (isCurrent()) setSession({ user, isOfficer, loading: false });
    });
  }, []);

  const uid = session.user?.uid;
  const isOfficer = session.isOfficer;

  useEffect(() => {
    // Only officers may read the directory, so the listener is attached after
    // the claim check — otherwise the rules reject it.
    if (!uid || !isOfficer) {
      setOfficer(null);
      setOfficerError(null);
      // Clear the one-attempt guard on sign-out, so signing back in really does
      // retry the backfill — which is what the profile page tells them to do.
      backfilled.current = null;
      return;
    }
    setOfficerError(null);

    return subscribeOfficer(
      uid,
      (record) => {
        setOfficer(record);
        if (record || backfilled.current === uid) return;

        // The claim exists but the record does not — an account that was made
        // police before the officers collection did. A restored session never
        // calls claimOfficerRole, so without this the profile page waits on a
        // document that will never arrive. Create it once.
        backfilled.current = uid;
        const current = auth.currentUser;
        if (!current) return;
        ensureOfficerRole(current).catch((err) =>
          setOfficerError(
            err instanceof Error ? err.message : "Could not create your officer record."
          )
        );
      },
      (err) => setOfficerError(err.message || "Could not read your officer record.")
    );
  }, [uid, isOfficer]);

  return (
    <AuthContext.Provider value={{ ...session, officer, officerError }}>
      {children}
    </AuthContext.Provider>
  );
}

/** Access the current auth state (user + officer role + profile + loading). */
export function useAuth() {
  return useContext(AuthContext);
}
