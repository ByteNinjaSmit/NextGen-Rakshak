"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { useRouter } from "next/navigation";
import {
  LayoutDashboard,
  PlusCircle,
  MapPin,
  History,
  LogOut,
  Loader2,
  UserCog,
} from "lucide-react";
import { BrandMark } from "@/components/brand-logo";
import { cn } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { signOutUser } from "@/lib/auth";
import { Button } from "@/components/ui/button";

const links = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/alerts/new", label: "New Alert", icon: PlusCircle },
  { href: "/matches", label: "Live Matches", icon: MapPin },
  { href: "/alerts/history", label: "Alert History", icon: History },
  { href: "/profile", label: "Officer Profile", icon: UserCog },
];

interface SidebarNavProps {
  /** Mobile-only: whether the slide-in drawer is open. Ignored on desktop, where the rail is always visible. */
  mobileOpen: boolean;
  onMobileClose: () => void;
}

export function SidebarNav({ mobileOpen, onMobileClose }: SidebarNavProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, officer } = useAuth();
  const [signingOut, setSigningOut] = useState(false);

  // Close the mobile drawer automatically whenever a link navigates the route.
  useEffect(() => {
    onMobileClose();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  async function onSignOut() {
    setSigningOut(true);
    try {
      await signOutUser();
      // Go straight to /login rather than waiting for the layout guard, so the
      // kiosk never shows a half-torn-down dashboard between the two.
      router.replace("/login");
    } catch {
      setSigningOut(false);
    }
  }

  const initial = (officer?.displayName ?? user?.displayName ?? user?.email ?? "O").trim()[0]?.toUpperCase() ?? "O";

  const content = (
    <div className="flex h-full w-72 shrink-0 flex-col bg-sidebar text-sidebar-foreground lg:w-64">
      <div className="flex items-center gap-3 border-b border-sidebar-border px-6 py-5">
        <BrandMark size={36} className="rounded-lg bg-white/95 p-1" />
        <div>
          <p className="text-lg font-bold leading-tight text-white">Rakshak</p>
          <p className="text-xs text-sidebar-foreground/70">Police Kiosk</p>
        </div>
      </div>
      <nav className="flex-1 space-y-1 overflow-y-auto p-3 scrollbar-thin">
        {links.map(({ href, label, icon: Icon }) => {
          const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                active
                  ? "bg-sidebar-active text-white"
                  : "text-sidebar-foreground/80 hover:bg-white/10 hover:text-white"
              )}
            >
              {active && (
                <span className="absolute inset-y-1.5 left-0 w-1 rounded-full bg-white" aria-hidden />
              )}
              <Icon className="h-4 w-4 shrink-0" />
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="space-y-3 border-t border-sidebar-border p-4">
        {user && (
          <div className="flex items-center gap-3" title={user.email ?? ""}>
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/10 text-sm font-semibold text-white">
              {initial}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-white">
                {officer?.displayName ?? user.displayName ?? user.email}
              </p>
              <p className="truncate text-xs text-sidebar-foreground/60">
                {officer?.station || officer?.badgeNumber || user.email}
              </p>
            </div>
          </div>
        )}
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start text-sidebar-foreground/80 hover:bg-white/10 hover:text-white"
          disabled={signingOut}
          onClick={onSignOut}
        >
          {signingOut ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
          Sign out
        </Button>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop: static rail, always visible */}
      <aside className="hidden lg:flex">{content}</aside>

      {/* Mobile: slide-in drawer with backdrop */}
      <div
        className={cn(
          "fixed inset-0 z-50 lg:hidden",
          mobileOpen ? "pointer-events-auto" : "pointer-events-none"
        )}
        aria-hidden={!mobileOpen}
      >
        <div
          className={cn(
            "absolute inset-0 bg-black/50 transition-opacity duration-300",
            mobileOpen ? "opacity-100" : "opacity-0"
          )}
          onClick={onMobileClose}
        />
        <div
          className={cn(
            "absolute inset-y-0 left-0 shadow-elevated transition-transform duration-300 ease-out",
            mobileOpen ? "translate-x-0" : "-translate-x-full"
          )}
        >
          {content}
        </div>
      </div>
    </>
  );
}
