import type { Metadata } from "next";
import { Suspense } from "react";
import { MatchesList } from "@/components/matches-list";

export const metadata: Metadata = { title: "Live Matches" };

export default function MatchesPage() {
  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Live Matches</h1>
        <p className="text-muted-foreground">
          Confirmed sightings reported by volunteers. Tap Dispatch to open the location in Google
          Maps.
        </p>
      </header>
      <Suspense fallback={<p className="text-sm text-muted-foreground">Loading matches…</p>}>
        <MatchesList />
      </Suspense>
    </div>
  );
}
