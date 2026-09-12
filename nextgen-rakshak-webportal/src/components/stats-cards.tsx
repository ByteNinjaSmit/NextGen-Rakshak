"use client";

import Link from "next/link";
import { Users, Bell, MapPin, CheckCircle2, Navigation, ShieldCheck } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { useActiveAlerts, useAllAlerts, useMatchCounts } from "@/hooks/use-alerts";
import { cn } from "@/lib/utils";

export function StatsCards() {
  const { alerts: activeAlerts } = useActiveAlerts();
  const { alerts: allAlerts } = useAllAlerts();
  // Server-side aggregates, not the length of the capped live feed. Null until
  // the first read resolves — shown as a dash rather than a misleading zero.
  const counts = useMatchCounts();
  const resolvedAlerts = allAlerts.length ? allAlerts.length - activeAlerts.length : 0;

  const stats = [
    {
      label: "Active Alerts",
      value: activeAlerts.length,
      icon: Bell,
      iconBg: "bg-destructive/10 text-destructive",
      href: "/",
    },
    {
      label: "Resolved Alerts",
      value: resolvedAlerts,
      icon: ShieldCheck,
      iconBg: "bg-success/10 text-success",
      href: "/alerts/history?status=resolved",
    },
    {
      label: "Total Matches",
      value: counts?.total ?? "—",
      icon: MapPin,
      iconBg: "bg-info/10 text-info",
      href: "/matches",
    },
    {
      label: "Awaiting Dispatch",
      value: counts?.pending ?? "—",
      icon: Users,
      iconBg: "bg-warning/15 text-warning-foreground",
      href: "/matches?status=pending",
    },
    {
      label: "Dispatched",
      value: counts?.dispatched ?? "—",
      icon: Navigation,
      iconBg: "bg-info/10 text-info",
      href: "/matches?status=dispatched",
    },
    {
      label: "Confirmed Found",
      value: counts?.accepted ?? "—",
      icon: CheckCircle2,
      iconBg: "bg-success/10 text-success",
      href: "/matches?status=accepted",
    },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {stats.map(({ label, value, icon: Icon, iconBg, href }) => (
        <Link key={label} href={href} className="group block">
          <Card className="transition-all group-hover:-translate-y-0.5 group-hover:shadow-elevated">
            <CardContent className="flex items-center gap-4 p-5">
              <div
                className={cn(
                  "flex h-11 w-11 shrink-0 items-center justify-center rounded-xl transition-transform group-hover:scale-105",
                  iconBg
                )}
              >
                <Icon className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-muted-foreground">{label}</p>
                <p className="text-2xl font-bold tabular-nums tracking-tight">{value}</p>
              </div>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  );
}
