#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
usage() {
    echo "Usage: bash launcher/build.sh (--development | --keystore PATH) [--output PATH]" >&2
    exit 2
}
mode= keystore= out="$ROOT/launcher/ZeroPulseTV.apk"
while [[ $# -gt 0 ]]; do
    case $1 in
        --development) [[ -z $mode ]] || usage; mode=development; shift ;;
        --keystore) [[ -z $mode && $# -ge 2 ]] || usage; mode=release; keystore=$2; shift 2 ;;
        --output) [[ $# -ge 2 ]] || usage; out=$2; shift 2 ;;
        *) usage ;;
    esac
done
[[ -n $mode ]] || usage
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/zeropulse-build.XXXXXX")
trap 'rm -rf -- "$BUILD_DIR"' EXIT
source "$ROOT/launcher/toolchain.sh"
if [[ $mode == development ]]; then
    keystore="$BUILD_DIR/dev.p12"
    export ZP_KEYSTORE_PASS=local-development
    keytool -genkeypair -storetype PKCS12 -keystore "$keystore" -storepass:env ZP_KEYSTORE_PASS \
        -keypass:env ZP_KEYSTORE_PASS -alias development -keyalg RSA -keysize 2048 -validity 365 \
        -dname 'CN=ZeroPulse Local Development'
else
    [[ -f "$keystore" ]] || { echo "Keystore file not found" >&2; exit 2; }
    if [[ -z ${ZP_KEYSTORE_PASS:-} ]]; then
        read -r -s -p "Keystore password: " ZP_KEYSTORE_PASS
        echo >&2
    fi
    export ZP_KEYSTORE_PASS
fi
compile_app
jar --create --file "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR" "$BUILD_DIR/classes.jar"
jar --update --no-manifest --file "$BUILD_DIR/base.apk" -C "$BUILD_DIR" classes.dex
"$BT/zipalign" -p -f 4 "$BUILD_DIR/base.apk" "$BUILD_DIR/aligned.apk"
"$BT/apksigner" sign --ks "$keystore" --ks-pass env:ZP_KEYSTORE_PASS --key-pass env:ZP_KEYSTORE_PASS \
    --out "$BUILD_DIR/candidate.apk" "$BUILD_DIR/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$BUILD_DIR/candidate.apk"
"$BT/zipalign" -c 4 "$BUILD_DIR/candidate.apk"
mkdir -p "$(dirname "$out")"
cp "$BUILD_DIR/candidate.apk" "$out"
shasum -a 256 "$out"
