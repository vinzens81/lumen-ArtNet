# Web-Oberfläche – Konzept

> **Sprache der Oberfläche: Englisch.** Die Skizzen unten verwenden zur Beschreibung noch deutsche Begriffe.
> In der App heißen die Modi **Setup** (Konfiguration), **Show** (Betrieb) und später **Party**. Weitere Beispiele:
> Szenen → *Scenes*, Programmer → *Programmer*, Aufnehmen → *Record*, Regiehinweis → *Cue note*.

Die Oberfläche hat zwei klar getrennte Modi:

| | **Konfiguration** (Einrichten & Proben) | **Betrieb** (Vorstellung) |
|---|---|---|
| Wer/Wann | Technik bei Einrichtung und Proben | Bedienung während der Vorstellung |
| Ziel | Projekt einrichten, Szenen bauen | Sicher und fehlerfrei GO drücken |
| Bearbeiten | alles | nichts. Nur Playback, Master, Blackout |
| Look | informationsreich | reduziert, große Elemente, extrem dunkel |

Später kommt als dritter Modus der **Party-Modus** (Live-Steuerung mit Effekten, BPM und Executors) dazu,
siehe [PARTY-MODE.md](PARTY-MODE.md). Die App-Shell sollte den Moduswechsel daher von Anfang an für mehr als zwei
Modi auslegen.

Der Wechsel erfolgt über einen Schalter oben rechts. **Betrieb → Konfiguration** erfordert die
**Konfigurations-PIN**, damit es nicht versehentlich während der Vorstellung passiert. Ist keine PIN gesetzt,
genügt eine Bestätigung. Mit PIN zeigt die Kopfleiste im Konfigurationsmodus „🔒 Locked“ bzw. „🔓 Unlocked“; eine
gesperrte Änderung öffnet das PIN-Feld (mit Ziffernblock für Touch).

## Look & Feel

**Leitidee: „Das UI leuchtet wie das Licht.“**

- **Dark Mode first** (`#0b0d12`). Im Betrieb noch dunkler, damit der Bildschirm im Saal nicht stört.
- **Glow statt Flächen:** Fixtures und Szenen-Kacheln strahlen in ihrer Farbe/Helligkeit.
- **Glassmorphism** für Panels, feine 1px-Ränder, Akzentfarbe Neon-Cyan `#3cf0ff`.
- **Modusfarbe:** Konfiguration = Cyan-Akzent, Betrieb = Bernstein/Rot-Akzent. Der Modus ist auf einen Blick erkennbar.
- **Typografie:** `Inter` für Text, `JetBrains Mono` für Adressen, Szenennummern und Zeiten.
- **Touch-first:** große Fader und Buttons, Tablet im Querformat als Pult.

---

## Modus 1: Konfiguration

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ◉ LUMEN  „Der Sturm“ ▾   [Patch] [Bühne] [Szenen] [Universen] [⚙]  KONFIG ◐ BETRIEB │
├────────────┬──────────────────────────────────────────────────┬────────────────┤
│ GRUPPEN    │                                                  │ PROGRAMMER     │
│ ▣ Alle     │         BÜHNENANSICHT / LISTE                    │ Front links    │
│ ▣ Front    │                                                  │ ────────────── │
│ ▣ Seiten   │      ◯ Spot M (Lichtkegel)                       │   ( Farbrad )  │
│ ▣ Hinter-  │                                                  │ Dimmer ▮▮▮▮▯   │
│   grund    │   ◉ FL      ◉ FM       ◉ FR                      │ Pan/Tilt [XY]  │
│            │  (warm)    (aus)     (warm)                      │ Rohkanäle ▸    │
├────────────┴──────────────────────────────────────────────────┴────────────────┤
│ Szene 4 „Sturm zieht auf“   [Aufnehmen ⏺] [Aktualisieren] [Programmer leeren]   │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Tabs
- **Projekt-Auswahl** (oben links): Projekt wechseln. Im Tab **Projects** anlegen, bearbeiten, exportieren
  (Download als `.lumen.json`) und importieren.
