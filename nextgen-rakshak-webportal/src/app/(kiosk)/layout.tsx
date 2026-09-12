"use client";

import { useState } from "react";
import { Menu } from "lucide-react";
import { FullScreenLoader } from "@/components/full-screen-loader";
import { SidebarNav } from "@/components/sidebar-nav";
import { NotificationBell } from "@/components/notification-bell";
import { BrandMark } from "@/components/brand-logo";
import { Button } from "@/components/ui/button";
import { useRequireOfficer } from "@/hooks/use-require-officer";

/**
 * Shell for every authenticated kiosk route. Renders nothing but a spinner
 * until the `police` claim is confirmed, so no protected content flashes
 * before the redirect to /login.
 */
export default function KioskLayout({ children }: { children: React.ReactNode }) {
  const { ready } = useRequireOfficer();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  if (!ready) return <FullScreenLoader />;

  return (
    <div className="flex h-dvh animate-in fade-in overflow-hidden bg-background duration-300">
      <SidebarNav mobileOpen={mobileNavOpen} onMobileClose={() => setMobileNavOpen(false)} />
      {/*
        min-h-0 overrides the flex default of min-height:auto, which otherwise
        lets this column grow to fit `main`'s content instead of respecting
        the h-dvh row above it — without it, tall dashboard content pushes the
        whole page (sidebar included) into document scroll instead of
        scrolling only inside `main`.
      */}
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-14 shrink-0 items-center gap-3 border-b bg-card/80 px-4 backdrop-blur supports-[backdrop-filter]:bg-card/60 sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="-ml-2 lg:hidden"
            onClick={() => setMobileNavOpen(true)}
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <div className="flex items-center gap-2 lg:hidden">
            <BrandMark size={22} />
            <span className="text-sm font-semibold">Rakshak</span>
          </div>
          <div className="ml-auto flex items-center gap-2">
            <NotificationBell />
          </div>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}
