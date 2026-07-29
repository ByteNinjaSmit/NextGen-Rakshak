"use client";

import { useEffect, useState } from "react";
import { Loader2, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/components/auth-provider";
import { updateOfficerProfile } from "@/lib/officers";
import type { OfficerProfileInput } from "@/types";

const EMPTY: OfficerProfileInput = {
  displayName: "",
  phone: "",
  station: "",
  badgeNumber: "",
};

export function OfficerProfileForm() {
  const { user, officer, officerError } = useAuth();
  const [form, setForm] = useState<OfficerProfileInput>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // The record arrives over onSnapshot a beat after the page mounts, and again
  // whenever another kiosk edits it — reseed the form each time.
  useEffect(() => {
    if (!officer) return;
    setForm({
      displayName: officer.displayName ?? "",
      phone: officer.phone ?? "",
      station: officer.station ?? "",
      badgeNumber: officer.badgeNumber ?? "",
    });
  }, [officer]);

  function set<K extends keyof OfficerProfileInput>(key: K, value: string) {
    setSaved(false);
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user) return;
    setError(null);
    setSaving(true);
    try {
      await updateOfficerProfile(user.uid, form);
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save your profile.");
    } finally {
      setSaving(false);
    }
  }

  if (!officer) {
    // Never spin indefinitely: AuthProvider backfills a missing record, and
    // reports here if even that failed.
    return officerError ? (
      <p className="py-6 text-sm text-destructive">
        {officerError} Sign out and back in to retry.
      </p>
    ) : (
      <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading your officer record…
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <div className="grid gap-2">
        <Label htmlFor="displayName">Name</Label>
        <Input
          id="displayName"
          value={form.displayName}
          onChange={(e) => set("displayName", e.target.value)}
          required
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="grid gap-2">
          <Label htmlFor="badgeNumber">Badge number</Label>
          <Input
            id="badgeNumber"
            value={form.badgeNumber}
            onChange={(e) => set("badgeNumber", e.target.value)}
            placeholder="e.g. MH-4821"
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="station">Station / post</Label>
          <Input
            id="station"
            value={form.station}
            onChange={(e) => set("station", e.target.value)}
            placeholder="e.g. Gate 3 Control Room"
          />
        </div>
      </div>

      <div className="grid gap-2">
        <Label htmlFor="phone">Contact number</Label>
        <Input
          id="phone"
          type="tel"
          value={form.phone}
          onChange={(e) => set("phone", e.target.value)}
          placeholder="+91…"
        />
      </div>

      <div className="flex items-center gap-3 pt-2">
        <Button type="submit" disabled={saving}>
          {saving && <Loader2 className="h-4 w-4 animate-spin" />}
          Save profile
        </Button>
        {saved && !saving && (
          <span className="flex items-center gap-1 text-sm text-muted-foreground">
            <Check className="h-4 w-4" /> Saved
          </span>
        )}
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
    </form>
  );
}
