"use client";

import { useEffect } from "react";
import { AlertTriangle, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/** Client-side crash boundary for every route below the root layout. */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Kiosk error:", error);
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 p-6">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <AlertTriangle className="mb-2 h-10 w-10 text-destructive" />
          <CardTitle className="text-xl">Something went wrong</CardTitle>
          <CardDescription>
            The kiosk hit an unexpected error. Alerts already filed are unaffected.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="break-words rounded-md bg-muted p-3 text-xs text-muted-foreground">
            {error.message || "Unknown error"}
            {error.digest && ` (${error.digest})`}
          </p>
          <Button onClick={reset} className="w-full">
            <RotateCw className="h-4 w-4" />
            Try again
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
