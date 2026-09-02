// Background FCM handler for the kiosk portal. Loaded by the browser as a
// service worker (not bundled by Next.js), so it uses the compat SDK via CDN
// and duplicates the config from src/lib/firebase.ts — env vars aren't
// available inside a service worker script. If the Firebase web config changes,
// update it here too.
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js");

firebase.initializeApp({
  apiKey: "AIzaSyD4fAQJ3heIVz0aYgmE8zPDZlNpuj-yjRM",
  authDomain: "nextgen-rakshak.firebaseapp.com",
  projectId: "nextgen-rakshak",
  storageBucket: "nextgen-rakshak.firebasestorage.app",
  messagingSenderId: "865839432135",
  appId: "1:865839432135:web:6a0963d13dbcbb0f7619e2",
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const { title, body } = payload.notification ?? {};
  // Stash where a click should navigate — fcmOptions.link is not applied when a
  // custom onBackgroundMessage handler is present, so notificationclick reads
  // this instead.
  const link = payload.data?.link || "/";
  self.registration.showNotification(title ?? "NextGen Rakshak", {
    body,
    icon: "/icon.png",
    data: { link },
  });
});

// Focus an existing kiosk tab (navigating it to the link) or open a new one.
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const link = event.notification.data?.link || "/";
  const url = new URL(link, self.location.origin).href;
  event.waitUntil(
    (async () => {
      const clients = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
      const tab = clients.find((c) => c.url.startsWith(self.location.origin));
      if (tab) {
        await tab.focus();
        if ("navigate" in tab) {
          try {
            await tab.navigate(url);
          } catch (_) {
            /* uncontrolled client — focus alone is the best we can do */
          }
        }
        return;
      }
      await self.clients.openWindow(url);
    })(),
  );
});
