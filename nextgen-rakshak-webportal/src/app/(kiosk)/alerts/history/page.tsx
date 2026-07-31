import type { Metadata } from "next";
import { AlertHistoryList } from "@/components/alert-history-list";

export const metadata: Metadata = { title: "Alert History" };

export default function AlertHistoryPage() {
  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">Alert History</h1>
        <p className="text-muted-foreground">
          Every alert filed at this kiosk, active or resolved. Tap View for full details.
        </p>
      </header>
      <AlertHistoryList />
    </div>
  );
}
