"use client";

import { useState } from "react";
import { FullScreenLoader } from "@/components/full-screen-loader";
import { SidebarNav } from "@/components/sidebar-nav";
import { NotificationBell } from "@/components/notification-bell";
import { useRequireOfficer } from "@/hooks/use-require-officer";
import { Menu } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Shell for every authenticated kiosk route. Renders nothing but a spinner
 * until the `police` claim is confirmed, so no protected content flashes
 * before the redirect to /login.
 */
export default function KioskLayout({ children }: { children: React.ReactNode }) {
  const { ready } = useRequireOfficer();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  if (!ready) return <FullScreenLoader />;

  return (
    <div className="flex h-screen animate-in fade-in overflow-hidden duration-300 relative">
      {/* Mobile Sidebar Overlay */}
      {sidebarOpen && (
        <div 
          className="fixed inset-0 z-40 bg-black/50 md:hidden" 
          onClick={() => setSidebarOpen(false)} 
        />
      )}

      {/* Sidebar - hidden on mobile unless open */}
      <div 
        className={`fixed inset-y-0 left-0 z-50 transform transition-transform duration-300 md:relative md:translate-x-0 ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <SidebarNav onClose={() => setSidebarOpen(false)} />
      </div>

      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-14 shrink-0 items-center justify-between border-b bg-white px-4 md:px-6 md:justify-end">
          <Button 
            variant="ghost" 
            size="icon" 
            className="md:hidden"
            onClick={() => setSidebarOpen(true)}
          >
            <Menu className="h-5 w-5" />
            <span className="sr-only">Toggle Menu</span>
          </Button>
          <NotificationBell />
        </header>
        <main className="flex-1 overflow-y-auto bg-white p-4 md:p-8">{children}</main>
      </div>
    </div>
  );
}
