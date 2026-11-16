"""Build the emulator-only test APK and its permission-free fixture. No device commands."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]

def build(sdk, jdk, bt, key, output):
    android = sdk / "platforms/android-34/android.jar"
    tools = sdk / "build-tools" / bt
    java = jdk / "bin"
    win = os.name == "nt"
    def run(args):
        subprocess.run([str(x) for x in args], check=True, env={**os.environ, "JAVA_HOME": str(jdk)})
    aapt = tools / ("aapt2.exe" if win else "aapt2")
    signer = tools / ("apksigner.bat" if win else "apksigner")
    d8 = tools / ("d8.bat" if win else "d8")
    align = tools / ("zipalign.exe" if win else "zipalign")
    with tempfile.TemporaryDirectory(prefix="zp-download-qa-") as tmp:
        tmp = Path(tmp)
        assets = tmp / "assets"
        assets.mkdir()
        def link(manifest, out):
            run([aapt, "link", "-o", out, "-I", android, "--manifest", manifest,
                 "--min-sdk-version", "26", "--target-sdk-version", "34"])
        def sign(src, dst):
            aligned = tmp / ("aligned-" + src.name)
            run([align, "-f", "4", src, aligned])
            run([signer, "sign", "--ks", key, "--ks-pass", "env:ZP_KEYSTORE_PASS",
                 "--key-pass", "env:ZP_KEYSTORE_PASS", "--out", dst, aligned])
            run([signer, "verify", dst])
        fixture = tmp / "fixture-base.apk"
        link(HERE / "fixture/AndroidManifest.xml", fixture)
        sign(fixture, assets / "fixture.apk")
        classes = tmp / "classes"
        classes.mkdir()
        stubs = tools / "core-lambda-stubs.jar"
        run([java / ("javac.exe" if win else "javac"), "-encoding", "UTF-8", "-source", "8", "-target", "8",
             "-Xlint:-options", "-bootclasspath", os.pathsep.join(map(str, [android, stubs])),
             "-sourcepath", ROOT / "launcher/src", "-d", classes,
             HERE / "DownloadRunner.java", HERE / "FixtureProvider.java", HERE / "FixturePicker.java"])
        with zipfile.ZipFile(tmp / "qa.jar", "w") as jar:
            for f in (classes / "tv/calmhome/downloadqa").rglob("*.class"):
                jar.write(f, f.relative_to(classes).as_posix())
        run([d8, "--release", "--min-api", "26", "--lib", android, "--classpath", classes,
             "--output", tmp, tmp / "qa.jar"])
        qa = tmp / "qa.apk"
        link(HERE / "AndroidManifest.xml", qa)
        with zipfile.ZipFile(qa, "a") as apk:
            apk.write(tmp / "classes.dex", "classes.dex")
            apk.write(assets / "fixture.apk", "assets/fixture.apk")
            apk.writestr("assets/fixture.sha256", hashlib.sha256((assets / "fixture.apk").read_bytes()).hexdigest())
        sign(qa, output)

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    for name in ["sdk", "jdk", "keystore", "output"]:
        p.add_argument("--" + name, type=Path, required=True)
    p.add_argument("--build-tools", default="34.0.0")
    args = p.parse_args()
    if not os.environ.get("ZP_KEYSTORE_PASS"):
        p.error("Set ZP_KEYSTORE_PASS for the development keystore signing this emulator's launcher")
    build(args.sdk.resolve(), args.jdk.resolve(), args.build_tools, args.keystore.resolve(), args.output.resolve())
