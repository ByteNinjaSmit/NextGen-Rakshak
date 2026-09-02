# Location & Geofencing — Review

Full pass over every place the system touches coordinates: kiosk alert GPS,
volunteer position publishing, the FCM geofence, match GPS, the mesh, and
`firestore.rules`.

## How it works (as designed)

| Step | Where | Detail |
|---|---|---|
| Kiosk captures its position when an alert is filed | `alert-form.tsx` `kioskLocation()` → `createAlert()` writes `alerts/{id}.geoLocation` (GeoPoint) | Best-effort; browser block / no fix → field omitted |
| Volunteer publishes last known position | `VolunteerRepository.publishLocation()` → `volunteers/{uid}.lastLocation` (GeoPoint) + `locationUpdatedAt` | via FusedLocation |
| Server geofences the push | `functions/src/notify.ts` `broadcastAlert()` — haversine kiosk↔volunteer, `GEOFENCE_RADIUS_KM = 2` | **fail-open**: unknown / stale / in-range → notified; only a fresh, valid, out-of-range fix is excluded |
| Volunteer confirms a sighting → GPS | `ReportMatchUseCase` → `LocationProvider.current()` → `matches/{id}.location` (GeoPoint) + `hasLocation` | offline volunteers still get a satellite fix; the mesh carries the coords + flag, the relay uploads them unchanged |
| Kiosk dispatches | `matches-list.tsx` → Google Maps link from `match.location` | "Dispatch (no location)" with no link when `hasLocation === false` |

The 2 km radius lives **only** in `notify.ts`; nothing on the mobile side needs
to mirror it. Mesh TTL is mobile-only. (CLAUDE.md corrected accordingly.)

## Findings

### P0 — fixed — `firestore.rules` would have rejected every match write

`FirestoreMatchSource.submit()` now writes a `hasLocation` field (added in commit
`6aed293`), but `validMatch()` in `firestore.rules` still had `hasOnly([...])`
**without** `hasLocation` — so once the rules are deployed (task DEP-01), every
match `create` from the app fails, online and via the mesh, with the offline
Room queue retrying forever. Added `hasLocation` to both `hasAll` and `hasOnly`
plus a `d.hasLocation is bool` check, and re-noted the "keep in step with
FirestoreMatchSource.submit()" contract.

### P2 — fixed — stale volunteer location could false-exclude a helper

The geofence excluded any volunteer whose `lastLocation` was beyond 2 km, with no
check on how old that fix was. A volunteer who was 50 km away yesterday and has
since travelled to the event — but not re-opened Home — would be silently skipped.
`notify.ts` now treats a `lastLocation` older than `STALE_LOCATION_MS` (6 h) as
unknown and falls through to notifying them.

### P2 — fixed — volunteer position only refreshed on the Home screen

`publishLocation()` was called once, from `HomeViewModel.init`. A volunteer who
opens the app, starts scanning and walks the crowd for an hour had a position
frozen at Home-load time. `ScanViewModel.init` now refreshes it too — the scan
screen is exactly when the volunteer is moving.

### P3 — fixed — GPS calls could hang the confirm button ~30 s

`LocationProvider.current()` (`getCurrentLocation`) has no app-level timeout and
can take ~30 s for a cold fix indoors. `ReportMatchUseCase` wraps it in
`withTimeoutOrNull(6 s)` — a sighting with `hasLocation = false` beats a spinning
button while a volunteer holds a child. `publishLocation()` wrapped at 8 s too.

### P3 — fixed — kiosk used low-accuracy geolocation

`alert-form.tsx` now passes `enableHighAccuracy: true` (timeout raised 5→8 s). The
kiosk is stationary at a desk; a 1 km error on a 2 km fence is not acceptable when
a slightly slower fix removes it.

### P3 — fixed — naming collision

`com.rakshak.app.utils.LocationServices` (the mesh Location-toggle check) sat one
import away from `com.google.android.gms.location.LocationServices` used by
`LocationProvider`. Renamed to `LocationSettings`.

## Left as-is (documented, not changed)

- **Relayed-match timestamp is the upload time, not the sighting time.** A
  sighting that crosses the mesh reaches Firestore with `timestamp ==
  request.time` (enforced by the rules so the feed cannot be re-ordered), so the
  kiosk's "5 min ago" is measured from upload, not from when the offline
  volunteer confirmed. Carrying a separate `sightedAt` through the mesh packet
  and the rules is possible but out of scope here; the delay is bounded by the
  mesh's own TTL/relay timing (seconds to low minutes in practice).
- **`geoLocation` on an alert is not shape-validated** in `firestore.rules` (no
  `validMatch`-style function for alert `create`). Low risk — the write already
  requires the `police` custom claim.
- **No kiosk map** of recent match locations (audit GAP-04) — dashboard shows
  counts only. Separate task.
- **VER-04** (measure the geofence: inside / outside / unknown) still to run.
