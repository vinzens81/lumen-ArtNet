# Lumen – Hinweise für Claude

DMX/Art-Net-Lichtsteuerung fürs Theater. Konzept und Planung stehen in `README.md` und `docs/`;
der aktuelle Stand steht in `docs/ROADMAP.md`.

**Sprache:** Dokumente (`README.md`, `docs/`, `CLAUDE.md`) auf Deutsch. Alles andere auf **Englisch**: Code, Kommentare,
Javadoc, Log- und Fehlermeldungen, API-Antworten, Testdaten, Beispielprofile in `fixture-types/` und die Web-Oberfläche.

## Build & Run

Für den Alltag gibt es zwei Skripte im Projekt-Root (`--help` zeigt alle Optionen):

```bash
./build.sh              # sauberer Build mit allen Tests; --fast ohne Tests
./run.sh                # startet das gebaute JAR (baut vorher, falls keins da ist); --build baut immer neu
./run.sh --test         # Art-Net aus, eigene DB data/test.db – zum Ausprobieren ohne echte Geräte
./run.sh -- --server.port=8099   # alles nach -- geht an Lumen
./package-mac.sh        # Mac-App: dist/Lumen-<Version>.dmg (Version 1.0.<Anzahl Commits>, --version 1.2.0 setzt sie)
```

Die Mac-App startet dasselbe JAR mit `-Dlumen.desktop=true` (`DesktopLauncher`: eigenes Fenster, Daten unter
`~/Library/Application Support/Lumen`, Log unter `~/Library/Logs/Lumen`). Ohne diese Option läuft Lumen wie bisher
ohne Fenster.

Die Maven-Befehle direkt:

```bash
./mvnw verify                                 # Build + alle Tests (inkl. ArchUnit)
./mvnw -pl lumen-app -am compile spring-boot:run   # App starten → http://127.0.0.1:8080/actuator/health
./mvnw -pl lumen-domain test                  # nur ein Modul testen
```

Frontend (`lumen-frontend`, Svelte 5 + Vite + TypeScript): Maven lädt Node selbst nach `lumen-frontend/node/`,
`npm run build` (inkl. `svelte-check`) landet in `target/classes/static` und wird von Spring Boot ausgeliefert.
Für schnelles UI-Arbeiten mit Hot Reload (Backend muss auf :8080 laufen, `/api` wird durchgereicht):

```bash
cd lumen-frontend && PATH="$PWD/node:$PATH" npm run dev     # → http://localhost:5173
PATH="$PWD/node:$PATH" npm test                              # Vitest
```

Die App lauscht auf UDP 6454 (Art-Net-Discovery). Der eigene ESP8266-Dimmer (Firmware in
`~/Documents/PlatformIO/Projects/Artnet`) ist die Test-Hardware; `EspNodeSimulator` im Art-Net-Adapter bildet sein
ArtPollReply-Format nach. Beim Testen mit laufender App keine DMX-Werte an echte Geräte schicken, ohne zu fragen.

Hinweis: Das Projekt liegt in `~/Documents`. Synchronisiert iCloud diesen Ordner, können Duplikate wie
`Klasse 2.class` in `target/` entstehen („Unable to find a single main class“). Dann hilft `./mvnw clean`.

Java 26, Spring Boot 4.1 (Jackson 3: `tools.jackson.*`), Maven Multi-Module (Parent: `pom.xml`,
Basis-Package `de.vinzens.lumen`). Relative Pfade wie `fixture-types/` und `data/lumen.db` (SQLite, wird beim
Start angelegt, nicht eingecheckt) beziehen sich auf das Projekt-Root. Tests in `lumen-app` nutzen
`src/test/resources/config/application.yml` (eigene DB unter `target/test-data/`, Art-Net-Ausgabe aus).

## Architekturregeln (hexagonal)

- `lumen-domain` – reines Java, keine Abhängigkeiten (auch kein Spring).
- `lumen-application` – Use Cases + Ports, hängt nur von `lumen-domain` ab, kein Spring.
- `lumen-adapter-*` – Adapter (Packages `de.vinzens.lumen.adapter.<name>`), werden erst in der Iteration angelegt,
  in der sie gebraucht werden.
- `lumen-app` – Spring Boot Main und Verdrahtung der Use Cases als Beans.
- Adapter hängen nicht voneinander ab; Domain/Application kennen weder Spring noch Jackson.
- `ArchitectureTest` (in `lumen-app`) prüft die Regeln; neue Adapter-Module dort als Abhängigkeit aufnehmen.
- REST-Antworten sind DTOs im Web-Adapter, Domain-Objekte werden nicht direkt serialisiert.
- Frontend: Design-Tokens in `src/app.css` (CSS-Variablen), API-Typen in `src/lib/types.ts` passend zu den DTOs halten,
  reine Logik in `src/lib/*.ts` mit Vitest testen. `svelte-check` läuft mit `--fail-on-warnings` (inkl. a11y).
- Schema-Änderungen nur als neue Flyway-Migration (`lumen-adapter-sqlite/src/main/resources/db/migration`),
  bestehende Migrationen nie ändern.
- Zeitabhängige Logik (Fades, Auto-Follow) bekommt eine `Clock` injiziert und wird ohne Spring getestet.
