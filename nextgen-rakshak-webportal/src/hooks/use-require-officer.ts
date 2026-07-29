"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/components/auth-provider";

/**
 * Client-side route guard for the kiosk.
 *
 * Firebase web auth keeps its session in IndexedDB, not in a cookie, so Next
 * middleware cannot see it — the redirect has to happen here. Firestore rules
 * enforce the same `police` claim server-side, so this is UX, not the security
 * boundary.
 *
 * Sends unauthorised visitors to `/login?next=<path>` so they land back on the
 * page they asked for after signing in.
 */
export function useRequireOfficer() {
  const { user, isOfficer, loading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const allowed = !!user && isOfficer;

  useEffect(() => {
    if (loading || allowed) return;
    const next = pathname && pathname !== "/" ? `?next=${encodeURIComponent(pathname)}` : "";
    router.replace(`/login${next}`);
  }, [loading, allowed, pathname, router]);

  return { ready: !loading && allowed, loading };
}
