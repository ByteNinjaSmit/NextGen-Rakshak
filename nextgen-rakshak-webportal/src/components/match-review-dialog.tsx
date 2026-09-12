"use client";

/* eslint-disable @next/next/no-img-element */
import { useEffect, useState } from "react";
import { ImageOff, Loader2, Phone, Mail, MapPin, CalendarClock, Radio } from "lucide-react";
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
import { useVolunteer } from "@/hooks/use-alerts";
import { formatTime, timeAgo } from "@/lib/utils";
import type { Alert, Match } from "@/types";

interface MatchReviewDialogProps {
  match: Match | null;
  onOpenChange: (open: boolean) => void;
}

function PhotoOrFallback({ src, alt }: { src: string; alt: string }) {
  if (!src)
    return (
      <div
        className="flex h-40 w-full items-center justify-center rounded-lg bg-muted"
        title="Photo unavailable"
      >
        <ImageOff className="h-8 w-8 text-muted-foreground" />
      </div>
    );
  return <img src={src} alt={alt} className="h-40 w-full rounded-lg object-cover" />;
}

function mapsLink(location: { latitude: number; longitude: number }) {
  return `https://www.google.com/maps?q=${location.latitude},${location.longitude}`;
}

/**
 * Full account/device record behind `match.volunteerId` — everything the
 * kiosk knows about who this sighting came from, beyond the name/role already
 * denormalised onto the match: phone, email, when the account registered, and
 * where that device last published a GPS fix.
 */
function VolunteerAccountPanel({ match }: { match: Match }) {
  const { volunteer, loading } = useVolunteer(match.volunteerId);
  const { volunteer: relay } = useVolunteer(match.relayedBy);

  return (
    <div className="space-y-2 rounded-lg border bg-muted/40 p-3 text-sm">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-3">
          {volunteer?.photoUrl ? (
            /* eslint-disable-next-line @next/next/no-img-element -- Google avatar, external host */
            <img
              src={volunteer.photoUrl}
              alt=""
              className="h-10 w-10 shrink-0 rounded-full bg-muted object-cover"
            />
          ) : (
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold text-muted-foreground">
              {(match.volunteerName || volunteer?.name || "?").trim()[0]?.toUpperCase() ?? "?"}
            </div>
          )}
          <div>
            <p className="font-medium">{match.volunteerName || volunteer?.name || "Name not provided"}</p>
            <p className="text-xs capitalize text-muted-foreground">{match.volunteerRole}</p>
          </div>
        </div>
        {match.relayedBy && (
          <span
            className="flex items-center gap-1 whitespace-nowrap text-xs text-muted-foreground"
            title="Carried to the server by another volunteer's phone over the offline mesh. The reporter is named by that relay rather than proven by their own session."
          >
            <Radio className="h-3 w-3" />
            via mesh
          </span>
        )}
      </div>

      {loading && <p className="text-xs text-muted-foreground">Loading account details…</p>}

      {!loading && !volunteer && (
        <p className="text-xs text-muted-foreground">
          No matching volunteer account found — this device may have signed in through the
          phone-only demo path.
        </p>
      )}

      {volunteer && (
        <div className="grid gap-1.5 border-t pt-2 sm:grid-cols-2">
          {volunteer.phone && (
            <a
              href={`tel:${volunteer.phone}`}
              className="flex items-center gap-1.5 text-foreground hover:underline"
            >
              <Phone className="h-3.5 w-3.5 text-muted-foreground" />
              {volunteer.phone}
            </a>
          )}
          {volunteer.email && (
            <span className="flex items-center gap-1.5 truncate">
              <Mail className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
              {volunteer.email}
            </span>
          )}
          {volunteer.registeredAt && (
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <CalendarClock className="h-3.5 w-3.5" />
              Registered {formatTime(volunteer.registeredAt)} on{" "}
              {volunteer.registeredAt.toDate().toLocaleDateString()}
            </span>
          )}
          {volunteer.lastLocation && (
            <a
              href={mapsLink(volunteer.lastLocation)}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-foreground hover:underline"
              title="Last GPS fix published by this device"
            >
              <MapPin className="h-3.5 w-3.5 text-muted-foreground" />
              Device location{volunteer.locationUpdatedAt && ` · ${timeAgo(volunteer.locationUpdatedAt)}`}
            </a>
          )}
        </div>
      )}

      {match.relayedBy && (
        <p className="border-t pt-2 text-xs text-muted-foreground">
          Relayed via {relay?.name || relay?.phone || "another volunteer's device"} over the
          offline mesh.
        </p>
      )}
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
      <DialogContent className="max-w-2xl">
        {match && (
          <>
            <DialogHeader>
              <DialogTitle>Review match for {match.childName}</DialogTitle>
              <DialogDescription>
                {Math.round(match.confidence * 100)}% similarity · {timeAgo(match.timestamp)}
              </DialogDescription>
            </DialogHeader>

            <VolunteerAccountPanel match={match} />

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
