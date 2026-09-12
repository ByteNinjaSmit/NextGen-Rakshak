# NextGen Rakshak — Police Kiosk Portal

Next.js 14 (App Router) web portal used at a help-desk / kiosk. An officer files
a missing-child alert, watches volunteer sightings arrive in real time, reviews
each sighting against the parent's photo, and closes the case.

Stack: Next.js 14 · React 18 · TypeScript · Tailwind + shadcn/ui (Radix) ·
`lucide-react` · Firebase JS SDK 10 (Auth, Firestore, Storage, Functions,
Messaging). Path alias `@/*` → `src/*`. All lists are live `onSnapshot`
subscriptions — nothing polls.

## Routes

| Route | Page | Notes |
|---|---|---|
| `/login` | Google sign-in | honours `?next=`, shows the reason an account was refused |
| `/` | Dashboard | stats cards, active alerts, live match preview |
| `/alerts/new` | File an alert | photo + details + browser GPS |
| `/alerts/history` | Alert history | active and resolved |
| `/matches` | Sighting feed | opens the review dialog |
| `/profile` | Officer profile | identity card + editable contact fields |

`src/app/(kiosk)/layout.tsx` is the guarded shell (sidebar + notification bell).
It renders a full-screen loader until the `police` claim is confirmed, so no
protected content flashes before the redirect. `/login` sits outside the guard.
`error.tsx`, `global-error.tsx` and `not-found.tsx` handle failures.

## Source layout

```
src/
├── app/
│   ├── layout.tsx            root layout — establishes the auth session only
│   ├── (kiosk)/              guarded routes: layout, page (dashboard),
│   │                         alerts/new, alerts/history, matches, profile
│   ├── login/                sign-in route (outside the guard)
│   ├── globals.css · icon.svg · apple-icon.png
│   └── error.tsx · global-error.tsx · not-found.tsx
├── components/
│   ├── auth-provider.tsx     session + `police` claim in context
│   ├── login-screen.tsx · sidebar-nav.tsx · brand-logo.tsx
│   ├── stats-cards.tsx · active-alerts-list.tsx · alert-history-list.tsx
│   ├── alert-form.tsx · alert-detail-dialog.tsx
│   ├── matches-list.tsx · pending-matches-preview.tsx · match-review-dialog.tsx
│   ├── notification-bell.tsx · officer-identity-card.tsx · officer-profile-form.tsx
│   ├── confirm-dialog.tsx · full-screen-loader.tsx
│   └── ui/                   shadcn primitives (badge, button, card, dialog,
│                             input, label, select, table, textarea)
├── hooks/                    use-alerts (active/all/matches/counts), use-require-officer
├── lib/                      firebase (init + push), auth, firestore, officers, utils
└── types/                    Alert, AlertInput, AlertAuthor, Match, Officer, statuses
```

## Authentication and authorisation

Google only. Signing in proves identity; **authority** is the `police` custom
claim, granted by the `claimOfficerRole` Cloud Function on first sign-in
(self-service — no allow-list, no approval step). The one refusal is an account
that already has a `volunteers/{uid}` document: one account, one role.

- `lib/auth.ts` — `signInWithGoogle` (popup, redirect fallback),
  `handleRedirectResult`, `ensureOfficerRole` (calls the function, then
  `getIdToken(true)` so the new claim lands on this session), `hasOfficerRole`,
  `signOutUser`.
- `hooks/use-require-officer.ts` — client-side redirect to `/login?next=…`.
  Firebase web auth lives in IndexedDB, so Next.js middleware cannot see the
  session; **this is UX, not the security boundary.** `firestore.rules` enforces
  the same claim server-side.

## Data API

`lib/firestore.ts`

| Function | Does |
|---|---|
| `uploadChildPhoto(file)` | uploads to `alert_images/…`; sanitises the filename, because `storage.rules` matches a single path segment |
| `createAlert(input, photo, author, origin?)` | uploads the photo, then writes the `alerts` document with `createdBy`, an empty `embedding` placeholder and a server timestamp |
| `resolveAlert(id)` | `active → resolved`, which triggers the server-side purge |
| `dispatchMatch` / `acceptMatch` / `dismissMatch` | the only mutation the kiosk may make to a match: its `status` |
| `subscribeActiveAlerts` / `subscribeAllAlerts` / `subscribeMatches` | live feeds |
| `fetchAlert(id)` | one alert, for the review dialog |
| `fetchMatchCounts()` | aggregate counts for the dashboard cards |

`lib/officers.ts` — `subscribeOfficer`, `updateOfficerProfile` (only
`displayName / phone / station / badgeNumber`, matching the rules' `hasOnly`
guard), `saveOfficerFcmToken`.

## Behaviour worth knowing

- **Alert form** — photo, name, age, gender, clothing, parent contact and
  last-seen place/date/time are required; identifying marks are optional. It asks
  the browser for a GPS fix at submit; if that is blocked the alert is still filed,
  just without a geofence (the Cloud Function then notifies every volunteer).
- **Match review** — shows the volunteer's captured face crop **beside** the
  parent's photo, flags a sighting that arrived over the mesh (`relayedBy`),
  suppresses the map link when `hasLocation` is false (the coordinates are `0,0`),
  and offers Accept / Dismiss (behind a confirm step) / Dispatch. A reviewed match
  locks.
- **Notification bell** — requests browser permission, registers
  `public/firebase-messaging-sw.js`, stores the token on `officers/{uid}.fcmToken`
  for `onMatchCreated` to use, and shows in-page toasts via `onMessage`.
  Requires `NEXT_PUBLIC_FIREBASE_VAPID_KEY`; without it push is disabled rather
  than crashing.
- **PWA / branding** — `public/manifest.webmanifest`, icons in `public/icons`,
  `src/app/icon.svg` + `apple-icon.png`, theme colour `#0E2A66`.

## Setup

```bash
cp .env.local.example .env.local   # fill from Firebase Console → Project settings
npm install
npm run dev                        # http://localhost:3000
```

Variables (all `NEXT_PUBLIC_`, from Project settings → Your apps → Web):
`FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`,
`FIREBASE_STORAGE_BUCKET`, `FIREBASE_MESSAGING_SENDER_ID`, `FIREBASE_APP_ID`,
`FIREBASE_MEASUREMENT_ID`, plus `FIREBASE_VAPID_KEY` (Cloud Messaging → Web Push
certificates) for the notification bell.

Two notes:

1. `getFunctions(app, "us-central1")` must match `setGlobalOptions({ region })`
   in `functions/src/index.ts`.
2. `public/firebase-messaging-sw.js` **duplicates the Firebase web config
   inline** — a service worker script gets no env vars. Update it too whenever the
   web config changes.

Scripts: `npm run dev` · `npm run build` · `npm run start` · `npm run lint`.

`.env*` is gitignored. Never commit it.

---

Full system documentation: [`../docs/SYSTEM-REFERENCE.md`](../docs/SYSTEM-REFERENCE.md).
