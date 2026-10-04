// Service Worker: macht die App nach dem ersten Laden offline nutzbar.
//
// Strategie:
// - Wasm-Dateien mit Inhalts-Hash im Namen ändern sich nie: zuerst aus dem Cache.
// - Alles andere (index.html, logicals.js, Schriften, Manifest): zuerst aus dem Netz, damit
//   Updates sofort ankommen; nur offline aus dem Cache. Früher wurde logicals.js zuerst aus
//   dem Cache geladen – nach einem Update verlangte die alte Datei dann Wasm-Dateien, die es
//   auf dem Server nicht mehr gab, und die App startete nicht.
// Die CI ersetzt @VERSION@; ein neuer Build bekommt so einen neuen Cache, der alte wird gelöscht.
const CACHE = "logicals-@VERSION@";
const IMMUTABLE = /\/[0-9a-f]{16,}\.wasm$/;

self.addEventListener("install", (event) => {
    // cache: "reload" umgeht den HTTP-Cache des Browsers (sonst evtl. veraltete Dateien).
    const urls = ["./", "index.html", "manifest.webmanifest"].map((u) => new Request(u, { cache: "reload" }));
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => cache.addAll(urls))
            .then(() => self.skipWaiting()),
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(
                keys.filter((k) => k.startsWith("logicals-") && k !== CACHE).map((k) => caches.delete(k)),
            ))
            .then(() => self.clients.claim()),
    );
});

function store(request, response) {
    if (response.ok) {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(request, copy));
    }
    return response;
}

self.addEventListener("fetch", (event) => {
    const request = event.request;
    const url = new URL(request.url);
    if (request.method !== "GET" || url.origin !== self.location.origin) return;

    if (IMMUTABLE.test(url.pathname)) {
        event.respondWith(caches.match(request).then((cached) => cached || fetch(request).then((r) => store(request, r))));
        return;
    }

    // Netz zuerst (am HTTP-Cache vorbei nur mit Prüfung), offline aus dem Cache.
    event.respondWith(
        fetch(request, { cache: "no-cache" })
            .then((response) => store(request, response))
            .catch(() => caches.match(request, { ignoreSearch: true })
                .then((cached) => cached || (request.mode === "navigate" ? caches.match("index.html") : undefined))
                .then((cached) => cached || Response.error())),
    );
});
