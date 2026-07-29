"use client";

import { useEffect, useState } from "react";
import { fetchMatchCounts, subscribeActiveAlerts, subscribeMatches } from "@/lib/firestore";
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

/** Live list of the most recent reported matches from Firestore. */
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

/**
 * Server-side match totals for the dashboard tiles.
 *
 * `useMatches` is capped at a recent window, so its length is not the total.
 * The live feed is still what drives the refresh: whenever a sighting arrives
 * or a status flips, the aggregates are re-read.
 */
export function useMatchCounts() {
  const { matches } = useMatches();
  // null until the first aggregate lands: seeding with zeroes would state
  // "no matches" as fact during the round-trip, mid-incident.
  const [counts, setCounts] = useState<{ total: number; pending: number } | null>(null);

  // Cheap change signal: a new sighting changes the newest id, a dispatch
  // changes the pending tally within the window.
  const signal = `${matches.length}:${matches[0]?.id ?? ""}:${
    matches.filter((m) => m.status === "pending").length
  }`;

  useEffect(() => {
    let cancelled = false;
    fetchMatchCounts()
      .then((next) => {
        if (!cancelled) setCounts(next);
      })
      .catch(() => undefined); // tiles keep their last good value
    return () => {
      cancelled = true;
    };
  }, [signal]);

  return counts;
}
