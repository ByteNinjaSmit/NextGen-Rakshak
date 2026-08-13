"use client";

import { FullScreenLoader } from "@/components/full-screen-loader";
import { SidebarNav } from "@/components/sidebar-nav";
import { NotificationBell } from "@/components/notification-bell";
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
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-14 shrink-0 items-center justify-end border-b bg-white px-6">
          <NotificationBell />
        </header>
        <main className="flex-1 overflow-y-auto bg-white p-8">{children}</main>
      </div>
    </div>
  );
}
