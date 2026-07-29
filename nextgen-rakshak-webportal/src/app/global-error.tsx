"use client";

import { useEffect } from "react";

/**
 * Last-resort boundary for crashes in the root layout itself (AuthProvider
 * setup, font loading, the Firebase client). `app/error.tsx` renders *inside*
 * the root layout, so it cannot catch those — this replaces the whole document.
 *
 * That replacement is also why the styling is inline: globals.css is imported
 * by the root layout that failed, so no stylesheet is guaranteed here.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Kiosk failed to start:", error);
  }, [error]);

  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "1.5rem",
          fontFamily: "system-ui, -apple-system, Segoe UI, sans-serif",
          background: "#f8fafc",
          color: "#0f172a",
        }}
      >
        <div style={{ maxWidth: "28rem", textAlign: "center" }}>
          <h1 style={{ fontSize: "1.25rem", fontWeight: 600, margin: "0 0 .5rem" }}>
            Rakshak kiosk failed to start
          </h1>
          <p style={{ margin: "0 0 1rem", fontSize: ".875rem", color: "#475569" }}>
            The portal could not load. Active alerts and filed matches are unaffected —
            they live in Firestore, not in this page.
          </p>
          <pre
            style={{
              margin: "0 0 1rem",
              padding: ".75rem",
              borderRadius: ".375rem",
              background: "#e2e8f0",
              fontSize: ".75rem",
              textAlign: "left",
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
            }}
          >
            {error.message || "Unknown error"}
            {error.digest ? ` (${error.digest})` : ""}
          </pre>
          <button
            onClick={reset}
            style={{
              padding: ".5rem 1rem",
              borderRadius: ".375rem",
              border: "none",
              background: "#0f172a",
              color: "#fff",
              fontSize: ".875rem",
              cursor: "pointer",
            }}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
