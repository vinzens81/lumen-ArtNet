# Kompositionen & dynamische Szenen

> **Status:** K1 (Engine, Datenbank, REST-API) ist umgesetzt, Editor/Vorschau (K2) und Extra-Tasten (K3) folgen
> (siehe [ROADMAP.md](ROADMAP.md), Iteration K). Abweichungen vom ursprünglichen Konzept stehen unter
> [Umsetzung K1](#umsetzung-k1).

## Problem

Eine Szene ist heute ein **statischer Look**: pro Fixture feste Attributwerte, die per GO eingeblendet werden und
dann stehen. Viele Theater-Momente brauchen aber **Bewegung im Licht**:

- RGB-PARs, die langsam zwischen Abendrot-Tönen wechseln
- ein Lauflicht über eine Reihe von Scheinwerfern (Rampe, Kirmes, „Zug fährt vorbei“)
- Kerzen- oder Feuerflackern auf einzelnen Geräten
- all das **gleichzeitig** und **zusätzlich** zu einem festen Grundlicht (z.B. Spielfläche warmweiß, Hintergrund lebt)

## Idee

Eine **Komposition** ist ein parametrisierter Ablauf über eine geordnete Auswahl von Fixtures. Kompositionen werden
in einer **Bibliothek im Projekt** gepflegt. Fügt man eine in eine Szene ein, entsteht eine **unabhängige Kopie**,
die man in der Szene frei anpassen kann (Farben, Tempo, Ziel, …), ohne andere Szenen zu verändern.

Eine Szene besteht danach aus zwei Teilen:

```
Szene 4 „Jahrmarkt“
├── Basis (statisch, wie heute)        Front-PARs warmweiß 80 %, Gegenlicht blau 40 %
└── Kompositionen (0..n, laufen mit)
    ├── „Kirmes-Lauflicht“             Gruppe „Rampe“ (8 Dimmer), 0,4 s pro Schritt, Level 100 %
    └── „Bunte Buden“                  Gruppe „Hintergrund-PARs“, Farbwechsel Rot→Gelb→Grün, 6 s, Level 70 %
```

Fixtures, die keine Komposition berührt, verhalten sich exakt wie heute.

Zusätzlich lassen sich einzelne Kompositionen im Betriebsmodus **frei per Taste zuschalten**, unabhängig von der
Szene (z.B. Gewitterblitze, siehe [Freie Kompositionen im Betrieb](#freie-kompositionen-im-betrieb)).

## Bausteine (Typen von Kompositionen)

**Vorlagen mit Parametern** decken die häufigsten Fälle ab, ohne dass man Schritte von Hand bauen muss. Jede Vorlage
ist intern eine reine Funktion `f(zeit, fixtureIndex, parameter) → Attributwerte`.

| Vorlage | Wirkt auf | Parameter |
|---|---|---|
| **Lauflicht (Chase)** | Intensität | Tempo (s pro Schritt), Breite (wie viele gleichzeitig an), Richtung (vor / zurück / Ping-Pong / zufällig), Übergang (hart ↔ weich), Min/Max-Intensität |
| **Farbwechsel** | Farbe | 2–n Farben, Haltezeit und Überblendzeit pro Farbe, Spreizung (0 = alle gleich, 100 % = versetzt → Farb-Lauflicht) |
| **Regenbogen** | Farbe | Tempo (s pro Umlauf), Sättigung, Spreizung |
| **Welle / Puls** | Intensität | Form (Sinus, Dreieck, Sägezahn, Rechteck), Tempo, Min/Max, Spreizung |
| **Flackern** | Intensität | Charakter (Kerze, Feuer, defekte Röhre), Stärke, Tempo; Zufall mit festem Seed, damit es reproduzierbar ist |

Dazu von Anfang an die freie **Schrittfolge (Chaser)**:
- Folge von Mini-Looks (Schritten). Jeder Schritt enthält Werte pro Fixture und Attribut und hat eine eigene Halte- und
  Überblendzeit.
- Ablauf: Loop oder Ping-Pong; Tempo-Faktor wie bei den Vorlagen
- Schritte werden wie Szenen **aus dem Programmer aufgenommen** („Record step“, „Update step“), dazu Einfügen,
  Duplizieren, Umsortieren und Löschen
- Ziel ist bei einer Schrittfolge implizit: alle Fixtures, die in mindestens einem Schritt vorkommen. Fehlt ein Gerät in
  einem Schritt, ist es in diesem Schritt dunkel (Intensität 0, wie bei Szenen).

Später:
- **Einmaliger Ablauf** für Vorlagen und Schrittfolgen: Der Ablauf läuft einmal durch und bleibt am Ende stehen
  (z.B. „Sonnenaufgang über 60 s“)
- **Bewegungsbild** für Moving Heads, siehe [MOVEMENT.md](MOVEMENT.md)
- **BPM-Kopplung** des Tempos (kommt mit der Beat-Clock aus dem [Party-Modus](PARTY-MODE.md))

### Ziel-Auswahl und Reihenfolge (Vorlagen)

- Ziel ist eine **Gruppe** oder eine **feste Liste von Fixtures**. Die **Reihenfolge** bestimmt Lauflicht-Richtung
  und Spreizung.
- Bei einer Gruppe gilt das Ziel **dynamisch**: Ändern sich die Mitglieder oder ihre Reihenfolge, folgt die
  Komposition automatisch, auch in allen Szenen-Kopien. Wird die Gruppe gelöscht, wird die Komposition im Editor
  als Problem gemeldet und bleibt dunkel.
- Eine Komposition setzt nur die Attribute, die ihr Typ betrifft, und nur bei Geräten, die sie haben.
  Ein Farbwechsel auf einem reinen Dimmer wird im Editor als Hinweis gemeldet, statt still nichts zu tun.
- Multi-Pixel-Geräte (Attribute wie `red@2`) können später als einzelne „Zellen“ im Ziel auftauchen.

## Bibliothek und Kopien

- Die **Bibliothek** (Tab „Compositions“) enthält die Kompositionen des Projekts. Sie ist Ausgangspunkt und
  Vorlagensammlung, steuert aber selbst kein Licht.
- **In eine Szene einfügen** kopiert die Komposition komplett (Typ, Parameter, Ziel, Schritte). Die Kopie merkt sich
  ihre **Herkunft** (`source_id`). Das dient nur der Anzeige („aus Bibliothek: Kirmes-Lauflicht“) und der Regel für
  nahtloses Weiterlaufen (siehe unten).
- Spätere Änderungen in der Bibliothek wirken **nicht** auf bestehende Kopien. Der Szenen-Editor kann eine Kopie aber
  auf Wunsch „aus der Bibliothek neu laden“, und eine angepasste Kopie lässt sich „in die Bibliothek übernehmen“.
- Löschen einer Bibliotheks-Komposition ist jederzeit möglich. Die Kopien bleiben erhalten und verlieren nur die Herkunft.

## Mischung in der Szene

Pro Fixture und Attribut gilt (vor dem bestehenden Layer-Merger und der Crossfade-Logik):

**Intensität:**
- Modus **absolut** (Standard für Lauflicht, Welle, Schrittfolge): Die Komposition liefert die Intensität. Steckt das
  Gerät auch in der Basis, gilt **HTP**: `max(basis, komposition × level)`. So bleibt ein Grundlicht stehen und das
  Lauflicht „blitzt darüber“.
- Modus **relativ** (Standard für Flackern): Die Komposition moduliert den Basiswert:
  `basis × faktor(t)`. Die Kerze flackert um die programmierte Helligkeit herum.

**Farbe und übrige Attribute (LTP):**
- Die Komposition **ersetzt** den Basiswert, gemischt über ihren Level:
  `basis + (komposition − basis) × level`. Bei Level 50 % liegt der Farbwechsel „halb“ über der Grundfarbe.
- Wirken mehrere Kompositionen auf dasselbe Attribut desselben Geräts, gewinnt die **spätere in der Liste der
  Szene**. Der Szenen-Editor zeigt solche Überschneidungen an.

**Geräte ohne Basiswert:** Ein Gerät, das nur in einer Komposition vorkommt, ist trotzdem Teil der Szene
(heute wären nicht enthaltene Geräte aus). Beim Farbwechsel ohne Basis-Intensität braucht es einen Default:
Vorschlag Intensität 100 % × Level, einstellbar in der Komposition.

## Zeit, Überblendung und Playback

- Kompositionen laufen auf der **virtuellen Playback-Uhr**. **Pause** friert also auch Lauflichter und Farbwechsel
  ein, „Fade abschließen“ betrifft nur den Szenenübergang, nicht den Ablauf der Komposition.
- **Fade-In:** Die Komposition läuft ab dem GO und wird mit dem Szenenlevel eingeblendet. Sie startet also nicht
  erst nach dem Fade.
- **Fade-Out:** Die Komposition der alten Szene **läuft während des Ausblendens weiter** und wird dunkler, statt
  einzufrieren. Das erweitert den heutigen Snapshot: Eingefroren wird nur der statische Anteil, laufende
  Kompositionen der ausblendenden Szene werden mitgeführt. Bei einem Doppel-GO werden ältere ausblendende
  Kompositionen in den Snapshot übernommen, damit weiterhin höchstens zwei Ebenen laufen.
- **Weiterlaufen in der Folgeszene:** Hat eine Kopie in der neuen Szene dieselbe Herkunft und denselben Typ wie eine
  Kopie der laufenden Szene, gilt sie als „dieselbe“ Komposition und **läuft nahtlos weiter**, ohne Phasensprung und
  ohne Ein-/Ausblenden (z.B. Kerzenflackern über drei Szenen). Geänderte Parameter (Level, Tempo, Farben) werden über
  die Fade-Zeit übernommen, die Phase bleibt erhalten.
  Pro Einsatz lässt sich **„Neu starten“** einschalten: Dann startet die Komposition beim GO von vorn und wird wie
  eine neue eingeblendet.
- Farbwerte, die die Komposition liefert, sind „LTP-Ziele, die sich bewegen“: `Crossfade.mix` bekommt pro Tick die
  **aktuellen** Zielwerte statt eines festen Szenenwerts. Die Regel „War das Gerät beim GO dunkel, springt die Farbe
  sofort“ bleibt.

```
            GO (Sz. 4)                                  GO (Sz. 5)
            │                                           │
Basis       │▁▂▄▆██████████████████████████████████████│█▇▅▃▁
Lauflicht   │▁▂▄▆█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█│▇▁▅▁▃▁▁       läuft beim Ausblenden weiter
Farbwechsel │▁▂▄▆ rot → gelb → grün → rot → gelb → grün│→ rot → …    in Sz. 5 auch enthalten: läuft nahtlos weiter
```

## Freie Kompositionen im Betrieb

Für Effekte, die nicht an eine Szene gebunden sind (Gewitterblitze, Feuerwerk, „Polizeilicht“ bei einem Gag):

- Im Projekt werden bis zu ~8 **Extra-Tasten** belegt. Jede Taste hält eine Kopie einer Bibliotheks-Komposition.
- Verhalten pro Taste: **Toggle** (an/aus mit eigener Ein-/Ausblendzeit) oder **Flash** (aktiv, solange gedrückt)
- Sie laufen als eigene Ausgabe-Ebene **über dem Playback** (Priorität zwischen Playback 50 und Programmer 100)
  und mischen nach denselben Regeln (Intensität HTP, Farbe LTP).
- Bedienung im Betriebsmodus: Tastenleiste unter dem GO-Bereich, Tastaturkürzel (`1`–`8`), später MIDI
  (siehe [MIDI.md](MIDI.md)). Aktive Tasten leuchten.
- GO, Back und Springen beeinflussen die Extra-Tasten nicht. Blackout und Grand Master wirken auch auf sie.
- Sie sind die Vorstufe der Executors im [Party-Modus](PARTY-MODE.md); dort kommen Fader und mehr Slots dazu.

## Bedienung (UI)

**Konfigurationsmodus, neuer Tab „Compositions“ (Bibliothek):**
- Liste der Kompositionen des Projekts mit animiertem Mini-Vorschaubalken
- Editor für Vorlagen: Vorlage wählen, Ziel (Gruppe oder Fixtures, Reihenfolge per Drag & Drop), Parameter als
  Fader/Farbfelder
- Editor für Schrittfolgen: Schrittliste mit Farbvorschau, Halte-/Überblendzeit pro Schritt, „Record step“ aus dem
  Programmer
- **Live-Vorschau:** Die Komposition läuft auf den Gerätekarten und, auf Wunsch, auf der echten Bühne.
  Technisch eine eigene Vorschau-Ebene (Priorität zwischen Playback und Programmer). Im Show-Modus gesperrt wie der Programmer.
- Belegung der Extra-Tasten für den Betrieb

**Szenen:**
- Im Szenen-Editor ein Abschnitt „Compositions“: aus der Bibliothek einfügen (Kopie), anpassen, entfernen,
  Reihenfolge, Level (0–100 %), Tempo-Faktor, „Neu starten“
- „Record scene“ nimmt wie bisher nur die statische Basis aus dem Programmer auf. Bereits enthaltene Kompositionen
  bleiben beim Aktualisieren erhalten.
- Szenenliste: Symbol für dynamische Szenen, Farbvorschau animiert

**Betriebsmodus:** Kompositionen der Szenen laufen einfach mit. Neu ist nur die Leiste mit den Extra-Tasten.
Optional zeigt die Szenenkarte, welche Kompositionen aktiv sind.

**Sicherheit:** Schnelles Flackern/Strobe über ~3 Hz wird im Editor gekennzeichnet (Fotosensibilität, im Theater
ggf. ankündigungspflichtig).

## Architektur

- **Domain** (`de.vinzens.lumen.domain.composition`): `Composition` (Name, Ziel, Typ + Parameter, Herkunft),
  versiegelte Typen pro Vorlage (`Chase`, `ColorCycle`, `Rainbow`, `Wave`, `Flicker`) und `StepSequence`, jeweils
  reine Funktionen ohne eigene Uhr. Die `Scene` erhält eine geordnete Liste ihrer Kompositions-Kopien mit Level,
  Tempo-Faktor und „Neu starten“. Eine `SceneLook`-Berechnung liefert `values(t)` = Basis ⊕ Kompositionen.
  `Playback`/`Crossfade` rechnen damit statt mit festen Werten.
- Extra-Tasten: eigenes Domain-Objekt (z.B. `CompositionKeys`) mit Toggle/Flash-Zustand und Fade, liefert einen
  `LayerOutput`.
- **Tests:** ausschließlich mit injizierter `Clock` bzw. expliziten Zeitpunkten. Pro Vorlage Tabellen-Tests
  (Zeit × Fixture-Index → erwarteter Wert), dazu Tests für Schrittfolgen (Halte-/Überblendzeiten, Ping-Pong) und
  Playback-Tests für Weiterlaufen beim Szenenwechsel, „Neu starten“, Pause und Doppel-GO.
- **Application:** Use Cases für die Bibliothek (CRUD, Schritte aufnehmen), Einfügen/Anpassen in Szenen,
  Extra-Tasten belegen und auslösen, Vorschau starten/stoppen.
- **Web-Adapter:** DTOs pro Vorlage (Parameter typisiert, kein freies JSON in der API), WebSocket-Event bei Änderungen
  und für den Zustand der Extra-Tasten.
- **Party-Modus:** Die Kompositions-Engine **ist** die Effekt-Engine aus [PARTY-MODE.md](PARTY-MODE.md),
  Schrittfolgen sind die dortigen Chaser. Executors bauen auf den Extra-Tasten auf. Dort kommen nur BPM-Kopplung,
  Fader und mehr Slots dazu.

## Datenmodell (Entwurf, neue Flyway-Migration)

Bibliothek, Szenen-Kopien und Extra-Tasten liegen in **einer** Tabelle. Der Besitzer ergibt sich aus `scene_id`
bzw. `key_slot`. So brauchen Ziele und Schritte nur eine Struktur.

```
composition
  id              INTEGER PK
  project_id      FK → project ON DELETE CASCADE
  scene_id        FK → scene ON DELETE CASCADE NULL   -- gesetzt = Kopie in dieser Szene
  key_slot        INTEGER NULL                  -- gesetzt = Extra-Taste 1..n im Betrieb
                                                -- beide NULL = Bibliothek
  source_id       FK → composition ON DELETE SET NULL NULL  -- Herkunft einer Kopie
  name            TEXT NOT NULL                 -- "Kirmes-Lauflicht"
  kind            TEXT NOT NULL                 -- 'chase' | 'color_cycle' | 'rainbow' | 'wave' | 'flicker' | 'steps'
  params          TEXT NOT NULL                 -- JSON, Schema je kind; (de)serialisiert nur im SQLite-Adapter
  group_id        FK → fixture_group ON DELETE SET NULL NULL  -- Ziel = Gruppe (dynamisch) …
  position        INTEGER NOT NULL DEFAULT 0    -- Reihenfolge in der Szene = Priorität
  level           REAL NOT NULL DEFAULT 1.0
  speed_factor    REAL NOT NULL DEFAULT 1.0
  restart         INTEGER NOT NULL DEFAULT 0    -- 1 = beim GO neu starten statt weiterlaufen
  key_mode        TEXT NULL                     -- 'toggle' | 'flash' (nur Extra-Tasten)
  CHECK (scene_id IS NULL OR key_slot IS NULL)
  UNIQUE(project_id, key_slot)

composition_target                              -- … oder feste Fixture-Liste (Vorlagen ohne Gruppe)
  composition_id  FK → composition ON DELETE CASCADE
  fixture_id      FK → fixture ON DELETE CASCADE
  position        INTEGER NOT NULL
  PRIMARY KEY(composition_id, fixture_id)

composition_step                                -- Schritte einer Schrittfolge
  composition_id  FK → composition ON DELETE CASCADE
  step_index      INTEGER NOT NULL
  hold_ms         INTEGER NOT NULL
  fade_ms         INTEGER NOT NULL
  PRIMARY KEY(composition_id, step_index)

composition_step_value                          -- Werte pro Schritt, normalisiert 0..1 (wie scene_value)
  composition_id  INTEGER NOT NULL
  step_index      INTEGER NOT NULL
  fixture_id      FK → fixture ON DELETE CASCADE
  attribute       TEXT NOT NULL
  value           REAL NOT NULL
  PRIMARY KEY(composition_id, step_index, fixture_id, attribute)
  FOREIGN KEY(composition_id, step_index) → composition_step ON DELETE CASCADE
```

## Entscheidungen (2026-10-02)

| Thema | Entscheidung |
|---|---|
| Szene ↔ Komposition | **Bibliothek + Kopie**: Einfügen kopiert, die Kopie ist in der Szene frei anpassbar |
| Ziel als Gruppe | **dynamisch**: Komposition folgt späteren Änderungen der Gruppe |
| Schrittfolgen (Chaser) | **von Anfang an** (K1/K2), nicht erst als Erweiterung |
| Folgeszene | **nahtlos weiterlaufen** bei gleicher Herkunft, pro Einsatz **„Neu starten“** umschaltbar |
| Einmalige Abläufe | ja, als Erweiterung in **K3** |
| Ohne Szene zuschaltbar | ja, als **Extra-Tasten** im Betriebsmodus (Toggle/Flash) |

## Umsetzung K1

- **Domain** `de.vinzens.lumen.domain.composition`: `Chase`, `ColorCycle`, `Rainbow`, `Wave`, `Flicker`,
  `StepSequence` (versiegelt unter `Pattern`), `Target` (`Group` | `Fixtures` | `Steps`), `CompositionSpec`,
  `CompositionMixer`. Farbe setzen Vorlagen als `red`/`green`/`blue`; Geräte ohne diese Kanäle bleiben unverändert.
- **Lauflicht:** „Übergang“ heißt `softness` (0 = hart, 1 = Geräte blenden um den Kopf herum ein und aus). Bei
  „zufällig“ gibt es nur harte Übergänge.
- **Flackern:** Wertrauschen mit festem Seed (gleiche Zeit → gleicher Wert). Charaktere: Kerze (ruhig), Feuer (zwei
  Frequenzen, tiefer), defekte Röhre (meist an, fällt gelegentlich aus).
- **Weiterlaufen:** Eine Kopie mit gleicher Herkunft und gleichem Typ übernimmt die Startzeit der laufenden. Sie wird
  dabei **nicht** aus- und eingeblendet: Ihr Beitrag geht während der Fade-In-Zeit linear von den alten zu den neuen
  Werten, ihr Level ebenso. Alle anderen Kompositionen der alten Szene laufen beim Ausblenden weiter.
- **Doppel-GO:** Abweichend vom Konzept wird bei einem GO während eines Fades die gesamte aktuelle Ausgabe
  eingefroren (auch die Kompositionen der Szene, die gerade einblendete). Weiterlaufende Kompositionen behalten
  trotzdem ihre Phase. So bleibt es bei höchstens zwei Ebenen und es springt nichts.
- **Datenbank:** zusätzlich die Spalte `target` (`group` | `fixtures` | `steps`). Wird eine Gruppe gelöscht, bleibt
  `target = group` mit `group_id = NULL` und die Komposition hat kein Ziel (dunkel).
- **REST:** `GET/POST /api/projects/{id}/compositions`, `GET/PUT/DELETE …/compositions/{cid}` (auch für Kopien),
  `POST …/compositions/{cid}/reload-from-library` · `/save-to-library`, `GET/POST …/scenes/{sid}/compositions`
  (`{libraryId}` fügt eine Kopie ein), `PUT …/scenes/{sid}/compositions/order` `{ids}`. Parameter sind pro Typ
  typisiert (Zeiten in ms, Farben `#rrggbb`, Aufzählungen klein geschrieben, unbekannte Felder → 400).
  Beispiel:
  ```json
  {"name": "Kirmes-Lauflicht", "kind": "chase",
   "params": {"stepMs": 400, "width": 1, "direction": "ping_pong", "softness": 0, "min": 0, "max": 1},
   "target": {"groupId": 3}, "level": 1, "speedFactor": 1, "restart": false}
  ```
- **Schrittfolgen mit Gruppen-Ziel** (offene Frage unten): vorerst Werte pro Fixture, wie aufgenommen.

## Noch offen (vor dem Start klären)

- Anzahl der Extra-Tasten und ob sie pro Szene ein-/ausgeblendet werden können (z.B. „Gewitter“ nur in Akt 2)
- Sollen Extra-Tasten bei „Ende“ / Blackout automatisch ausgehen?
- Schrittfolgen mit Gruppen-Ziel: Werte pro Fixture (wie aufgenommen) oder relativ zur Position in der Gruppe, damit
  auch Schrittfolgen dynamisch mit der Gruppe mitwachsen?
