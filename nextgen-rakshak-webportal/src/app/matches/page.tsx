import { MatchesList } from "@/components/matches-list";

export default function MatchesPage() {
  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">Live Matches</h1>
        <p className="text-muted-foreground">
          Confirmed sightings reported by volunteers. Tap Dispatch to open the location in Google
          Maps.
        </p>
      </header>
      <MatchesList />
    </div>
  );
}
