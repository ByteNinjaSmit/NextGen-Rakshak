import type { Metadata } from "next";
import Link from "next/link";
import { PlusCircle } from "lucide-react";
import { StatsCards } from "@/components/stats-cards";
import { ActiveAlertsList } from "@/components/active-alerts-list";
import { PendingMatchesPreview } from "@/components/pending-matches-preview";
import { MatchStatusChart } from "@/components/match-status-chart";
import { TopVolunteers } from "@/components/top-volunteers";
import { Button } from "@/components/ui/button";

export const metadata: Metadata = { title: "Dashboard" };

export default function DashboardPage() {
  return (
    <div className="mx-auto max-w-7xl space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Dashboard</h1>
            <span className="flex items-center gap-1.5 rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success">
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
              </span>
              Live
            </span>
          </div>
          <p className="text-muted-foreground">
            Live overview of active missing-child alerts and volunteer matches.
          </p>
        </div>
        <Button asChild className="self-start sm:self-auto">
          <Link href="/alerts/new">
            <PlusCircle className="h-4 w-4" />
            New Alert
          </Link>
        </Button>
      </header>

      <StatsCards />

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold sm:text-xl">Active Alerts</h2>
            <Button asChild variant="ghost" size="sm">
              <Link href="/alerts/history">View all</Link>
            </Button>
          </div>
          <ActiveAlertsList />
        </section>

        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold sm:text-xl">Live Match Updates</h2>
            <Button asChild variant="ghost" size="sm">
              <Link href="/matches">View all</Link>
            </Button>
          </div>
          <PendingMatchesPreview />
        </section>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="space-y-3">
          <h2 className="text-lg font-semibold sm:text-xl">Match Status Breakdown</h2>
          <MatchStatusChart />
        </section>

        <section className="space-y-3">
          <h2 className="text-lg font-semibold sm:text-xl">Top Reporting Volunteers</h2>
          <TopVolunteers />
        </section>
      </div>
    </div>
  );
}
