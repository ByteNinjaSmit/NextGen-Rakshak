"use client";

import Link from "next/link";
import { useState } from "react";
import { usePathname } from "next/navigation";
import { useRouter } from "next/navigation";
import {
  LayoutDashboard,
  PlusCircle,
  MapPin,
  ShieldAlert,
  LogOut,
  Loader2,
  UserCog,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { signOutUser } from "@/lib/auth";
import { Button } from "@/components/ui/button";

const links = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/alerts/new", label: "New Alert", icon: PlusCircle },
  { href: "/matches", label: "Live Matches", icon: MapPin },
  { href: "/profile", label: "Officer Profile", icon: UserCog },
];

export function SidebarNav() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, officer } = useAuth();
  const [signingOut, setSigningOut] = useState(false);

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

  return (
    <aside className="flex w-64 shrink-0 flex-col bg-[#0b1e3f] text-slate-200">
      <div className="flex items-center gap-2 border-b border-white/10 px-6 py-5">
        <ShieldAlert className="h-6 w-6 text-white" />
        <div>
          <p className="text-lg font-bold leading-tight text-white">Rakshak</p>
          <p className="text-xs text-slate-400">Police Kiosk</p>
        </div>
      </div>
      <nav className="flex-1 space-y-1 p-3">
        {links.map(({ href, label, icon: Icon }) => {
          const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-[#1c3a6e] text-white"
                  : "text-slate-300 hover:bg-white/10 hover:text-white"
              )}
            >
              <Icon className="h-4 w-4" />
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="space-y-2 border-t border-white/10 p-4">
        {user && (
          <div className="space-y-0.5" title={user.email ?? ""}>
            <p className="truncate text-xs font-medium text-white">
              {officer?.displayName ?? user.displayName ?? user.email}
            </p>
            <p className="truncate text-xs text-slate-400">
              {officer?.station || officer?.badgeNumber || user.email}
            </p>
          </div>
        )}
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start text-slate-300 hover:bg-white/10 hover:text-white"
          disabled={signingOut}
          onClick={onSignOut}
        >
          {signingOut ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
          Sign out
        </Button>
      </div>
    </aside>
  );
}
