"use client";

/* eslint-disable @next/next/no-img-element */
import { useState } from "react";
import { ImageOff, Navigation, RotateCw } from "lucide-react";
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
import { MatchReviewDialog } from "@/components/match-review-dialog";
import { useMatches } from "@/hooks/use-alerts";
import { dispatchMatch } from "@/lib/firestore";
import { timeAgo } from "@/lib/utils";
import type { BadgeProps } from "@/components/ui/badge";
import type { Match, MatchStatus } from "@/types";

function mapsUrl(match: Match) {
  const { latitude, longitude } = match.location;
  return `https://www.google.com/maps?q=${latitude},${longitude}`;
}

const statusVariant: Record<MatchStatus, BadgeProps["variant"]> = {
  pending: "secondary",
  dispatched: "success",
  accepted: "success",
  dismissed: "destructive",
};

export function MatchesList() {
  const { matches, loading } = useMatches();
  const [failed, setFailed] = useState<string[]>([]);
  const [selectedMatch, setSelectedMatch] = useState<Match | null>(null);

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

  if (loading) return <p className="text-sm text-muted-foreground">Loading matches…</p>;
  if (matches.length === 0)
    return (
      <Card>
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          No matches reported yet. Confirmed matches from volunteers appear here in real time.
        </CardContent>
      </Card>
    );

  return (
    <Card>
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
            {matches.map((match) => (
              <TableRow key={match.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    {match.imageUrl ? (
                      <img
                        src={match.imageUrl}
                        alt={match.childName}
                        className="h-10 w-10 rounded-md object-cover"
                      />
                    ) : (
                      // Photo purged when the case was resolved.
                      <div
                        className="flex h-10 w-10 items-center justify-center rounded-md bg-muted"
                        title="Photo deleted — case resolved"
                      >
                        <ImageOff className="h-4 w-4 text-muted-foreground" />
                      </div>
                    )}
                    <span className="font-medium">{match.childName}</span>
                  </div>
                </TableCell>
                <TableCell>{Math.round(match.confidence * 100)}%</TableCell>
                <TableCell>
                  <span className="capitalize">{match.volunteerRole}</span>
                  {match.relayedBy && (
                    <span
                      className="ml-2 whitespace-nowrap text-xs text-muted-foreground"
                      title="Carried to the server by another volunteer's phone over the offline mesh. The reporter is named by that relay rather than proven by their own session."
                    >
                      via mesh
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">{timeAgo(match.timestamp)}</TableCell>
                <TableCell>
                  <Badge variant={statusVariant[match.status]}>{match.status}</Badge>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex flex-col items-end gap-1">
                    <div className="flex gap-2">
                      <Button size="sm" variant="outline" onClick={() => setSelectedMatch(match)}>
                        Review
                      </Button>
                      <Button size="sm" asChild onClick={() => markDispatched(match)}>
                        <a href={mapsUrl(match)} target="_blank" rel="noopener noreferrer">
                          <Navigation className="h-4 w-4" />
                          Dispatch
                        </a>
                      </Button>
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
      <MatchReviewDialog
        match={selectedMatch}
        onOpenChange={(open) => !open && setSelectedMatch(null)}
      />
    </Card>
  );
}
