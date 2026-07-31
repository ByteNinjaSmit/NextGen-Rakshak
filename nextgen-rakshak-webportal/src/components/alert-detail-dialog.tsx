"use client";

/* eslint-disable @next/next/no-img-element */
import { ImageOff } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { formatTime, timeAgo } from "@/lib/utils";
import type { Alert } from "@/types";

interface AlertDetailDialogProps {
  alert: Alert | null;
  onOpenChange: (open: boolean) => void;
}

export function AlertDetailDialog({ alert, onOpenChange }: AlertDetailDialogProps) {
  return (
    <Dialog open={!!alert} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        {alert && (
          <>
            <DialogHeader>
              <DialogTitle>{alert.childName}</DialogTitle>
              <DialogDescription>
                Alert <span className="font-mono">{alert.id}</span> · filed {timeAgo(alert.timestamp)}
              </DialogDescription>
            </DialogHeader>

            <div className="flex gap-4">
              {alert.imageUrl ? (
                <img
                  src={alert.imageUrl}
                  alt={alert.childName}
                  className="h-32 w-32 shrink-0 rounded-md object-cover"
                />
              ) : (
                <div
                  className="flex h-32 w-32 shrink-0 items-center justify-center rounded-md bg-muted"
                  title="Photo deleted — case resolved"
                >
                  <ImageOff className="h-8 w-8 text-muted-foreground" />
                </div>
              )}
              <div className="min-w-0 flex-1 space-y-1 text-sm">
                <div className="flex items-center gap-2">
                  <Badge variant={alert.status === "active" ? "destructive" : "success"}>
                    {alert.status === "active" ? "Active" : "Resolved"}
                  </Badge>
                </div>
                <p className="text-muted-foreground">
                  {alert.age} yrs · {alert.gender}
                </p>
                <p className="text-muted-foreground">Contact: {alert.parentContact}</p>
                {alert.createdBy && (
                  <p className="text-muted-foreground">
                    Reported by {alert.createdBy.name}
                    {alert.createdBy.station && ` · ${alert.createdBy.station}`}
                  </p>
                )}
              </div>
            </div>

            <div className="space-y-3 text-sm">
              <div>
                <p className="font-medium">Clothing Description</p>
                <p className="text-muted-foreground">{alert.clothingDesc}</p>
              </div>
              <div>
                <p className="font-medium">Last Seen Location</p>
                <p className="text-muted-foreground">{alert.lastSeen}</p>
              </div>
              {(alert.lastSeenDate || alert.lastSeenTime) && (
                <div>
                  <p className="font-medium">Last Seen Date &amp; Time</p>
                  <p className="text-muted-foreground">
                    {alert.lastSeenDate || "—"} {alert.lastSeenTime && `at ${alert.lastSeenTime}`}
                  </p>
                </div>
              )}
              {alert.identifyingMarks && (
                <div>
                  <p className="font-medium">Other Identifying Marks</p>
                  <p className="text-muted-foreground">{alert.identifyingMarks}</p>
                </div>
              )}
              <div>
                <p className="font-medium">Filed At</p>
                <p className="text-muted-foreground">{formatTime(alert.timestamp)}</p>
              </div>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
