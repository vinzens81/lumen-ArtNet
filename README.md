# Lumen – DMX/Art-Net Lichtsteuerung fürs Theater

> Arbeitstitel „Lumen“ – kann jederzeit umbenannt werden.

Eine webbasierte Lichtsteuerung für Theaterstücke: Ein Spring-Boot-Service stellt eine moderne Web-Oberfläche
bereit, über die DMX-Geräte per **Art-Net** gesteuert werden. Eine Show besteht aus einer Liste von **Szenen**, die
per **GO** mit parallelem Fade-Out/Fade-In ineinander überblenden.

```
 Browser (Tablet / Laptop)
   ├─ Konfiguration: Projekt, Patch, Szenen bauen
   └─ Betrieb:       Szenenliste, GO, Master, Blackout
        │  HTTP + WebSocket
        ▼
 ┌──────────────────────────────┐       Art-Net (UDP 6454)       ┌──────────────┐
 │  Lumen Server (Spring Boot)  │ ─────────────────────────────▶ │ Art-Net Node │──DMX──▶ 💡💡💡
 │  hexagonal, Java 26, Maven   │                                └──────────────┘
 └──────────────────────────────┘
     ▲ liest                 ▲ liest/schreibt
 fixture-types/*.yml      data/lumen.db (SQLite)
 Gerätebibliothek         Projekte: Patch, Universen, Szenen, Fade-Zeiten
```

## Ziele

- **Flexibel:** Neue Gerätetypen durch Ablegen einer YAML-Datei, ohne neu zu kompilieren.
- **Projektbasiert:** Jedes Stück bzw. jede Spielstätte ist ein Projekt in SQLite mit eigenem Patch und eigenen Szenen.
- **Showtauglich:** Getrennte Modi für Konfiguration und Betrieb, robustes Playback, Wiederaufnahme nach Neustart.
- **Sauber:** Hexagonale Architektur, Domain ohne Framework-Abhängigkeiten, gut testbar.
- **Schön:** Dunkles, „glühendes“ UI, touch-tauglich für Tablets.
- **Iterativ:** Jede Ausbaustufe ist für sich lauffähig.
- **Erweiterbar:** Später kommt ein Party-Modus für klassische Live-Lichtsteuerung dazu. Die Engine ist darauf vorbereitet.

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Hexagonale Architektur, Module, Domain, DMX-Engine, Art-Net, API |
| [docs/FIXTURE-LIBRARY.md](docs/FIXTURE-LIBRARY.md) | YAML-Format der Gerätebibliothek |
| [docs/PROJECT-DATA.md](docs/PROJECT-DATA.md) | SQLite-Schema für Projekte, Patch und Szenen |
| [docs/PLAYBACK.md](docs/PLAYBACK.md) | Szenen, Fade-In/Stay/Fade-Out, Überblendlogik, GO |
| [docs/UI.md](docs/UI.md) | Konfigurations- und Betriebsmodus, Look & Feel |
| [docs/COMPOSITIONS.md](docs/COMPOSITIONS.md) | *Geplant:* Kompositionen (Lauflicht, Farbwechsel, …) als dynamischer Teil von Szenen |
| [docs/MIDI.md](docs/MIDI.md) | *Optional:* USB-MIDI-Controller mit Fadern & Buttons, MIDI-Learn, LED-Feedback |
| [docs/PARTY-MODE.md](docs/PARTY-MODE.md) | *Zukunft:* Party-Modus mit Live-Steuerung, Effekten, BPM und Executors |
| [docs/MOVEMENT.md](docs/MOVEMENT.md) | *Zukunft:* Bewegungsbilder für Moving Heads & Scanner (Offset, Scale, Form) |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Iterative Teilschritte mit Definition of Done |

## Tech-Stack

| Bereich | Wahl |
|---|---|
| Sprache | Java 26 |
| Framework | Spring Boot 4.x (Web, WebSocket, Validation, Actuator) |
| Build | Maven Multi-Module |
| Gerätebibliothek | YAML via Jackson `jackson-dataformat-yaml` |
| Projektdaten | SQLite (`sqlite-jdbc`), Spring `JdbcClient`, Flyway-Migrationen |
| Art-Net | Eigene schlanke Implementierung (ArtDmx, später ArtPoll) |
| Tests | JUnit 5, AssertJ, ArchUnit, Spring Boot Test |
| Frontend | Vite + TypeScript + Svelte 5, gebaut via `frontend-maven-plugin`, von Spring Boot ausgeliefert |
