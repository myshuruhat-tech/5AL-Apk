const CACHE_NAME = 'live-screen-v59';
const APP_SHELL = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png'];
// Third-party assets cached on first load so the app still works when the CDN
// is blocked or the TV's connection is weak.
const CDN_ASSETS = [
  'https://cdn.jsdelivr.net/npm/hls.js@1',
  'https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;700;900&family=Manrope:wght@400;600;700;800&family=Poppins:wght@700;800&display=swap'
];
const CDN_HOSTS = ['cdn.jsdelivr.net', 'fonts.googleapis.com', 'fonts.gstatic.com'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(async (cache) => {
      await Promise.all(APP_SHELL.map((u) => cache.add(u).catch(() => {})));
      await Promise.all(CDN_ASSETS.map((u) => cache.add(u).catch(() => {})));
      // optional local copy of hls.js, cached only if the file exists
      await cache.add('./hls.min.js').catch(() => {});
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.pathname.startsWith('/.netlify/functions')) return; // never cache API calls

  // CDN scripts and fonts: cache-first, refresh in background.
  if (CDN_HOSTS.includes(url.hostname)) {
    event.respondWith(
      caches.match(req).then((hit) => {
        const net = fetch(req).then((res) => {
          if (res && (res.ok || res.type === 'opaque')) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then((c) => c.put(req, copy)).catch(() => {});
          }
          return res;
        }).catch(() => hit);
        return hit || net;
      })
    );
    return;
  }

  if (url.origin !== location.origin) return; // other cross-origin (streams, APIs): leave alone

  // The page itself (index.html): network-first so a new deploy shows up on the very next
  // open; falls back to the cached copy if the network is slow (3s) or offline.
  if (req.mode === 'navigate' || url.pathname === '/' || url.pathname.endsWith('/index.html')) {
    event.respondWith(
      caches.match(req).then((hit) => {
        const net = fetch(req).then((response) => {
          if (response && response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((c) => c.put(req, copy)).catch(() => {});
          }
          return response;
        });
        if (!hit) return net;
        const timeout = new Promise((resolve) => setTimeout(() => resolve(hit), 3000));
        return Promise.race([net.catch(() => hit), timeout]);
      })
    );
    return;
  }

  // Own files: stale-while-revalidate — show the cached copy instantly,
  // refresh it in the background (new version appears on the next open).
  event.respondWith(
    caches.match(req).then((hit) => {
      const net = fetch(req)
        .then((response) => {
          if (response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(req, copy)).catch(() => {});
          }
          return response;
        })
        .catch(() => hit);
      return hit || net;
    })
  );
});
