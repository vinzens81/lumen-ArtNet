# Gerätebibliothek (YAML)

Die Gerätebibliothek beschreibt **was ein Gerätemodell kann**: Kanäle, Modi, Wertebereiche.
Sie enthält **keine** projektspezifischen Angaben (Adresse, Universe, Position, Name im Stück) –
die stehen im Projekt in der SQLite-Datenbank, siehe [PROJECT-DATA.md](PROJECT-DATA.md).

```
fixture-types/                   (Ordner konfigurierbar über lumen.fixture-library-dir)
├── generic/
│   ├── generic-dimmer.yml
│   └── generic-rgbw-par.yml
└── examples/
    └── example-moving-head-spot.yml
```

Alle `*.yml` / `*.yaml` im Ordner und in Unterordnern werden geladen (Unterordner z.B. pro Hersteller). Die `id` muss eindeutig sein – über sie referenziert ein
Projekt den Gerätetyp.

Änderungen an den Dateien übernimmt Lumen im laufenden Betrieb: Der Ordner wird alle 2 s geprüft
(`lumen.fixture-library-watch-interval`), neu geladen wird, sobald sich die Dateien eine Runde lang nicht mehr ändern.
Patch, Ausgabe und alle Browser übernehmen das neue Profil sofort. Ein fehlerhaftes Profil wird übersprungen und
erscheint als Issue.

---

## Beispiel: LED-PAR mit zwei Modi

```yaml
id: generic-rgbw-par
manufacturer: Generic
model: LED PAR 7x10W RGBW
category: par                    # par | fresnel | profile | moving-head | scanner | bar | dimmer | strobe | fog | other

modes:
  - name: 4ch
    channels:
      - function: red
      - function: green
      - function: blue
      - function: white

  - name: 8ch
    channels:
      - function: dimmer
      - function: red
      - function: green
      - function: blue
      - function: white
      - function: strobe
        ranges:
          - { from: 0,  to: 9,   label: Aus }
          - { from: 10, to: 255, label: Strobe langsam → schnell }
      - function: program
        default: 0
      - function: generic
        name: Programmgeschwindigkeit
```

## Beispiel: Moving Head mit 16-Bit-Pan/Tilt und Farbrad

```yaml
id: moving-head-spot
manufacturer: Example
model: Spot 60
category: moving-head

physical:
  pan-range: 540                 # Grad – für die UI-Anzeige
  tilt-range: 270

modes:
  - name: 13ch
    channels:
      - function: pan
        resolution: 16           # belegt 2 Kanäle (coarse + fine)
      - function: tilt
        resolution: 16
      - function: speed
        default: 0
      - function: dimmer
      - function: shutter
        default: 255
      - function: color-wheel
        ranges:
          - { from: 0,   to: 9,   label: Weiß,  color: "#ffffff" }
          - { from: 10,  to: 19,  label: Rot,   color: "#ff0000" }
          - { from: 20,  to: 29,  label: Blau,  color: "#0033ff" }
          - { from: 30,  to: 39,  label: Grün,  color: "#00ff44" }
      - function: gobo
      - function: gobo-rotation
      - function: focus
      - function: prism
      - function: reset
```

## Beispiel: klassischer Dimmerkanal (Halogen-Scheinwerfer am Dimmerpack)

```yaml
id: generic-dimmer
manufacturer: Generic
model: Dimmerkanal
category: dimmer
modes:
  - name: 1ch
    channels:
      - function: dimmer
```

---

## Unterstützte `function`-Werte

| Kategorie | Werte | Verhalten beim Szenenwechsel ([PLAYBACK.md](PLAYBACK.md)) |
|---|---|---|
| Intensität | `dimmer` | HTP, wird mit Szenen-Level skaliert |
| Farbe | `red`, `green`, `blue`, `white`, `warm-white`, `cold-white`, `amber`, `uv`, `lime`, `cyan`, `magenta`, `yellow`, `cto` | LTP, stufenlos überblendet |
| Position | `pan`, `tilt` | LTP, stufenlos überblendet |
| Strahl (stufenlos) | `focus`, `zoom`, `iris`, `gobo-rotation` | LTP, stufenlos überblendet |
| Diskret | `color-wheel`, `gobo`, `prism`, `shutter`, `strobe`, `program`, `macro`, `reset` | LTP, springt beim GO |
| Sonstiges | `speed` | LTP, springt beim GO |
| | `fog`, `fan`, `generic` | LTP, stufenlos; `generic` braucht `name` |

Optionale Felder pro Kanal: `name`, `default` (0–255, bei 16 Bit 0–65535), `resolution` (8/16), `invert` (bool), `ranges`,
`fade: snap|smooth` (überschreibt das Standardverhalten).

**Geräte ohne Dimmerkanal** (z.B. 4ch-RGBW-Modus) bekommen einen **virtuellen Dimmer**: Die Engine skaliert die
Farbkanäle (`red`, `green`, `blue`, `white`, `warm-white`, `cold-white`, `amber`, `uv`, `lime`), sodass sie sich im
Programmer und beim Faden genau wie Geräte mit Dimmer verhalten. Ohne gesetzten Dimmer bleibt ein Gerät dunkel – auch
wenn Farben gesetzt sind. Subtraktive Farben (`cyan`, `magenta`, `yellow`) werden nicht skaliert.

