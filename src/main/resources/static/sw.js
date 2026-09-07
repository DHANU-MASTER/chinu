/**
 * AI Teacher service worker.
 * - Pre-caches the app shell so the PWA opens instantly and works offline.
 * - Static assets: cache-first with background refresh (stale-while-revalidate).
 * - API calls (/api/…): always network — lesson content must be live.
 */
var CACHE_NAME = 'ai-teacher-v1';

var CORE_ASSETS = [
  'index.html',
  'landing.html',
  'css/style.css',
  'js/speech.js',
  'js/voice-input.js',
  'js/i18n.js',
  'js/recording.js',
  'js/teaching.js',
  'js/assessment.js',
  'js/progress.js',
  'manifest.webmanifest',
  'icon.svg',
  'icon-maskable.svg'
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      return cache.addAll(CORE_ASSETS);
    }).then(function () {
      return self.skipWaiting();
    })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (key) {
        return key !== CACHE_NAME;
      }).map(function (key) {
        return caches.delete(key);
      }));
    }).then(function () {
      return self.clients.claim();
    })
  );
});

self.addEventListener('fetch', function (event) {
  var url = new URL(event.request.url);

  // Never cache API traffic — lesson content must be live.
  if (url.pathname.indexOf('/api/') !== -1 || event.request.method !== 'GET') {
    return;
  }

  event.respondWith(
    caches.match(event.request).then(function (cached) {
      var fetchPromise = fetch(event.request).then(function (response) {
        if (response && response.ok) {
          var copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) {
            cache.put(event.request, copy);
          });
        }
        return response;
      }).catch(function () {
        return cached;
      });
      return cached || fetchPromise;
    })
  );
});
