# FCM Push & Notification Flow — Review

End-to-end pass over both push paths and the token lifecycle.

## The two flows

### A — new alert → volunteers

```
kiosk createAlert() → alerts/{id}
  → onAlertCreated (functions) → broadcastAlert()
      → haversine geofence (fail-open) → FCM data-only multicast to volunteers/{uid}.fcmToken
          → RakshakMessagingService.onMessageReceived (every app state)
              → NotificationHelper.showAlert(alertId, title, body)
                  → tap → MainActivity (singleTop, EXTRA_ALERT_ID)
                      → AppNavigation deep-links to the Scan screen
```

### B — volunteer sighting → filing officer

```
mobile match → matches/{id}
  → onMatchCreated (functions) → reads alerts/{alertId}.createdBy.uid
      → notifyOfficerOfMatch() → FCM to officers/{uid}.fcmToken (data.link = /matches)
          → foreground: onKioskMessage → notification-bell inbox
          → background: firebase-messaging-sw.js onBackgroundMessage → showNotification
              → notificationclick → focus/open the kiosk at /matches
```

Token lifecycle: volunteer token written on sign-in (`register()`) and refreshed
by `onNewToken`; officer token written by the notification bell. Both Cloud
Functions prune a token FCM reports as permanently invalid (`fcmToken = ""`).

## Findings and fixes

### P1 — alert notification was wrong when the app was backgrounded

`broadcastAlert` sent a message with **both** a `notification` block and `data`.
When an FCM message carries a `notification` payload and the app is not in the
foreground, Android displays it **itself** and `onMessageReceived` never runs —
so the volunteer got a plain default notification on the default channel, not the
app's `IMPORTANCE_HIGH` `rakshak_alerts` channel, and the tap went nowhere useful.
Only the foreground case used `NotificationHelper.showAlert`.

**Fix:** `broadcastAlert` now sends a **data-only** message (`title`/`body`/
`alertId`/`type` in `data`, `android.priority: "high"`).
`RakshakMessagingService.onMessageReceived` runs in every app state and builds the
notification the same way each time.

### P1 — notification tap did not open the scan screen (audit GAP-03)

`NotificationHelper` launched `MainActivity` with no extra, so a tap landed on
Home (or Login). Synopsis §7 step 5: "tapping it opens the app directly into the
camera scan screen."

**Fix:** `showAlert` puts `EXTRA_ALERT_ID` on the intent (distinct `PendingIntent`
per alert). `MainActivity` is now `singleTop`, reads the extra in `onCreate` /
`onNewIntent`, and passes it to `AppNavigation`, which navigates to Scan once the
volunteer is signed in and Home has settled (so Back from Scan is sane).

### P1 — officer match notification click did nothing

The kiosk service worker defines its own `onBackgroundMessage`, so FCM's
`fcmOptions.link` is never applied, and the SW had no `notificationclick`
listener — clicking the "New Match Sighting" notification just… sat there.

**Fix:** `notifyOfficerOfMatch` also puts `link: "/matches"` in `data`; the SW
stores it on the shown notification and a new `notificationclick` handler focuses
an existing kiosk tab (navigating it to `/matches`) or opens one.

### P2 — sign-in could fail or hang on FCM registration

`volunteers.register(volunteer)` ran **inside** the sign-in `runCatching`, and
`FcmTokenProvider.token()` has no timeout. A slow/failed token fetch either made a
successful sign-in report an error, or hung the sign-in coroutine with the busy
spinner up.

**Fix:** `register()` wraps the token fetch in `withTimeoutOrNull(8 s)` and
tolerates an empty token (recovered by `onNewToken` / the next launch);
`LoginViewModel` wraps the whole `register()` call in its own `runCatching`.

### P2 — notification id collided on child name

`showAlert` used `childName.hashCode()` as the notification id, so two alerts for
children with the same name would overwrite each other. Now keyed on `alertId`.

### P2 — kiosk "Enable alerts" had no error path

`requestKioskNotifications()` threw if the VAPID key was unset (`getToken`), and
`onEnable` had no `catch`. Now it fails closed (returns `null` with a console
warning) and the bell shows a short reason under the button
("blocked in site settings" / "push could not be set up").

## Verified correct (no change)

- Geofence fail-open + stale-fix guard (see `docs/location-geofence-review.md`).
- Invalid-token pruning on both paths.
- `onMatchCreated` skips cleanly when the alert has no `createdBy.uid`.
- `firestore.rules` allows `fcmToken` on the officer-profile `hasOnly` list; a
  volunteer writes its own `volunteers/{uid}` doc.
- `/icon.png` exists in `public/` for the web notification.
- SW config is intentionally duplicated (env vars are unavailable in a worker) —
  comment now says to keep it in sync.

## Still open

- **Deployment (DEP-01/02).** None of this has run against real FCM — the
  functions have never been deployed. First deploy must confirm a data-only alert
  reaches a device and a match push reaches the kiosk (VER-03).
- Volunteer has no in-app indicator when notification permission is denied.
