"use client";

import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/components/auth-provider";
import { FullScreenLoader } from "@/components/full-screen-loader";
import { LoginScreen } from "@/components/login-screen";

/**
 * `next` comes from the URL, so it is only honoured when it is a same-origin
 * absolute path — otherwise a crafted link could bounce an officer off-site
 * straight after sign-in.
 */
function safeNext(raw: string | null): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return "/";
  return raw;
}

function LoginPageContent() {
  const { user, isOfficer, loading } = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const signedIn = !!user && isOfficer;

  useEffect(() => {
    if (!loading && signedIn) router.replace(next);
  }, [loading, signedIn, next, router]);

  // Hold the spinner through the redirect so the login card never flashes for
  // an officer who already has a session.
  if (loading || signedIn) return <FullScreenLoader />;

  return <LoginScreen />;
}

export default function LoginPage() {
  // useSearchParams opts the subtree into client rendering; the Suspense
  // boundary keeps the rest of the route statically prerenderable.
  return (
    <Suspense fallback={<FullScreenLoader />}>
      <LoginPageContent />
    </Suspense>
  );
}
