"use client";

import { Users, Bell, MapPin } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useActiveAlerts, useMatches } from "@/hooks/use-alerts";

export function StatsCards() {
  const { alerts } = useActiveAlerts();
  const { matches } = useMatches();

  const pending = matches.filter((m) => m.status === "pending").length;

  const stats = [
    { label: "Active Alerts", value: alerts.length, icon: Bell },
    { label: "Total Matches", value: matches.length, icon: MapPin },
    { label: "Awaiting Dispatch", value: pending, icon: Users },
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
