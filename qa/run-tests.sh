#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [[ $# -gt 0 ]]; then echo "Usage: bash qa/run-tests.sh" >&2; exit 2; fi
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/zeropulse-tests.XXXXXX")
trap 'rm -rf -- "$BUILD_DIR"' EXIT
source "$ROOT/launcher/toolchain.sh"
src="$ROOT/launcher/src/tv/calmhome/launcher"
sources=("$src/downloads/DownloadPolicy.java" "$src/downloads/DownloadRecord.java")
while IFS= read -r -d '' file; do sources+=("$file"); done < <(
    find "$src/core" "$ROOT/qa/tests" -type f -name '*.java' -print0
)
mkdir "$BUILD_DIR/tests"
javac -encoding UTF-8 -d "$BUILD_DIR/tests" "${sources[@]}"
java -cp "$BUILD_DIR/tests" CoreTests "$ROOT/launcher/assets/wallpapers/credits.tsv"
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.core.HomeGlassTests
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.core.VisualPolishTests "$ROOT/launcher/assets/wallpapers" "$ROOT/launcher/src"
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.downloads.CatalogTests
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.downloads.DownloadTests
compile_app
if command -v node >/dev/null; then
    node --test "$ROOT/qa/tests/expected-version.test.mjs" "$ROOT/website/test.mjs"
else
    echo "SKIP Node version and website tests: Node.js unavailable."
fi
