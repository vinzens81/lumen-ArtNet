#!/usr/bin/env bash
# Starts Lumen from the built JAR; the UI is then at http://127.0.0.1:8080
#
#   ./run.sh                  normal start: project data in data/lumen.db, Art-Net output on
#   ./run.sh --test           safe try-out: Art-Net off (no DMX to real devices), own database
#                             data/test.db, backups in data/test-backups/
#   ./run.sh --build          build first (without tests), then start
#   ./run.sh -- <options>     everything after -- goes to Lumen, e.g. -- --server.port=8099
#
# If no JAR exists yet, it is built first. Stop Lumen with Ctrl+C.
set -euo pipefail
cd "$(dirname "$0")"

usage() {
	sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
}

test_mode=false
build=false
lumen_args=()
while [ $# -gt 0 ]; do
	case "$1" in
		--test) test_mode=true ;;
		--build) build=true ;;
		-h | --help) usage; exit 0 ;;
		--) shift; lumen_args+=("$@"); break ;;
		*) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
	shift
done

find_jar() {
	ls lumen-app/target/lumen-app-*.jar 2>/dev/null | grep -v '\.original$' | head -n 1 || true
}

if $build || [ -z "$(find_jar)" ]; then
	./build.sh --fast
fi
jar=$(find_jar)

# the port Lumen will use (default 8080, or --server.port=… after --)
port=8080
for arg in ${lumen_args[@]+"${lumen_args[@]}"}; do
	case "$arg" in --server.port=*) port="${arg#--server.port=}" ;; esac
done
if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
	echo "Port $port is already in use – is Lumen already running?" >&2
	lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null >&2 || true
	exit 1
fi

java_args=()
if $test_mode; then
	echo "▶ Starting Lumen in test mode: Art-Net off, database data/test.db"
	java_args+=(--lumen.artnet.enabled=false --lumen.database-file=data/test.db --lumen.backup.dir=data/test-backups)
else
	echo "▶ Starting Lumen: database data/lumen.db, Art-Net output on"
fi
echo "  UI: http://127.0.0.1:$port   (stop with Ctrl+C)"

# fixture-types/ and data/ are resolved relative to the project folder
exec java -jar "$jar" ${java_args[@]+"${java_args[@]}"} ${lumen_args[@]+"${lumen_args[@]}"}
