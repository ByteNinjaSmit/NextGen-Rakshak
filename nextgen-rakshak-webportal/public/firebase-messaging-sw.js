// Background FCM handler for the kiosk portal. Loaded by the browser as a
// service worker (not bundled by Next.js), so it uses the compat SDK via CDN
// and duplicates the config from src/lib/firebase.ts — env vars aren't
// available inside a service worker script.
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
  self.registration.showNotification(title ?? "NextGen Rakshak", {
    body,
    icon: "/icon.png",
  });
});
