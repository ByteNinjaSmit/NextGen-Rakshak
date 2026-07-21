"use client";

/* eslint-disable @next/next/no-img-element */
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useActiveAlerts } from "@/hooks/use-alerts";
import { resolveAlert } from "@/lib/firestore";
import { timeAgo } from "@/lib/utils";

export function ActiveAlertsList() {
  const { alerts, loading } = useActiveAlerts();

  if (loading) return <p className="text-sm text-muted-foreground">Loading alerts…</p>;
  if (alerts.length === 0)
    return (
      <Card>
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          No active alerts. Create one from <span className="font-medium">New Alert</span>.
        </CardContent>
      </Card>
    );

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {alerts.map((alert) => (
        <Card key={alert.id}>
          <CardContent className="flex gap-4 p-4">
            <img
              src={alert.imageUrl}
              alt={alert.childName}
              className="h-20 w-20 shrink-0 rounded-md object-cover"
            />
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-2">
                <p className="truncate font-semibold">{alert.childName}</p>
                <Badge variant="destructive">Active</Badge>
              </div>
              <p className="text-sm text-muted-foreground">
                {alert.age} yrs · {alert.gender}
              </p>
              <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{alert.clothingDesc}</p>
              <div className="mt-2 flex items-center justify-between">
                <span className="text-xs text-muted-foreground">{timeAgo(alert.timestamp)}</span>
                <Button size="sm" variant="outline" onClick={() => resolveAlert(alert.id)}>
                  Resolve
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
