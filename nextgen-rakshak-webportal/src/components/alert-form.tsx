"use client";

/* eslint-disable @next/next/no-img-element */
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { createAlert } from "@/lib/firestore";
import type { AlertInput } from "@/types";

const empty: AlertInput = {
  childName: "",
  age: 0,
  gender: "Male",
  clothingDesc: "",
  parentContact: "",
  lastSeen: "",
};

export function AlertForm() {
  const router = useRouter();
  const [form, setForm] = useState<AlertInput>(empty);
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function update<K extends keyof AlertInput>(key: K, value: AlertInput[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function onPhoto(file: File | null) {
    setPhoto(file);
    setPreview(file ? URL.createObjectURL(file) : null);
  }

  /**
   * Best-effort kiosk location, used to geofence the alert to nearby volunteers.
   * Resolves to undefined if the browser blocks/lacks geolocation — the alert is
   * still sent (server falls back to notifying everyone).
   */
  function kioskLocation(): Promise<{ lat: number; lng: number } | undefined> {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      return Promise.resolve(undefined);
    }
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => resolve(undefined),
        { timeout: 5000, maximumAge: 60_000 },
      );
    });
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!photo) {
      setError("A clear photo of the child is required.");
      return;
    }
    setSubmitting(true);
    try {
      const origin = await kioskLocation();
      await createAlert(form, photo, origin);
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create alert.");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="photo">Child Photo</Label>
        <div className="flex items-center gap-4">
          <label
            htmlFor="photo"
            className="flex h-28 w-28 cursor-pointer items-center justify-center overflow-hidden rounded-md border border-dashed bg-muted/40 text-muted-foreground hover:bg-muted"
          >
            {preview ? (
              <img src={preview} alt="preview" className="h-full w-full object-cover" />
            ) : (
              <Upload className="h-6 w-6" />
            )}
          </label>
          <input
            id="photo"
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => onPhoto(e.target.files?.[0] ?? null)}
          />
          <p className="text-sm text-muted-foreground">
            Upload a clear, front-facing photo. Used to compute the face embedding.
          </p>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="childName">Child Name</Label>
          <Input
            id="childName"
            required
            value={form.childName}
            onChange={(e) => update("childName", e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="age">Age</Label>
          <Input
            id="age"
            type="number"
            min={0}
            max={18}
            required
            value={form.age || ""}
            onChange={(e) => update("age", Number(e.target.value))}
          />
        </div>
        <div className="space-y-2">
          <Label>Gender</Label>
          <Select value={form.gender} onValueChange={(v) => update("gender", v as AlertInput["gender"])}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="Male">Male</SelectItem>
              <SelectItem value="Female">Female</SelectItem>
              <SelectItem value="Other">Other</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="parentContact">Parent Contact</Label>
          <Input
            id="parentContact"
            type="tel"
            required
            placeholder="+91…"
            value={form.parentContact}
            onChange={(e) => update("parentContact", e.target.value)}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="clothingDesc">Clothing Description</Label>
        <Textarea
          id="clothingDesc"
          required
          placeholder="e.g. red t-shirt, blue shorts, white sandals"
          value={form.clothingDesc}
          onChange={(e) => update("clothingDesc", e.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="lastSeen">Last Seen Location</Label>
        <Input
          id="lastSeen"
          required
          placeholder="e.g. near Gate 3 food court"
          value={form.lastSeen}
          onChange={(e) => update("lastSeen", e.target.value)}
        />
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex gap-3">
        <Button type="submit" disabled={submitting}>
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Sending Alert…" : "Send Alert"}
        </Button>
        <Button type="button" variant="outline" onClick={() => router.push("/")}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
