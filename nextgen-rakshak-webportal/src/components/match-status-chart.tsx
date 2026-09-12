"use client";

import { Card, CardContent } from "@/components/ui/card";
import { useMatchCounts } from "@/hooks/use-alerts";
import { cn } from "@/lib/utils";

/**
 * Bar fill classes mirror the status Badge variants used everywhere else
 * (pending/dispatched/accepted/dismissed) so the same status always reads as
 * the same color across the kiosk.
 */
const rows: { key: "pending" | "dispatched" | "accepted" | "dismissed"; label: string; bar: string }[] = [
  { key: "pending", label: "Pending", bar: "bg-warning" },
  { key: "dispatched", label: "Dispatched", bar: "bg-info" },
  { key: "accepted", label: "Confirmed found", bar: "bg-success" },
  { key: "dismissed", label: "Dismissed", bar: "bg-destructive" },
];

export function MatchStatusChart() {
  const counts = useMatchCounts();

  if (!counts) {
    return (
      <Card>
        <CardContent className="space-y-3 p-5">
          {rows.map((r) => (
            <div key={r.key} className="h-6 animate-pulse rounded bg-muted" />
          ))}
        </CardContent>
      </Card>
    );
  }

  const max = Math.max(1, ...rows.map((r) => counts[r.key]));

  return (
    <Card>
      <CardContent className="space-y-4 p-5">
        {rows.map((r) => {
          const value = counts[r.key];
          const pct = Math.round((value / max) * 100);
          return (
            <div key={r.key} title={`${r.label}: ${value} match${value === 1 ? "" : "es"}`}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="text-muted-foreground">{r.label}</span>
                <span className="font-semibold tabular-nums">{value}</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className={cn("h-full rounded-full transition-all", r.bar)}
                  style={{ width: `${Math.max(value > 0 ? 3 : 0, pct)}%` }}
                />
              </div>
            </div>
          );
        })}
        {/* Screen-reader table view of the same data. */}
        <table className="sr-only">
          <caption>Match status breakdown</caption>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key}>
                <th scope="row">{r.label}</th>
                <td>{counts[r.key]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  );
}