- **Universen:** Art-Net-Ziele anlegen (IP/Broadcast, Net/SubNet/Universe), später Node-Discovery.
- **Patch:**
  - Gerätebibliothek links durchsuchen, Gerät per „+“ oder Drag & Drop hinzufügen (Name, Modus, Universe, Adresse,
    Anzahl). Die nächste freie Adresse wird vorgeschlagen.
  - **Adress-Raster** (512 Zellen pro Universe) zeigt belegte Bereiche farbig, Überschneidungen rot.
  - Gruppen zuweisen.
- **Stage:** Fixtures per Drag & Drop auf einem Bühnengrundriss platzieren (optional Hintergrundbild). Oben ist die
  Hinterbühne, unten das Publikum. Die Fixtures leuchten wie die echte Ausgabe; nicht platzierte stehen links.
- **Compositions:** Bibliothek der Kompositionen (Lauflicht, Farbwechsel, Regenbogen, Welle, Flackern,
  Schrittfolge) mit Editor, Live-Vorschau und „Show on stage“. Eingefügt werden sie im Szenen-Editor (Abschnitt
  „Compositions“), jede Szene bekommt eine eigene Kopie. Siehe [COMPOSITIONS.md](COMPOSITIONS.md).
- **DMX:** Monitor aller 512 Kanäle eines Universes, live.
- **Szenen:** Die Szenenliste bearbeiten (Nummer, Name, Regiehinweis, Auto-Follow; sortiert nach Nummer) und Szenen
  aufnehmen. Umsortieren per Drag & Drop am Griff ⋮⋮ (oder Alt+↑/↓): Die Reihenfolge bleibt die Nummer, die gezogene
  Szene bekommt eine freie Nummer zwischen ihren neuen Nachbarn (zwischen 2 und 3 → 2.5, sonst die nächste ganze
  Zahl); die anderen behalten ihre Nummern. „+ Empty scene“ fügt nach der markierten Szene ein (ebenso mit einer Nummer
  dazwischen), ohne Markierung am Ende. „▶ Show on stage“ spielt die Szene samt Kompositionen auf der echten Bühne
  (über dem Playback, unter dem Programmer), „■ Stop showing“ bzw. „■ Stop“ in der Kopfleiste beendet das.
- **Settings:** Konfigurations-PIN, Netzwerkzugriff für Tablet/Handy (mit Adressen), Liste der DB-Backups und
  „Back up now“. Die globalen Fade-Zeiten stehen beim Projekt. Später Tastenkürzel und MIDI-Mapping mit MIDI-Learn
  ([MIDI.md](MIDI.md)).

### Programmer (rechts)
Wird **dynamisch aus dem Gerätetyp** aufgebaut, daher genügt für neue Geräte eine neue YAML:
- RGB/RGBW → **Farbrad** + Presets + Weißanteil · `color-wheel` → **Farbknöpfe** aus den YAML-`ranges`
- `pan`/`tilt` → **XY-Pad** · `strobe`/`shutter` → segmentierter Fader · `generic` → Fader mit Name
- Mehrfachauswahl (Gruppe, Shift-Klick, Lasso): Änderungen wirken auf alle ausgewählten Fixtures
- „Rohkanäle“ zeigt Adresse + DMX-Wert jedes Kanals

### Szenen bauen
1. Szene auswählen oder neu anlegen
2. **Vorschau:** Die Szene wird in den Programmer geladen und ist live sichtbar
3. Im Programmer anpassen
4. **Aufnehmen** (neue Szene) bzw. **Aktualisieren** (bestehende überschreiben)
5. Optional **Probe-Überblendung**: von der vorherigen Szene mit den echten Fade-Zeiten abspielen

---

## Modus 2: Betrieb

