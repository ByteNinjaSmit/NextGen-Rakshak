"use client";

import { FullScreenLoader } from "@/components/full-screen-loader";
import { SidebarNav } from "@/components/sidebar-nav";
import { useRequireOfficer } from "@/hooks/use-require-officer";

/**
 * Shell for every authenticated kiosk route. Renders nothing but a spinner
 * until the `police` claim is confirmed, so no protected content flashes
 * before the redirect to /login.
 */
export default function KioskLayout({ children }: { children: React.ReactNode }) {
  const { ready } = useRequireOfficer();

  if (!ready) return <FullScreenLoader />;

  return (
    <div className="flex h-screen animate-in fade-in overflow-hidden duration-300">
      <SidebarNav />
      <main className="flex-1 overflow-y-auto bg-muted/30 p-8">{children}</main>
    </div>
  );
}
