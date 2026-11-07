# AGENTS.md – guidelines for coding agents

Lumen is a DMX / Art-Net / sACN lighting controller for theatre: patch, looks, compositions, scenes, a show
playback with script, a tablet remote and a party mode. Concept and plans are in `README.md` and `docs/`; what is
done and what comes next is in `docs/ROADMAP.md`. Read the doc for the area you touch before changing it
(`docs/ARCHITECTURE.md`, `docs/PLAYBACK.md`, `docs/UI.md`, `docs/LOOKS-COMPOSITIONS-SCENES.md`, …).

## Language

Everything is **English**: code, comments, Javadoc, log and error messages, API responses, test data, example
profiles in `fixture-types/`, the web UI, `README.md`, `docs/` and `CHANGELOG.md`. Only `CLAUDE.md` is German.
Talk to the user in the language they write in.

## Build, run, test

```bash
./build.sh                  # clean build with all tests; --fast skips tests
./run.sh                    # starts the built JAR (builds first if there is none); --build always rebuilds
./run.sh --test             # Art-Net off, own DB data/test.db – for trying things without real devices
./run.sh -- --server.port=8099   # everything after -- goes to Lumen
./package-mac.sh            # Mac app dist/Lumen-<version>.dmg (version 1.0.<number of commits>)

./mvnw verify                          # build + all tests, including ArchUnit
./mvnw -pl lumen-domain test           # one module

cd lumen-frontend
PATH="$PWD/node:$PATH" npm run dev     # hot reload on :5173, /api goes to the backend on :8080
PATH="$PWD/node:$PATH" npm test        # Vitest
PATH="$PWD/node:$PATH" npm run build   # svelte-check --fail-on-warnings + Vite build into target/classes/static
```

Maven installs Node into `lumen-frontend/node/`; use that one, not a global Node. Stack: Java 26, Spring Boot 4.1
(Jackson 3, packages `tools.jackson.*`), Maven multi-module, base package `de.vinzens.lumen`, SQLite + Flyway,
Svelte 5 + Vite + TypeScript.

## Architecture (hexagonal) – enforced by `ArchitectureTest`

- `lumen-domain` – plain Java, no dependencies at all (no Spring, no Jackson).
- `lumen-application` – use cases and ports, depends only on `lumen-domain`, no Spring.
- `lumen-adapter-*` – adapters in `de.vinzens.lumen.adapter.<name>`; adapters never depend on each other. A new
  adapter module is created only when it is needed, and added as a dependency of `ArchitectureTest` in `lumen-app`.
- `lumen-app` – Spring Boot main and the wiring of use cases as beans.
- REST responses are DTOs in the web adapter; domain objects are never serialized directly.
- Validation errors are `DomainValidationException` with readable messages; the API returns them as `errors`.
- Time-dependent logic (fades, auto-follow, beat clock) gets a `java.time.Clock` injected and is tested without
  Spring.
- Schema changes only as a **new** Flyway migration in `lumen-adapter-sqlite/src/main/resources/db/migration`
  (next free `V<n>__…sql`). Never edit an existing migration.

## Frontend (`lumen-frontend`)

- Design tokens are CSS variables in `src/app.css` – use them, no hard-coded colors.
- API types in `src/lib/types.ts` match the web DTOs; change both together.
- Pure logic goes into `src/lib/*.ts` with a Vitest test next to it (`*.test.ts`); components stay thin.
- `svelte-check` runs with `--fail-on-warnings`, including a11y – a warning breaks the build.
- Show views (show, remote, `#/view/*`) are used live on stage: nothing may jump or move while the show runs,
  panels have fixed sizes, touch targets are large, accidental presses of dangerous buttons (Stop, Blackout) are
  hard. Think about landscape tablet, portrait tablet and phone.
- A component shared by several views (e.g. `MasterBar`) gets an option for view-specific behavior instead of a
  change that affects every view.

## License

Lumen is **GPL-3.0-or-later** (`LICENSE`). New dependencies must be GPL-3.0 compatible (Apache-2.0, MIT, BSD, LGPL,
OFL for fonts are fine); ask before adding anything else. Do not copy code from other projects unless its license
allows it, and name the source.

## Code style

- Small, focused changes that follow the patterns already in the code; look for an existing helper before writing
  a new one.
- Comments and Javadoc say *why* and what something means for the user, in full sentences; no comments that
  repeat the code.
- Records and immutable domain objects; `Optional` instead of `null` in the domain.
- Tests: domain and application logic with plain unit tests, repositories against a temporary SQLite file,
  `lumen-app` tests with `src/test/resources/config/application.yml` (own DB under `target/test-data/`, Art-Net
  output off).

## Safety: real light, real shows

Lumen drives real fixtures in a theatre. Treat the running instance as production.

- **Never send DMX to real devices without asking.** That includes setting the programmer, GO, keys, faders,
  master, blackout and loading looks/scenes. Check `GET /api/output/health` first: which nodes are online, which
  universes they serve.
- Prefer `./run.sh --test` (Art-Net off, `data/test.db`) for trying things out.
- Do not touch the data of the user: `data/lumen.db` and, for the Mac app, `~/Library/Application Support/Lumen`
  (log in `~/Library/Logs/Lumen`). Never write into the SQLite file of a running instance; use the REST API.
- When changing show content in a running instance via REST (looks, compositions, scene keys, …): read the current
  state first, change only what was asked, keep everything else as it was (e.g. a `PUT` of scene keys replaces all
  keys of the scene), and verify afterwards. If a step briefly changes live output (recording a look from the
  programmer), clear the programmer right after and tell the user.
- Port 8080 (HTTP) and UDP 6454 (Art-Net) are used by a running Lumen; start a second instance on another port.
- The own ESP8266 dimmer is the test hardware; `EspNodeSimulator` in the Art-Net adapter imitates its
  ArtPollReply. sACN output is so far only tested in code, not on real receivers.

## Definition of done

1. `./mvnw verify` is green for backend changes; `npm test` and `npm run build` are green for frontend changes.
2. New logic has tests.
3. `CHANGELOG.md`: an entry under the newest version (`## 1.0.<commits> · <date>`, sections `### Added`,
   `### Changed`, `### Fixed`), written for the user, not the developer. The app parses this file for
   Settings → What's new, so keep the format.
4. The matching doc in `docs/` describes the new behavior (UI changes in `docs/UI.md`); `docs/ROADMAP.md` when an
   iteration item is done.
5. Say what was verified and what was not (e.g. "not tried in a browser", "not tested on hardware").

## Git

- Commit and push only when the user asks. Larger features on a branch (`feature/<name>`) with a pull request.
- One commit per change; a file with two unrelated changes is staged hunk by hunk.
- Commit message: one line, English, what changed for the user, no prefix – e.g.
  `Remote: blackout in the middle of the master bar, away from the ▼ button`.
- Author is the configured git user. **No AI co-author or "generated with" trailers.**

## Known pitfalls

- The project lives in `~/Documents`; iCloud sync can create duplicates such as `Klasse 2.class` in `target/`
  ("Unable to find a single main class"). `./mvnw clean` fixes it.
- Relative paths (`fixture-types/`, `data/lumen.db`) are relative to the project root.
- The Mac app (`/Applications/Lumen.app`) runs its own bundled JAR: frontend or backend changes only show there
  after `./package-mac.sh` and reinstalling.
- zsh does not split unquoted variables: `J='-H Content-Type:application/json'; curl $J …` sends a broken header.
