"use client";

import { useEffect, useState } from "react";
import { subscribeActiveAlerts, subscribeMatches } from "@/lib/firestore";
import type { Alert, Match } from "@/types";

/** Live list of active alerts from Firestore. */
export function useActiveAlerts() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = subscribeActiveAlerts((data) => {
      setAlerts(data);
      setLoading(false);
    });
    return unsub;
  }, []);

  return { alerts, loading };
}

/** Live list of reported matches from Firestore. */
export function useMatches() {
  const [matches, setMatches] = useState<Match[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = subscribeMatches((data) => {
      setMatches(data);
      setLoading(false);
    });
    return unsub;
  }, []);

  return { matches, loading };
}
