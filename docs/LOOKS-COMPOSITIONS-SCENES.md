# Looks, Compositions, Scenes – drei Stufen

> **Status:** umgesetzt (Iteration S, siehe [ROADMAP.md](ROADMAP.md)). Ersetzt das Modell aus Iteration K
> (Szene = Basis + Kopien von Kompositionen, Szenen auf Extra-Tasten).
> Abweichungen vom Plan stehen unter [Umsetzung](#umsetzung).

## Warum

Heute gibt es zu viele Wege zum selben Ziel: feste Werte in der Szene, Kopien von Kompositionen in der Szene,
Bibliotheks-Kompositionen, Vorlagen mit Parametern, Szenen auf Extra-Tasten und Szenen nur für Tasten (Szenen in
Szenen). Das neue Modell hat genau drei Stufen, jede baut auf der darunter auf:

```
Scene 4 „Jahrmarkt"   (GO-Liste: Nummer, Regiehinweis, Fades, Delay, Auto-Follow, Textbuch)
├── Compositions  (jede läuft für sich, Reihenfolge = Priorität)
│   ├── „Grundlicht warm"    1 Schritt   → Look „Spielfläche warm"
│   ├── „Buden bunt"         3 Schritte  → Looks Rot / Gelb / Grün · Fade 2 s, Hold 4 s (Schritt 2: Hold 8 s)
│   └── „Kirmes-Lauflicht"   8 Schritte, erzeugt vom Generator „Lauflicht“ auf Gruppe „Rampe“
└── Tasten 1–8
    ├── 1 „Gewitter"   Flash
    └── 2 „Polizei"    Toggle, Fade 0,5 s
```

## Stufe 1 – Look

Ein **Look** ist eine einfache Einstellung: feste Werte pro Fixture und Attribut (0–1), dazu die Presets, aus denen
sie stammen (wie heute bei Szenen).

- Aufnehmen mit „Record look“ im Programmer; „Load“ holt ihn in den Programmer, „Update“ speichert zurück.
- Der **Verlauf** (Revisions, Rückgängig) hängt am Look statt an der Szene.
- **Gemeinsame Looks** stehen im Reiter „Looks“ und können in mehreren Compositions stecken. Eine Änderung wirkt
  überall, die Liste zeigt „used in 3 compositions“. Ein verwendeter Look lässt sich nicht löschen.
- **Eigene Looks einer Composition:** Looks, die per „Record step“ oder vom Generator entstehen, gehören zu ihrer
  Composition (`owner`). Sie erscheinen nur dort, nicht im Reiter „Looks“ – sonst würde ein Lauflicht über 8 Geräte
  die Liste mit 8 Looks füllen. „Make shared“ macht einen eigenen Look zu einem gemeinsamen. Wird die Composition
  gelöscht, verschwinden ihre eigenen Looks mit.

## Stufe 2 – Composition

Eine **Composition** ist eine Folge von 1–n Schritten. Jeder Schritt verweist auf einen Look.

- **Standardzeiten der Composition:** Fade und Hold, dazu der Ablauf **Loop**, **Ping-Pong** oder **Once** (läuft
  einmal durch und bleibt beim letzten Schritt stehen, z.B. ein Sonnenaufgang).
- **Pro Schritt überschreibbar:** Fade und/oder Hold. Leer = Standardzeit (der Editor zeigt sie als Platzhalter).
- Eine Composition mit einem Schritt ist ein statisches Licht.
- **Intensität absolut oder relativ:** absolut (Standard) setzt die Helligkeit, mit anderen Compositions gilt HTP.
  Relativ skaliert die Helligkeit dessen, was in der Szene darunter liegt (Kerzenflackern um ein Grundlicht herum).
- **Geteilt:** Eine Composition kann in beliebig vielen Szenen stecken. Eine Änderung wirkt in allen Szenen. Für
  eine Variante gibt es „Duplicate“. Es gibt keine Kopien und keine Unterscheidung Bibliothek/Szene mehr.
- **Pro Kopf** bei Multi-Pixel-Geräten bleibt erhalten (Werte wie `red@3` in den Looks).

### Parallele Schritte

Schritte können **gleichzeitig** laufen (Change Request vom 2026-10-06): Jeder Schritt hat ein „Start“ – nach dem
Schritt davor (Standard), „Begin“ oder zusammen mit einer anderen Zeile. Gleichzeitig startende Schritte bilden eine
**Zeile** und stehen im Editor nebeneinander. Entschieden: Eine Zeile hat **eine** Fade- und Hold-Zeit (die ihres
ersten Schritts), damit „Start after“ eindeutig bleibt; Loop, Ping-Pong und Once laufen über die Zeilen. Gespeichert
wird nur `composition_step.parallel` (startet mit dem Schritt davor, Flyway `V20`). Treffen sich zwei Looks einer
Zeile auf einem Fixture, gilt für die Intensität HTP, für alles andere der spätere Schritt.

Regeln innerhalb der Schritte (wie heute bei Schrittfolgen): Ein Fixture der Composition, das in einem Schritt nicht
vorkommt oder dort keine Intensität hat, liefert in diesem Schritt Intensität 0. Mit HTP bleibt so das Grundlicht
anderer Compositions stehen. Übrige Attribute behalten ihren letzten Wert.

### Generatoren statt Vorlagen

Lauflicht, Farbwechsel, Regenbogen, Welle und Flackern sind keine eigene Art von Composition mehr, sondern
**Generatoren**: Sie erzeugen die Schritte (und deren eigene Looks) einer ganz normalen Composition. Danach lässt sich
jeder Schritt wie jeder andere bearbeiten.

| Generator | Einstellungen | erzeugt |
|---|---|---|
| Lauflicht | Tempo (s pro Schritt), Breite, Richtung (vor / zurück / Ping-Pong / zufällig), weich ↔ hart, Min/Max | ein Schritt pro Gerät/Kopf (bzw. pro Position bei Breite > 1), Hold/Fade aus Tempo und „weich“ |
| Farbwechsel | 2–n Farben mit Hold und Fade, Spreizung (0 = alle gleich, 100 % = Farb-Lauflicht) | ein Schritt pro Farbe mit deren Hold und Fade; mit Spreizung pro Schritt versetzte Farben je Gerät |
| Regenbogen | Tempo (s pro Umlauf), Sättigung, Spreizung | 12 Farbtöne mit Fades, Spreizung über die Geräte |
| Welle | Form (Sinus, Dreieck, Sägezahn, Rechteck), Tempo, Min/Max, Spreizung | 16 Stützpunkte einer Periode mit Fades (Sinus wird zu einem geglätteten Vieleck) |
| Flackern | Charakter (Kerze, Feuer, defekte Röhre), Stärke, Tempo, fester Zufalls-Seed | ca. 40 zufällige Helligkeiten mit kurzen, unregelmäßigen Zeiten, Intensität relativ |

Ziel eines Generators ist eine Gruppe oder eine feste Liste von Fixtures; die Reihenfolge bestimmt Richtung und
Spreizung. Ein Generator setzt nur Attribute, die die Geräte haben – ein Farbwechsel auf einem reinen Dimmer wird im
Editor als Hinweis gemeldet, statt still nichts zu tun.

Folgen, die man kennen sollte:
- **Gruppen sind nicht mehr dynamisch:** Die Schritte enthalten feste Geräte. Ändert sich die Gruppe, muss man neu
  erzeugen. Damit das bequem bleibt, merkt sich die Composition die **Generator-Einstellungen** (`generator`, nur
  zum erneuten Erzeugen, ohne Einfluss auf das Abspielen): „Regenerate“ erzeugt die Schritte mit geänderten
  Einstellungen neu. Von Hand geänderte Schritte gehen dabei verloren, der Editor warnt.
- **Flackern wiederholt sich** nach seinen ~40 Schritten (wenige Sekunden). Durch unregelmäßige Zeiten fällt das
  kaum auf; bei Bedarf mehr Schritte erzeugen.
- Weiche Übergänge werden linear überblendet statt als Kurve gerechnet. Für Bühnenlicht reicht das.

## Stufe 3 – Scene

Eine **Szene** ist eine Menge von Compositions, dazu wie bisher Nummer, Name, Regiehinweis, Fade-In/-Out, Delay,
Fade-Kurve, Auto-Follow und Textbuch.

- **Pro Einsatz** einer Composition in der Szene: **Level** (0–100 %), **Tempo-Faktor** und **„Restart at GO“**.
  Die Reihenfolge ist die Priorität: Bei Farbe und übrigen Attributen gewinnt die spätere (LTP), Intensität mischt HTP.
- **Unabhängig:** Jede Composition hat ihre eigene Uhr ab dem GO der Szene.
- **Keine feste Basis mehr:** Das Grundlicht ist eine Composition mit einem Schritt.
- **Weiterlaufen:** Steckt dieselbe Composition auch in der nächsten Szene, läuft sie nahtlos weiter (ohne
  Phasensprung, Level gleitet über die Fade-Zeit), außer „Restart at GO“ ist gesetzt. Grundlage ist jetzt einfach
  die Composition selbst statt der Herkunft einer Kopie.
- **Schnellweg „Record scene“** im Programmer: legt Look, Composition (1 Schritt) und Szene in einem Klick an. Einfache
  Szenen sind damit so schnell gebaut wie heute. „Update“ in der Szene aktualisiert den Look ihrer ersten
  Composition, wenn diese nur einen Schritt hat.
- **Keine Szenen in Szenen:** Szenen auf Extra-Tasten gibt es nicht mehr.
- **Fader** (Saallicht, Arbeitslicht) spielen eine **Composition**. Szenen außerhalb der GO-Liste gibt es nicht mehr
  (Change Request 2026-10-06, Flyway `V21`): Ein Fader, der eine solche Szene spielte, spielt deren erste Composition,
  die Szene wurde entfernt.

### Licht-Marker und Looks (2026-10-06)

Eine Zeile `//## Name (Infotext)##` im Textbuch teilt die Szene in **Abschnitte**: Start (vor dem ersten Marker) und je
einer nach jedem Marker. Jeder Abschnitt hat eigene Compositions und Looks und steht für sich. Überschreitet die
Textposition im Betrieb einen Marker (auch rückwärts), blendet `Playback.goToSection` mit den Fade-Zeiten der Szene um;
was in beiden Abschnitten steckt, läuft weiter und bewegt sich nicht. Abschnitte sind über den Marker-Namen
gespeichert (`scene_composition.section`, `scene_look`), gleichnamige Marker teilen sich ihr Licht. Szenen können
außerdem gemeinsame **Looks** direkt spielen (`SceneLook`, mit Level), unter den Compositions.

### Tasten

- Jede Szene hat eigene **Tasten 1–8**. Jede Taste spielt eine Composition, **Toggle** oder **Flash**, mit eigener
  Fade-Zeit. Eine neue Szene übernimmt die Tasten der Szene davor.
- **GO:** Eine eingeschaltete Taste bleibt an, wenn dieselbe Composition in der neuen Szene auf einer Taste liegt
  (auch auf einer anderen Nummer); sonst blendet sie aus. Am Ende der Show gehen alle Tasten aus.
- Mischung wie heute: Tasten liegen über der Szene, Intensität HTP bzw. relativ, Farbe LTP mit Level × Fade; bei
  mehreren Tasten gewinnt die höhere Nummer. Blackout und Grand Master wirken auch auf sie. Tasten laufen auf der
  echten Uhr, Pause hält sie nicht an.

## Mischung und Zeit

Diese Regeln stammen aus Iteration K und gelten unverändert für Compositions in Szenen, auf Tasten und Fadern.

**Mischung pro Fixture und Attribut** (vor dem Layer-Merger und der Crossfade-Logik):
- **Intensität absolut:** HTP, `max(darunter, composition × level)` – ein Grundlicht bleibt stehen, ein Lauflicht
  „blitzt darüber“.
- **Intensität relativ** (Flackern): moduliert, was darunter liegt, `darunter × faktor(t)` – die Kerze flackert um
  die programmierte Helligkeit herum.
- **Farbe und übrige Attribute (LTP):** `darunter + (composition − darunter) × level`. Bei Level 50 % liegt ein
  Farbwechsel „halb“ über der Grundfarbe. Wirken mehrere Compositions auf dasselbe Attribut, gewinnt die spätere;
  der Szenen-Editor zeigt solche Überschneidungen an.

**Zeit, Überblendung und Playback:**
- Compositions der Szene laufen auf der **virtuellen Playback-Uhr**: **Pause** friert auch Lauflichter und
  Farbwechsel ein, „Finish fade“ betrifft nur den Szenenübergang, nicht den Ablauf.
- **Fade-In:** Die Composition läuft ab dem GO (bzw. ab dem Ende des Delays) und wird mit dem Szenenlevel eingeblendet.
- **Fade-Out:** Die Compositions der alten Szene **laufen beim Ausblenden weiter** und werden dunkler, statt
  einzufrieren. Bei einem Doppel-GO werden ältere ausblendende Compositions in den Snapshot übernommen, damit
  höchstens zwei Ebenen laufen.
- Für den Crossfade sind die Werte einer Composition „LTP-Ziele, die sich bewegen“: `Crossfade.mix` bekommt pro Tick
  die **aktuellen** Zielwerte. Die Regel „War das Gerät beim GO dunkel, springt die Farbe sofort“ bleibt.

```
            GO (Sz. 4)                                  GO (Sz. 5)
            │                                           │
Grundlicht  │▁▂▄▆██████████████████████████████████████│█▇▅▃▁
Lauflicht   │▁▂▄▆█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█▁█│▇▁▅▁▃▁▁       läuft beim Ausblenden weiter
Farbwechsel │▁▂▄▆ rot → gelb → grün → rot → gelb → grün│→ rot → …    in Sz. 5 auch enthalten: läuft nahtlos weiter
```

## Was gleich bleibt

Programmer, Presets (verweisen jetzt aus Looks statt aus Szenen), Fader (spielen heute Compositions), Party-Modus (heute eine Matrix, siehe [PARTY-MODE.md](PARTY-MODE.md)), Fade-Logik
und Crossfade, „Show on stage“ (für Look, Composition und Szene), Probe-Überblendung, Cue-Liste zum Drucken.

## Datenmodell (Flyway V19)

```
look
  id, project_id, name
  owner_composition_id  FK → composition ON DELETE CASCADE NULL   -- gesetzt = eigener Look einer Composition
look_value(look_id, fixture_id, attribute, value)                  -- ersetzt scene_value
look_preset(look_id, fixture_id, attribute_group, preset_id)       -- ersetzt scene_preset
look_revision, look_revision_value, look_revision_preset           -- ersetzt scene_revision*

composition
  id, project_id, name
  fade_ms, hold_ms                         -- Standardzeiten
  play_mode   'loop' | 'ping_pong' | 'once'
  intensity   'absolute' | 'relative'
  per_head
  generator   TEXT NULL                    -- JSON: Generator-Einstellungen, nur für „Regenerate“
composition_step(composition_id, step_index, look_id, fade_ms NULL, hold_ms NULL)

scene                                       -- wie bisher, key_only → in_cue_list (umgekehrt)
scene_composition(scene_id, composition_id, position, level, speed_factor, restart)
scene_key(scene_id, slot 1–8, composition_id, key_mode, fade_ms)

entfällt: scene_value, scene_preset, scene_revision*, composition.scene_id/key_slot/source_id/kind/params/target/
          group_id/level/speed_factor/restart/once/key_mode/key_fade_ms, composition_target,
          composition_step_value, scene_extra_key, extra_key_scene
```

## Bestehende Projekte umwandeln

Einmalig beim ersten Start mit der neuen Version, vorher legt Lumen automatisch ein Backup an. Derselbe Umwandler
liest auch alte Export-Dateien.

1. **Feste Werte einer Szene** → Composition „4 Jahrmarkt“ (1 Schritt) mit eigenem Look an erster Stelle der
   Szene. Presets und Verlauf kommen mit.
2. **Kopien von Kompositionen in Szenen** → Kopien mit gleicher Herkunft und gleichem Inhalt werden zu **einer**
   Composition (so läuft sie weiter nahtlos von Szene zu Szene). Abweichende Kopien werden eigene Compositions
   („Kirmes-Lauflicht (4)“). Level, Tempo und „Neu starten“ wandern in `scene_composition`.
3. **Schrittfolgen** → ihre Schritte werden eigene Looks der Composition.
4. **Vorlagen** (Lauflicht usw.) → mit dem passenden Generator in Schritte umgewandelt, die Einstellungen bleiben für
   „Regenerate“ erhalten. Bei Gruppen-Zielen gelten die Mitglieder zum Zeitpunkt der Umwandlung.
5. **Bibliotheks-Kompositionen** → Compositions (in keiner Szene).
6. **Extra-Tasten mit Komposition** → Taste in jeder Szene, in der sie freigeschaltet war.
7. **Szenen auf Tasten** und Szenen nur für Tasten: Besteht die Szene aus einer Composition, kommt diese auf die
   Taste. Besteht sie aus mehreren, verteilt Lumen sie auf freie Tasten derselben Szenen; reicht der Platz nicht,
   bleibt die Taste leer. Was umgebaut oder weggelassen wurde, steht als Warnung im **Log** des Starts.
8. **Szenen nur für Tasten**, die ein Fader spielt, bleiben als Szenen „not in cue list“. Die übrigen werden nach der
   Umwandlung nicht mehr gebraucht und ebenfalls als „not in cue list“ behalten – löschen kann man sie von Hand.

## UI

- **Reiter (Setup):** Programmer · **Looks** (neu) · Compositions · Scenes
- **Programmer:** „Record look“, „Record scene“ (Schnellweg), „Record step“ (in die offene Composition)
- **Looks:** Liste mit Farbvorschau und „used in …“; Load, Update, Umbenennen, Duplizieren, Löschen, Verlauf
- **Compositions:** Liste mit animierter Vorschau. Editor: Standardzeiten und Ablauf; Schrittliste, pro Schritt Look
  wählen (gemeinsam oder eigen), „Record step“, Fade/Hold mit Platzhalter; „Generate…“ öffnet den Generator
  „Regenerate“; Live-Vorschau und „Show on stage“; „used in scenes …“
- **Scenes:** Szenen-Editor mit Compositions (vorhandene hinzufügen, neu aus dem Programmer, neu per Generator),
  Reihenfolge per Drag & Drop, Level, Tempo, Restart; Abschnitt „Keys 1–8“; Zeiten und Textbuch wie bisher
- **Show-Modus:** unverändert; die Tastenleiste zeigt die Tasten der aktuellen Szene mit Namen

## Umsetzung (Iteration S)

Auf einem eigenen Branch, jeder Schritt mit grünem `./build.sh`.

1. **S1 – Domain & Engine:** Look, Composition mit Look-Schritten und Standardzeiten, Szene mit Compositions und
   Tasten; Generatoren als reine Funktionen (Tabellen-Tests); `Playback` und Tasten auf dem neuen Modell (mit `Clock`
   getestet). Die bestehende Engine bleibt: Szenen haben keine Basis mehr, alles läuft über Compositions.
2. **S2 – Datenbank & Umwandlung:** Flyway `V19` mit Java-Umwandler, Tests gegen eine Datenbank im alten Format;
   Export Version 2, Import alter Dateien über denselben Umwandler.
3. **S3 – REST & WebSocket** für Looks, Compositions, Szenen und Tasten; alte Endpunkte entfallen.
4. **S4 – UI:** Looks, Compositions-Editor mit Generator, Szenen-Editor mit Tasten, Show-Modus; alte Wege entfernen.
5. **S5 – Doku:** PLAYBACK.md, UI.md, PROJECT-DATA.md nachziehen; das alte Konzept (COMPOSITIONS.md) ist
   inzwischen in diesem Dokument aufgegangen.

## Entscheidungen (2026-10-06)

| Thema | Entscheidung |
|---|---|
| Vorlagen (Lauflicht, Regenbogen, …) | **Generatoren**, die Schritte einer normalen Composition erzeugen; keine eigene Art |
| Composition in mehreren Szenen | **geteilt**: eine Änderung wirkt in allen Szenen; pro Einsatz nur Level, Tempo, Restart |
| Taste beim GO | bleibt an, wenn dieselbe Composition in der neuen Szene auf einer Taste liegt |
| Fader | spielen eine **Composition** (seit 2026-10-06; vorher eine Szene „not in cue list“) |

## Umsetzung

- **Domain:** `NamedLook` (Look mit Name und optionalem Besitzer) und `LookRevision` im Paket `look`; `Composition`,
  `CompositionStep`, `CompositionSpec` (Standardzeiten, `PlayMode`, `IntensityMode`, `GeneratorSettings`); `Scene`
  mit `SceneComposition` und `SceneKey`. Die Engine blieb: Eine Composition wird zur `StepSequence` ihrer Looks
  (`Composition.sequence`), `Playback` spielt Szenen ohne eigene Werte, nur mit Compositions. Weiterlaufen bei
  gleicher Composition-ID. `ExtraKeys` nimmt pro Szene die Tasten und führt eine eingeschaltete Taste weiter, wenn
  ihre Composition in der neuen Szene auf irgendeiner Taste liegt; sonst blendet sie mit ihrer Fade-Zeit aus.
- **Generatoren** (`Generator`) tasten die bisherigen Effekte ab: Ein hartes Lauflicht spielt exakt wie vorher, weiche
  Übergänge werden linear überblendet. Sehr schnelle Effekte bekommen weniger Schritte, damit kein Schritt kürzer als
  20 ms ist.
- **Eigene Looks:** Auch der Schnellweg „Record scene“ und die Umwandlung legen den Look als eigenen Look der neuen
  Composition an (nicht als gemeinsamen) – sonst würde der Reiter „Looks“ mit einem Look pro Szene volllaufen.
  „◆ Make shared“ macht ihn bei Bedarf gemeinsam.
- **Umwandlung:** Flyway `V19` benennt die alten Tabellen in `legacy_*` um, `LegacyMigrationService` wandelt beim
  Start jedes Projekt einmal um (vorher ein Backup `…-before-looks.db`) und merkt es sich in `legacy_conversion`.
  Alte Export-Dateien (Version 1) gehen durch denselben `LegacyConverter`. Farbeffekte bekommen ihre eigene Intensität
  nur noch, wenn keine der Szenen, in denen sie laufen, den Geräten eine Intensität gibt (früher galt sie nur ohne
  Basis-Intensität). Level und Tempo einer Taste gibt es nicht mehr; abweichende Werte stehen im Log.
  Geprüft mit einer echten Datenbank (Schema V12): Szenen, Kompositionen, Tasten und die Resume-Szene kamen richtig an.
- **REST:** `/looks` (CRUD, `record`, `revisions`, `restore`, `load`, `duplicate`), `/compositions` (CRUD,
  `duplicate`, `record-step`, `generate`), `/scenes/{id}/compositions` und `/scenes/{id}/keys` (PUT, ganze Liste).
  Weggefallen: `/extra-keys`, `/scenes/{id}/revisions`, `reload-from-library`, `save-to-library`. WebSocket-Thema
  `looks`. Export Version 2.
- **UI:** neuer Reiter „Looks“, Composition-Editor mit Schritten aus Looks und Generator (`EffectForm`), Szenen-Editor
  mit Compositions und Tasten. Einen Vorschau-Schritt vor dem Erzeugen gibt es nicht: Erzeugen ersetzt die Schritte
  sofort, die Live-Vorschau zeigt das Ergebnis, „Generate steps again“ ändert es.
