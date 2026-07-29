"use client";

import { BadgeCheck } from "lucide-react";
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
    <Card>
      <CardContent className="flex items-center gap-4 pt-6">
        {photoURL ? (
          /* eslint-disable-next-line @next/next/no-img-element -- Google avatar, external host */
          <img
            src={photoURL}
            alt=""
            className="h-14 w-14 shrink-0 rounded-full bg-muted object-cover"
          />
        ) : (
          // An empty src makes the browser re-request the page and render a
          // broken icon, so accounts without a Google photo get initials.
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-muted text-lg font-semibold text-muted-foreground">
            {initial}
          </div>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate font-semibold">{name}</p>
            <Badge variant="secondary" className="gap-1">
              <BadgeCheck className="h-3 w-3" />
              Police
            </Badge>
          </div>
          <p className="truncate text-sm text-muted-foreground">
            {officer?.email ?? user?.email}
          </p>
          <p className="text-xs text-muted-foreground">
            Registered {formatDate(officer?.createdAt)} · Last sign-in{" "}
            {formatDate(officer?.lastLoginAt)}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
