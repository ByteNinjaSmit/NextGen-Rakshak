"use client";

/* eslint-disable @next/next/no-img-element */
import { useEffect, useState } from "react";
import { ImageOff, Loader2, User, Phone, Shirt, Clock, Map } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
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
  return <img src={src} alt={alt} className="h-40 w-full rounded-md object-cover shadow-sm" />;
}

function ProgressRing({ radius, stroke, progress }: { radius: number; stroke: number; progress: number }) {
  const normalizedRadius = radius - stroke * 2;
  const circumference = normalizedRadius * 2 * Math.PI;
  const strokeDashoffset = circumference - (progress / 100) * circumference;

  return (
    <div className="relative flex flex-col items-center justify-center">
      <svg height={radius * 2} width={radius * 2} className="-rotate-90">
        <circle
          stroke="#e2e8f0"
          fill="transparent"
          strokeWidth={stroke}
          r={normalizedRadius}
          cx={radius}
          cy={radius}
        />
        <circle
          stroke={progress > 80 ? "#22c55e" : progress > 50 ? "#eab308" : "#ef4444"}
          fill="transparent"
          strokeWidth={stroke}
          strokeDasharray={circumference + " " + circumference}
          style={{ strokeDashoffset }}
          strokeLinecap="round"
          r={normalizedRadius}
          cx={radius}
          cy={radius}
          className="transition-all duration-1000 ease-in-out"
        />
      </svg>
      <div className="absolute flex flex-col items-center justify-center">
        <span className="text-[13px] font-bold leading-none">{progress}%</span>
      </div>
    </div>
  );
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
      <DialogContent className="max-w-2xl gap-6">
        {match && (
          <>
            <DialogHeader className="flex flex-row items-start justify-between sm:items-center">
              <div className="flex flex-col space-y-2 text-left">
                <DialogTitle className="text-2xl font-bold">Review match for {match.childName}</DialogTitle>
                <div className="flex flex-wrap items-center gap-2">
                  <div className="flex items-center gap-1.5 rounded-full border bg-muted/40 px-3 py-1 text-xs text-muted-foreground w-fit">
                    <span className="font-medium text-foreground">{match.volunteerName || "Unknown"}</span> 
                    <span>reported at {match.timestamp.toDate().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span>
                  </div>
                  {match.relayedBy && (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] uppercase tracking-wider text-slate-500 font-medium">
                      Via Mesh
                    </span>
                  )}
                </div>
              </div>
              
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-muted-foreground hidden sm:inline-block">
                  {timeAgo(match.timestamp)}
                </span>
                <div className="flex flex-col items-center justify-center">
                  <ProgressRing radius={24} stroke={4} progress={Math.round(match.confidence * 100)} />
                  <span className="text-[10px] uppercase tracking-widest text-muted-foreground mt-1 font-semibold">Similarity</span>
                </div>
              </div>
            </DialogHeader>

            <div className="grid gap-6 sm:grid-cols-2">
              <div className="space-y-1.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Original Alert Photo</p>
                <PhotoOrFallback src={alert?.imageUrl ?? ""} alt={`${match.childName} — original`} />
              </div>
              <div className="space-y-1.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Sighting Photo</p>
                <PhotoOrFallback src={match.imageUrl} alt={`${match.childName} — sighting`} />
              </div>
            </div>

            <div className="rounded-lg border bg-card p-4">
              {loadingAlert && <p className="text-sm text-muted-foreground flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin"/> Loading alert details…</p>}
              {!loadingAlert && !alert && (
                <p className="text-sm text-muted-foreground">Original alert details unavailable.</p>
              )}
              {alert && (
                <div className="grid gap-y-4 gap-x-6 sm:grid-cols-2">
                  <div className="flex items-start gap-3">
                    <div className="rounded-md bg-muted p-1.5">
                      <User className="h-4 w-4 text-foreground" />
                    </div>
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-0.5">Age / Gender</p>
                      <p className="text-sm font-medium leading-none">{alert.age} yrs · {alert.gender}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="rounded-md bg-muted p-1.5">
                      <Phone className="h-4 w-4 text-foreground" />
                    </div>
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-0.5">Contact</p>
                      <p className="text-sm font-medium leading-none">{alert.parentContact}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3 sm:col-span-2">
                    <div className="rounded-md bg-muted p-1.5">
                      <Shirt className="h-4 w-4 text-foreground" />
                    </div>
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-0.5">Clothing</p>
                      <p className="text-sm font-medium leading-none">{alert.clothingDesc}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3 sm:col-span-2">
                    <div className="rounded-md bg-muted p-1.5">
                      <Clock className="h-4 w-4 text-foreground" />
                    </div>
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-0.5">Last Seen</p>
                      <p className="text-sm font-medium leading-none">{alert.lastSeen}</p>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <div className="flex flex-col sm:flex-row gap-6 items-center rounded-xl bg-muted/30 border p-4">
              <div className="shrink-0 relative overflow-hidden rounded-full shadow-inner border bg-muted" style={{ width: 140, height: 140 }}>
                <iframe
                  title="Match Location"
                  width="100%"
                  height="100%"
                  style={{ border: 0, position: 'absolute', top: 0, left: 0 }}
                  loading="lazy"
                  allowFullScreen
                  src={`https://maps.google.com/maps?q=${match.location.latitude},${match.location.longitude}&z=15&output=embed`}
                ></iframe>
              </div>
              
              <div className="flex flex-1 flex-col justify-center gap-3 w-full">
                <Button asChild variant="secondary" className="w-full justify-start">
                  <a href={`https://www.google.com/maps?q=${match.location.latitude},${match.location.longitude}`} target="_blank" rel="noopener noreferrer">
                    <Map className="mr-2 h-4 w-4" />
                    Open in Google Maps
                  </a>
                </Button>
                
                <div className="grid grid-cols-2 gap-3">
                  <ConfirmDialog
                    destructive
                    trigger={
                      <Button variant="outline" className="w-full border-destructive/30 text-destructive hover:bg-destructive/10" disabled={reviewed}>
                        Decline
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
                  <Button onClick={onAccept} disabled={reviewed || accepting} className="w-full bg-green-600 hover:bg-green-700 text-white">
                    {accepting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                    Accept
                  </Button>
                </div>
              </div>
            </div>
            
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
