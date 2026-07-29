"use client";

import { Users, Bell, MapPin } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useActiveAlerts, useMatchCounts } from "@/hooks/use-alerts";

export function StatsCards() {
  const { alerts } = useActiveAlerts();
  // Server-side aggregates, not the length of the capped live feed. Null until
  // the first read resolves — shown as a dash rather than a misleading zero.
  const counts = useMatchCounts();

  const stats = [
    { label: "Active Alerts", value: alerts.length, icon: Bell },
    { label: "Total Matches", value: counts?.total ?? "—", icon: MapPin },
    { label: "Awaiting Dispatch", value: counts?.pending ?? "—", icon: Users },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      {stats.map(({ label, value, icon: Icon }) => (
        <Card key={label}>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
            <Icon className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{value}</div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
