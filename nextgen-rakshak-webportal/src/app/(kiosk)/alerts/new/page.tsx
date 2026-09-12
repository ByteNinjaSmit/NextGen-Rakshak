import type { Metadata } from "next";
import { Card, CardContent } from "@/components/ui/card";
import { AlertForm } from "@/components/alert-form";

export const metadata: Metadata = { title: "New Alert" };

export default function NewAlertPage() {
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">New Alert</h1>
        <p className="text-muted-foreground">
          Report a missing child. On submit, the photo is uploaded and pushed to all volunteers
          within range.
        </p>
      </header>
      <Card>
        <CardContent className="pt-6">
          <AlertForm />
        </CardContent>
      </Card>
    </div>
  );
}
