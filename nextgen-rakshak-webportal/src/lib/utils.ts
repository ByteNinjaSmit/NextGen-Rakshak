import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Merge Tailwind class names, resolving conflicts. Used by all shadcn/ui components. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Format a Firestore Timestamp (or Date) as a short local time string. */
export function formatTime(value: { toDate?: () => Date } | Date | null | undefined) {
  if (!value) return "—";
  const date = value instanceof Date ? value : value.toDate?.();
  if (!date) return "—";
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

/** Human-readable "time ago" from a Firestore Timestamp or Date. */
export function timeAgo(value: { toDate?: () => Date } | Date | null | undefined) {
  if (!value) return "just now";
  const date = value instanceof Date ? value : value.toDate?.();
  if (!date) return "just now";
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ago`;
}