**Attribut-IDs:** Jeder Kanal steuert ein Attribut, dessen ID der `function` entspricht (`dimmer`, `red`, …).
Generic-Kanäle werden über ihren Namen unterschieden: `generic:<name>`, z.B. `generic:Program speed`.

### Multi-Pixel-Geräte

LED-Bars mit einzeln steuerbaren Segmenten (Pixeln) beschreibt ein `heads`-Block: Seine Kanäle wiederholen sich für
jeden Kopf, nach den Kanälen des ganzen Geräts (mit `first: true` davor). Beispiel:
`fixture-types/generic/generic-led-bar-8-pixel.yml`.

```yaml
modes:
  - name: 26ch
    channels:                    # das ganze Gerät
      - function: dimmer
      - function: strobe
    heads:
      count: 8                   # 8 Segmente
      channels:                  # wiederholt sich pro Segment
        - function: red
        - function: green
        - function: blue
```

Ungewöhnliche Reihenfolgen (z.B. erst alle Rot-, dann alle Grün-Kanäle) gehen mit `head: <n>` am einzelnen Kanal.

- **Attribute pro Kopf:** `red@3` ist das Rot von Kopf 3. Das Attribut ohne Kopf (`red`) gilt für alle Köpfe ohne
  eigenen Wert – der Farbkreis färbt also die ganze Bar, im Programmer lässt sich unter „Heads“ ein einzelner Kopf wählen.
- **Dimmer pro Kopf:** Haben die Köpfe keinen eigenen Dimmerkanal, bekommt jeder einen virtuellen (`dimmer@3`). Gibt es
  einen Dimmer für das ganze Gerät, dimmt der alles, der Kopf-Dimmer senkt nur seinen Kopf zusätzlich ab.
- **Kompositionen:** Mit „Each head separately“ läuft z.B. ein Lauflicht über die Segmente statt über ganze Geräte.

### Import aus der Open Fixture Library

In der Patch-Ansicht übernimmt „Import from Open Fixture Library“ ein Gerät von
[open-fixture-library.org](https://open-fixture-library.org): dort *Download → Open Fixture Library JSON* wählen, die Datei
und den Hersteller angeben (die OFL-Datei enthält ihn nicht). Lumen schreibt daraus ein normales YAML-Profil nach
`<hersteller>/<id>.yml` in die Bibliothek; es lässt sich danach wie jedes andere bearbeiten.

- Fähigkeiten werden auf Lumen-Funktionen abgebildet (`Intensity` → `dimmer`, `ColorIntensity` → `red` …,
  `WheelSlot` → `color-wheel`/`gobo` je nach Rad, `ShutterStrobe` → `shutter` oder `strobe`, …). Die einzelnen
  Bereiche werden zu `ranges` mit Beschriftung und Farbe.
- Fine-Kanäle direkt hinter ihrem Grobkanal werden zu 16 Bit, Matrix-Kanäle (Pixel) zu Köpfen.
- Was Lumen nicht ausdrücken kann, wird vereinfacht und als Warnung gemeldet (z.B. ein Kanal mit Farbrad *und*
  Radrotation, ein Fine-Kanal an anderer Stelle, Pixelgruppen). Vor dem Einsatz auf der Bühne die Kanäle prüfen.
- Gibt es die ID schon, fragt der Dialog, ob das vorhandene Profil ersetzt werden soll.

### Mitgelieferte Profile in der Mac-App

Die Mac-App kopiert ihre Profile beim Start in `~/Library/Application Support/Lumen/fixture-types` und merkt sich in
`.bundled-profiles.properties`, welche Version installiert wurde. Bei einem Update der App werden Profile, die nicht
verändert wurden, durch die neue Version ersetzt. **Selbst bearbeitete Profile bleiben**; die neue mitgelieferte
Version liegt daneben als `<name>.yml.shipped` (wird nicht geladen) zum Vergleichen. Gelöschte Profile kommen nicht
wieder. Installationen vor dieser Funktion kennen die alte Version nicht: Ein dort abweichendes Profil gilt als
bearbeitet und bleibt.

---

## Validierung der Bibliothek

- Pflichtfelder, eindeutige `id`, bekannte `function`s, Werte 0–255, Farben als `#rrggbb`
- `generic` ohne `name` → Fehler
- Fehlerhafte Dateien werden übersprungen, nicht der ganze Start abgebrochen. Probleme sind über
  `GET /api/library/issues` abrufbar und werden im Konfigurationsmodus angezeigt.

**Änderungen an der Bibliothek vs. bestehende Projekte:** Verweist ein Projekt auf einen Typ/Modus, der fehlt
oder dessen Kanalanzahl sich geändert hat, zeigt der Patch eine Warnung (z.B. „überlappt jetzt mit Fixture X“).
