#!/usr/bin/env bash
# Builds Lumen: backend, web UI and the runnable JAR (lumen-app/target/lumen-app-*.jar).
#
#   ./build.sh          clean build with all tests (Java, ArchUnit, Vitest, svelte-check)
#   ./build.sh --fast   clean build without tests – e.g. before trying something out
#
# Always starts clean: in an iCloud-synced folder, copies like "Foo 2.class" or "classes 4" can appear in
# target/ and make the start hang. iCloud may even turn them into cloud placeholders that 'mvn clean' cannot
# delete, so the target/ folders are removed first. Passing tests print nothing, a full build takes about a minute.
set -euo pipefail
cd "$(dirname "$0")"

usage() {
	sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
}

tests=true
for arg in "$@"; do
	case "$arg" in
		--fast) tests=false ;;
		-h | --help) usage; exit 0 ;;
		*) echo "Unknown option: $arg" >&2; usage >&2; exit 2 ;;
	esac
done

if ! command -v java >/dev/null 2>&1; then
	echo "Java is not installed (Lumen needs Java 26)." >&2
	exit 1
fi

maven_args=(clean)
if $tests; then
	echo "▶ Building Lumen with all tests …"
	maven_args+=(verify)
else
	echo "▶ Building Lumen without tests …"
	maven_args+=(package -DskipTests)
fi

start=$(date +%s)
rm -rf lumen-*/target
./mvnw -q "${maven_args[@]}"

jar=$(ls lumen-app/target/lumen-app-*.jar 2>/dev/null | grep -v '\.original$' | head -n 1)
echo "✔ Done in $(($(date +%s) - start)) s: $jar"
echo "  Start with ./run.sh"
