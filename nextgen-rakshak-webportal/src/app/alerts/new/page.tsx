import { Card, CardContent } from "@/components/ui/card";
import { AlertForm } from "@/components/alert-form";

export default function NewAlertPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">New Alert</h1>
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
