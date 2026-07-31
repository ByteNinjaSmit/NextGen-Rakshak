import type { Metadata } from "next";
import Link from "next/link";
import { StatsCards } from "@/components/stats-cards";
import { ActiveAlertsList } from "@/components/active-alerts-list";
import { PendingMatchesPreview } from "@/components/pending-matches-preview";
import { Button } from "@/components/ui/button";

export const metadata: Metadata = { title: "Dashboard" };

export default function DashboardPage() {
  return (
    <div className="mx-auto max-w-6xl space-y-8">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-muted-foreground">
          Live overview of active missing-child alerts and volunteer matches.
        </p>
      </header>

      <StatsCards />

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold">Active Alerts</h2>
            <Button asChild variant="ghost" size="sm">
              <Link href="/alerts/history">View all</Link>
            </Button>
          </div>
          <ActiveAlertsList />
        </section>

        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold">Live Match Updates</h2>
            <Button asChild variant="ghost" size="sm">
              <Link href="/matches">View all</Link>
            </Button>
          </div>
          <PendingMatchesPreview />
        </section>
      </div>
    </div>
  );
}
