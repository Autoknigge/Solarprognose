// Cloudflare Worker: Abruf-Server für die App "Solarprognose".
// Hält den Open-Meteo-Schlüssel, nimmt nur geprüfte Parameter an und merkt sich Antworten.
//
// Secrets (nicht im Code):
//   OPEN_METEO_API_KEY  optional: Schlüssel des kostenpflichtigen Open-Meteo-Tarifs. Ohne Schlüssel nutzt der Worker
//                       die kostenlose Schnittstelle (nur nicht-kommerzielle Nutzung, Fair-Use-Grenzen von Open-Meteo)
//   APP_TOKEN           optional, einfacher Zugangscode, den die App im Header X-App-Token mitschickt

const FORECAST_TTL = 1800; // 30 Minuten: Open-Meteo aktualisiert stündlich
const SEARCH_TTL = 86400; // Ortsnamen ändern sich praktisch nie
const YEARLY_TTL = 30 * 86400; // PVGIS-Jahreswerte sind Langzeitmittel und ändern sich kaum
const PRICES_TTL = 600; // Börsenpreise: nach der Veröffentlichung fest, morgige Preise kommen gegen 13 Uhr
const BZN = new Set(["DE-LU", "AT"]);

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
          "&hourly=global_tilted_irradiance,temperature_2m,cloud_cover,precipitation,snowfall,weather_code,wind_gusts_10m,uv_index,sunshine_duration" +
          `&tilt=${tilt}&azimuth=${az}&forecast_days=7&past_days=2&timezone=auto`
      )
    );
  });
}

const PROFILE_LEVELS = [1000, 975, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200, 150];

// Wolkenprofil und Regen: Bewölkung und Höhe je Druckfläche, dazu tiefe/mittlere/hohe Wolken.
async function profile(url, env, ctx) {
  let lat = num(url, "latitude");
  let lon = num(url, "longitude");
  if (![lat, lon].every(Number.isFinite)) return json({ error: "bad_params" }, 400);
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return json({ error: "bad_coordinates" }, 400);
  lat = Math.round(lat * 100) / 100;
  lon = Math.round(lon * 100) / 100;
  const vars = [
    "precipitation", "cloud_cover_low", "cloud_cover_mid", "cloud_cover_high",
    ...PROFILE_LEVELS.map((l) => `cloud_cover_${l}hPa`),
    ...PROFILE_LEVELS.map((l) => `geopotential_height_${l}hPa`),
  ].join(",");
  return cached(ctx, `https://cache.invalid/profile/${lat}/${lon}`, FORECAST_TTL, () =>
    upstream(openMeteo(env, "api", `latitude=${lat}&longitude=${lon}&hourly=${vars}&forecast_days=7&timezone=auto`))
  );
}

// Langzeit-Monatserträge je kWp von PVGIS (Europäische Kommission, JRC). Die App rechnet mit der Modulleistung hoch.
async function yearly(url, ctx) {
  let lat = num(url, "latitude");
  let lon = num(url, "longitude");
  let tilt = num(url, "tilt");
  let az = num(url, "azimuth");
  let loss = num(url, "loss");
  if (![lat, lon, tilt, az, loss].every(Number.isFinite)) return json({ error: "bad_params" }, 400);
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return json({ error: "bad_coordinates" }, 400);
  if (tilt < 0 || tilt > 90 || az < -180 || az > 180 || loss < 0 || loss > 60) return json({ error: "bad_angles" }, 400);
  lat = Math.round(lat * 100) / 100;
  lon = Math.round(lon * 100) / 100;
  tilt = Math.round(tilt);
  az = Math.round(az);
  loss = Math.round(loss);

  return cached(ctx, `https://cache.invalid/yearly/${lat}/${lon}/${tilt}/${az}/${loss}`, YEARLY_TTL, () =>
    upstream(
      "https://re.jrc.ec.europa.eu/api/v5_3/PVcalc" +
        `?lat=${lat}&lon=${lon}&peakpower=1&loss=${loss}&angle=${tilt}&aspect=${az}&outputformat=json`
    )
  );
}

