import type { Metadata } from "next";
import { StatsCards } from "@/components/stats-cards";
import { ActiveAlertsList } from "@/components/active-alerts-list";

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

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">Active Alerts</h2>
        <ActiveAlertsList />
      </section>
    </div>
  );
}
