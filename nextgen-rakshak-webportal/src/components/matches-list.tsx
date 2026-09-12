"use client";

/* eslint-disable @next/next/no-img-element */
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  ChevronLeft,
  ChevronRight,
  ImageOff,
  Navigation,
  RotateCw,
  Inbox,
  Search,
  Users,
  X,
} from "lucide-react";
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
import { MatchReviewDialog } from "@/components/match-review-dialog";
import { useMatches, useVolunteer } from "@/hooks/use-alerts";
import { dispatchMatch } from "@/lib/firestore";
import { cn } from "@/lib/utils";
import { timeAgo } from "@/lib/utils";
import type { BadgeProps } from "@/components/ui/badge";
import type { Match, MatchStatus } from "@/types";

/** Chip search box only earns its keep once there are enough children to scroll past. */
const CHILD_SEARCH_THRESHOLD = 7;
const PAGE_SIZE_OPTIONS = [10, 15, 25, 50] as const;
const DEFAULT_PAGE_SIZE = 15;

function isMatchStatus(value: string | null): value is MatchStatus {
  return value === "pending" || value === "dispatched" || value === "accepted" || value === "dismissed";
}

const statusFilterOptions: { value: "all" | MatchStatus; label: string }[] = [
  { value: "all", label: "All statuses" },
  { value: "pending", label: "Pending" },
  { value: "dispatched", label: "Dispatched" },
  { value: "accepted", label: "Accepted" },
  { value: "dismissed", label: "Dismissed" },
];

function mapsUrl(match: Match) {
  const { latitude, longitude } = match.location;
  return `https://www.google.com/maps?q=${latitude},${longitude}`;
}

/** Absent `hasLocation` means the match predates the field — treat as located. */
function hasUsableLocation(match: Match) {
  return match.hasLocation !== false;
}

const statusVariant: Record<MatchStatus, BadgeProps["variant"]> = {
  pending: "warning",
  dispatched: "info",
  accepted: "success",
  dismissed: "destructive",
};

function MatchThumb({ match }: { match: Match }) {
  if (!match.imageUrl)
    return (
      <div
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted"
        title="Photo deleted — case resolved"
      >
        <ImageOff className="h-4 w-4 text-muted-foreground" />
      </div>
    );
  return <img src={match.imageUrl} alt={match.childName} className="h-10 w-10 shrink-0 rounded-lg object-cover" />;
}

/**
 * Name/role come straight off the match (denormalised at write time); phone
 * and email are a live lookup of the volunteer's account (`volunteers/{uid}`)
 * — the device data behind the sighting, not just what was stamped onto it.
 */
function ReportedBy({ match }: { match: Match }) {
  const { volunteer } = useVolunteer(match.volunteerId);
  return (
    <div className="leading-tight">
      {match.volunteerName || volunteer?.name ? (
        <>
          <div className="font-medium">{match.volunteerName || volunteer?.name}</div>
          <div className="text-xs capitalize text-muted-foreground">{match.volunteerRole}</div>
        </>
      ) : (
        <span className="capitalize">{match.volunteerRole}</span>
      )}
      {volunteer?.phone && <div className="text-xs text-muted-foreground">{volunteer.phone}</div>}
      {volunteer?.email && <div className="text-xs text-muted-foreground">{volunteer.email}</div>}
      {match.relayedBy && (
        <span
          className="whitespace-nowrap text-xs text-muted-foreground"
          title="Carried to the server by another volunteer's phone over the offline mesh. The reporter is named by that relay rather than proven by their own session."
        >
          via mesh
        </span>
      )}
    </div>
  );
}

