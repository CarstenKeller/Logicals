// Service Worker: macht die App nach dem ersten Laden offline nutzbar.
//
// Strategie: Antworten der eigenen Seite werden zwischengespeichert. Die Startseite wird
// bevorzugt aus dem Netz geladen (damit Updates ankommen), alles andere zuerst aus dem Cache.
// Die CI ersetzt @VERSION@; ein neuer Build bekommt so einen neuen Cache, der alte wird gelöscht.
const CACHE = "logicals-@VERSION@";

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => cache.addAll(["./", "index.html", "logicals.js", "manifest.webmanifest"]))
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

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;

    if (request.mode === "navigate") {
        // Startseite: Netz zuerst, offline aus dem Cache.
        event.respondWith(
            fetch(request)
                .then((response) => {
                    const copy = response.clone();
                    caches.open(CACHE).then((cache) => cache.put("index.html", copy));
                    return response;
                })
                .catch(() => caches.match("index.html")),
        );
        return;
    }

    // Alles andere: Cache zuerst, sonst Netz (und dann zwischenspeichern).
    event.respondWith(
        caches.match(request).then((cached) => cached || fetch(request).then((response) => {
            if (response.ok) {
                const copy = response.clone();
                caches.open(CACHE).then((cache) => cache.put(request, copy));
            }
            return response;
        })),
    );
});
