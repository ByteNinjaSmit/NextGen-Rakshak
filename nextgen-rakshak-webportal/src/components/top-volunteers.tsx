"use client";

import { useMemo } from "react";
import type { Timestamp } from "firebase/firestore";
import { Trophy, Users } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { useMatches } from "@/hooks/use-alerts";
import { cn, timeAgo } from "@/lib/utils";
import type { Match } from "@/types";

const TOP_N = 5;

interface VolunteerTally {
  key: string;
  name: string;
  role: string;
  count: number;
  lastSeen: Timestamp;
}

export function TopVolunteers() {
  const { matches, loading } = useMatches();

  const top = useMemo(() => {
    const byVolunteer = new Map<string, VolunteerTally>();
    for (const m of matches as Match[]) {
      const key = m.volunteerId || m.volunteerName || m.volunteerRole;
      const existing = byVolunteer.get(key);
      if (existing) {
        existing.count += 1;
        if (m.timestamp && m.timestamp.toMillis() > existing.lastSeen.toMillis()) {
          existing.lastSeen = m.timestamp;
        }
      } else {
        byVolunteer.set(key, {
          key,
          name: m.volunteerName || m.volunteerRole,
          role: m.volunteerRole,
          count: 1,
          lastSeen: m.timestamp,
        });
      }
    }
    return [...byVolunteer.values()].sort((a, b) => b.count - a.count).slice(0, TOP_N);
  }, [matches]);

  if (loading)
    return (
      <Card>
        <CardContent className="space-y-3 p-5">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-muted" />
          ))}
        </CardContent>
      </Card>
    );

  if (top.length === 0)
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
          <Users className="h-8 w-8 text-muted-foreground/50" />
          No volunteer sightings reported yet.
        </CardContent>
      </Card>
    );

  return (
    <Card>
      <CardContent className="divide-y p-0">
        {top.map((v, i) => (
          <div key={v.key} className="flex items-center gap-3 px-4 py-3 text-sm">
            <span
              className={cn(
                "flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold",
                i === 0
                  ? "bg-warning/20 text-warning-foreground"
                  : "bg-muted text-muted-foreground"
              )}
            >
              {i === 0 ? <Trophy className="h-3.5 w-3.5" /> : i + 1}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{v.name}</p>
              <p className="truncate text-xs capitalize text-muted-foreground">
                {v.role} · last sighting {timeAgo(v.lastSeen)}
              </p>
            </div>
            <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs font-semibold tabular-nums text-muted-foreground">
              {v.count} sighting{v.count === 1 ? "" : "s"}
            </span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
