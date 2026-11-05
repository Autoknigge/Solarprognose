# Solarprognose

Android-App (Kotlin, Jetpack Compose) für die erwartete Solarstromerzeugung. Paketname `de.autoknigge.solarprognose`.

Funktionen in Version 1.0: Stundenkurve (2 oder 7 Tage), Tagesertrag mit Wettersymbol, Heute im Verlauf, Jahresprognose pro Monat (PVGIS), Börsenstrompreis (Energy-Charts), Tabelle, mehrere Anlagen, Startbildschirm-Widget, tägliche Benachrichtigung. Die App ist kostenlos und enthält keine Werbung.

## Erster Build

1. Neues GitHub-Repo `Solarprognose` anlegen und den Inhalt dieses Ordners hochladen (Branch `main`).
2. Unter *Actions* läuft der Build `Build` automatisch. Das Ergebnis liegt als Artefakt `Solarprognose` mit `Solarprognose.apk` und `Solarprognose.aab`.
3. Ohne Secrets gilt: Debug-Signatur, Direktabruf bei der kostenlosen Open-Meteo-Schnittstelle. Das reicht für Tests auf dem eigenen Handy.

Der Code wurde ohne Compiler geschrieben. Falls der erste Build mit einem Fehler abbricht, den Text des Fehlers aus dem Actions-Log kopieren und weitergeben, dann wird er behoben.

## Secrets (Settings → Secrets and variables → Actions)

| Secret | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | Upload-Schlüssel als Base64 (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Daten zum Schlüssel |
| `API_BASE_URL` | Adresse des Workers, zum Beispiel `https://solar-api.autoknigge.de` (siehe Abschnitt Worker) |
| `APP_TOKEN` | Zugangscode, den App und Worker teilen (beliebige lange Zeichenfolge) |
| `OPEN_METEO_API_KEY` | Optional: Schlüssel des kostenpflichtigen Open-Meteo-Tarifs, wird nur vom Worker-Workflow gebraucht |
| `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` | Zum Einspielen des Workers (siehe Abschnitt Worker) |

Schlüssel erzeugen, falls noch keiner existiert:

```
keytool -genkeypair -v -keystore release.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

## Release

Ein Tag ohne „v“ erzeugt Version und Release, zum Beispiel:

```
git tag 1.0 && git push origin 1.0
```

`versionName` kommt aus dem Tag, `versionCode` aus der Build-Nummer des Workflows. Das Release enthält `Solarprognose.apk` (zum Testen) und `Solarprognose.aab` (für den Play Store).

## Wichtig: Wetterdaten

Die App nutzt Open-Meteo. Die kostenlose Schnittstelle ist laut Nutzungsbedingungen nur für nicht-kommerzielle Zwecke erlaubt und hat Fair-Use-Grenzen. Eine kostenlose App ohne Werbung und ohne Einnahmen fällt nach dem Wortlaut darunter. Bei Werbung, Bezahlfunktionen oder sehr vielen Nutzern braucht es einen kostenpflichtigen Tarif (https://open-meteo.com/en/pricing). Den Schlüssel dazu bekommt nicht die App, sondern der Worker: Legst du das GitHub-Secret `OPEN_METEO_API_KEY` an, nutzt der Worker die bezahlte Schnittstelle, ohne das Secret die kostenlose. Ohne `API_BASE_URL` ruft die App Open-Meteo direkt ab, das ist nur für Tests auf dem eigenen Handy gedacht.

Die Namensnennung (CC BY 4.0) steht in der App auf der Startseite und in den Einstellungen.

## Worker (Cloudflare)

Der Ordner `worker/` enthält einen kleinen Abruf-Server. Er nimmt von der App nur Ort, Neigung und Ausrichtung entgegen, prüft die Werte, hängt den Open-Meteo-Schlüssel an und merkt sich jede Antwort 30 Minuten (Ortssuche einen Tag). Der Schlüssel liegt nie in der App. Gleiche Gegend und gleiche Winkel teilen sich einen Abruf, das spart Kontingent beim kostenpflichtigen Tarif.

Einrichtung:

1. Optional: Open-Meteo-Tarif abschließen und den Schlüssel als GitHub-Secret `OPEN_METEO_API_KEY` ablegen. Ohne Schlüssel läuft der Worker über die kostenlose Schnittstelle.
2. In Cloudflare unter *My Profile → API Tokens* einen Token mit der Vorlage „Edit Cloudflare Workers“ erstellen. Für die eigene Domain braucht er zusätzlich die Berechtigung *Zone → DNS → Edit* für autoknigge.de. Token und Konto-ID als `CLOUDFLARE_API_TOKEN` und `CLOUDFLARE_ACCOUNT_ID` ablegen.
3. `APP_TOKEN` als beliebige lange Zeichenfolge festlegen und als Secret ablegen. `API_BASE_URL` auf `https://solar-api.autoknigge.de` setzen.
4. Der Workflow `Worker veröffentlichen` startest du von Hand (Actions → Worker veröffentlichen → Run workflow). Er führt die Tests aus und spielt den Worker samt Secrets ein. Der Name `solar-api.autoknigge.de` steht in `worker/wrangler.toml` und lässt sich dort ändern.
5. Prüfen: `https://solar-api.autoknigge.de/` liefert ohne Zugangscode `unauthorized`, mit Header `X-App-Token` `{"ok":true,…}`.

Die eigene Domain ist Absicht: Der Zwischenspeicher funktioniert bei Cloudflare nicht unter `workers.dev`. Der Zugangscode steckt in der App und hält nur zufällige Nutzer fern, keine entschlossenen. Als zweite Bremse in Cloudflare unter *Security → WAF → Rate limiting rules* eine Regel für den Host `solar-api.autoknigge.de` anlegen, zum Beispiel höchstens 60 Anfragen pro Minute je IP-Adresse.

Lokaler Test des Workers: `node worker/test/test.mjs`.

## Aufbau

- `Calc.kt`: Leistungsrechnung (Einstrahlung, Temperaturverlust, Wirkungsgrad, Grenze)
- `Api.kt`, `Repo.kt`, `Store.kt`: Abruf, Zwischenspeicher, lokale Daten
- `Extras.kt`, `ExtraViews.kt`: Jahreswerte (PVGIS), Börsenpreise, Wettersymbole, zusätzliche Karten
- `Screens.kt`, `Charts.kt`, `Theme.kt`: Oberfläche
- `Work.kt`, `Widget.kt`: Benachrichtigung und Widget (WorkManager)
- `worker/`: Cloudflare Worker (Abruf-Server) mit Tests
- `docs/`: Store-Eintrag (die Datenschutzerklärung liegt im Repo der Webseite unter `solarprognose/datenschutz.html`)
