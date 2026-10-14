# Lumen – DMX/Art-Net lighting control for the theatre

> Working title "Lumen" – may be renamed at any time.

A web-based lighting control for theatre plays: a Spring Boot service provides a modern web interface that controls
DMX fixtures via **Art-Net** or **sACN** (E1.31). A show is a list of **scenes** that cross-fade into each other on **GO**, with fade-out
and fade-in running in parallel.

```
 Browser (tablet / laptop)
   ├─ Setup:     project, patch, build scenes
   └─ Show:      scene list, GO, master, blackout
        │  HTTP + WebSocket
        ▼
 ┌──────────────────────────────┐       Art-Net (UDP 6454)       ┌──────────────┐
 │  Lumen server (Spring Boot)  │ ─────────────────────────────▶ │ Art-Net node │──DMX──▶ 💡💡💡
 │  hexagonal, Java 26, Maven   │                                └──────────────┘
 └──────────────────────────────┘
     ▲ reads                 ▲ reads/writes
 fixture-types/*.yml      data/lumen.db (SQLite)
 fixture library          projects: patch, universes, scenes, fade times
```

## Goals

- **Flexible:** new fixture types by dropping in a YAML file, without recompiling.
- **Project-based:** each play or venue is a project in SQLite with its own patch and scenes.
- **Show-ready:** separate modes for setup and show, robust playback, resume after a restart.
- **Clean:** hexagonal architecture, a domain without framework dependencies, easy to test.
- **Beautiful:** a dark, "glowing" UI, touch-friendly for tablets.
- **Iterative:** every stage of development runs on its own.
- **Extensible:** a party mode for classic live lighting control sits next to the show mode; the engine is built for it.

## Documentation

| Document | Content |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Hexagonal architecture, modules, domain, DMX engine, Art-Net, sACN, API |
| [docs/FIXTURE-LIBRARY.md](docs/FIXTURE-LIBRARY.md) | YAML format of the fixture library |
| [docs/PROJECT-DATA.md](docs/PROJECT-DATA.md) | SQLite schema for projects, patch and scenes |
| [docs/PLAYBACK.md](docs/PLAYBACK.md) | Scenes, fade-in/stay/fade-out, cross-fade logic, GO |
| [docs/UI.md](docs/UI.md) | Setup and show mode, look & feel |
| [docs/LOOKS-COMPOSITIONS-SCENES.md](docs/LOOKS-COMPOSITIONS-SCENES.md) | Looks → compositions → scenes: the three levels of a show, generators for effects, mixing rules |
| [docs/MIDI.md](docs/MIDI.md) | *Optional:* USB MIDI controller with faders & buttons, MIDI learn, LED feedback |
| [docs/PARTY-MODE.md](docs/PARTY-MODE.md) | Party mode: live control from a matrix of fields |
| [docs/MOVEMENT.md](docs/MOVEMENT.md) | Moves – drawn figures for pan/tilt fixtures, size, shift, offset |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Iterative steps with a definition of done |

## Tech stack

| Area | Choice |
|---|---|
| Language | Java 26 |
| Framework | Spring Boot 4.x (Web, WebSocket, Validation, Actuator) |
| Build | Maven multi-module |
| Fixture library | YAML via Jackson `jackson-dataformat-yaml` |
| Project data | SQLite (`sqlite-jdbc`), Spring `JdbcClient`, Flyway migrations |
| Art-Net | Own lean implementation (ArtDmx, ArtPoll discovery) |
| sACN | Own lean implementation (E1.31 data packets, multicast or unicast, universe discovery) |
| Tests | JUnit 5, AssertJ, ArchUnit, Spring Boot Test |
| Frontend | Vite + TypeScript + Svelte 5, built via `frontend-maven-plugin`, served by Spring Boot |
