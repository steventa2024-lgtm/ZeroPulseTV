"""Real UI and package tests restricted to the explicitly selected disposable Android Emulator."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

class Emulator:
    def __init__(self, adb, serial, evidence):
        if not re.fullmatch(r"emulator-\d+", serial):
            raise ValueError("Use an explicit emulator serial.")
        self.adb = [str(adb), "-s", serial]
        self.out = evidence
        self.out.mkdir(parents=True, exist_ok=True)
        if self.shell("getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("Selected target is not an Android Emulator.")

    def command(self, *args):
        return subprocess.check_output(self.adb + list(args), text=True, timeout=200, errors="replace")

    def shell(self, *args):
        return self.command("shell", *args)

    def tree(self):
        # uiautomator waits for idle and may briefly fail during activity transitions.
        for _ in range(3):
            self.shell("uiautomator", "dump", "/sdcard/zp-qa.xml")
            raw = self.shell("cat", "/sdcard/zp-qa.xml")
            try:
                return ET.fromstring(raw.strip())
            except ET.ParseError:
                time.sleep(.5)
        raise RuntimeError("Android did not produce a UI hierarchy")

    def capture(self, label, tree=None):
        (self.out / (label + ".png")).write_bytes(subprocess.check_output(self.adb + ["exec-out", "screencap", "-p"]))
        t = self.tree() if tree is None else tree
        (self.out / (label + ".xml")).write_bytes(ET.tostring(t, encoding="utf-8"))
        return t

    def grant_fixtures(self):
        result = self.shell("content", "call", "--uri", "content://tv.calmhome.downloadqa.samples", "--method", "grant")
        if "Bundle" not in result:
            raise RuntimeError("Missing test document grants: " + result)

    def phase(self, phase, *extra):
        self.grant_fixtures()
        result = self.shell("am", "instrument", "-w", "-e", "phase", phase, *extra, "tv.calmhome.downloadqa/.DownloadRunner")
        (self.out / (phase + ".txt")).write_text(result, encoding="utf-8")
        if "result=PASS " + phase not in result:
            raise RuntimeError(result)
        return result

    @staticmethod
    def contains(t, value):
        return any(value.casefold() in " ".join([n.get("text", ""), n.get("content-desc", "")]).casefold() for n in t.iter())

    @staticmethod
    def focus(t):
        for n in t.iter("node"):
            if n.get("focused") == "true":
                return n.get("content-desc") or n.get("text") or n.get("bounds")
        return None

    def key(self, key):
        self.shell("input", "keyevent", key)
        time.sleep(.45)

    def tap(self, node):
        bounds = [int(x) for x in re.findall(r"\d+", node.attrib["bounds"])]
        if len(bounds) != 4:
            raise RuntimeError("Missing Android UI button bounds")
        self.shell("input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
        time.sleep(.7)

    def visual(self):
        component = "tv.calmhome.launcher/.MainActivity"
        initial = self.shell("am", "start", "-W", "-n", component)
        (self.out / "startup.txt").write_text(initial, encoding="utf-8")
        time.sleep(1.5)
        home = self.capture("01-home")
        if not self.contains(home, "Downloads"):
            raise RuntimeError("Home navigation not visible")
        self.shell("am", "start", "-W", "-n", component, "--es", "zp_open", "downloads")
        time.sleep(1)
        center = self.capture("02-download-center")
        if not self.contains(center, "Download Center") or not self.contains(center, "Live Sports"):
            raise RuntimeError("Download Center missing")
        before = self.focus(center)
        self.key("KEYCODE_DPAD_RIGHT")
        movies = self.capture("03-download-center-dpad-right")
        after = self.focus(movies)
        if not before or not after or before == after or "Movies" not in after:
            raise RuntimeError(f"Unexpected D-pad movement: {before} -> {after}")
        self.key("KEYCODE_DPAD_CENTER")
        opened = self.capture("04-category")
        if not self.contains(opened, "Add a trusted source"):
            raise RuntimeError("Category empty state not visible")
        self.key("KEYCODE_BACK")
        if self.focus(self.tree()) != after:
            raise RuntimeError("Back did not restore the selected category")
        self.key("KEYCODE_BACK")
        if not self.contains(self.tree(), "Home"):
            raise RuntimeError("Back did not return to Home")

    def online(self):
        self.command("wait-for-device")
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline and self.shell("getprop", "sys.boot_completed").strip() != "1":
            time.sleep(2)
        if self.shell("getprop", "sys.boot_completed").strip() != "1":
            raise RuntimeError("Emulator reboot timed out")
        self.shell("wm", "dismiss-keyguard")
        time.sleep(2)

    def allow_source_in_settings(self):
        self.shell("am", "start", "-W", "-a", "android.settings.MANAGE_UNKNOWN_APP_SOURCES", "-d", "package:tv.calmhome.launcher")
        for _ in range(5):
            t = self.tree()
            options = list(t.iter("node"))
            toggle = next((n for n in options if n.get("checkable") == "true" and n.get("clickable") == "true"), None)
            labels = [n for n in options if n.get("text", "").casefold() in ("allow from this source", "zeropulse tv")]
            if toggle is not None and self.contains(t, "ZeroPulse TV"):
                # On a list of apps, find the switch in the ZeroPulse row rather than changing another app.
                for label in labels:
                    if label.get("text") != "ZeroPulse TV":
                        continue
                    for row in t.iter("node"):
                        if label in list(row) or label in list(row.iter())[1:]:
                            matches = [n for n in row.iter("node") if n.get("checkable") == "true"]
                            if len(matches) == 1:
                                toggle = matches[0]
                if toggle.get("checked") != "true":
                    self.tap(toggle)
                self.capture("05-system-source-permission")
                self.key("KEYCODE_BACK")
                return
            if labels:
                self.tap(labels[-1])
            else:
                break
        self.capture("05-source-settings-failure")
        raise RuntimeError("Could not locate ZeroPulse's installation-source switch in Android settings")

    def consent(self, phase, choice):
        self.grant_fixtures()
        log = self.out / (phase + ".txt")
        args = self.adb + ["shell", "am", "instrument", "-w", "-e", "phase", phase, "tv.calmhome.downloadqa/.DownloadRunner"]
        with log.open("w", encoding="utf-8") as dest:
            proc = subprocess.Popen(args, stdout=dest, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 120
                while proc.poll() is None and time.monotonic() < deadline:
                    t = self.tree()
                    if self.contains(t, "ZeroPulse QA sample"):
                        button = next((n for n in t.iter("node") if n.get("text", "").strip().casefold() == choice and
                                       n.get("package", "").endswith(("packageinstaller", "permissioncontroller"))), None)
                        if button is not None:
                            self.capture(phase + "-android-confirmation", t)
                            self.tap(button)
                            break
                    time.sleep(.5)
                proc.wait(timeout=180)
            finally:
                if proc.poll() is None:
                    proc.terminate()
        if "result=PASS " + phase not in log.read_text(encoding="utf-8"):
            self.capture(phase + "-failure")
            raise RuntimeError(log.read_text(encoding="utf-8"))

    def run(self):
        try:
            self.visual()
            self.phase("verify")
            seed = self.phase("seed")
            match = re.search(r"downloadId=(\d+)", seed)
            if not match:
                raise RuntimeError(seed)
            self.command("reboot")
            self.online()
            self.phase("resume", "-e", "id", match[1])
            self.allow_source_in_settings()
            self.consent("install-deny", "cancel")
            self.consent("install-allow", "install")
            self.shell("am", "start", "-W", "-n", "tv.calmhome.downloadfixture/android.app.Activity")
            self.capture("08-fixture-open")
        finally:
            (self.out / "gfxinfo.txt").write_text(self.shell("dumpsys", "gfxinfo", "tv.calmhome.launcher"), encoding="utf-8")
            (self.out / "memory.txt").write_text(self.shell("dumpsys", "meminfo", "tv.calmhome.launcher"), encoding="utf-8")
            (self.out / "crash-log.txt").write_text(self.command("logcat", "-b", "crash", "-d"), encoding="utf-8")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--adb", default="adb")
    p.add_argument("--serial", required=True)
    p.add_argument("--evidence", required=True, type=Path)
    opts = p.parse_args()
    Emulator(opts.adb, opts.serial, opts.evidence).run()
