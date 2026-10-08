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
│ ◉ LUMEN  „Der Sturm“ ▾   [Patch] [Bühne] [Looks] [Compositions] [Szenen] [Universen] [⚙]  KONFIG ◐ BETRIEB │
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
  - „⇣ Import from Open Fixture Library“ unter der Bibliothek übernimmt ein Gerät von open-fixture-library.org
    ([FIXTURE-LIBRARY.md](FIXTURE-LIBRARY.md#import-aus-der-open-fixture-library)), Warnungen erscheinen im Dialog.
- **Stage:** Fixtures per Drag & Drop auf einem Bühnengrundriss platzieren (optional Hintergrundbild). Oben ist die
  Hinterbühne, unten das Publikum. Die Fixtures leuchten wie die echte Ausgabe; nicht platzierte stehen links.
  Jedes Fixture hat eine **Form**: Spot (rund mit Lichtfleck), Bar (LED-Bar, ein Segment pro Kopf) oder Strip
  (LED-Band als dünne Pixel-Linie). „Auto“ zeichnet Geräte der Kategorie `bar` und Multi-Pixel-Geräte als Bar, alle
  anderen als Spot. Für das ausgewählte Fixture lassen sich links Form, Drehung und Länge einstellen; `R` dreht um 15°.
  Rechts über der Bühne erscheint dazu das Steuer-Panel des Programmers als Overlay (dieselbe Komponente wie im Reiter
  Programmer, einspaltig); ✕ blendet es aus, „🎛 Controls“ wieder ein, ein anderes Fixture öffnet es erneut.
- **Looks:** feste Werte aus dem Programmer („⏺ Record look“), mit Farbvorschau, „Load into programmer“, „Update from
  programmer“ (ersetzen oder mergen), Verlauf mit „↶ Undo“/„Restore“, Duplizieren und Löschen (nicht, solange eine
  Composition den Look spielt). Links stehen die gemeinsamen Looks; „Show the looks of compositions“ zeigt dazu die
  eigenen Looks der Compositions (aufgenommene oder erzeugte Schritte), „◆ Make shared“ macht einen davon gemeinsam.
- **Compositions:** Schritte, die Looks spielen. Editor mit Standardzeiten (Fade, Hold), Ablauf (Loop, Ping-Pong, Once),
  Intensität (setzt / skaliert das Licht darunter), Schrittliste (Look wählen, eigene Zeiten mit der Standardzeit als
  Platzhalter, Load/Update des Looks, wiederholen, umsortieren), „⏺ Record step“, gemeinsame Looks hinzufügen und
  „✦ Generate from an effect…“ (Lauflicht, Farbwechsel, Regenbogen, Welle, Flackern). Dazu Live-Vorschau, „Show on stage“
  und „In scenes …“. Läuft eine Composition per „Show on stage“, trägt sie in der Liste links „ON STAGE“. Die Schritte stehen in **Zeilen**: Schritte einer Zeile laufen parallel und stehen nebeneinander,
  Fade/Hold gelten für die ganze Zeile. „Start“ pro Schritt: „Begin“ (erste Zeile), „with …“ (parallel zu einer Zeile)
  oder „after …“ (neue Zeile danach); „⏺ ∥“ nimmt den Programmer als parallelen Schritt in eine Zeile auf.
  Siehe [LOOKS-COMPOSITIONS-SCENES.md](LOOKS-COMPOSITIONS-SCENES.md#parallele-schritte).
- **Party:** Editor der Party-Matrix (siehe [PARTY-MODE.md](PARTY-MODE.md)): Zeilen und Spalten (je bis 12), Feld
  anklicken und als Text, Szene oder Composition einrichten, Beschriftung, Füll- und Rahmenfarbe, Toggle/Flash und
  Gruppe; ein Klick zwischen zwei Zeilen zieht eine Trennlinie.
- **DMX:** Monitor aller 512 Kanäle eines Universes, live.
- **Szenen:** Die Szenenliste bearbeiten (Nummer, Name, Regiehinweis, Auto-Follow; sortiert nach Nummer) und Szenen
  aufnehmen. Umsortieren per Drag & Drop am Griff ⋮⋮ (oder Alt+↑/↓): Die Reihenfolge bleibt die Nummer, die gezogene
  Szene bekommt eine freie Nummer zwischen ihren neuen Nachbarn (zwischen 2 und 3 → 2.5, sonst die nächste ganze
  Zahl); die anderen behalten ihre Nummern. „+ Empty scene“ fügt nach der markierten Szene ein (ebenso mit einer Nummer
  dazwischen), ohne Markierung am Ende. Jede Szene gehört zur Cue-Liste. Darunter belegt „Faders“ bis zu 8 Fader mit
  einer Composition (z.B. Saallicht), Name optional. Die Liste nimmt 30 % der Breite ein, der Editor 70 %; „Save“
  steht unten im Editor und bleibt beim Scrollen sichtbar.
  Das **Textbuch** wird formatiert bearbeitet: Sprechernamen in ihrer Farbe aus dem Betrieb, Regieanweisungen violett,
  Licht-Marker orange, Steuerzeichen (`**`, `:`, `//##`) abgeschwächt. Gespeichert bleibt reiner Text; eine
  Werkzeugleiste setzt den Sprecher der aktuellen Zeile, macht die Markierung zur Regieanweisung und fügt einen
  Licht-Marker ein. „👁 Preview“ zeigt den Text wie im Betrieb.
  Im Editor: Abschnitt **Compositions** (vorhandene hinzufügen, „⏺ From programmer“, „✦ Effect“; pro Einsatz Level,
  Tempo und „Restart at GO“; umsortieren; ein Klick auf den Namen öffnet die Composition – Änderungen gelten in allen
  Szenen) und **Keys** 1–8 (Composition, Toggle/Flash, Fade). Eine neue Szene übernimmt die Tasten der Szene davor.
  „Update from programmer“ und „↶ Undo“ wirken auf den Look der ersten Composition, wenn diese einen Schritt hat (der
  Schnellweg für einfache Szenen). „⧉ Duplicate“ kopiert die Szene direkt dahinter mit denselben Compositions und
  Tasten. Bei den Fade-Zeiten gibt es zusätzlich die Kurve (Linear, S-curve, Square law; leer = die des Projekts).
  „▶ Show on stage“ spielt die Szene samt Compositions auf der echten Bühne
  (über dem Playback, unter dem Programmer), „■ Stop showing“ bzw. „■ Stop“ in der Kopfleiste beendet das.
- **Settings:** Konfigurations-PIN, Netzwerkzugriff für Tablet/Handy (mit Adressen), Liste der DB-Backups und
  „Back up now“. Die globalen Fade-Zeiten stehen beim Projekt. Später Tastenkürzel und MIDI-Mapping mit MIDI-Learn
  ([MIDI.md](MIDI.md)).

### Programmer (rechts)
Die Fixtures stehen in der Mitte als **Liste** (Leuchtpunkt, voller Name – lange Namen brechen um –, Gerätetyp und
Modus, Adresse, Level, bei Multi-Pixel-Geräten die Köpfe). Das Panel rechts bekommt den größten Teil der Breite
(Gruppen : Liste : Panel ≈ 200 px : 2 : 3); seine Abschnitte stehen nebeneinander in Spalten, sobald Platz ist.
Wird **dynamisch aus dem Gerätetyp** aufgebaut, daher genügt für neue Geräte eine neue YAML:
- RGB/RGBW → **Farbrad** + Presets + Weißanteil · `color-wheel` → **Farbknöpfe** aus den YAML-`ranges`
- `pan`/`tilt` → **XY-Pad** · `strobe`/`shutter` → segmentierter Fader · `generic` → Fader mit Name
- Mehrfachauswahl (Gruppe, Shift-Klick, Lasso): Änderungen wirken auf alle ausgewählten Fixtures
- **Presets** oben im Panel, je Gruppe Color / Position / Beam: Antippen setzt das Preset auf die Auswahl, „+“ nimmt
  die Werte der Auswahl als neues Preset auf, „✎ Edit“ zum Umbenennen, Aktualisieren (aus dem Programmer) und Löschen.
  Eine Szene merkt sich, welches Preset ein Gerät nutzt – wird das Preset aktualisiert, ändern sich alle Szenen mit.
  Ein Wert von Hand löst das Gerät für diese Gruppe vom Preset. Die Szenen-Ansicht zeigt die Presets pro Gerät an.
- **Heads** bei Multi-Pixel-Geräten: „All“ oder ein einzelner Kopf, alle Regler wirken dann auf diesen Kopf; die
  Gerätekarte zeigt die Köpfe als kleine Leiste
- „Rohkanäle“ zeigt Adresse + DMX-Wert jedes Kanals

### Szenen bauen
1. Szene auswählen oder neu anlegen
2. **Vorschau:** Die Szene wird in den Programmer geladen und ist live sichtbar
3. Im Programmer anpassen
4. **Aufnehmen** (neue Szene) bzw. **Aktualisieren**: „Replace“ ersetzt das ganze Bild, „Merge“ übernimmt nur, was
   der Programmer setzt – wahlweise nur bestimmte Attributgruppen (Intensity, Color, Position, Beam, Other) und/oder
   nur die im Programmer ausgewählten Geräte. Das vorige Bild bleibt in der History
5. Optional **Probe-Überblendung** („⇢ Rehearse fade“ im Szenen-Editor): spielt das GO in die Szene auf der Bühne –
   von der Szene davor in der Cue-Liste (bei der ersten Szene von dunkel) mit den echten Fade-Zeiten inkl. Delay und
   Kompositionen. Fortschritt im Editor, danach bleibt die Szene stehen, bis die Vorschau gestoppt wird
6. **Druckbare Cue-Liste** („🖨 Cue list“): eigene Seite ohne App-Rahmen, auch als PDF über den Druckdialog

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
- **Fortschritt als Stapel** rechts: die Szene davor (klein, eingerückt; während des Fades „Fading out“ mit Balken),
  **Jetzt** groß, **Next** etwas kleiner, „Then“ (die übernächste) noch kleiner und „+ n more scenes“; eine Linie mit
  Punkten verbindet sie. Hat die Szene Licht-Marker, listet die „Now“-Box ihre Abschnitte (Start und jeder Marker
  mit Infotext), der laufende ist hervorgehoben. Ein Marker-Wechsel blendet nur innerhalb der Szene: ein kleiner
  Balken unter dem Marker zeigt den Fortschritt, der Stapel und der große Balken bleiben dem Szenenwechsel vorbehalten
  (`fade.withinScene` im Playback-Status).
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

### Bühne im Betrieb
Der Reiter **Stage** neben Scenes, Script und DMX zeigt die Bühnenansicht nur lesend: Die Fixtures leuchten wie die
echte Ausgabe, verschieben, Formen ändern und Tastenkürzel der Bühnenansicht gibt es hier nicht.

### DMX im Betrieb
Links lässt sich zwischen **Scenes** (Szenenliste) und **DMX** umschalten: Der DMX-Monitor zeigt nur die belegten
Kanäle, nach Fixture gruppiert und alle Universen untereinander, live, während GO, Back, Pause und die Extra-Tasten rechts bedienbar bleiben (auch per Tastatur). Die Wahl merkt sich
der Browser.

### Fader (Betrieb)
Unter den Extra-Tasten liegen die belegten Fader (z.B. Saallicht) mit Schieberegler und „0“/„Full“. Sie wirken
sofort, unabhängig von GO; auf dem Handy sind sie ausgeblendet. Siehe [PLAYBACK.md](PLAYBACK.md#fader-im-betrieb-zb-saallicht).

### Extra-Tasten (Betrieb)
Unter GO liegt die Tastenleiste mit den Tasten der aktuellen Szene (Name der Composition, an/aus). Sie leuchten beim
Ein- und Ausblenden mit. Alle 8 Plätze sind immer da (unbelegte gestrichelt), damit GO und die Tasten nie springen. Auf dem Handy ist die Leiste ausgeblendet.

---

## Technik
- Svelte 5 + TypeScript + Vite, Styling mit CSS-Variablen (Design Tokens), kein schweres UI-Framework.
- Farbrad, Fader und XY-Pad als eigene Komponenten (Pointer Events für Touch und Maus).
- Zentraler Store, gespeist aus WebSocket-Updates. Fortschrittsbalken werden clientseitig interpoliert
  (Server sendet Startzeit + Dauer), damit sie flüssig laufen.
- Bildschirm bleibt in der Show-Ansicht an (`lib/wakeLock.svelte.ts`): Wake Lock API, wo der Browser sie erlaubt
  (HTTPS oder localhost), sonst NoSleep.js mit unsichtbarem Video – das funktioniert auch auf Tablets über HTTP,
  startet dort aber erst nach einem Tippen (Hinweis „Tap to keep the screen on“). Später PWA im Vollbild.
- Der Rechner selbst schläft nicht ein, solange Lumen über `run.sh` läuft (`caffeinate` auf macOS).
