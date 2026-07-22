"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { onAuthStateChanged, type User } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { hasOfficerRole } from "@/lib/auth";

interface AuthState {
  user: User | null;
  /** True only when the signed-in user carries the `police` custom claim. */
  isOfficer: boolean;
  loading: boolean;
}

const AuthContext = createContext<AuthState>({
  user: null,
  isOfficer: false,
  loading: true,
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    isOfficer: false,
    loading: true,
  });

  useEffect(() => {
    // A session is restored on reload without going through signInWithGoogle,
    // so the claim is re-checked here rather than trusted from sign-in alone.
    return onAuthStateChanged(auth, async (user) => {
      if (!user) {
        setState({ user: null, isOfficer: false, loading: false });
        return;
      }
      let isOfficer = false;
      try {
        isOfficer = await hasOfficerRole(user);
      } catch {
        isOfficer = false;
      }
      setState({ user, isOfficer, loading: false });
    });
  }, []);

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}

/** Access the current auth state (user + officer role + loading). */
export function useAuth() {
  return useContext(AuthContext);
}
