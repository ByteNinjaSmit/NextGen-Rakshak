"use client";

import { Card, CardContent } from "@/components/ui/card";
import { useMatches } from "@/hooks/use-alerts";
import { timeAgo } from "@/lib/utils";
import { CheckCircle2 } from "lucide-react";

const PREVIEW_LIMIT = 5;

export function PendingMatchesPreview() {
  const { matches, loading } = useMatches();

  if (loading)
    return (
      <Card>
        <CardContent className="divide-y p-0">
          {[0, 1, 2].map((i) => (
            <div key={i} className="space-y-2 px-4 py-3">
              <div className="h-4 w-2/3 animate-pulse rounded bg-muted" />
              <div className="h-3 w-1/3 animate-pulse rounded bg-muted" />
            </div>
          ))}
        </CardContent>
      </Card>
    );

  const pending = matches.filter((m) => m.status === "pending").slice(0, PREVIEW_LIMIT);

  if (pending.length === 0)
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
          <CheckCircle2 className="h-8 w-8 text-muted-foreground/50" />
          No pending matches.
        </CardContent>
      </Card>
    );

  return (
    <Card>
      <CardContent className="divide-y p-0">
        {pending.map((match) => {
          const pct = Math.round(match.confidence * 100);
          return (
            <div key={match.id} className="flex items-center gap-3 px-4 py-3 text-sm">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-warning/15 text-xs font-bold text-warning-foreground">
                {pct}%
              </div>
              <div className="min-w-0">
                <p className="truncate">
                  Match for <span className="font-medium">{match.childName}</span>
                </p>
                <p className="truncate text-xs text-muted-foreground">
                  by{" "}
                  {match.volunteerName ? `${match.volunteerName} (${match.volunteerRole})` : match.volunteerRole}{" "}
                  · {timeAgo(match.timestamp)}
                </p>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
