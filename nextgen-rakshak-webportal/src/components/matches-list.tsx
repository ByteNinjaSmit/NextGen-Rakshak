"use client";

/* eslint-disable @next/next/no-img-element */
import { Navigation } from "lucide-react";
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
import { useMatches } from "@/hooks/use-alerts";
import { dispatchMatch } from "@/lib/firestore";
import { timeAgo } from "@/lib/utils";
import type { Match } from "@/types";

function mapsUrl(match: Match) {
  const { latitude, longitude } = match.location;
  return `https://www.google.com/maps?q=${latitude},${longitude}`;
}

export function MatchesList() {
  const { matches, loading } = useMatches();

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
                    <img
                      src={match.imageUrl}
                      alt={match.childName}
                      className="h-10 w-10 rounded-md object-cover"
                    />
                    <span className="font-medium">{match.childName}</span>
                  </div>
                </TableCell>
                <TableCell>{Math.round(match.confidence * 100)}%</TableCell>
                <TableCell className="capitalize">{match.volunteerRole}</TableCell>
                <TableCell className="text-muted-foreground">{timeAgo(match.timestamp)}</TableCell>
                <TableCell>
                  <Badge variant={match.status === "dispatched" ? "success" : "secondary"}>
                    {match.status}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    size="sm"
                    asChild
                    onClick={() => dispatchMatch(match.id)}
                  >
                    <a href={mapsUrl(match)} target="_blank" rel="noopener noreferrer">
                      <Navigation className="h-4 w-4" />
                      Dispatch
                    </a>
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
