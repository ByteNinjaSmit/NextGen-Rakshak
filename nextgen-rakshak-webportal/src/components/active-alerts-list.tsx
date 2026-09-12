"use client";

/* eslint-disable @next/next/no-img-element */
import { useEffect, useMemo, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { useActiveAlerts } from "@/hooks/use-alerts";
import { resolveAlert } from "@/lib/firestore";
import { timeAgo } from "@/lib/utils";
import { ChevronLeft, ChevronRight, Inbox, Search } from "lucide-react";

/** Search box only earns its keep once there are enough active alerts to scroll past. */
const SEARCH_THRESHOLD = 7;
const PAGE_SIZE = 9;

export function ActiveAlertsList() {
  const { alerts, loading } = useActiveAlerts();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);

  const filteredAlerts = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return alerts;
    return alerts.filter((a) => a.childName.toLowerCase().includes(q));
  }, [alerts, search]);

  useEffect(() => {
    setPage(1);
  }, [search]);

  const totalPages = Math.max(1, Math.ceil(filteredAlerts.length / PAGE_SIZE));
  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  const pageStart = (page - 1) * PAGE_SIZE;
  const pagedAlerts = filteredAlerts.slice(pageStart, pageStart + PAGE_SIZE);

  if (loading)
    return (
      <div className="space-y-3">
        {[0, 1].map((i) => (
          <Card key={i}>
            <CardContent className="flex gap-4 p-4">
              <div className="h-20 w-20 shrink-0 animate-pulse rounded-lg bg-muted" />
              <div className="flex-1 space-y-2 py-1">
                <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
                <div className="h-3 w-1/3 animate-pulse rounded bg-muted" />
                <div className="h-3 w-2/3 animate-pulse rounded bg-muted" />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    );
  if (alerts.length === 0)
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
          <Inbox className="h-8 w-8 text-muted-foreground/50" />
          No active alerts. Create one from <span className="font-medium text-foreground">New Alert</span>.
        </CardContent>
      </Card>
    );

  return (
    <div className="grid gap-3">
      {alerts.length > SEARCH_THRESHOLD && (
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search child name…"
            className="pl-9"
          />
        </div>
      )}

      {filteredAlerts.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
            <Inbox className="h-8 w-8 text-muted-foreground/50" />
            No active alerts match &quot;{search}&quot;.
          </CardContent>
        </Card>
      )}

      {pagedAlerts.map((alert) => (
        <Card key={alert.id} className="transition-shadow hover:shadow-elevated">
          <CardContent className="flex gap-4 p-4">
            <img
              src={alert.imageUrl}
              alt={alert.childName}
              className="h-20 w-20 shrink-0 rounded-lg object-cover"
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

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-3 pt-1">
          <p className="text-xs text-muted-foreground">
            Showing {pageStart + 1}–{Math.min(pageStart + PAGE_SIZE, filteredAlerts.length)} of{" "}
            {filteredAlerts.length}
          </p>
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              <ChevronLeft className="h-4 w-4" />
              Prev
            </Button>
            <span className="text-xs text-muted-foreground tabular-nums">
              Page {page} of {totalPages}
            </span>
            <Button
              size="sm"
              variant="outline"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              Next
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