export function MatchesList() {
  const { matches, loading } = useMatches();
  const searchParams = useSearchParams();
  const initialStatus = searchParams.get("status");
  const [failed, setFailed] = useState<string[]>([]);
  const [selectedMatch, setSelectedMatch] = useState<Match | null>(null);
  const [childFilter, setChildFilter] = useState<string>("all");
  const [childSearch, setChildSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | MatchStatus>(
    isMatchStatus(initialStatus) ? initialStatus : "all"
  );
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);

  /**
   * One entry per alert, not per child name — two different alerts can name
   * the same child, and picking "Smitraj" must only ever narrow the list to
   * sightings of that specific alert, not every child sharing a name. The
   * thumbnail is the newest sighting photo for that alert (matches arrive
   * newest-first), so the chip itself doubles as a quick face check.
   */
  const childOptions = useMemo(() => {
    const byAlert = new Map<string, { alertId: string; childName: string; imageUrl: string; count: number }>();
    for (const match of matches) {
      const entry = byAlert.get(match.alertId);
      if (entry) entry.count += 1;
      else
        byAlert.set(match.alertId, {
          alertId: match.alertId,
          childName: match.childName,
          imageUrl: match.imageUrl,
          count: 1,
        });
    }
    return [...byAlert.values()].sort((a, b) => a.childName.localeCompare(b.childName));
  }, [matches]);

  const visibleChildOptions = useMemo(() => {
    const q = childSearch.trim().toLowerCase();
    if (!q) return childOptions;
    return childOptions.filter((c) => c.childName.toLowerCase().includes(q));
  }, [childOptions, childSearch]);

  const filteredMatches = matches.filter(
    (m) =>
      (childFilter === "all" || m.alertId === childFilter) &&
      (statusFilter === "all" || m.status === statusFilter)
  );
  const selectedChildName = childOptions.find((c) => c.alertId === childFilter)?.childName;

  // Reset to page 1 whenever a filter narrows/widens the result set, so a
  // stale page number never lands on an empty page.
  useEffect(() => {
    setPage(1);
  }, [childFilter, statusFilter, pageSize]);

  const totalPages = Math.max(1, Math.ceil(filteredMatches.length / pageSize));
  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  const pageStart = (page - 1) * pageSize;
  const pagedMatches = filteredMatches.slice(pageStart, pageStart + pageSize);

  /**
   * Mark the match dispatched. Deliberately not awaited by the click handler:
   * the anchor must navigate to Maps within the user gesture, so the write runs
   * alongside it and a failure is surfaced in the row instead of being swallowed.
   * Skips the write when the match is already dispatched.
   */
  async function markDispatched(match: Match) {
    if (match.status === "dispatched") return;
    setFailed((prev) => prev.filter((id) => id !== match.id));
    try {
      await dispatchMatch(match.id);
    } catch {
      setFailed((prev) => [...prev, match.id]);
    }
  }

  function DispatchButton({ match }: { match: Match }) {
    return hasUsableLocation(match) ? (
      <Button size="sm" asChild onClick={() => markDispatched(match)}>
        <a href={mapsUrl(match)} target="_blank" rel="noopener noreferrer">
          <Navigation className="h-4 w-4" />
          Dispatch
        </a>
      </Button>
    ) : (
      <Button
        size="sm"
        onClick={() => markDispatched(match)}
        title="Volunteer had no GPS fix — no map location for this sighting"
      >
        <Navigation className="h-4 w-4" />
        Dispatch (no location)
      </Button>
    );
  }

  if (loading) return <p className="text-sm text-muted-foreground">Loading matches…</p>;
  if (matches.length === 0)
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
          <Inbox className="h-8 w-8 text-muted-foreground/50" />
          No matches reported yet. Confirmed matches from volunteers appear here in real time.
        </CardContent>
      </Card>
    );

  return (
    <>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        {childOptions.length > CHILD_SEARCH_THRESHOLD && (
          <div className="relative w-full sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={childSearch}
              onChange={(e) => setChildSearch(e.target.value)}
              placeholder="Search child name…"
              className="pl-9"
            />
          </div>
        )}
        <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as "all" | MatchStatus)}>
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

      <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1 scrollbar-thin">
        <button
          type="button"
          onClick={() => setChildFilter("all")}
          className={cn(
            "flex shrink-0 items-center gap-2 rounded-full border py-1.5 pl-1.5 pr-3 text-sm font-medium transition-colors",
            childFilter === "all"
              ? "border-primary bg-primary text-primary-foreground shadow-sm"
              : "border-border bg-card text-foreground hover:bg-muted"
          )}
        >
          <span
            className={cn(
              "flex h-7 w-7 items-center justify-center rounded-full",
              childFilter === "all" ? "bg-white/20" : "bg-muted"
            )}
          >
            <Users className="h-3.5 w-3.5" />
          </span>
          All children
          <span
            className={cn(
              "rounded-full px-1.5 py-0.5 text-xs font-semibold tabular-nums",
              childFilter === "all" ? "bg-white/20" : "bg-muted text-muted-foreground"
            )}
          >
            {matches.length}
          </span>
        </button>

        {visibleChildOptions.map((c) => {
          const active = childFilter === c.alertId;
          return (
            <button
              key={c.alertId}
              type="button"
              onClick={() => setChildFilter(active ? "all" : c.alertId)}
              className={cn(
                "flex shrink-0 items-center gap-2 rounded-full border py-1.5 pl-1.5 pr-3 text-sm font-medium transition-colors",
                active
                  ? "border-primary bg-primary text-primary-foreground shadow-sm"
                  : "border-border bg-card text-foreground hover:bg-muted"
              )}
            >
              {c.imageUrl ? (
                <img src={c.imageUrl} alt="" className="h-7 w-7 rounded-full object-cover" />
              ) : (
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-muted text-xs font-semibold text-muted-foreground">
                  {c.childName.trim()[0]?.toUpperCase() ?? "?"}
                </span>
              )}
              <span className="max-w-[9rem] truncate">{c.childName}</span>
              {active ? (
                <X className="h-3.5 w-3.5 shrink-0" />
              ) : (
                <span className="rounded-full bg-muted px-1.5 py-0.5 text-xs font-semibold tabular-nums text-muted-foreground">
                  {c.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {filteredMatches.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
            <Inbox className="h-8 w-8 text-muted-foreground/50" />
            No live matches for {selectedChildName ?? "this child"} right now.
          </CardContent>
        </Card>
      ) : (
        <>
      {/* Desktop / tablet: full table */}
      <Card className="hidden md:block">
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Child</TableHead>
                <TableHead>Confidence</TableHead>
                <TableHead>Reported By</TableHead>
                <TableHead>When</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {pagedMatches.map((match) => (
                <TableRow key={match.id}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <MatchThumb match={match} />
                      <span className="font-medium">{match.childName}</span>
                    </div>
                  </TableCell>
                  <TableCell>{Math.round(match.confidence * 100)}%</TableCell>
                  <TableCell>
                    <ReportedBy match={match} />
                  </TableCell>
                  <TableCell className="text-muted-foreground">{timeAgo(match.timestamp)}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariant[match.status]}>{match.status}</Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex flex-col items-end gap-1">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" onClick={() => setSelectedMatch(match)}>
                          Review
                        </Button>
                        <DispatchButton match={match} />
                      </div>
                      {failed.includes(match.id) && (
                        <button
                          type="button"
                          onClick={() => markDispatched(match)}
                          className="flex items-center gap-1 text-xs text-destructive hover:underline"
                        >
                          <RotateCw className="h-3 w-3" />
                          Not marked dispatched — retry
                        </button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Mobile: stacked cards */}
      <div className="grid gap-3 md:hidden">
        {pagedMatches.map((match) => (
          <Card key={match.id}>
            <CardContent className="space-y-3 p-4">
              <div className="flex items-start gap-3">
                <MatchThumb match={match} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate font-semibold">{match.childName}</p>
                    <Badge variant={statusVariant[match.status]}>{match.status}</Badge>
                  </div>
                  <p className="text-sm text-muted-foreground">{Math.round(match.confidence * 100)}% match</p>
                </div>
              </div>
              <div className={cn("flex items-center justify-between text-sm")}>
                <ReportedBy match={match} />
                <span className="text-xs text-muted-foreground">{timeAgo(match.timestamp)}</span>
              </div>
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <Button size="sm" variant="outline" onClick={() => setSelectedMatch(match)}>
                  Review
                </Button>
                <DispatchButton match={match} />
              </div>
              {failed.includes(match.id) && (
                <button
                  type="button"
                  onClick={() => markDispatched(match)}
                  className="flex items-center gap-1 text-xs text-destructive hover:underline"
                >
                  <RotateCw className="h-3 w-3" />
                  Not marked dispatched — retry
                </button>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {filteredMatches.length > 0 && (
        <div className="flex flex-col gap-3 pt-1 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span>
              Showing {pageStart + 1}–{Math.min(pageStart + pageSize, filteredMatches.length)} of{" "}
              {filteredMatches.length}
            </span>
            <span className="flex items-center gap-1.5">
              <label htmlFor="matches-page-size">Rows per page</label>
              <Select value={String(pageSize)} onValueChange={(v) => setPageSize(Number(v))}>
                <SelectTrigger id="matches-page-size" className="h-8 w-[4.5rem]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAGE_SIZE_OPTIONS.map((size) => (
                    <SelectItem key={size} value={String(size)}>
                      {size}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </span>
          </div>
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

      <MatchReviewDialog
        match={selectedMatch}
        onOpenChange={(open) => !open && setSelectedMatch(null)}
      />
    </>
  );
}
