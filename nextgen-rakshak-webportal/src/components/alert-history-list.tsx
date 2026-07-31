"use client";

import { useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { AlertDetailDialog } from "@/components/alert-detail-dialog";
import { useAllAlerts } from "@/hooks/use-alerts";
import { timeAgo } from "@/lib/utils";
import type { Alert } from "@/types";

export function AlertHistoryList() {
  const { alerts, loading } = useAllAlerts();
  const [selected, setSelected] = useState<Alert | null>(null);

  if (loading) return <p className="text-sm text-muted-foreground">Loading alert history…</p>;
  if (alerts.length === 0)
    return (
      <Card>
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          No alerts have been filed yet.
        </CardContent>
      </Card>
    );

  return (
    <>
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Alert ID</TableHead>
                <TableHead>Child Name</TableHead>
                <TableHead>Age</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Time</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Reported By</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {alerts.map((alert) => (
                <TableRow key={alert.id}>
                  <TableCell className="font-mono text-xs text-muted-foreground">
                    {alert.id.slice(0, 8)}
                  </TableCell>
                  <TableCell className="font-medium">{alert.childName}</TableCell>
                  <TableCell>{alert.age}</TableCell>
                  <TableCell className="max-w-[200px] truncate">{alert.lastSeen}</TableCell>
                  <TableCell className="text-muted-foreground">{timeAgo(alert.timestamp)}</TableCell>
                  <TableCell>
                    <Badge variant={alert.status === "active" ? "destructive" : "success"}>
                      {alert.status === "active" ? "Active" : "Resolved"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {alert.createdBy?.name ?? "—"}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="outline" onClick={() => setSelected(alert)}>
                      View
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <AlertDetailDialog alert={selected} onOpenChange={(open) => !open && setSelected(null)} />
    </>
  );
}
