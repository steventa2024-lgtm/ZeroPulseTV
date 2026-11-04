# Shared by build.sh and qa/run-tests.sh. Requires Bash 3.2 or newer.
# ROOT and BUILD_DIR are set by the caller.
if [[ -z ${JAVA_HOME:-} && $(uname -s) == Darwin ]]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
fi
if [[ -n ${JAVA_HOME:-} ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"$HOME/Library/Android/sdk"}}
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
if [[ -n ${BUILD_TOOLS_VERSION:-} ]]; then
    BT="$SDK/build-tools/$BUILD_TOOLS_VERSION"
else
    for version in 34.0.0 36.0.0 android-14; do
        BT="$SDK/build-tools/$version"
        [[ -x "$BT/aapt2" ]] && break
    done
fi
for file in "$ANDROID_JAR" "$BT/core-lambda-stubs.jar" "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner"; do
    [[ -f "$file" ]] || { echo "Missing $file. See docs/BUILDING.md." >&2; exit 1; }
done
for tool in javac java jar keytool; do
    command -v "$tool" >/dev/null || { echo "Missing JDK tool: $tool" >&2; exit 1; }
done

compile_app() {
    local commit stamp file
    local sources=()
    mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/gen/tv/calmhome/launcher"
    "$BT/aapt2" compile --dir "$ROOT/launcher/res" -o "$BUILD_DIR/resources.zip"
    "$BT/aapt2" link -o "$BUILD_DIR/base.apk" -I "$ANDROID_JAR" \
        --manifest "$ROOT/launcher/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 31 \
        -A "$ROOT/launcher/assets" --java "$BUILD_DIR/gen" --auto-add-overlay "$BUILD_DIR/resources.zip"
    commit=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)
    [[ -z $(git -C "$ROOT" status --porcelain 2>/dev/null) ]] || commit="$commit-modified"
    stamp=$(date '+%Y%m%d.%H%M')
    cat >"$BUILD_DIR/gen/tv/calmhome/launcher/BuildInfo.java" <<EOF
package tv.calmhome.launcher;
public final class BuildInfo {
    public static final String BUILD = "$stamp";
    public static final String COMMIT = "$commit";
    private BuildInfo() {}
}
EOF
    while IFS= read -r -d '' file; do sources+=("$file"); done < <(
        find "$ROOT/launcher/src" "$BUILD_DIR/gen" -type f -name '*.java' -print0
    )
    javac -Xlint:all,-options,-serial -source 8 -target 8 -encoding UTF-8 \
        -bootclasspath "$ANDROID_JAR:$BT/core-lambda-stubs.jar" -d "$BUILD_DIR/classes" "${sources[@]}"
}
