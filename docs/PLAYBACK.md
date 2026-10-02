# Szenen & Playback

Für ein Theaterstück ist die Show eine **geordnete Liste von Szenen**. Die Bedienerin oder der Bediener drückt an den
richtigen Stellen **GO**, und das Licht blendet in die nächste Szene über.

## Lebenszyklus einer Szene

Jede Szene durchläuft drei Phasen:

| Phase | Dauer | Was passiert |
|---|---|---|
| **Fade-In** | global `fade_in_ms` | Szene blendet von 0 auf 100 % ein |
| **Stay** | bis zum nächsten GO, oder optional `auto_follow_ms` | Szene steht |
| **Fade-Out** | global `fade_out_ms` | Szene blendet auf 0 % aus |

Die Fade-Zeiten gelten **projektweit** und werden im Konfigurationsmodus eingestellt (Default jeweils 3 s).

### Auto-Follow (optional pro Szene)

Standardmäßig steht eine Szene, bis jemand GO drückt. Pro Szene kann zusätzlich eine **Stay-Dauer**
(`auto_follow_ms`) gesetzt werden. Dann löst die Engine das GO zur nächsten Szene selbst aus.

- Die Stay-Zeit beginnt, **wenn der Fade-In abgeschlossen ist**.
- Ein manuelles GO vor Ablauf hat Vorrang und bricht den Countdown ab.
- **Pause** hält auch den Countdown an. **Back** und **Springen** setzen ihn zurück.
- Ketten sind möglich (Szene 5 → 5.1 → 5.2 automatisch), z.B. für Abläufe wie „Sonnenuntergang in drei Stufen“.
- Die letzte Szene der Liste kann kein Auto-Follow haben. Ein Ende („Fade to Black“) erfolgt nur per GO.

```
        GO                    automatisch              automatisch
        │                     │                        │
Sz. 5   │▁▂▄▆██████████████████▇▅▃▁                     │
        │ fade-in │ stay 8 s  │                        │
Sz. 5.1 │                     │▁▂▄▆█████████████████████▇▅▃▁
        │                     │ fade-in │ stay 10 s    │
Sz. 5.2 │                     │                        │▁▂▄▆████████… (bis GO)
```

## Überblendung: Fade-Out und Fade-In laufen parallel

Ein GO startet **gleichzeitig** den Fade-Out der aktuellen und den Fade-In der nächsten Szene:

```
                  GO                                        GO
                  │                                         │
Szene 1  ▁▃▅▇█████│█▇▆▅▄▃▂▁                                  │
         fade-in  │ fade-out (5 s)                          │
                  │                                         │
Szene 2           │▁▂▄▆█████████████████████████████████████│█▇▆▅▄▃▂▁
                  │fade-in (3 s)   stay (bis GO)            │ fade-out
                  │                                         │
Szene 3           │                                         │▁▂▄▆██████…
──────────────────┴─────────────────────────────────────────┴──────────▶ t
```

Sind die Zeiten unterschiedlich (z.B. In 3 s, Out 5 s), starten beide beim GO und enden zu verschiedenen Zeitpunkten.

## Wie die Engine mischt

Jede laufende Szene hat einen **Level** `L(t)` zwischen 0 und 1, der durch Fade-In/-Out bestimmt wird.
Pro Fixture und Attribut wird im Render-Loop (~40 Hz) berechnet:

**Intensität (Dimmer, auch virtueller Dimmer): HTP – Highest Takes Precedence**
```
dimmer = max( dimmer_alt × L_alt(t) ,  dimmer_neu × L_neu(t) )
```
- Gerät ist in beiden Szenen an → es dimmt sauber von alt nach neu, ohne Einbruch, wenn die Werte ähnlich sind.
- Gerät nur in der alten Szene → fadet mit `fade_out_ms` aus.
- Gerät nur in der neuen Szene → fadet mit `fade_in_ms` ein.

