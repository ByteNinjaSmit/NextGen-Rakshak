"use client";

import { useState } from "react";
import { ShieldAlert, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/components/auth-provider";
import { SignInCancelledError, ensureOfficerRole, signInWithGoogle, signOutUser } from "@/lib/auth";

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden>
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.27-4.74 3.27-8.1Z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23Z"
      />
      <path
        fill="#FBBC05"
        d="M5.84 14.1a6.6 6.6 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84Z"
      />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38Z"
      />
    </svg>
  );
}

export function LoginScreen() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A session can survive with no `police` claim (the claim call failed, or the
  // account signed in through another Rakshak app). Retry the grant instead of
  // asking for the Google popup again.
  const needsRetry = !!user;

  async function onSignIn() {
    setError(null);
    setLoading(true);
    try {
      if (user) await ensureOfficerRole(user);
      else await signInWithGoogle();
    } catch (err) {
      // A closed/blocked popup isn't a failure worth surfacing — just let them retry.
      if (!(err instanceof SignInCancelledError)) {
        setError(err instanceof Error ? err.message : "Sign-in failed.");
      }
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen animate-in fade-in items-center justify-center bg-muted/30 p-6 duration-300">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <ShieldAlert className="mb-2 h-10 w-10 text-primary" />
          <CardTitle className="text-xl">Rakshak Police Kiosk</CardTitle>
          <CardDescription>
            {needsRetry
              ? `Signed in as ${user?.email ?? "this account"}, but it is not registered as a police account yet.`
              : "Sign in with your authorised Google account to create alerts and track matches."}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Button onClick={onSignIn} disabled={loading} className="w-full" variant="outline">
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <GoogleIcon />}
            {needsRetry ? "Register this account" : "Continue with Google"}
          </Button>
          {needsRetry && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full text-muted-foreground"
              disabled={loading}
              onClick={() => signOutUser()}
            >
              Use a different account
            </Button>
          )}
          {error && <p className="text-center text-sm text-destructive">{error}</p>}
        </CardContent>
      </Card>
    </div>
  );
}
