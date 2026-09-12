"use client";

import { useEffect, useState } from "react";
import { fetchMatchCounts, subscribeActiveAlerts, subscribeAllAlerts, subscribeMatches } from "@/lib/firestore";
import { fetchVolunteer } from "@/lib/volunteers";
import type { Alert, Match, Volunteer } from "@/types";

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

/** Live list of every alert (active + resolved), for Alert History. */
export function useAllAlerts() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = subscribeAllAlerts((data) => {
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
  const [counts, setCounts] = useState<{
    total: number;
    pending: number;
    dispatched: number;
    accepted: number;
    dismissed: number;
  } | null>(null);

  // Cheap change signal: a new sighting changes the newest id, any status
  // flip changes the per-status tally within the window.
  const signal = `${matches.length}:${matches[0]?.id ?? ""}:${["pending", "dispatched", "accepted", "dismissed"]
    .map((s) => matches.filter((m) => m.status === s).length)
    .join(",")}`;

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

// Module-level so every row on a page (matches list, review dialog) that names
// the same volunteer shares one read instead of each firing its own getDoc.
const volunteerCache = new Map<string, Volunteer | null>();

/**
 * The account/device record behind a match's `volunteerId` — name, phone,
 * email and last known location, beyond what's denormalised onto the match.
 * One-shot (not live): a sighting is a snapshot in time, and the reviewing
 * officer cares who reported it, not whether they've since changed their name.
 */
export function useVolunteer(uid?: string) {
  const [volunteer, setVolunteer] = useState<Volunteer | null>(
    uid ? volunteerCache.get(uid) ?? null : null,
  );
  const [loading, setLoading] = useState(!!uid && !volunteerCache.has(uid));

  useEffect(() => {
    if (!uid) {
      setVolunteer(null);
      setLoading(false);
      return;
    }
    if (volunteerCache.has(uid)) {
      setVolunteer(volunteerCache.get(uid) ?? null);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchVolunteer(uid)
      .then((record) => {
        volunteerCache.set(uid, record);
        if (!cancelled) setVolunteer(record);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [uid]);

  return { volunteer, loading };
}
