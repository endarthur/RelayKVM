// RelayKVM Service Worker
const CACHE_NAME = 'relaykvm-1.2.66';
const ASSETS = [
  './',
  './index.html',
  './portal.html',
  './relaykvm-adapter.js',
  './manifest.json',
  './icons/icon.svg',
  './icons/icon-16.png',
  './icons/icon-32.png',
  './icons/icon-48.png',
  './icons/icon-128.png',
  './icons/icon-192.png',
  './icons/icon-512.png'
];

// Install - cache assets
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('RelayKVM: Caching assets');
        return cache.addAll(ASSETS);
      })
      .then(() => self.skipWaiting())
  );
});

// Activate - clean old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => {
      return Promise.all(
        keys.filter(key => key !== CACHE_NAME)
            .map(key => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch - serve from cache, fallback to network
self.addEventListener('fetch', event => {
  // Skip non-GET requests
  if (event.request.method !== 'GET') return;

  // Skip cross-origin requests (fonts, etc.)
  if (!event.request.url.startsWith(self.location.origin)) return;

  event.respondWith(
    caches.match(event.request)
      .then(cached => {
        if (cached) {
          // Return cached version, but update cache in background
          event.waitUntil(
            fetch(event.request)
              .then(response => {
                if (response.ok) {
                  caches.open(CACHE_NAME)
                    .then(cache => cache.put(event.request, response));
                }
              })
              .catch(() => {}) // Ignore network errors
          );
          return cached;
        }

        // Not cached - fetch from network
        return fetch(event.request)
          .then(response => {
            // Cache successful responses
            if (response.ok) {
              const clone = response.clone();
              caches.open(CACHE_NAME)
                .then(cache => cache.put(event.request, clone));
            }
            return response;
          });
      })
  );
});
