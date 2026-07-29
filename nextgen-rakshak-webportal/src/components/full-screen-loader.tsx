import { Loader2 } from "lucide-react";

/** Neutral full-page spinner used while auth state or a redirect resolves. */
export function FullScreenLoader({ label }: { label?: string }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-muted/30">
      <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      {label && <p className="text-sm text-muted-foreground">{label}</p>}
    </div>
  );
}
