"use client";

/* eslint-disable @next/next/no-img-element */
import { useEffect, useState } from "react";
import { ImageOff, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { acceptMatch, dismissMatch, fetchAlert } from "@/lib/firestore";
import { timeAgo } from "@/lib/utils";
import type { Alert, Match } from "@/types";

interface MatchReviewDialogProps {
  match: Match | null;
  onOpenChange: (open: boolean) => void;
}

function PhotoOrFallback({ src, alt }: { src: string; alt: string }) {
  if (!src)
    return (
      <div
        className="flex h-40 w-full items-center justify-center rounded-md bg-muted"
        title="Photo unavailable"
      >
        <ImageOff className="h-8 w-8 text-muted-foreground" />
      </div>
    );
  return <img src={src} alt={alt} className="h-40 w-full rounded-md object-cover" />;
}

export function MatchReviewDialog({ match, onOpenChange }: MatchReviewDialogProps) {
  const [alert, setAlert] = useState<Alert | null>(null);
  const [loadingAlert, setLoadingAlert] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accepting, setAccepting] = useState(false);

  useEffect(() => {
    if (!match) {
      setAlert(null);
      return;
    }
    let cancelled = false;
    setLoadingAlert(true);
    fetchAlert(match.alertId)
      .then((data) => {
        if (!cancelled) setAlert(data);
      })
      .finally(() => {
        if (!cancelled) setLoadingAlert(false);
      });
    return () => {
      cancelled = true;
    };
  }, [match]);

  const reviewed = match?.status === "accepted" || match?.status === "dismissed";

  async function onAccept() {
    if (!match) return;
    setError(null);
    setAccepting(true);
    try {
      await acceptMatch(match.id);
      onOpenChange(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to accept match.");
    } finally {
      setAccepting(false);
    }
  }

  return (
    <Dialog open={!!match} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        {match && (
          <>
            <DialogHeader>
              <DialogTitle>Review match for {match.childName}</DialogTitle>
              <DialogDescription>
                {Math.round(match.confidence * 100)}% similarity · {timeAgo(match.timestamp)}
              </DialogDescription>
            </DialogHeader>

            <div className="flex items-center justify-between rounded-md border bg-muted/40 px-3 py-2 text-sm">
              <div>
                <p className="text-xs text-muted-foreground">Reported By</p>
                <p className="font-medium">{match.volunteerName || "Name not provided"}</p>
                <p className="text-xs capitalize text-muted-foreground">{match.volunteerRole}</p>
              </div>
              {match.relayedBy && (
                <span
                  className="whitespace-nowrap text-xs text-muted-foreground"
                  title="Carried to the server by another volunteer's phone over the offline mesh. The reporter is named by that relay rather than proven by their own session."
                >
                  via mesh
                </span>
              )}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <p className="text-sm font-medium">Original Alert Photo</p>
                <PhotoOrFallback src={alert?.imageUrl ?? ""} alt={`${match.childName} — original`} />
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium">Sighting Photo</p>
                <PhotoOrFallback src={match.imageUrl} alt={`${match.childName} — sighting`} />
              </div>
            </div>

            <div className="space-y-2 text-sm">
              {loadingAlert && <p className="text-muted-foreground">Loading alert details…</p>}
              {!loadingAlert && !alert && (
                <p className="text-muted-foreground">Original alert details unavailable.</p>
              )}
              {alert && (
                <div className="grid gap-1 sm:grid-cols-2">
                  <p>
                    <span className="text-muted-foreground">Age/Gender: </span>
                    {alert.age} yrs · {alert.gender}
                  </p>
                  <p>
                    <span className="text-muted-foreground">Contact: </span>
                    {alert.parentContact}
                  </p>
                  <p className="sm:col-span-2">
                    <span className="text-muted-foreground">Clothing: </span>
                    {alert.clothingDesc}
                  </p>
                  <p className="sm:col-span-2">
                    <span className="text-muted-foreground">Last Seen: </span>
                    {alert.lastSeen}
                  </p>
                  {alert.identifyingMarks && (
                    <p className="sm:col-span-2">
                      <span className="text-muted-foreground">Identifying Marks: </span>
                      {alert.identifyingMarks}
                    </p>
                  )}
                </div>
              )}
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <ConfirmDialog
                destructive
                trigger={
                  <Button variant="outline" disabled={reviewed}>
                    Dismiss
                  </Button>
                }
                title="Dismiss this case?"
                description="Are you sure you want to dismiss this case? This marks the match as a false positive and cannot be undone."
                confirmLabel="Dismiss"
                onConfirm={async () => {
                  await dismissMatch(match.id);
                  onOpenChange(false);
                }}
              />
              <Button onClick={onAccept} disabled={reviewed || accepting}>
                {accepting && <Loader2 className="h-4 w-4 animate-spin" />}
                Accept
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
