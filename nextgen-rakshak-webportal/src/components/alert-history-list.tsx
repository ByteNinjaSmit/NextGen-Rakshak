"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ChevronLeft, ChevronRight, Inbox, Search } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { AlertDetailDialog } from "@/components/alert-detail-dialog";
import { useAllAlerts } from "@/hooks/use-alerts";
import { timeAgo } from "@/lib/utils";
import type { Alert, AlertStatus } from "@/types";

const PAGE_SIZE = 10;

function isAlertStatus(value: string | null): value is AlertStatus {
  return value === "active" || value === "resolved";
}

const statusFilterOptions: { value: "all" | AlertStatus; label: string }[] = [
  { value: "all", label: "All statuses" },
  { value: "active", label: "Active" },
  { value: "resolved", label: "Resolved" },
];

export function AlertHistoryList() {
  const { alerts, loading } = useAllAlerts();
  const searchParams = useSearchParams();
  const initialStatus = searchParams.get("status");
  const [selected, setSelected] = useState<Alert | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | AlertStatus>(
    isAlertStatus(initialStatus) ? initialStatus : "all"
  );
  const [page, setPage] = useState(1);

  const filteredAlerts = useMemo(() => {
    const q = search.trim().toLowerCase();
    return alerts.filter(
      (a) =>
        (statusFilter === "all" || a.status === statusFilter) &&
        (!q || a.childName.toLowerCase().includes(q))
    );
  }, [alerts, search, statusFilter]);

  useEffect(() => {
    setPage(1);
  }, [search, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filteredAlerts.length / PAGE_SIZE));
  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  const pageStart = (page - 1) * PAGE_SIZE;
  const pagedAlerts = filteredAlerts.slice(pageStart, pageStart + PAGE_SIZE);

  if (loading) return <p className="text-sm text-muted-foreground">Loading alert history…</p>;
  if (alerts.length === 0)
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
          <Inbox className="h-8 w-8 text-muted-foreground/50" />
          No alerts have been filed yet.
        </CardContent>
      </Card>
    );

  return (
    <>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search child name…"
            className="pl-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as "all" | AlertStatus)}>
          <SelectTrigger className="w-full sm:w-44">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            {statusFilterOptions.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {filteredAlerts.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
            <Inbox className="h-8 w-8 text-muted-foreground/50" />
            No alerts match your filters.
          </CardContent>
        </Card>
      )}

      {filteredAlerts.length > 0 && (
        <>
      {/* Desktop / tablet: full table */}
      <Card className="hidden md:block">
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
              {pagedAlerts.map((alert) => (
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

      {/* Mobile: stacked cards */}
      <div className="grid gap-3 md:hidden">
        {pagedAlerts.map((alert) => (
          <Card key={alert.id}>
            <CardContent className="space-y-2 p-4">
              <div className="flex items-center justify-between gap-2">
                <p className="truncate font-semibold">{alert.childName}</p>
                <Badge variant={alert.status === "active" ? "destructive" : "success"}>
                  {alert.status === "active" ? "Active" : "Resolved"}
                </Badge>
              </div>
              <p className="text-sm text-muted-foreground">
                {alert.age} yrs · {alert.lastSeen}
              </p>
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>{alert.createdBy?.name ?? "—"}</span>
                <span>{timeAgo(alert.timestamp)}</span>
              </div>
              <Button size="sm" variant="outline" className="w-full" onClick={() => setSelected(alert)}>
                View details
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

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
        </>
      )}

      <AlertDetailDialog alert={selected} onOpenChange={(open) => !open && setSelected(null)} />
    </>
  );
}
