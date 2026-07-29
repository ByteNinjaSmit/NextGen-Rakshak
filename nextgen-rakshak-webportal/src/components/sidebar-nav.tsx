"use client";

import Link from "next/link";
import { useState } from "react";
import { usePathname } from "next/navigation";
import { LayoutDashboard, PlusCircle, MapPin, ShieldAlert, LogOut, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/components/auth-provider";
import { signOutUser } from "@/lib/auth";
import { Button } from "@/components/ui/button";

const links = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/alerts/new", label: "New Alert", icon: PlusCircle },
  { href: "/matches", label: "Live Matches", icon: MapPin },
];

export function SidebarNav() {
  const pathname = usePathname();
  const { user } = useAuth();
  const [signingOut, setSigningOut] = useState(false);

  async function onSignOut() {
    setSigningOut(true);
    try {
      await signOutUser();
    } catch {
      setSigningOut(false);
    }
  }

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r bg-card">
      <div className="flex items-center gap-2 border-b px-6 py-5">
        <ShieldAlert className="h-6 w-6 text-primary" />
        <div>
          <p className="text-lg font-bold leading-tight">Rakshak</p>
          <p className="text-xs text-muted-foreground">Police Kiosk</p>
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
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              )}
            >
              <Icon className="h-4 w-4" />
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="space-y-2 border-t p-4">
        {user && (
          <div className="truncate text-xs text-muted-foreground" title={user.email ?? ""}>
            {user.displayName ?? user.email}
          </div>
        )}
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start text-muted-foreground"
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
