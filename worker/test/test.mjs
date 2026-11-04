// Lokaler Test mit nachgebautem Cache und Anbieter. Aufruf: node worker/test/test.mjs
import assert from "node:assert/strict";

// --- Mock: Cache API und fetch zum Anbieter
const store = new Map();
globalThis.caches = {
  default: {
    async match(req) {
      const r = store.get(req.url);
      return r ? r.clone() : undefined;
    },
    async put(req, res) {
      store.set(req.url, res);
    },
  },
};
const upstreamCalls = [];
let upstreamStatus = 200;
globalThis.fetch = async (url) => {
  upstreamCalls.push(String(url));
  if (upstreamStatus !== 200) return new Response("nope", { status: upstreamStatus });
  return new Response(JSON.stringify({ ok: true, from: String(url).includes("geocoding") ? "geo" : "forecast" }), { status: 200 });
};

const { default: worker } = await import("../src/index.js");
const pending = [];
const ctx = { waitUntil: (p) => pending.push(p) };
const env = { OPEN_METEO_API_KEY: "KEY123", APP_TOKEN: "geheim" };
const call = async (path, headers = { "x-app-token": "geheim" }, e = env, method = "GET") => {
  const res = await worker.fetch(new Request("https://solar-api.example" + path, { method, headers }), e, ctx);
  await Promise.all(pending.splice(0));
  return res;
};

// 1. Gültiger Abruf, Parameter gerundet, Schlüssel nur Richtung Anbieter
let r = await call("/forecast?latitude=52.8234&longitude=8.0456&tilt=35.4&azimuth=-10");
assert.equal(r.status, 200);
assert.equal(upstreamCalls.length, 1);
assert.match(upstreamCalls[0], /^https:\/\/customer-api\.open-meteo\.com\/v1\/forecast\?latitude=52\.82&longitude=8\.05&/);
assert.match(upstreamCalls[0], /tilt=35&azimuth=-10&forecast_days=7&timezone=auto&apikey=KEY123$/);
assert.ok(!(await r.text()).includes("KEY123"));

// 2. Zweiter Abruf aus dem Zwischenspeicher, auch bei leicht anderen Koordinaten
r = await call("/forecast?latitude=52.8201&longitude=8.0451&tilt=35&azimuth=-10");
assert.equal(r.status, 200);
assert.equal(upstreamCalls.length, 1, "kein neuer Abruf beim Anbieter");

// 3. Ungültige Eingaben
for (const q of [
  "/forecast?latitude=abc&longitude=8&tilt=35&azimuth=0",
  "/forecast?latitude=52&longitude=8&tilt=35",
  "/forecast?latitude=95&longitude=8&tilt=35&azimuth=0",
  "/forecast?latitude=52&longitude=200&tilt=35&azimuth=0",
  "/forecast?latitude=52&longitude=8&tilt=120&azimuth=0",
  "/forecast?latitude=52&longitude=8&tilt=35&azimuth=400",
  "/forecast?latitude=&longitude=8&tilt=35&azimuth=0",
]) {
  r = await call(q);
  assert.equal(r.status, 400, q);
}
assert.equal(upstreamCalls.length, 1);

// 4. Zugangscode
r = await call("/forecast?latitude=52&longitude=8&tilt=35&azimuth=0", {});
assert.equal(r.status, 401);
r = await call("/forecast?latitude=52&longitude=8&tilt=35&azimuth=0", { "x-app-token": "falsch" });
assert.equal(r.status, 401);
r = await call("/forecast?latitude=52&longitude=8&tilt=35&azimuth=0", {}, { OPEN_METEO_API_KEY: "KEY123" });
assert.equal(r.status, 200, "ohne APP_TOKEN-Secret ist kein Code nötig");

// 5. Ortssuche: Länge, Kodierung, Zwischenspeicher
r = await call("/search?name=a");
assert.equal(r.status, 400);
const before = upstreamCalls.length;
r = await call("/search?name=" + encodeURIComponent("Cappeln (Oldenburg)"));
assert.equal(r.status, 200);
assert.match(upstreamCalls.at(-1), /customer-geocoding-api\.open-meteo\.com\/v1\/search\?name=Cappeln%20\(Oldenburg\)&count=8&language=de&format=json&apikey=KEY123$/);
r = await call("/search?name=" + encodeURIComponent("CAPPELN (oldenburg)"));
assert.equal(upstreamCalls.length, before + 1, "Groß-/Kleinschreibung teilt sich den Zwischenspeicher");

// 6. Fehler beim Anbieter werden nicht zwischengespeichert
upstreamStatus = 429;
r = await call("/forecast?latitude=10&longitude=10&tilt=20&azimuth=0");
assert.equal(r.status, 502);
upstreamStatus = 200;
r = await call("/forecast?latitude=10&longitude=10&tilt=20&azimuth=0");
assert.equal(r.status, 200);

// 7. Sonstiges
assert.equal((await call("/forecast?latitude=1&longitude=1&tilt=1&azimuth=1", { "x-app-token": "geheim" }, env, "POST")).status, 405);
assert.equal((await call("/irgendwas")).status, 404);

// 8. Ohne Schlüssel: kostenlose Hosts, kein apikey
const n0 = upstreamCalls.length;
r = await call("/forecast?latitude=30&longitude=30&tilt=30&azimuth=0", {}, {});
assert.equal(r.status, 200);
assert.match(upstreamCalls[n0], /^https:\/\/api\.open-meteo\.com\/v1\/forecast\?latitude=30&longitude=30&/);
assert.ok(!upstreamCalls[n0].includes("apikey"));
r = await call("/search?name=Hannover", {}, {});
assert.match(upstreamCalls.at(-1), /^https:\/\/geocoding-api\.open-meteo\.com\/v1\/search\?name=Hannover&count=8&language=de&format=json$/);

console.log("Alle Worker-Tests bestanden");
