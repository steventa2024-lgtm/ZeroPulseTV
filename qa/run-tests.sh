#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [[ $# -gt 1 ]]; then echo "Usage: bash qa/run-tests.sh [OpenPulse checkout]" >&2; exit 2; fi
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/zeropulse-tests.XXXXXX")
trap 'rm -rf -- "$BUILD_DIR"' EXIT
source "$ROOT/launcher/toolchain.sh"
src="$ROOT/launcher/src/tv/calmhome/launcher"
sources=("$src/downloads/DownloadPolicy.java" "$src/downloads/DownloadRecord.java")
while IFS= read -r -d '' file; do sources+=("$file"); done < <(
    find "$src/core" "$src/jarvis" "$ROOT/qa/tests" -type f -name '*.java' -print0
)
mkdir "$BUILD_DIR/tests"
javac -encoding UTF-8 -d "$BUILD_DIR/tests" "${sources[@]}"
java -cp "$BUILD_DIR/tests" CoreTests "$ROOT/launcher/assets/wallpapers/credits.tsv"
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.core.VoiceTests
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.jarvis.JarvisTests "$ROOT/launcher/src"
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.downloads.CatalogTests
java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.downloads.DownloadTests
if [[ $# == 1 ]]; then
    java -cp "$BUILD_DIR/tests" tv.calmhome.launcher.jarvis.JarvisIntegrationTests "$1/packages/gateway"
fi
compile_app
if command -v node >/dev/null; then
    node --test "$ROOT/qa/tests/claude-code-provider.test.mjs" "$ROOT/qa/tests/recognizer-routing.test.mjs" "$ROOT/website/test.mjs"
else
    echo "SKIP Node provider and website tests: Node.js unavailable."
fi
