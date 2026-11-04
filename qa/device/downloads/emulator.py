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
        return subprocess.check_output(self.adb + list(args), text=True, encoding="utf-8", timeout=200, errors="replace")

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
        if phase != "inflight-resume":
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
        self.key("KEYCODE_DPAD_RIGHT")  # Leave touch mode introduced by the system setup UI.
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
        # Returning from instrumentation can leave the target stopped. Show it once before
        # Android's TV settings enumerates eligible source applications.
        self.shell("am", "start", "-W", "-n", "tv.calmhome.launcher/.MainActivity")
        self.shell("am", "start", "-W", "-a", "android.settings.MANAGE_UNKNOWN_APP_SOURCES", "-d", "package:tv.calmhome.launcher")
        for _ in range(12):
            t = self.tree()
            options = list(t.iter("node"))
            toggles = [n for n in options if n.get("checkable") == "true"]
            toggle = toggles[0] if len(toggles) == 1 else None
            labels = [n for n in options if n.get("text", "").casefold() in ("allow from this source", "zeropulse tv")]
            if toggles and self.contains(t, "ZeroPulse TV"):
                # On a list of apps, find the switch in the ZeroPulse row rather than changing another app.
                for label in labels:
                    if label.get("text") != "ZeroPulse TV":
                        continue
                    for row in t.iter("node"):
                        if label in list(row) or label in list(row.iter())[1:]:
                            matches = [n for n in row.iter("node") if n.get("checkable") == "true"]
                            if len(matches) == 1:
                                toggle = matches[0]
                if toggle is None:
                    raise RuntimeError("Unable to isolate the ZeroPulse-specific system switch.")
                if toggle.get("checked") != "true":
                    self.tap(toggle)
                self.capture("05-system-source-permission")
                self.key("KEYCODE_BACK")
                return
            if labels:
                self.tap(labels[-1])
            else:
                time.sleep(1)
        self.capture("05-source-settings-failure")
        raise RuntimeError("Could not locate ZeroPulse's installation-source switch in Android settings")

    def click_text(self, label, contains=False, pkg="tv.calmhome.launcher", timeout=16):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for n in self.tree().iter("node"):
                text = n.get("text", "").strip()
                if n.get("package") == pkg and (label in text if contains else label == text):
                    self.tap(n)
                    return
            time.sleep(.3)
        raise RuntimeError("No actionable UI label: " + label)

    def consent(self, phase, choice):
        # Seed through the actual native job. Exercise installation via the owner's normal
        # foreground controls, outside instrumentation (which stops its target when it exits).
        seed = self.phase("seed")
        (self.out / (phase + "-seed.txt")).write_text(seed, encoding="utf-8")
        id_ = re.search(r"downloadId=(\d+)", seed)[1]
        self.shell("am", "start", "-W", "-n", "tv.calmhome.launcher/.MainActivity", "--es", "zp_open", "downloads")
        self.click_text("Manage Downloads")
        self.click_text("Ready for installation", contains=True)
        self.capture(phase + "-inspected-apk")
        self.click_text("Install with Android approval")
        deadline = time.monotonic() + 60
        prompt_seen = False
        continued = False
        while time.monotonic() < deadline:
            t = self.tree()
            if self.contains(t, "ZeroPulse QA sample"):
                choices = (choice, "update") if choice == "install" else (choice,)
                button = next((n for n in t.iter("node") if n.get("text", "").strip().casefold() in choices and
                               n.get("package", "").endswith(("packageinstaller", "permissioncontroller"))), None)
                if button is not None:
                    self.capture(phase + "-android-confirmation", t)
                    self.tap(button)
                    prompt_seen = True
                    break
            if not continued and time.monotonic() > deadline - 53 and self.contains(t, "Continue Android approval"):
                self.click_text("Continue Android approval")
                continued = True
        if not prompt_seen:
            self.capture(phase + "-missing-system-prompt")
            raise RuntimeError("The system confirmation dialog did not appear.")
        deadline = time.monotonic() + 70
        while time.monotonic() < deadline:
            t = self.tree()
            if phase == "install-allow" and self.contains(t, "Open app"):
                self.capture(phase + "-confirmed", t)
                self.click_text("Add to dock")
                self.click_text("Open app")
                time.sleep(1)
                foreground = self.shell("dumpsys", "activity", "activities")
                if not any("topResumedActivity" in line and "tv.calmhome.downloadfixture/" in line for line in foreground.splitlines()):
                    raise RuntimeError("Open app did not launch the installed TV fixture")
                self.capture("08-fixture-open")
                self.key("KEYCODE_BACK")
                break
            if phase == "install-deny" and self.contains(t, "Retry"):
                self.capture(phase + "-confirmed", t)
                break
            time.sleep(.5)
        else:
            self.capture(phase + "-missing-result")
            raise RuntimeError("No terminal installation result appeared in the launcher.")
        self.phase(phase, "-e", "id", id_)

    def inflight_reboot(self):
        before = int(self.shell("settings", "get", "global", "boot_count").strip())
        self.grant_fixtures()
        log = self.out / "inflight-seed.txt"
        with log.open("w", encoding="utf-8") as stream:
            process = subprocess.Popen(self.adb + ["shell", "am", "instrument", "-w", "-e", "phase",
                    "inflight-seed", "tv.calmhome.downloadqa/.DownloadRunner"], stdout=stream, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 100
                while time.monotonic() < deadline:
                    lines = log.read_text(encoding="utf-8")
                    match = re.search(r"downloadId=(\d+)", lines)
                    if match and "bytesBefore=1024" in lines and "INSTRUMENTATION_STATUS_CODE: 1" in lines:
                        break
                    if process.poll() is not None:
                        raise RuntimeError("Partial transfer did not reach checkpoint: " + lines)
                    time.sleep(.2)
                else:
                    raise RuntimeError("Timeout waiting for partial transfer checkpoint")
                download_id = match[1]
                self.command("reboot")
                self.online()
            finally:
                if process.poll() is None:
                    process.terminate()
                process.wait(timeout=10)
        after = int(self.shell("settings", "get", "global", "boot_count").strip())
        if after <= before:
            raise RuntimeError("Android boot count did not advance")
        # Probe only the fixture process: allow the OS job to run before opening any launcher UI,
        # instrumenting the launcher or providing fresh grants on the new boot.
        deadline = time.monotonic() + 140
        while time.monotonic() < deadline:
            probe = self.shell("content", "call", "--uri", "content://tv.calmhome.downloadqa.samples", "--method", "probe")
            if re.search(rf"reopened={after}\b", probe):
                break
            time.sleep(2)
        else:
            raise RuntimeError("Android did not reopen the partially transferred fixture after boot")
        time.sleep(3)  # Allow the short APK to finish inspection before instrumenting the process.
        result = self.phase("inflight-resume", "-e", "id", download_id)
        (self.out / "inflight-result.txt").write_text(
            f"Boot count {before} -> {after}\nDownload ID {download_id}\nCheckpoint 1024 bytes\n{probe}\n{result}", encoding="utf-8")
        print("In-flight reboot recovery: PASS", flush=True)

    def restart_recovery(self):
        seed = self.phase("seed")
        (self.out / "reboot-seed.txt").write_text(seed, encoding="utf-8")
        match = re.search(r"downloadId=(\d+)", seed)
        if not match:
            raise RuntimeError(seed)
        self.command("reboot")
        self.online()
        self.phase("resume", "-e", "id", match[1])

    def run(self):
        outcomes = []
        def test(label, action):
            try:
                action()
                outcomes.append(label + ": PASS")
                print(outcomes[-1], flush=True)
                return True
            except Exception as error:
                outcomes.append(label + ": FAIL " + str(error))
                print(outcomes[-1], flush=True)
                return False

        test("Prepare disposable fixture", lambda: self.phase("prepare"))
        test("TV D-pad", self.visual)
        test("JobScheduler, APK parser and actual cancel/retry", lambda: self.phase("verify"))
        test("HTTPS release transfer and self-update refusal", lambda: self.phase("https"))
        test("Persistence across emulator reboot", self.restart_recovery)
        if test("Android source settings", self.allow_source_in_settings):
            test("Android installer cancellation", lambda: self.consent("install-deny", "cancel"))
            test("Android approval, package detection, Open app and Add to dock", lambda: self.consent("install-allow", "install"))
        (self.out / "gfxinfo.txt").write_text(self.shell("dumpsys", "gfxinfo", "tv.calmhome.launcher"), encoding="utf-8")
        (self.out / "memory.txt").write_text(self.shell("dumpsys", "meminfo", "tv.calmhome.launcher"), encoding="utf-8")
        (self.out / "crash-log.txt").write_text(self.command("logcat", "-b", "crash", "-d"), encoding="utf-8")
        (self.out / "results.txt").write_text("\n".join(outcomes) + "\n", encoding="utf-8")
        if any(": FAIL" in line for line in outcomes):
            raise SystemExit(1)

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--adb", default="adb")
    p.add_argument("--serial", required=True)
    p.add_argument("--evidence", required=True, type=Path)
    p.add_argument("--inflight-only", action="store_true", help="run only the gated mid-transfer reboot regression")
    opts = p.parse_args()
    emulator = Emulator(opts.adb, opts.serial, opts.evidence)
    if opts.inflight_only:
        emulator.inflight_reboot()
    else:
        emulator.run()
