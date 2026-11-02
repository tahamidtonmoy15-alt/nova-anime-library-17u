// NOVA service worker — bump CACHE on every release so users always get the newest files.
const CACHE = 'nova-shell-v17-reels-premium';
const FONTS = 'anime-glass-fonts-v1';
const CORE = ['./', './index.html', './manifest.webmanifest', './hero.webp', './favicon.png', './apple-touch-icon.png', './icon-192.png', './icon-512.png', './icon-maskable-512.png'];

// Cache each file separately: one missing file must never break the whole install.
self.addEventListener('install', e => e.waitUntil(
  caches.open(CACHE)
    .then(c => Promise.all(CORE.map(u => c.add(u).catch(err => console.warn('SW skip', u, err)))))
    .then(() => self.skipWaiting())
));

self.addEventListener('activate', e => e.waitUntil(
  caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith('nova-shell-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim())
));

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const u = new URL(req.url);

  // Google Fonts: cache after first load so the design looks the same offline
  if (u.hostname === 'fonts.googleapis.com' || u.hostname === 'fonts.gstatic.com') {
    e.respondWith(caches.open(FONTS).then(async c => {
      const hit = await c.match(req);
      const net = fetch(req).then(r => { if (r && (r.ok || r.type === 'opaque')) c.put(req, r.clone()); return r; }).catch(() => hit || Response.error());
      return hit || net;
    }));
    return;
  }
  if (u.origin !== location.origin) return;

  // Pages: network first (3s) so updates arrive, cache fallback for offline
  if (req.mode === 'navigate') {
    e.respondWith(new Promise(resolve => {
      let done = false;
      const finish = r => { if (!done && r) { done = true; resolve(r); } };
      const timer = setTimeout(() => caches.match('./index.html').then(finish), 3000);
      fetch(req).then(r => {
        clearTimeout(timer);
        if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put('./index.html', copy)); }
        finish(r);
      }).catch(() => {
        clearTimeout(timer);
        caches.match('./index.html').then(r => { if (!done) { done = true; resolve(r || Response.error()); } });
      });
    }));
    return;
  }

  // Static files: cache first, then network
  e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(r => {
    if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
    return r;
  })));
});
