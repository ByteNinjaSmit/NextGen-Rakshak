"use client";

import { Loader2 } from "lucide-react";
import { useAuth } from "@/components/auth-provider";
import { LoginScreen } from "@/components/login-screen";
import { SidebarNav } from "@/components/sidebar-nav";

/** Gates the kiosk: shows the Google login until an officer is signed in. */
export function AuthGate({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/30">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!user) return <LoginScreen />;

  return (
    <div className="flex h-screen overflow-hidden">
      <SidebarNav />
      <main className="flex-1 overflow-y-auto bg-muted/30 p-8">{children}</main>
    </div>
  );
}
