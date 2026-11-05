# Play-Store-Eintrag: Solarprognose

## Texte (Deutsch)

**App-Name (max. 30 Zeichen):** Solarprognose

**Kurzbeschreibung (max. 80 Zeichen):**
Erwarteter Solarertrag für heute und die nächsten 7 Tage, mit Widget.

**Ausführliche Beschreibung:**

Wie viel Strom liefert deine Photovoltaik-Anlage heute, morgen und in den nächsten Tagen? Solarprognose rechnet es aus.

Du gibst Ort, Modulleistung, Neigung und Ausrichtung deiner Anlage an. Die App holt die Strahlungsprognose für genau diese Dachfläche und zeigt dir:

• Leistung pro Stunde als Kurve, für 2 oder 7 Tage
• Ertrag pro Tag in kWh
• Heute, morgen, beste Tage der Woche auf einen Blick, mit Wettersymbol
• Heute im Verlauf: Prognose, bisher erzeugte Menge und erwarteter Rest
• Jahresprognose pro Monat als Balkendiagramm
• Börsenstrompreis pro Stunde für heute und morgen
• Wolken und Temperatur zu jeder Stunde

Mehr davon:

• Mehrere Anlagen oder Dachflächen mit eigenen Werten
• Widget für den Startbildschirm mit Ertrag für heute und morgen
• Tägliche Nachricht mit der Tagesprognose zu einer Uhrzeit deiner Wahl
• Verluste und Wechselrichter-Grenze einstellbar
• Kein Konto, keine Anmeldung, deine Anlagendaten bleiben auf dem Gerät

Die Werte sind eine Modellrechnung auf Basis von Wetterdaten (Open-Meteo, CC BY 4.0). Jahreswerte: PVGIS der Europäischen Kommission. Börsenpreise: Energy-Charts, Fraunhofer ISE (CC BY 4.0). Verschattung und Schnee sind nicht berücksichtigt.

Eine Idee von autoknigge.de

## Einstellungen in der Play Console

- **Kategorie:** Wetter (alternativ Tools)
- **Kontakt:** E-Mail-Adresse, Website https://autoknigge.de (Impressum: https://autoknigge.de/impressum.html, in der App unter Einstellungen verlinkt)
- **Datenschutzerklärung:** https://autoknigge.de/solarprognose/datenschutz.html
- **Werbung:** „Enthält Werbung“ = Nein
- **Zielgruppe:** nicht für Kinder; ab 18 oder „13 und älter“ wählen, keine Kinder als Zielgruppe
- **Inhaltsfreigabe (Fragebogen):** keine Gewalt, keine Nutzerinhalte, keine Käufe; Ergebnis voraussichtlich „USK 0 / PEGI 3“
- **App-Zugriff:** keine Anmeldung nötig
- **Berechtigungen:** INTERNET, Netzwerkstatus, POST_NOTIFICATIONS (nur für die tägliche Nachricht)

## Angaben zur Datensicherheit (Data safety)

- **Daten erhoben/geteilt:** Standort wird nicht vom Gerät erhoben (kein GPS). Der vom Nutzer gewählte Ort geht an den eigenen Abruf-Server bei Cloudflare und von dort an Open-Meteo.
- **Datenerhebung durch Dritte:** keine Werbe- oder Analyse-SDKs in der App. Der Abruf läuft über den eigenen Server bei Cloudflare zu Open-Meteo, siehe Datenschutzerklärung.
- **Verschlüsselung bei der Übertragung:** Ja (HTTPS).
- **Löschung beantragen:** Daten liegen nur lokal, Deinstallation löscht sie.
- Bitte prüfe die Angaben im Formular gegen die aktuelle Google-Dokumentation, bevor du absendest.

## Grafiken, die du noch brauchst

- App-Symbol 512 × 512 px: `docs/app-icon-512.png` (dasselbe Bild wie das Symbol auf dem Handy)
- Logo für die Kopfzeile der App und Grundlage für die Funktionsgrafik: `docs/logo-solarprognose.png` (1139 × 537 px; die Funktionsgrafik muss 1024 × 500 px groß sein, also leicht zuschneiden)
- Funktionsgrafik 1024 × 500 px
- Mindestens 2, besser 4 bis 8 Screenshots vom Handy (Startseite, Diagramme, Anlage bearbeiten, Widget, Einstellungen)

## Vor der Veröffentlichung

1. Nichts zu tun für Werbung: Die App enthält keine.
2. Den Worker einrichten (siehe README, Abschnitt Worker). `API_BASE_URL` und `APP_TOKEN` müssen vor dem Release-Build als Secrets gesetzt sein, sonst ruft die App direkt die kostenlose Schnittstelle ab. Ein bezahlter Open-Meteo-Tarif ist optional, siehe README.
3. Neue private Entwicklerkonten brauchen vor der Veröffentlichung meist einen geschlossenen Test mit mehreren Testern über mehrere Wochen. Aktuelle Zahl in der Play Console unter „Test und Veröffentlichung“ nachlesen.
4. Release als Tag anlegen (zum Beispiel `1.0`), die Datei `Solarprognose.aab` im Release hochladen.
