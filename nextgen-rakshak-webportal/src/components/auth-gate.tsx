"use client";

import { Loader2 } from "lucide-react";
import { useAuth } from "@/components/auth-provider";
import { LoginScreen } from "@/components/login-screen";
import { SidebarNav } from "@/components/sidebar-nav";

/**
 * Gates the kiosk. Requires both a signed-in Google account **and** the `police`
 * custom claim — an authenticated but unauthorised account is shown the login
 * screen, never the kiosk. Firestore rules enforce the same claim server-side,
 * so this is a UX guard rather than the security boundary.
 */
export function AuthGate({ children }: { children: React.ReactNode }) {
  const { user, isOfficer, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/30">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!user || !isOfficer) return <LoginScreen />;

  return (
    <div className="flex h-screen animate-in fade-in overflow-hidden duration-300">
      <SidebarNav />
      <main className="flex-1 overflow-y-auto bg-muted/30 p-8">{children}</main>
    </div>
  );
}
