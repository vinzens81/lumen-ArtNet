#!/usr/bin/env bash
# Builds the Mac app: dist/Lumen-<version>.dmg with Lumen.app and its own Java runtime inside.
#
#   ./package-mac.sh                    build without tests and package, version 1.0.<number of commits>
#   ./package-mac.sh --version 1.2.0    with an explicit version (macOS wants 1–3 numbers, the first at least 1)
#   ./package-mac.sh --test             build with all tests first
#   ./package-mac.sh --no-build         package the JAR that is already built
#
# Install: open the DMG and drag Lumen into "Applications". An installed Lumen is replaced by the new one; the
# data stays in ~/Library/Application Support/Lumen. The app is not signed with an Apple developer ID: if macOS
# refuses to open a copied DMG, right-click Lumen.app → Open (only the first time).
set -euo pipefail
cd "$(dirname "$0")"

usage() {
	sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
}

build=(./build.sh --fast)
version=""
while [ $# -gt 0 ]; do
	case "$1" in
		--version) version="${2:-}"; shift ;;
		--test) build=(./build.sh) ;;
		--fast) build=(./build.sh --fast) ;; # the default; kept so older calls still work
		--no-build) build=() ;;
		-h | --help) usage; exit 0 ;;
		*) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
	shift
done

if [ "$(uname -s)" != "Darwin" ]; then
	echo "The Mac app can only be built on macOS." >&2
	exit 1
fi
if ! command -v jpackage >/dev/null 2>&1; then
	echo "jpackage not found – it comes with the JDK (Java 26)." >&2
	exit 1
fi

if [ -z "$version" ]; then
	version="1.0.$(git rev-list --count HEAD 2>/dev/null || echo 0)"
fi
if ! [[ "$version" =~ ^[1-9][0-9]*(\.[0-9]+){0,2}$ ]]; then
	echo "Invalid version '$version' – macOS wants 1–3 numbers, the first at least 1, e.g. 1.2.0" >&2
	exit 2
fi
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
	echo "⚠ There are uncommitted changes – they are part of this build."
fi

if [ ${#build[@]} -gt 0 ]; then
	"${build[@]}"
fi
jar=$(ls lumen-app/target/lumen-app-*.jar 2>/dev/null | grep -v '\.original$' | grep -Ev ' [0-9]+\.jar$' | head -n 1 || true)
if [ -z "$jar" ]; then
	echo "No JAR found – build first (without --no-build)." >&2
	exit 1
fi

echo "▶ Packaging Lumen $version …"
work=target/macos
rm -rf "$work"
mkdir -p "$work/input" dist

# app contents: the JAR and the fixture profiles shipped with it (copied to the user's library on first start)
cp "$jar" "$work/input/lumen-app.jar"
cp -R fixture-types "$work/input/fixture-types"

# icon: drawn by MakeIcon.java, scaled to all sizes macOS wants
iconset="$work/Lumen.iconset"
mkdir -p "$iconset"
java packaging/macos/MakeIcon.java "$work/icon.png"
for size in 16 32 128 256 512; do
	sips -z $size $size "$work/icon.png" --out "$iconset/icon_${size}x${size}.png" >/dev/null
	sips -z $((size * 2)) $((size * 2)) "$work/icon.png" --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$iconset" -o "$work/Lumen.icns"

dmg="dist/Lumen-$version.dmg"
rm -f "$dmg"
jpackage \
	--type app-image \
	--name Lumen \
	--app-version "$version" \
	--vendor "Lumen" \
	--description "DMX/Art-Net lighting control for the theatre" \
	--input "$work/input" \
	--main-jar lumen-app.jar \
	--java-options "-Dlumen.desktop=true" \
	--java-options '-Dlumen.bundled-fixture-library=$APPDIR/fixture-types' \
	--icon "$work/Lumen.icns" \
	--mac-package-identifier de.vinzens.lumen \
	--mac-package-name Lumen \
	--mac-app-category public.app-category.utilities \
	--dest "$work/image"

# no App Nap: macOS would otherwise throttle the render loop while Lumen is in the background
app="$work/image/Lumen.app"
/usr/libexec/PlistBuddy -c "Add :NSAppSleepDisabled bool true" "$app/Contents/Info.plist"
codesign --force --deep --sign - "$app"

jpackage \
	--type dmg \
	--app-image "$app" \
	--name Lumen \
	--app-version "$version" \
	--vendor "Lumen" \
	--dest dist

echo "✔ $dmg ($(du -h "$dmg" | cut -f1))"
echo "  Open it and drag Lumen into Applications."
