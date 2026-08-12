"use client";

/* eslint-disable @next/next/no-img-element */
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/confirm-dialog";
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
    <div className="grid gap-3">
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
              {alert.createdBy && (
                <p className="mt-1 truncate text-xs text-muted-foreground">
                  Filed by {alert.createdBy.name}
                  {alert.createdBy.station && ` · ${alert.createdBy.station}`}
                </p>
              )}
              <div className="mt-2 flex items-center justify-between">
                <span className="text-xs text-muted-foreground">{timeAgo(alert.timestamp)}</span>
                {/*
                  Resolving is destructive: the Cloud Function purges the photo
                  and the embedding, which ends all matching for this child.
                  Gate it behind an explicit confirmation.
                */}
                <ConfirmDialog
                  destructive
                  trigger={
                    <Button size="sm" variant="outline">
                      Resolve
                    </Button>
                  }
                  title={`Resolve the alert for ${alert.childName}?`}
                  description={
                    <>
                      This closes the case and <strong>permanently deletes</strong> the child&apos;s
                      photo and face embedding. Volunteers will stop being able to match{" "}
                      {alert.childName}, and this cannot be undone. Only resolve once the child is
                      safely with their guardian.
                    </>
                  }
                  confirmLabel="Resolve and purge"
                  onConfirm={() => resolveAlert(alert.id)}
                />
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
