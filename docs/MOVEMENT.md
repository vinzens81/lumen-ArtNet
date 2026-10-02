# Bewegungsbilder für Moving Heads & Scanner (Zukunft)

> **Status:** Future Implementation, wird aktuell **nicht** umgesetzt. Hier werden nur die Anforderungen und
> offenen Fragen gesammelt, damit wir später gezielt einsteigen können.

## Problem

Einzelne Pan/Tilt-Werte reichen nur für **statische Positionen** („Spot auf die Bühnenmitte“). Für Bewegung
braucht es **Bewegungsbilder**: eine Form, die über die Zeit abgefahren wird und sich in Lage, Größe und Tempo
anpassen lässt, für eine ganze Gruppe gleichzeitig mit Versatz zwischen den Geräten.

Betrifft:
- **Moving Heads:** Pan/Tilt in Grad, große Bereiche (z.B. 540°/270°), oft 16 Bit
- **Scanner** (Spiegel-Scanner): kleiner Bereich, schnellere Bewegung, ggf. andere Achsenrichtung → in der Bibliothek
  eigene `category: scanner`

## Parameter eines Bewegungsbilds (erster Entwurf)

| Parameter | Bedeutung |
|---|---|
| **Form** | Kreis, Ellipse, Acht, Linie (horizontal/vertikal), Dreieck, Quadrat, Welle, Random/Lissajous, später freie Pfade |
| **Zentrum / Offset** | Pan- und Tilt-Offset: um welchen Punkt die Bewegung stattfindet (z.B. eine gespeicherte Position) |
| **Scale** | Größe der Form, **getrennt für Pan und Tilt** (Ellipse aus Kreis, flache Acht, …) |
| **Rotation** | Drehung der Form im Pan/Tilt-Raum |
| **Geschwindigkeit** | Zyklen pro Minute oder BPM-gekoppelt (½, 1, 2, 4 Takte) |
| **Richtung** | vorwärts / rückwärts / Ping-Pong |
| **Phase / Spreizung** | Versatz zwischen den Geräten einer Gruppe (0° = synchron, 360° = gleichmäßig verteilt) |
| **Spiegelung** | z.B. linke Hälfte der Gruppe gespiegelt („Fächer“, „Ballyhoo“) |
| **Fade-In der Bewegung** | Größe wächst beim Start von 0 auf Scale, statt abrupt zu starten |

Berechnung später als reine Funktion:

```
position(t, fixtureIndex) = offset + rotate( scale ⊙ form( phase(t, speed) + spreizung(fixtureIndex) ) )
```

## Offene Fragen

- **Koordinatensystem:** Grad, normalisiert (0..1) oder ein gemeinsamer „Bühnenraum“? Unterschiedliche Pan/Tilt-Bereiche
  und Montagearten (hängend, stehend, invertiert) müssen auf ein einheitliches System abgebildet werden.
- **Montage-Infos im Patch:** Ausrichtung, Invertierung Pan/Tilt, Pan/Tilt-Swap pro Fixture (Projektdaten, nicht YAML)
- **Grenzen:** Pan/Tilt-Limits pro Fixture, damit nichts ins Publikum blendet
- **Theater vs. Party:** Bewegung auch als Szenen-Attribut im Theater-Modus (langsamer Schwenk) oder nur im Party-Modus?
- **Übergänge:** Was passiert beim Szenenwechsel mit einer laufenden Bewegung: weich in die neue Form überblenden
  oder anhalten und neu starten?
- **Geschwindigkeitsgrenzen:** Geräte haben eine maximale Verfahrgeschwindigkeit. Zu schnelle Formen werden
  „abgeschnitten“. Ein `speed`-Kanal im Gerät kann helfen oder stören.
- **Editor-UI:** Form-Vorschau mit animiertem Punkt pro Gerät, Offset per XY-Pad ziehen, Scale per Pinch/Fader

## Abhängigkeiten

- Baut auf der Effekt-Engine und der BPM-Clock aus dem [Party-Modus](PARTY-MODE.md) auf.
- Läuft als `OutputLayer` in der bestehenden Engine ([ARCHITECTURE.md](ARCHITECTURE.md#dmx-engine)). Es ist kein Umbau nötig.
- Bis dahin gibt es nur **statische Pan/Tilt-Positionen** über das XY-Pad im Programmer und in Szenen.
