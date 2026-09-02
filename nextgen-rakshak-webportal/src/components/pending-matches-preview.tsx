"use client";

import { Card, CardContent } from "@/components/ui/card";
import { useMatches } from "@/hooks/use-alerts";
import { timeAgo } from "@/lib/utils";

const PREVIEW_LIMIT = 5;

export function PendingMatchesPreview() {
  const { matches, loading } = useMatches();

  if (loading) return <p className="text-sm text-muted-foreground">Loading matches…</p>;

  const pending = matches.filter((m) => m.status === "pending").slice(0, PREVIEW_LIMIT);

  if (pending.length === 0)
    return (
      <Card>
        <CardContent className="py-10 text-center text-sm text-muted-foreground">
          No pending matches.
        </CardContent>
      </Card>
    );

  return (
    <Card>
      <CardContent className="divide-y p-0">
        {pending.map((match) => (
          <div key={match.id} className="px-4 py-3 text-sm">
            <p>
              Match for <span className="font-medium">{match.childName}</span> (
              {Math.round(match.confidence * 100)}%)
            </p>
            <p className="text-xs text-muted-foreground">
              by {match.volunteerName ? `${match.volunteerName} (${match.volunteerRole})` : match.volunteerRole} ·{" "}
              {timeAgo(match.timestamp)}
            </p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