function berlinDate(offsetDays) {
  const d = new Date(Date.now() + offsetDays * 86400000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Berlin", year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}

// Börsenstrompreise (Day-Ahead) von Energy-Charts, Fraunhofer ISE. Heute und morgen, falls schon veröffentlicht.
async function prices(url, ctx) {
  const bzn = url.searchParams.get("bzn") || "DE-LU";
  if (!BZN.has(bzn)) return json({ error: "bad_zone" }, 400);
  const start = berlinDate(0);
  const end = berlinDate(2);
  return cached(ctx, `https://cache.invalid/prices/${bzn}/${start}`, PRICES_TTL, () =>
    upstream(`https://api.energy-charts.info/price?bzn=${bzn}&start=${start}&end=${end}`)
  );
}

// Strommix (Energy-Charts, Fraunhofer ISE): nur erlaubte Abrufe, gemeinsamer Zwischenspeicher für alle Nutzer.
// Energy-Charts begrenzt die Anfragen je IP-Adresse; so ruft nur der Worker ab, höchstens alle paar Minuten je Reihe.
const EC_ENDPOINTS = new Set(["public_power", "cbpf", "cbet", "public_power_forecast", "ren_share_forecast"]);
const EC_TYPES = new Set(["solar", "wind_onshore", "wind_offshore", "load"]);
const EC_LIVE_TTL = 300; // Bereiche mit heutigem Tag: Daten kommen alle 15 Minuten
const EC_LONG_TTL = 3600; // lange Bereiche (Jahr) und reine Vergangenheit
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

async function ecUpstream(url) {
  for (let attempt = 0; attempt < 2; attempt++) {
    let r;
    try {
      r = await fetch(url, { headers: { "user-agent": "solarprognose-worker" } });
    } catch (e) {
      return { error: json({ error: "upstream_unreachable" }, 502) };
    }
    if (r.status === 429 && attempt === 0) {
      await new Promise((res) => setTimeout(res, 1500));
      continue;
    }
    if (!r.ok) return { error: json({ error: "upstream_error", status: r.status }, 502) };
    return { text: await r.text() };
  }
  return { error: json({ error: "upstream_error", status: 429 }, 502) };
}

async function ec(endpoint, url, ctx) {
  if (!EC_ENDPOINTS.has(endpoint)) return json({ error: "not_found" }, 404);
  const q = new URLSearchParams();
  q.set("country", "de");
  let ttl = 900;
  let key = `https://cache.invalid/ec/${endpoint}`;
  if (endpoint !== "ren_share_forecast") {
    const start = url.searchParams.get("start") || "";
    const end = url.searchParams.get("end") || "";
    if (!DATE_RE.test(start) || !DATE_RE.test(end)) return json({ error: "bad_dates" }, 400);
    const ds = Date.parse(start + "T00:00:00Z");
    const de = Date.parse(end + "T00:00:00Z");
    const days = (de - ds) / 86400000;
    if (!Number.isFinite(days) || days < 0 || days > 400 || ds < Date.parse("2015-01-01T00:00:00Z")) {
      return json({ error: "bad_dates" }, 400);
    }
    q.set("start", start);
    q.set("end", end);
    key += `/${start}/${end}`;
    ttl = days > 31 || end < berlinDate(0) ? EC_LONG_TTL : EC_LIVE_TTL;
  }
  if (endpoint === "public_power_forecast") {
    const t = url.searchParams.get("production_type") || "";
    if (!EC_TYPES.has(t)) return json({ error: "bad_type" }, 400);
    q.set("production_type", t);
    q.set("forecast_type", "current");
    key += `/${t}`;
  }
  return cached(ctx, key, ttl, () => ecUpstream(`https://api.energy-charts.info/${endpoint}?${q.toString()}`));
}

// Vorübergehend: Statuscodes der Energy-Charts-Abrufe (keine Daten), zur Fehlersuche
async function diag() {
  const d = berlinDate(0);
  const tests = ["public_power?country=de", "public_power_forecast?country=de&production_type=solar&forecast_type=current",
    "public_power_forecast?country=de&production_type=wind_onshore&forecast_type=current",
    "public_power_forecast?country=de&production_type=wind_offshore&forecast_type=current",
    "public_power_forecast?country=de&production_type=load&forecast_type=current"];
  const out = [];
  for (const t of tests) {
    const url = `https://api.energy-charts.info/${t}&start=${d}&end=${d}`;
    try {
      const r = await fetch(url, { headers: { "user-agent": "solarprognose-worker" } });
      let info = "";
      if (r.ok) {
        const o = JSON.parse(await r.text());
        const t2 = o.unix_seconds || [];
        info = " Punkte=" + t2.length + " letzter=" + (t2.length ? new Date(t2[t2.length - 1] * 1000).toISOString() : "-") + " Felder=" + Object.keys(o).join(",");
      } else info = " " + (await r.text()).slice(0, 120);
      out.push(t.split("?")[0] + (t.match(/production_type=(\w+)/) || ["", ""])[1] + " -> " + r.status + info);
    } catch (e) { out.push(t + " -> Fehler " + e); }
  }
  return new Response(out.join("\n"), { headers: { "content-type": "text/plain; charset=utf-8" } });
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
    if (new URL(request.url).pathname === "/diag") return diag();
    if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) {
      return json({ error: "unauthorized" }, 401);
    }
    const url = new URL(request.url);
    if (url.pathname === "/forecast") return forecast(url, env, ctx);
    if (url.pathname === "/profile") return profile(url, env, ctx);
    if (url.pathname === "/search") return search(url, env, ctx);
    if (url.pathname === "/yearly") return yearly(url, ctx);
    if (url.pathname === "/prices") return prices(url, ctx);
    if (url.pathname.startsWith("/ec/")) return ec(url.pathname.slice(4), url, ctx);
    if (url.pathname === "/") return json({ ok: true, service: "solarprognose-api" });
    return json({ error: "not_found" }, 404);
  },
};