Optimiert für eine Aufgabe: zur richtigen Zeit GO drücken, ohne Fehler.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ● BETRIEB   „Der Sturm“                  Art-Net ● live      21:14:03   🔒 Konfig │
├──────────────────────────────────────────────┬─────────────────────────────────┤
│ SZENEN                                       │  JETZT                          │
│   1    Einlass                       ✓       │  ┃ 4  Sturm zieht auf           │
│   2    Akt 1 – Morgendämmerung       ✓       │  ┃ ████████████▒▒▒▒  Fade-In 2.1s │
│   3    Am Strand                     ✓       │                                 │
│ ▶ 4    Sturm zieht auf          ◀ JETZT      │  AUSBLENDEND                    │
│   5    Blitz                    ◀ NÄCHSTE    │    3  Am Strand  ▒▒▒░░░  1.4s   │
│   6    Nach dem Sturm                        │                                 │
│   6.5  Prospero allein                       │  NÄCHSTE                        │
│   7    Finale                                │  ┃ 5  Blitz                     │
│                                              │  ┃ „GO bei: Donnerschlag (Ton)“ │
│                                              │                                 │
│                                              │  ┌───────────────────────────┐  │
│                                              │  │           G O             │  │
│                                              │  │        (Leertaste)        │  │
│                                              │  └───────────────────────────┘  │
│                                              │  [◀ Back] [⏸ Pause] [⏭ Fertig]  │
├──────────────────────────────────────────────┴─────────────────────────────────┤
│ MASTER ▮▮▮▮▮▮▮▮▮▮▯ 92 %                                           [ BLACKOUT ]  │
└────────────────────────────────────────────────────────────────────────────────┘
```

- **Szenenliste** links: erledigte Szenen gedimmt, aktuelle hervorgehoben, nächste markiert.
  Antippen merkt eine Szene als *nächste* vor (Sprung), ausgelöst wird sie erst mit GO.
- **Jetzt / Ausblendend / Nächste** mit Fortschrittsbalken und Restzeit der laufenden Fades.
- **Regiehinweis** der nächsten Szene groß sichtbar (das Stichwort für GO).
- **GO-Button** riesig, auf Touch reagiert er sofort (pointerdown). Doppel-GO-Schutz über eine kurze Sperrzeit
  (konfigurierbar, z.B. 300 ms).
- Kleine **Farbvorschau** pro Szene in der Liste (Gradient der enthaltenen Farben).
- **Auto-Follow:** Szenen mit Stay-Dauer tragen ein ⟳-Symbol in der Liste. Während der Stay-Zeit zeigt „Jetzt“
  einen Countdown („weiter in 6 s“). Ein Tipp auf GO löst sofort aus.
- Nach Neustart: Banner „Show bei Szene 4 fortsetzen?“
- Kein Programmer, kein Patch, keine Szenenbearbeitung. Nur Master und Blackout.

### Tastatur (Betrieb)
`Leertaste` GO · `←` Back · `P` Pause · `Enter` Fade abschließen · `B` Blackout · `↑/↓` nächste Szene wählen ·
`1`–`8` Extra-Tasten (Flash: solange gedrückt)

### Extra-Tasten (Betrieb)
Unter GO liegt die Tastenleiste mit den belegten Extra-Tasten (Name, Typ-Symbol, an/aus). Sie leuchten beim Ein- und
Ausblenden mit. In der aktuellen Szene nicht freigeschaltete Tasten sind ausgegraut. Auf dem Handy ist die Leiste
ausgeblendet.

---

## Technik
- Svelte 5 + TypeScript + Vite, Styling mit CSS-Variablen (Design Tokens), kein schweres UI-Framework.
- Farbrad, Fader und XY-Pad als eigene Komponenten (Pointer Events für Touch und Maus).
- Zentraler Store, gespeist aus WebSocket-Updates. Fortschrittsbalken werden clientseitig interpoliert
  (Server sendet Startzeit + Dauer), damit sie flüssig laufen.
- Wake Lock API im Betrieb (Bildschirm geht nicht aus), später PWA im Vollbild.