**Farbe, Position, Zoom, … (stufenlos): LTP – Latest Takes Precedence, überblendet**
```
wert = start + (ziel_neu − start) × fortschritt_in(t)
```
- `start` = der tatsächlich ausgegebene Wert im Moment des GO.
- War das Gerät beim GO dunkel, springt der Wert sofort aufs Ziel (unsichtbar, kein „Farbschmieren“ beim Einblenden).
- Ist ein Attribut in der neuen Szene nicht gesetzt, bleibt der bisherige Wert stehen.

**Diskrete Kanäle (Gobo, Farbrad, Prisma, Strobe, …):** springen beim GO auf den neuen Wert.

**Kurven:** standardmäßig linear für die Intensität im DMX-Wert, optional später „S-Kurve“ bzw. wahrnehmungsgerecht.

## GO während eines laufenden Fades

Damit es nie hakt, arbeitet die Engine mit einem **Snapshot**: Beim GO wird der **aktuelle Ausgabezustand
eingefroren** und als „alte Szene“ mit `fade_out_ms` ausgeblendet. Dadurch laufen immer höchstens zwei Ebenen
(Snapshot + neue Szene) und ein schnelles Doppel-GO funktioniert ohne Sprünge.

## Bedienbefehle im Betrieb

| Befehl | Taste | Verhalten |
|---|---|---|
| **GO** | `Leertaste` | Nächste Szene in der Liste mit Überblendung starten |
| **Back** | `←` | Vorherige Szene **in der Liste** (nicht die zuletzt gespielte) mit denselben Fade-Zeiten |
| **Springen** | Szene antippen (oder `↑`/`↓`) + GO | Beliebige Szene als nächste vormerken |
| **Pause** | `P` | Laufende Fades einfrieren, erneutes Drücken setzt fort |
| **Fade abschließen** | `Enter` | Laufenden Fade sofort beenden (Zielzustand) |
| **Blackout** | `B` | Sofort alles aus (Grand Master 0), erneutes Drücken stellt wieder her |
| **Grand Master** | Fader | Skaliert alle Intensitäten |

- **Vor der ersten Szene** ist der Zustand „Blackout / Einlass“. Das erste GO blendet Szene 1 ein.
- **Nach der letzten Szene** blendet ein GO alles mit `fade_out_ms` auf Schwarz („Ende“).
- Die aktuelle Szene wird in `playback_state` gespeichert. Nach einem Neustart bietet der Betriebsmodus an, dort
  weiterzumachen (Szene wird direkt ohne Fade gesetzt).

## Umsetzung

- `Playback` (Domain) rechnet auf einer **virtuellen Uhr**, die während Pause stillsteht – Fades und Auto-Follow-
  Countdown frieren dadurch ein und laufen nahtlos weiter.
- `Crossfade.mix` setzt die Mischregeln oben um; das Fade-Verhalten pro Kanal kommt aus dem Geräteprofil
  (inkl. `fade: snap|smooth`).
- Das Playback ist eine Ausgabe-Ebene (Priorität 50) **unter** dem Programmer (100).
- Doppel-GO-Schutz: Client 300 ms, Server 200 ms.
- Beim Start des Servers steht das Playback auf „Ready“ (dunkel); Wiederaufnahme nach Neustart folgt in Iteration 8.

## Programmer vs. Playback

- Im **Konfigurationsmodus** setzt der **Programmer** Werte direkt („Live-Bearbeitung“), daraus werden Szenen
  gespeichert bzw. aktualisiert.
- Im **Betriebsmodus** gibt nur das Playback aus. Der Programmer ist gesperrt (der Server lehnt Änderungen mit 409 ab),
  damit niemand während der Vorstellung versehentlich etwas verstellt.
- Beim Wechsel Konfiguration → Betrieb wird der Programmer geleert. Der Modus ist Server-Zustand: alle verbundenen
  Browser wechseln mit. Zurück in den Konfigurationsmodus geht es nur mit Bestätigung (später PIN).

## Mögliche Erweiterungen (Backlog)

- Fade-Zeiten pro Szene überschreiben (global bleibt Default). Praktisch z.B. für einen Blitz mit 0 s Fade.
- Verzögerung (Delay) zwischen Fade-Out und Fade-In
- Externe GO-Taste (USB-Fußschalter / OSC); MIDI-Controller siehe [MIDI.md](MIDI.md)
