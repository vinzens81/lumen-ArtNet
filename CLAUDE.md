# Lumen – Hinweise für Claude

DMX/Art-Net-Lichtsteuerung fürs Theater. Konzept und Planung stehen in `README.md` und `docs/`;
der aktuelle Stand steht in `docs/ROADMAP.md`.

**Sprache:** Dokumente (`README.md`, `docs/`, `CLAUDE.md`) auf Deutsch. Alles andere auf **Englisch**: Code, Kommentare,
Javadoc, Log- und Fehlermeldungen, API-Antworten, Testdaten, Beispielprofile in `fixture-types/` und die Web-Oberfläche.

## Build & Run

```bash
./mvnw verify                                 # Build + alle Tests (inkl. ArchUnit)
./mvnw -pl lumen-app -am compile spring-boot:run   # App starten → http://127.0.0.1:8080/actuator/health
./mvnw -pl lumen-domain test                  # nur ein Modul testen
```

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
- Schema-Änderungen nur als neue Flyway-Migration (`lumen-adapter-sqlite/src/main/resources/db/migration`),
  bestehende Migrationen nie ändern.
- Zeitabhängige Logik (Fades, Auto-Follow) bekommt eine `Clock` injiziert und wird ohne Spring getestet.
