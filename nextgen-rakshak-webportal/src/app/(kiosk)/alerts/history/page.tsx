import type { Metadata } from "next";
import { Suspense } from "react";
import { AlertHistoryList } from "@/components/alert-history-list";

export const metadata: Metadata = { title: "Alert History" };

export default function AlertHistoryPage() {
  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Alert History</h1>
        <p className="text-muted-foreground">
          Every alert filed at this kiosk, active or resolved. Tap View for full details.
        </p>
      </header>
      <Suspense fallback={<p className="text-sm text-muted-foreground">Loading alert history…</p>}>
        <AlertHistoryList />
      </Suspense>
    </div>
  );
}
