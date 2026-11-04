// Cloudflare Worker: Abruf-Server für die App "Solarprognose".
// Hält den Open-Meteo-Schlüssel, nimmt nur geprüfte Parameter an und merkt sich Antworten.
//
// Secrets (nicht im Code):
//   OPEN_METEO_API_KEY  optional: Schlüssel des kostenpflichtigen Open-Meteo-Tarifs. Ohne Schlüssel nutzt der Worker
//                       die kostenlose Schnittstelle (nur nicht-kommerzielle Nutzung, Fair-Use-Grenzen von Open-Meteo)
//   APP_TOKEN           optional, einfacher Zugangscode, den die App im Header X-App-Token mitschickt

const FORECAST_TTL = 1800; // 30 Minuten: Open-Meteo aktualisiert stündlich
const SEARCH_TTL = 86400; // Ortsnamen ändern sich praktisch nie

const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };

function json(obj, status = 200, extra = {}) {
  return new Response(JSON.stringify(obj), { status, headers: { ...JSON_HEADERS, ...extra } });
}

function num(url, key) {
  const v = url.searchParams.get(key);
  if (v === null || v.trim() === "") return NaN;
  return Number(v);
}

async function cached(ctx, keyUrl, ttl, produce) {
  const cache = caches.default;
  const key = new Request(keyUrl);
  const hit = await cache.match(key);
  if (hit) return hit;
  const body = await produce();
  if (body.error) return body.error;
  const res = new Response(body.text, {
    status: 200,
    headers: { ...JSON_HEADERS, "cache-control": `public, max-age=${ttl}` },
  });
  ctx.waitUntil(cache.put(key, res.clone()));
  return res;
}

async function upstream(url) {
  let r;
  try {
    r = await fetch(url, { headers: { "user-agent": "solarprognose-worker" } });
  } catch (e) {
    return { error: json({ error: "upstream_unreachable" }, 502) };
  }
  if (!r.ok) return { error: json({ error: "upstream_error", status: r.status }, 502) };
  return { text: await r.text() };
}

// Mit Schlüssel: kostenpflichtige Hosts (customer-*), ohne Schlüssel: kostenlose Hosts.
function openMeteo(env, kind, query) {
  const key = env.OPEN_METEO_API_KEY;
  const host = key ? `customer-${kind}` : kind;
  const path = kind === "api" ? "v1/forecast" : "v1/search";
  return `https://${host}.open-meteo.com/${path}?${query}` + (key ? `&apikey=${encodeURIComponent(key)}` : "");
}

async function forecast(url, env, ctx) {
  let lat = num(url, "latitude");
  let lon = num(url, "longitude");
  let tilt = num(url, "tilt");
  let az = num(url, "azimuth");
  if (![lat, lon, tilt, az].every(Number.isFinite)) return json({ error: "bad_params" }, 400);
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return json({ error: "bad_coordinates" }, 400);
  if (tilt < 0 || tilt > 90 || az < -180 || az > 180) return json({ error: "bad_angles" }, 400);

  // Auf ca. 1 km runden: gleiche Gegend, gleiche Antwort, weniger Abrufe beim Anbieter
  lat = Math.round(lat * 100) / 100;
  lon = Math.round(lon * 100) / 100;
  tilt = Math.round(tilt);
  az = Math.round(az);

  return cached(ctx, `https://cache.invalid/forecast/${lat}/${lon}/${tilt}/${az}`, FORECAST_TTL, () => {
    return upstream(
      openMeteo(
        env,
        "api",
        `latitude=${lat}&longitude=${lon}` +
          "&hourly=global_tilted_irradiance,temperature_2m,cloud_cover" +
          `&tilt=${tilt}&azimuth=${az}&forecast_days=7&timezone=auto`
      )
    );
  });
}

async function search(url, env, ctx) {
  const name = (url.searchParams.get("name") || "").trim();
  if (name.length < 2 || name.length > 60) return json({ error: "bad_name" }, 400);

  return cached(ctx, `https://cache.invalid/search/${encodeURIComponent(name.toLowerCase())}`, SEARCH_TTL, () => {
    return upstream(openMeteo(env, "geocoding-api", `name=${encodeURIComponent(name)}&count=8&language=de&format=json`));
  });
}

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);
    if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) {
      return json({ error: "unauthorized" }, 401);
    }
    const url = new URL(request.url);
    if (url.pathname === "/forecast") return forecast(url, env, ctx);
    if (url.pathname === "/search") return search(url, env, ctx);
    if (url.pathname === "/") return json({ ok: true, service: "solarprognose-api" });
    return json({ error: "not_found" }, 404);
  },
};
