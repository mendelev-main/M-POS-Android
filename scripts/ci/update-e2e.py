#!/usr/bin/env python3
"""Drive the real Android installer from outside the app being replaced. No production endpoints."""
import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import shutil
import socketserver
import subprocess
import tempfile
import threading
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.mendelev.mpos.debug"
PORT = 18765


def adb(*args, check=True, timeout=45):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True, timeout=timeout).stdout


def ui_nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/update-window.xml", check=False)
    raw = adb("shell", "cat", "/sdcard/update-window.xml")
    return list(ET.fromstring(raw[raw.index("<?xml"):]).iter("node"))


def wait_text(pattern, timeout=75, click=False):
    print(f"E2E UI: {pattern}; click={click}", flush=True)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            for node in ui_nodes():
                if re.search(pattern, node.get("text", ""), re.I) and node.get("enabled", "true") == "true":
                    if click:
                        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
                        adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    return node.get("text")
        except (ET.ParseError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError(f"UI text not found: {pattern}")


def launch(fixture=True):
    args = ["shell", "am", "start", "-n", PACKAGE + "/com.mendelev.mpos.MainActivity"]
    if fixture:
        args += ["--es", "mpos.testUpdateUrl", f"http://10.0.2.2:{PORT}/update.json"]
    # PackageManager exposes the new version before all replacement/lifecycle work is finished.
    # Simulate a user opening the app after the installer completes, not a microsecond race.
    for attempt in range(4):
        try:
            # Do not use am -W: API 28 can retain a waiter for an Activity killed by package replacement.
            adb(*args, timeout=15)
            wait_text("^Настройки$", timeout=12, click=True)
            return
        except (AssertionError, subprocess.TimeoutExpired):
            if attempt == 3:
                raise


class UnsafeDeviceError(RuntimeError):
    pass


def require_emulator(serial):
    if not re.fullmatch(r"emulator-[0-9]+", serial):
        raise UnsafeDeviceError("This destructive acceptance test may only use a disposable Android Emulator, never a physical tablet")


def request_installation():
    wait_text("^Установить обновление$", click=True)
    # Production builds must exercise Android's first-time unknown-source permission UI.
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        nodes = ui_nodes()
        if any(re.search(r"^(Cancel|Отмена)$", node.get("text", ""), re.I) for node in nodes):
            return
        if any(re.search(r"Allow from this source|Разрешить установку", node.get("text", ""), re.I) for node in nodes):
            switches = [node for node in nodes if node.get("checkable") == "true"]
            if not switches or switches[0].get("checked") != "true":
                wait_text("Allow from this source|Разрешить установку", click=True)
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            wait_text("^Установить обновление$", click=True)
            continue
        time.sleep(1)
    raise AssertionError("Android installer/permission flow did not open")


def main():
    global PACKAGE
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-apk", type=Path)
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    production = args.base_apk is not None
    if production != (args.manifest is not None):
        parser.error("--base-apk and --manifest must be supplied together")
    require_emulator(adb("get-serialno").strip())
    if production:
        PACKAGE = "com.mendelev.mpos"
    with tempfile.TemporaryDirectory() as tmp:
        folder = Path(tmp)
        base = folder / "base.apk"
        tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
        if production:
            shutil.copy2(args.base_apk, base)
            manifest = json.loads(args.manifest.read_text())
            assert manifest["applicationId"] == PACKAGE
            badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(base)], text=True)
            base_code = int(re.search(r"versionCode='(\d+)'", badging).group(1))
            target_code = manifest["versionCode"]
            assert base_code < target_code
        else:
            shutil.copy2(ROOT / "app/build/outputs/apk/debug/app-debug.apk", base)
            env = dict(os.environ, MPOS_VERSION_CODE="2", MPOS_VERSION_NAME="0.0.2-e2e")
            subprocess.run([str(ROOT / "gradlew"), "--no-daemon", "assembleDebug"], cwd=ROOT, env=env, check=True)
            apk = folder / "mpos-native.apk"
            shutil.copy2(ROOT / "app/build/outputs/apk/debug/app-debug.apk", apk)
            signature = subprocess.check_output([str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
            cert = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", signature).group(1)
            manifest = {"schemaVersion": 1, "channel": "stable", "applicationId": PACKAGE, "versionCode": 2,
                        "versionName": "0.0.2-e2e-debug", "minSdk": 28, "apkUrl": f"http://10.0.2.2:{PORT}/mpos-native.apk",
                        "apkBytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                        "certificateSha256": cert, "notes": "Проверка обновления с сохранением данных"}
            (folder / "update.json").write_text(json.dumps(manifest))
            base_code, target_code = 1, 2
        handler = lambda *args, **kwargs: http.server.SimpleHTTPRequestHandler(*args, directory=str(folder), **kwargs)
        with socketserver.TCPServer(("127.0.0.1", PORT), handler) as server:
            threading.Thread(target=server.serve_forever, daemon=True).start()
            print(f"E2E: install base package {PACKAGE}", flush=True)
            adb("uninstall", PACKAGE, check=False)
            adb("install", str(base))
            if not production:
                adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "allow")
            if not production:
                adb("shell", "run-as", PACKAGE, "mkdir", "-p", "files")
                adb("shell", "run-as", PACKAGE, "sh", "-c", "'echo preserved-before-update > files/updater-e2e-sentinel'")
            launch(fixture=not production)
            wait_text("Доступна новая версия")
            wait_text("^Скачать обновление$", click=True)
            wait_text("Обновление готово к установке")
            # Process restart must recover the verified download without fetching it again.
            adb("shell", "am", "force-stop", PACKAGE)
            launch(fixture=not production)
            wait_text("Обновление готово к установке")
            checked = wait_text("^Последняя проверка:")
            # Tampering with a ready APK must be detected immediately before granting installer access.
            if not production:
                adb("shell", "run-as", PACKAGE, "sh", "-c", "'echo corrupted > files/updates/candidate.apk'")
                wait_text("^Установить обновление$", click=True)
                wait_text("Размер APK не совпадает")
                assert f"versionCode={base_code} " in adb("shell", "dumpsys", "package", PACKAGE)
                wait_text("^Скачать обновление$", click=True)
                wait_text("Обновление готово к установке")
            request_installation()
            wait_text("^(Cancel|Отмена)$", click=True)
            wait_text("Установка не завершена")
            assert f"versionCode={base_code} " in adb("shell", "dumpsys", "package", PACKAGE)
            request_installation()
            wait_text("^(Update|Install|Обновить|Установить)$", click=True)
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline:
                if f"versionCode={target_code} " in adb("shell", "dumpsys", "package", PACKAGE):
                    break
                time.sleep(1)
            else:
                raise AssertionError("System installer did not update package")
            if not production:
                assert "preserved-before-update" in adb("shell", "run-as", PACKAGE, "cat", "files/updater-e2e-sentinel")
            launch(fixture=False)
            wait_text("Установлена версия " + re.escape(manifest["versionName"]))
            wait_text("Приложение успешно обновлено")
            if production:
                assert wait_text("^Последняя проверка:") == checked
            reports = ROOT / "app/build/reports/update-e2e"
            reports.mkdir(parents=True, exist_ok=True)
            (reports / "updated-settings.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20).stdout)
            print(f"UPDATE E2E PASSED: package={PACKAGE}, check/download/restore/cancel/system install {base_code}→{target_code}; app-private data preserved")
            server.shutdown()


if __name__ == "__main__":
    try:
        main()
    except UnsafeDeviceError:
        raise
    except Exception:
        reports = ROOT / "app/build/reports/update-e2e"
        reports.mkdir(parents=True, exist_ok=True)
        try:
            (reports / "logcat.txt").write_text(adb("logcat", "-d", "-t", "1000", check=False))
        except Exception as error:
            (reports / "capture-error.txt").write_text(str(error))
        try:
            (reports / "screen.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20).stdout)
        except Exception:
            pass
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/update-window.xml", check=False)
            (reports / "window.xml").write_text(adb("shell", "cat", "/sdcard/update-window.xml", check=False))
        except Exception:
            pass
        raise
