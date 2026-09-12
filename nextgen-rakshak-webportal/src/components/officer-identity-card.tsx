"use client";

import { BadgeCheck, Bell, BellOff } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useAuth } from "@/components/auth-provider";

function formatDate(ts?: { toDate: () => Date }): string {
  if (!ts?.toDate) return "—";
  return ts.toDate().toLocaleString();
}

/** Read-only, server-owned half of the officer record. */
export function OfficerIdentityCard() {
  const { user, officer } = useAuth();
  const photoURL = officer?.photoURL ?? user?.photoURL ?? null;
  const name = officer?.displayName ?? user?.displayName ?? "Officer";
  const initial = (name.trim()[0] ?? "O").toUpperCase();

  return (
    <Card className="overflow-hidden">
      <div className="h-20 bg-gradient-to-r from-primary to-sidebar-active" />
      <CardContent className="-mt-10 pt-0">
        {photoURL ? (
          /* eslint-disable-next-line @next/next/no-img-element -- Google avatar, external host */
          <img
            src={photoURL}
            alt=""
            className="h-20 w-20 shrink-0 rounded-full bg-muted object-cover ring-4 ring-card"
          />
        ) : (
          // An empty src makes the browser re-request the page and render a
          // broken icon, so accounts without a Google photo get initials.
          <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full bg-muted text-2xl font-semibold text-muted-foreground ring-4 ring-card">
            {initial}
          </div>
        )}
        {/*
          Name/badge/email live entirely below the avatar rather than beside
          it — a row squeezed next to the avatar can grow taller than the
          banner overlap at larger font sizes/zoom and get clipped under the
          banner. Stacking below guarantees clearance regardless of text size.
        */}
        <div className="mt-3 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate font-semibold">{name}</p>
            <Badge variant="secondary" className="gap-1">
              <BadgeCheck className="h-3 w-3" />
              Police
            </Badge>
          </div>
          <p className="truncate text-sm text-muted-foreground">
            {officer?.email ?? user?.email}
          </p>
        </div>
      </CardContent>
      <CardContent className="space-y-1 pt-3 text-xs text-muted-foreground">
        <p>
          Registered {formatDate(officer?.createdAt)} · Last sign-in {formatDate(officer?.lastLoginAt)}
        </p>
        <p>Profile last saved {formatDate(officer?.updatedAt)}</p>
        <p className="flex items-center gap-1.5">
          {officer?.fcmToken ? (
            <>
              <Bell className="h-3.5 w-3.5" /> Push notifications synced to this browser
            </>
          ) : (
            <>
              <BellOff className="h-3.5 w-3.5" /> Push notifications not enabled — use the bell icon
              in the header
            </>
          )}
        </p>
      </CardContent>
    </Card>
  );
}
