# Moves – Figuren für Pan/Tilt-Geräte

> **Status:** umgesetzt am 2026-10-08 (Stufen 1–5), siehe [Umsetzung](#umsetzung-2026-10-08). Beschlossen am 2026-10-08.

## Worum es geht

Bisher gibt es für Moving Heads und Washlights nur **feste Positionen** (XY-Pad im Programmer, in Looks gespeichert).
Moves bringen **Bewegung**: Ein Kopf fährt eine gezeichnete Figur ab, endlos oder einmal als „Reise“ von A nach B. Er
kann allein fahren oder zusammen mit anderen Köpfen, die versetzt einsteigen.

Dabei gilt eine klare Trennung: **Looks sagen, wie das Licht aussieht** (Helligkeit, Farbe, Gobo, Zoom, …), **Moves
sagen, wohin es zeigt** – auch wenn ein Kopf nur steht. Pan und Tilt kommen in der Wiedergabe ausschließlich aus
Moves.

Vorbild ist der Figureneditor mit Bewegungsszene in DMXControl 2: Die Figuren werden ohne Geräte gezeichnet, die
Zuordnung zu Geräten mit Größe und Lage kommt erst danach.

## Entscheidungen (2026-10-08)

| Thema | Entscheidung |
|---|---|
| Koordinaten | **DMX-Werte**, nicht Grad: X = Pan, Y = Tilt, jeweils 0–255 (intern fein aufgelöst, 16 Bit) |
| Lage | **Absolut**: Die Figur liegt dort, wo sie gezeichnet und verschoben ist. Ein Look darunter verschiebt sie nicht |
| Versatz zwischen Geräten | **In Schritten**: Gerät 1 steigt bei Punkt 1 ein, Gerät 2 bei Punkt 1 + Versatz, … |
| Einordnung | Figur und Move sind eigene Objekte wie Looks; ein Move spielt in Szenen, Lichtmarkern, Party und Tasten |
| Once mit Versatz | **beides wählbar**: nacheinander („Schlange“) oder gleichzeitig ab versetzten Punkten |
| Position nur über Moves | **Streng**: Looks, Compositions und Presets enthalten kein Pan/Tilt mehr. Eine Umwandlung bestehender Daten ist nicht nötig, es gibt noch keine Bewegungen |
| Feste Position | ein Move, dessen Figur nur **einen Punkt** hat |
| Lage pro Gerät | jedes Gerät eines Moves hat zusätzlich eine **eigene Verschiebung**, damit verschieden hängende Köpfe auf dieselbe Stelle zeigen |
| Grenzen pro Gerät | gelten für die Wiedergabe, **nicht für den Programmer** |
| Bewegen im Dunkeln | **pro Szene** einstellbar: ausdimmen, fahren, eindimmen, statt leuchtend über die Bühne zu fahren |
| Drehung | im DMX-Raster; ein gedrehter Kreis wird auf der Bühne zur Ellipse, wenn Pan mehr Grad abdeckt als Tilt |

## Begriffe

```
Figur  „Acht“        nur die Form: Punkte in Reihenfolge, offen oder geschlossen – ohne Geräte
  └─ Move „Acht über der Bühne“  Figur + Geräte + Größe, Verschiebung, Drehung, Tempo, Wiederholung, Versatz
       └─ Szene 4 / Lichtmarker / Party-Feld / Taste   spielt den Move
```

- Eine **Figur** gehört zum Projekt und kann von mehreren Moves benutzt werden. Wird sie geändert, ändern sich alle
  Moves damit (wie bei Presets).
- Ein **Move** ist das, was man in einer Szene auswählt, so wie einen Look oder eine Composition.
- Ein einfacher Spot auf den Sänger besteht also aus zwei Teilen: Look „Weiß 80 %“ + Move „Sänger Mitte“. Dafür lässt
  sich jede Farbe mit jeder Position kombinieren, und eine Position wird an einer Stelle gepflegt.

## Stufe 1 – Figur

| Feld | Bedeutung |
|---|---|
| Name | z.B. „Acht“, „Weg Bühne links → Mitte“ |
| Punkte | geordnete Liste von (X, Y) im Bereich 0–255, mindestens 1 – **ein Punkt = feste Position** |
| Geschlossen | ja: nach dem letzten Punkt geht es zurück zum ersten (Kreise, Achten); nein: offener Weg (Reisen) |

Die Punkte werden **mit geraden Linien** verbunden. Weiche Kurven kommen später (siehe [Später](#später)).

### Figureneditor

```
┌ Figure: [ Acht           ▾] [+ New] [Duplicate] [Delete] ───────────────────────────────┐
│ Template: [Circle ▾] Points: [8] [Apply]        ☑ Closed   ☑ Show numbers   [Clear]     │
│  255 ┼───────────────────────────────────────────────┐                                  │
│      │            ②────────③                         │   Points                         │
│      │          ╱            ╲                       │   1  X  64   Y 128   ✕            │
│ Tilt │       ①                 ④                     │   2  X  96   Y 192   ✕            │
│      │        ┆                 │                    │   3  …                           │
│      │        ⑥────────────────⑤   (┆ = closing line) │                                  │
│    0 ┼──────────────── Pan ─────────────────────────┘ 255                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Klick** in die Fläche hängt einen Punkt hinten an. Aufeinanderfolgende Punkte werden verbunden, die Linie vom
  letzten zum ersten Punkt (bei „Closed“) ist gestrichelt. Pfeile auf den Linien zeigen die Richtung.
- **Ziehen** verschiebt einen Punkt, **Klick auf eine Linie** fügt dort einen Punkt ein, **Alt+Klick** oder ✕ in der
  Liste löscht ihn. Die Liste rechts erlaubt genaue Werte und das Umsortieren.
- **Nummern** an den Punkten zeigen die Reihenfolge. Sie sind wichtig für den Versatz der Geräte.
- **Vorlagen** mit wählbarer Punktzahl: Kreis, Acht, Linie waagrecht, Linie senkrecht, Dreieck, Rechteck, Zufall.
  „Apply“ ersetzt die Punkte; danach lässt sich alles von Hand ändern.
- Raster mit Hilfslinien bei 64/128/192; Snap auf ganze Werte, mit Shift auf das 16er-Raster.

## Stufe 2 – Move

| Feld | Bedeutung | Standard |
|---|---|---|
| Name | z.B. „Acht über der Bühne“ | |
| Figur | Auswahl aus der Bibliothek oder „New figure…“ (öffnet den Figureneditor) | |
| Geräte | geordnete Liste von Fixtures mit Pan **und** Tilt; die Reihenfolge bestimmt den Versatz | |
| Größe X / Y | Skalierung der Figur um ihre Mitte, getrennt für Pan und Tilt, in % | 100 % |
| Verschiebung X / Y | Verschiebung der ganzen Figur in DMX-Werten (−255 … +255) | 0 |
| Drehung | Drehung um die Mitte der Figur in Grad (Ansicht im DMX-Raster) | 0° |
| Dauer | Zeit für einen Durchlauf der Figur in Sekunden (bei einem Punkt ausgeblendet, wie Wiederholung und Versatz) | 8 s |
| Wiederholung | **Loop** (endlos), **Once** (einmal, bleibt am Ende stehen), **Ping-Pong** (hin und zurück) | Loop |
| Versatz | Schritte, um die jedes weitere Gerät später einsteigt (0 = alle synchron) | 0 |
| Einstieg bei Once | **Nacheinander** (alle fahren dieselbe Reise, Gerät *i* startet später) oder **Gleichzeitig** (alle starten sofort, Gerät *i* ab Punkt `1 + i × Versatz`) | Nacheinander |

Pro Gerät, als Ausnahme in der Geräteliste:

| Feld | Bedeutung |
|---|---|
| Verschiebung X / Y | eigene Korrektur in DMX-Werten, zusätzlich zur Verschiebung für alle |
| Spiegeln X / Y | Pan bzw. Tilt um die Mitte der Figur gespiegelt, z.B. für symmetrisch laufende Köpfe links und rechts |
| Rückwärts | fährt die Figur in umgekehrter Richtung |

Größe, Verschiebung und Drehung gelten für **alle** Geräte des Moves (Schritt 4 der Idee). Die **Verschiebung pro
Gerät** gleicht aus, dass die Köpfe an verschiedenen Stellen hängen: Ohne sie zeigen vier Köpfe auf demselben
DMX-Punkt auf vier verschiedene Stellen der Bühne, und jeder malt die Acht woanders hin. Im Editor wird sie pro Gerät
mit einem kleinen XY-Pad nachgestellt, während der Kopf auf der Bühne die Figur zeigt.

```
Move „Sänger Mitte“   Figur: 1 Punkt (128, 100)
  MH 1  Shift X −18  Y +6
  MH 2  Shift X  −5  Y +4
  MH 3  Shift X  +7  Y +4
```

### Move-Editor

```
┌ Move: [ Acht über der Bühne ▾] [+ New move] ──────────────────────────────────────────────┐
│ Figure [ Acht ▾] [Edit figure]          Fixtures: [MH 1] [MH 2] [MH 3] [MH 4]  [+ Add]     │
│ ┌──────────────────────────────┐   Size    X [ 80 %]  Y [ 50 %]                            │
│ │   ·  · (Figur grau)          │   Shift   X [ +20 ]  Y [ −10 ]                            │
│ │  ●1 ───── ●2   (Figur nach   │   Rotate  [ 0° ]                                          │
│ │   ╲        ╲   Größe/Shift)  │   Time    [ 8.0 s ] per round   Repeat (Loop|Once|Ping-pong)│
│ │    ●3 ──── ●4  ● = Gerät,    │   Offset  [ 2 ] steps per fixture                         │
│ │                animiert)     │   ─────────────────────────────────────────                │
│ └──────────────────────────────┘   MH 1  ☐ Mirror X ☐ Mirror Y ☐ Reverse   starts at ①     │
│ [▶ Preview] [Show on stage]        MH 2  ☑ Mirror X ☐ Mirror Y ☐ Reverse   starts at ③     │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

- In der Fläche erscheint die Grundfigur blass und die Figur nach Größe, Verschiebung und Drehung kräftig. Darauf
  fährt pro Gerät ein Punkt mit Namen (Vorschau im Browser, ohne Ausgabe).
- **Größe und Verschiebung** lassen sich auch direkt in der Fläche einstellen: Ziehen der Figur verschiebt sie,
  die Eckgriffe ändern die Größe.
- „Show on stage“ spielt den Move wie heute bei Compositions auf der Bühne (bewegt echte Köpfe), mit dem bekannten
  Hinweis in der Kopfleiste und Stop.
- Geräte ohne Pan/Tilt werden nicht angeboten.
- Die Spalte „starts at“ zeigt, bei welchem Punkt jedes Gerät einsteigt.

## Stufe 3 – Nutzung

- **Szenen und Lichtmarker:** In jedem Teil der Szene gibt es neben Looks und Compositions eine Auswahl **Moves**.
  Wie bei Compositions läuft ein Move, der in beiden Szenen oder Teilen steht, **ohne Unterbrechung weiter**.
- **Party:** Ein Feld der Matrix kann einen Move ein- und ausschalten (Toggle oder Flash).
- **Tasten** einer Szene können einen Move spielen.
- **Programmer:** Pan/Tilt stellt man weiter mit dem XY-Pad ein, der Programmer überschreibt wie heute alles, auch
  einen laufenden Move. Beim Speichern trennt er:
  - **Record look** speichert alles außer Pan/Tilt.
  - **Record position** legt einen Move mit einem Punkt an (oder aktualisiert ihn): Der Punkt ist die Mitte der
    Positionen der ausgewählten Geräte, jedes Gerät bekommt seine Abweichung davon als eigene Verschiebung.
  - **Record look + position** macht beides in einem Schritt und kann beides gleich in die Szene setzen.
- **Ohne Move** behält ein Kopf seine letzte Position (bestehende Regel: was die neue Szene nicht setzt, bleibt
  stehen). Ein Wechsel nur der Farbe lässt die Köpfe also nicht zurück in die Mitte fahren.

## Wie gerechnet wird

Alles als reine Funktion der Zeit, wie die übrigen Compositions. Mit `t` = Zeit seit dem Start × Tempo-Faktor:

```
1. Weg:       Figur → Polygonzug (geschlossen: zurück zum ersten Punkt); Länge L, Punkt k bei Wegstrecke s_k
2. Fortschritt eines Geräts i:
     Loop:       p = frac( t / Dauer + s_(i × Versatz mod n) / L )
     Ping-Pong:  wie Loop, aber jede zweite Runde rückwärts
     Once:       wie Loop ohne frac, bei 1 stehen bleiben; Versatz je nach „Einstieg“ (siehe unten)
     Rückwärts:  p → 1 − p
3. Punkt auf dem Weg bei Strecke p × L          → (x, y), gleichmäßige Geschwindigkeit entlang des Wegs
4. Spiegeln:     x → 2·cx − x  bzw.  y → 2·cy − y        (c = Mitte der Figur, Mitte der Bounding Box)
5. Lage:         (x, y) = c + Shift + Shift_Gerät + Drehung( Größe ⊙ ((x, y) − c) )
6. Begrenzen:    auf 0–255 und auf die Grenzen des Geräts (siehe unten)
7. Ausgabe:      Pan = x / 255, Tilt = y / 255  (normiert, 16 Bit bei Geräten mit Fine-Kanal)
```

- **Gleichmäßige Geschwindigkeit:** Die Zeit verteilt sich nach der Länge der Abschnitte, nicht gleich pro Punkt.
  Sonst würde der Kopf über lange Strecken rasen und über kurze schleichen.
- **Versatz in Schritten:** Bei Loop und Ping-Pong beginnt Gerät *i* an Punkt `1 + i × Versatz` (zyklisch). Bei
  **Once** (Reise) fahren alle denselben Weg von Anfang an, Gerät *i* startet um `i × Versatz` Schritte später, also
  um die Zeit, die der Weg bis Punkt `1 + i × Versatz` braucht. Bis dahin steht es auf dem Startpunkt. Das ergibt die
  „Schlange“, die nacheinander dieselbe Reise macht. Mit Einstieg **Gleichzeitig** starten alle sofort, Gerät *i*
  ab Punkt `1 + i × Versatz`, und jedes bleibt am Ende des Wegs stehen; bei offenen Figuren ist der Weg für spätere
  Geräte also kürzer, bei geschlossenen Figuren fährt jedes genau eine Runde bis zu seinem Startpunkt.
- **Mischen:** Moves setzen nur Pan und Tilt, Looks und Compositions alles andere. Stehen zwei Moves mit demselben
  Gerät in einer Szene, gewinnt der spätere in der Liste (LTP). Ein Move hat keinen Level.
- **Übergänge:** Beim GO fährt ein Kopf mit dem Fade-In von seiner Position zum Weg und folgt ihm dann. War er
  beim GO dunkel, springt er sofort dorthin (bestehende Regel für stufenlose Attribute). Läuft derselbe Move in der
  neuen Szene weiter, gibt es keinen Übergang.
- **Fine-Kanäle:** Bewegungen ruckeln mit 8 Bit sichtbar. Der Renderer gibt Pan/Tilt schon heute mit 16 Bit aus,
  wenn das Profil einen Fine-Kanal hat.

## Bewegen im Dunkeln (pro Szene)

Fährt ein Kopf beim GO leuchtend von einer Position zur nächsten, sieht das Publikum den Lichtfleck über die Bühne
wandern. Oft soll der Kopf stattdessen **ausdimmen, im Dunkeln fahren und an der neuen Stelle wieder eindimmen**
(an Lichtpulten „Move in Black“).

Im Szenen-Editor gibt es dafür bei den Fade-Zeiten:

| Feld | Bedeutung | Standard |
|---|---|---|
| **Move in dark** | an/aus für das GO in diese Szene | aus |
| **Dim out** | Zeit zum Ausdimmen der Köpfe, die fahren | 0,5 s (Projekt) |
| **Travel** | Zeit, die die Köpfe im Dunkeln zum Fahren bekommen; die tatsächliche Motorgeschwindigkeit kennt Lumen nicht | 1,5 s (Projekt) |

Ablauf für einen Kopf, der beim GO leuchtet und in der neuen Szene **woanders hinzeigt** (mehr als 2 DMX-Werte
Unterschied bei Pan oder Tilt):

```
GO
│ Dim out 0,5 s │ Travel 1,5 s           │ Fade-In der Szene 3 s
│█▇▅▃▁          │                        │▁▃▅▇█
│ Position alt  │→ springt auf die neue Position, Farbe/Gobo springen mit
```

- Pan/Tilt **springen** zu Beginn von „Travel“ auf das Ziel. Der Kopf fährt dann so schnell er kann, also im
  Dunkeln. Farbe, Gobo und Zoom springen gleich mit, auch ihr Wechsel bleibt unsichtbar.
- Danach dimmt der Kopf mit dem **Fade-In der Szene** auf seine neue Helligkeit.
- Ein laufender Move (Figur) startet seine Zeit erst, wenn der Kopf eindimmt, damit er nicht schon unterwegs ist.
- **War der Kopf beim GO dunkel**, fällt „Dim out“ weg. Er springt sofort auf die neue Position und wartet „Travel“
  ab, bevor er eindimmt. So taucht er nicht auf, bevor er angekommen ist.
- **Köpfe, die an derselben Stelle bleiben**, und alle Geräte ohne Pan/Tilt blenden ganz normal mit den Zeiten der
  Szene über, ohne Dunkelphase.
- Läuft **derselbe Move** in der neuen Szene weiter, ist das keine Positionsänderung: Es gibt keine Dunkelphase.
- Der **Delay** der Szene gilt wie bisher: Die Köpfe dimmen frühestens nach dem Delay ein, also nach
  `max(Delay, Dim out + Travel)`.
- Die Stay-Zeit (Auto-Follow) beginnt erst, wenn alle Köpfe eingedimmt sind. Die Fortschrittsanzeige in der Show
  rechnet die Dunkelphase mit.
- **Lichtmarker** einer Szene mit „Move in dark“ verhalten sich ebenso (mit Dim out und Travel, ohne Delay, wie
  bei Lichtmarkern üblich).
- Party und Tasten: vorerst ohne Dunkelphase (siehe [Später](#später)).

## Grenzen pro Gerät

Im **Patch** bekommt jedes Gerät mit Pan/Tilt optional einen erlaubten Bereich: Pan min/max und Tilt min/max in
DMX-Werten. Die Grenzen gelten für die **Wiedergabe**, also für alle Moves in Szenen, Party und Tasten. So leuchtet
kein Kopf ins Publikum oder in den Schnürboden, egal aus welcher Szene der Wert kommt.

Der **Programmer** ist ausgenommen: Beim Einrichten soll man jeden Punkt anfahren können. Das XY-Pad zeigt den
erlaubten Bereich als Rahmen an. Wird eine Position außerhalb als Move gespeichert, begrenzt sie erst die Wiedergabe.

## Was sich an Bestehendem ändert

| Bereich | Heute | Mit Moves |
|---|---|---|
| Looks | können Pan/Tilt enthalten | kein Pan/Tilt mehr; der Look-Editor zeigt die Gruppe „Position“ nicht, die API lehnt sie ab |
| Compositions | Schritte sind Looks | dadurch ebenfalls ohne Pan/Tilt; Generatoren bieten Pan/Tilt nicht an |
| Presets | Farbe, Position, Beam | nur noch Farbe und Beam; eine gespeicherte Position ist ein Move mit einem Punkt |
| Programmer | „Record“ speichert alles in einen Look | Record look / Record position / Record look + position (siehe oben) |
| Party | Felder schalten Szenen und Compositions | zusätzlich Moves |

Eine Umwandlung bestehender Daten ist nicht nötig: Es gibt noch keine Bewegungen und keine gespeicherten Positionen.
Sollte eine ältere Projektdatei doch Pan/Tilt in Looks oder Positions-Presets enthalten, verwirft der Import sie mit
einer Warnung.

Im Move-Editor ist der erlaubte Bereich eines ausgewählten Geräts als Rahmen in der Fläche sichtbar. Punkte außerhalb
werden rot markiert.

## Datenmodell (neue Flyway-Migration)

```
figure        (id, project_id, name, closed)
figure_point  (figure_id, position, x, y)                    -- x, y: 0.0–1.0 (= 0–255 DMX, fein aufgelöst)
move          (id, project_id, name, figure_id, size_x, size_y, shift_x, shift_y, rotation_deg,
               duration_ms, repeat_mode, step_offset, once_start)
              -- once_start: sequential | together; figure_id: ON DELETE RESTRICT
move_fixture  (move_id, position, fixture_id, shift_x, shift_y, mirror_x, mirror_y, reverse)
scene_move    (scene_id, section, position, move_id)         -- section wie bei scene_composition
scene         + move_in_dark, dim_out_ms, travel_ms           -- NULL bei den Zeiten = Projektzeit
project       + dim_out_ms, travel_ms                         -- Standard 500 / 1500
fixture       + pan_min, pan_max, tilt_min, tilt_max  (NULL = ohne Grenze)
party_cell    + move_id
scene_key     + move_id                                       -- eine Taste spielt eine Composition oder einen Move
```

- Löschen einer Figur, die ein Move benutzt, und eines Moves, der in Szenen, Party oder Tasten steht, wird abgelehnt
  („where used“ wie bei Compositions).
- Wird ein Fixture aus dem Patch gelöscht, fällt es aus den Moves heraus.
- Projektdateien: neue Version 4 mit Figuren und Moves; Versionen 1–3 bleiben importierbar.

## Umsetzung (Skizze)

- **Domain** (`de.vinzens.lumen.domain.move`): `Figure` (Validierung: ≥ 1 Punkt, Werte 0–1), `FigurePath`
  (Polygonzug, Länge, Punkt bei Strecke), `MoveSpec`, `MovePattern implements Pattern` (das sealed Interface
  bekommt einen weiteren Typ). Ein Move spielt als `ActiveComposition` (eigene Id-Reihe wie `ofLook`, `sourceId` für
  das Weiterlaufen über Szenen). Grenzen auf der Wiedergabe-Ebene, bevor der Programmer
  darübergelegt wird (`LayerMerger`), getestet ohne Spring.
  Move in dark: `Playback.startTransition` bestimmt beim GO die Köpfe, die fahren; `Crossfade` bekommt für sie eine
  Dunkelphase (Dim out → Sprung → Travel → Fade-In), `SceneTiming` die neuen Felder.
- **Application:** `ManageFiguresUseCase`, `ManageMovesUseCase`; Szenen, Party und Tasten lernen Moves. Looks und
  Presets lehnen Pan/Tilt ab (Validierung in `LookSpec` und `PresetSpec`), „Record position“ im Programmer.
- **Adapter:** SQLite-Repositories, REST unter `/api/projects/{id}/figures` und `/moves`, DTOs im Web-Adapter,
  Import/Export.
- **Frontend:** Setup-Reiter **Moves** (Liste links, Figuren- und Move-Editor rechts), Auswahl in
  `SceneCompositions`, `PartyGrid` und den Tasten. Geometrie (Weg, Vorlagen, Lage, Vorschau) als reine Funktionen
  in `src/lib/moves.ts` mit Vitest, dieselben Regeln wie im Backend.
- **Tests:** gleichmäßige Geschwindigkeit, geschlossene/offene Figur, Versatz in Loop und Once (beide Einstiege), Ping-Pong,
  Spiegeln, Drehung, Verschiebung pro Gerät, Figur mit einem Punkt, Begrenzen, zwei Moves auf einem Gerät (LTP),
  Weiterlaufen über Szenen, Kopf ohne Move bleibt stehen, Grenzen in der Wiedergabe, Programmer ohne Grenzen,
  Looks/Presets ohne Pan/Tilt, „Record position“ (Mitte + Abweichung pro Gerät), Move in dark (leuchtend → fahren,
  dunkel → fahren, bleibt stehen, derselbe Move läuft weiter, Delay, Stay-Zeit, Lichtmarker).

Vorgeschlagene Reihenfolge, jeweils mit Commit:

1. Figuren: Domain, Speicherung, REST, Figureneditor mit Vorlagen
2. Moves: Domain mit Rechnung, Speicherung, REST, Move-Editor mit Vorschau und „Show on stage“
3. Nutzung in Szenen und Lichtmarkern, Party und Tasten; Programmer „Record position“; Looks und Presets ohne
   Pan/Tilt; Projektdateien Version 4
4. Move in dark pro Szene (Crossfade mit Dunkelphase, Zeiten im Szenen-Editor und in der Show-Anzeige)
5. Grenzen pro Gerät im Patch und in der Wiedergabe (nicht im Programmer), Rahmen im XY-Pad

## Umsetzung (2026-10-08)

Wie oben beschrieben, mit diesen Details:

- **Domain:** `domain.move` (`Figure`, `FigurePath`, `MoveSpec`, `MoveFixture`, …); `MovePattern` liegt im Package
  `composition`, weil das sealed Interface `Pattern` nur Typen im selben Package erlaubt. Moves spielen als
  `ActiveComposition.ofMove` mit Ids ab `ActiveComposition.MOVE_IDS` (2⁴⁰ + Move-Id).
- **Position halten:** `Playback` merkt sich beim GO Pan/Tilt aller Geräte und legt die neuen Moves darüber. Ein
  ausblendender Move bleibt beim GO stehen; der Kopf gleitet mit dem Fade-In von dort zum neuen Move (dunkle Köpfe
  springen). So fährt ein Kopf ohne Move in der neuen Szene nicht weiter oder zurück.
- **Move in dark:** Alle Köpfe, die im Dunkeln fahren, dimmen gemeinsam ein (nach Dim out + Travel, sobald einer von
  ihnen beim GO leuchtete); ein dunkler Kopf wartet dann ggf. etwas länger. Die Fortschrittsanzeige bekommt den
  späteren Beginn des Fade-Ins als `delay`.
- **Grenzen:** in DMX-Werten am Fixture (`limits` in der Patch-API), angewendet in `OutputEngine` auf alles unter dem
  Programmer (Playback, Party, Vorschau „Show on stage“).
- **Bestehende Daten:** Migration V25 löscht Pan/Tilt aus Looks und Positions-Presets; der Import älterer
  Projektdateien lässt sie mit einer Warnung im Log weg.
- **Programmer:** „Record position“ als eigener Knopf; „Record scene“ legt auf Wunsch die Position der Köpfe als Move
  in die neue Szene (Look + Position).
- Der Figureneditor wird mit der Maus bedient; genaue Werte und das Umsortieren gehen über die Punktliste.

## Später

- Weiche Kurven statt gerader Linien (Spline durch die Punkte)
- Haltezeit pro Punkt (für Reisen: anhalten, warten, weiterfahren)
- Tempo im Takt (BPM, Tap) für die Party
- Relativ zu einer Look-Position statt absolut
- Versatz „gleichmäßig verteilt“ statt in Schritten
- Weitere Kanäle entlang der Figur (z.B. Farbe abhängig von der Position)
- Move in dark auch für Party-Felder und Tasten
- Den Kanal „Blackout while moving“ mancher Geräte nutzen
- Maximale Fahrgeschwindigkeit eines Geräts berücksichtigen (Warnung, wenn eine Figur zu schnell ist)
