#!/usr/bin/env python3
"""Real process boundary for native restore. Emulator debug data only, after upgrade assertions passed."""
import importlib.util
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("update_driver", Path(__file__).with_name("update-e2e.py"))
u = importlib.util.module_from_spec(spec)
spec.loader.exec_module(u)


def main():
    u.require_emulator(u.adb("get-serialno").strip())
    assert u.adb("shell", "getprop", "ro.build.version.sdk").strip() == "36"
    assert u.adb("shell", "getprop", "ro.hardware").strip() in {"ranchu", "goldfish"}
    reports = ROOT / "app/build/reports/native-restore-e2e"
    reports.mkdir(parents=True, exist_ok=True)
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    tests = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    u.adb("uninstall", u.PACKAGE)
    u.adb("install", str(apk))
    u.adb("install", "-r", str(tests))
    runner = u.PACKAGE + ".test/com.mendelev.mpos.MposTestRunner"
    seed = u.adb("shell", "am", "instrument", "-w", "-e", "nativeRestoreMode", "seed", runner, timeout=120)
    (reports / "seed.txt").write_text(seed)
    assert "NATIVE RESTORE PROCESS SEED PASSED" in seed and "FAILED" not in seed, seed[-5000:]
    u.adb("shell", "am", "force-stop", u.PACKAGE)
    u.adb("shell", "am", "start", "-n", u.PACKAGE + "/com.mendelev.mpos.MainActivity")
    u.wait_text("Восстановление завершено.*Администрирование закрыто")
    (reports / "fresh-process-recovered.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True, timeout=20).stdout)
    u.wait_text("^Продолжить$", click=True)
    u.wait_text("^Native E2E source$")
    verify = u.adb("shell", "am", "instrument", "-w", "-e", "nativeRestoreMode", "verify", runner, timeout=120)
    (reports / "verify.txt").write_text(verify)
    assert "NATIVE RESTORE PROCESS VERIFY PASSED" in verify and "FAILED" not in verify, verify[-5000:]
    print("NATIVE RESTORE E2E PASSED: old process sealed; fresh process selected DB+photos together, admin locked, paid40 preserved, remaining60 completed once, target30 protected", flush=True)


if __name__ == "__main__":
    main()
