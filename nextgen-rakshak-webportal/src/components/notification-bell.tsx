"use client";

import { useEffect, useRef, useState } from "react";
import { Bell, BellOff, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { onKioskMessage, requestKioskNotifications } from "@/lib/firebase";
import { saveOfficerFcmToken } from "@/lib/officers";
import { useAuth } from "@/components/auth-provider";
import type { MessagePayload } from "firebase/messaging";
import { cn } from "@/lib/utils";

type KioskNotification = {
  id: string;
  title: string;
  body: string;
  receivedAt: Date;
};

/**
 * Foreground-only inbox: messages arrive via onKioskMessage while this tab is
 * open. Backgrounded/closed-tab delivery is handled by
 * public/firebase-messaging-sw.js, which shows an OS notification directly
 * and never reaches this component.
 */
export function NotificationBell() {
  const { user } = useAuth();
  const [permission, setPermission] = useState<NotificationPermission | "unsupported">("default");
  const [enabling, setEnabling] = useState(false);
  const [enableError, setEnableError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<KioskNotification[]>([]);
  const [unread, setUnread] = useState(0);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setPermission("unsupported");
      return;
    }
    setPermission(Notification.permission);
  }, []);

  useEffect(() => {
    if (permission !== "granted") return;
    let unsubscribe = () => {};
    onKioskMessage((payload: MessagePayload) => {
      const title = payload.notification?.title ?? "New update";
      const body = payload.notification?.body ?? "";
      setNotifications((prev) => [
        { id: crypto.randomUUID(), title, body, receivedAt: new Date() },
        ...prev,
      ].slice(0, 20));
      setUnread((count) => count + 1);
    }).then((unsub) => {
      unsubscribe = unsub;
    });
    return () => unsubscribe();
  }, [permission]);

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  async function onEnable() {
    setEnabling(true);
    setEnableError(null);
    try {
      const token = await requestKioskNotifications();
      if (token && user) await saveOfficerFcmToken(user.uid, token);
      setPermission(token ? "granted" : Notification.permission);
      if (!token && Notification.permission === "granted") {
        setEnableError("Notifications are on, but push could not be set up. Check the kiosk's push configuration.");
      } else if (Notification.permission === "denied") {
        setEnableError("Notifications are blocked for this site. Re-enable them in the browser's site settings.");
      }
    } catch (err) {
      console.error(err);
      setEnableError("Could not enable notifications. Try again.");
    } finally {
      setEnabling(false);
    }
  }

  function onToggleOpen() {
    setOpen((v) => !v);
    setUnread(0);
  }

  if (permission === "unsupported") return null;

  return (
    <div className="relative" ref={panelRef}>
      {permission === "granted" ? (
        <Button variant="ghost" size="icon" className="relative" onClick={onToggleOpen} aria-label="Notifications">
          <Bell className="h-5 w-5" />
          {unread > 0 && (
            <Badge
              variant="destructive"
              className="absolute -right-1 -top-1 h-5 min-w-5 justify-center rounded-full px-1 text-[10px]"
            >
              {unread > 9 ? "9+" : unread}
            </Badge>
          )}
        </Button>
      ) : (
        <div className="flex flex-col items-start gap-1">
          <Button variant="ghost" size="sm" onClick={onEnable} disabled={enabling}>
            {enabling ? <Loader2 className="h-4 w-4 animate-spin" /> : <BellOff className="h-4 w-4" />}
            Enable alerts
          </Button>
          {enableError && <p className="max-w-64 text-xs text-destructive">{enableError}</p>}
        </div>
      )}

      {open && permission === "granted" && (
        <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-md border bg-white shadow-lg">
          <div className="border-b px-4 py-2 text-sm font-semibold">Notifications</div>
          <div className="max-h-80 overflow-y-auto">
            {notifications.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-muted-foreground">No notifications yet.</p>
            ) : (
              notifications.map((n, i) => (
                <div
                  key={n.id}
                  className={cn("px-4 py-3 text-sm", i !== notifications.length - 1 && "border-b")}
                >
                  <p className="font-medium">{n.title}</p>
                  {n.body && <p className="text-muted-foreground">{n.body}</p>}
                  <p className="mt-1 text-xs text-muted-foreground">
                    {n.receivedAt.toLocaleTimeString()}
                  </p>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
