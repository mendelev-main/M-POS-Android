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
    # A failed dump must never reuse a previous screen and tap coordinates from a stale window.
    adb("shell", "rm", "-f", "/sdcard/update-window.xml", check=False)
    adb("shell", "uiautomator", "dump", "/sdcard/update-window.xml", check=False)
    raw = adb("shell", "cat", "/sdcard/update-window.xml", check=False)
    if "<?xml" not in raw:
        return []  # UIAutomator may not produce a window during a process/Activity transition.
    return list(ET.fromstring(raw[raw.index("<?xml"):]).iter("node"))


_launcher_recovery_used = False


def launcher_anr_close(nodes):
    """Only the emulator's Quickstep launcher ANR, never an M-POS or generic ANR."""
    labels = [n for n in nodes if n.get("resource-id") == "android:id/alertTitle"]
    if len(labels) != 1 or labels[0].get("package") != "android" or labels[0].get("text") != "Quickstep isn't responding":
        return None
    return next((n for n in nodes if n.get("package") == "android" and n.get("resource-id") == "android:id/aerr_close"
                 and n.get("text") == "Close app" and n.get("enabled") == "true"), None)


def wait_text(pattern, timeout=75, click=False, preferred_resource_id=None):
    global _launcher_recovery_used
    print(f"E2E UI: {preferred_resource_id or pattern}; click={click}", flush=True)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            nodes = ui_nodes()
            launcher_close = launcher_anr_close(nodes)
            if launcher_close is not None and not _launcher_recovery_used:
                require_emulator(adb("get-serialno").strip())
                reports = ROOT / "app/build/reports/update-e2e"
                reports.mkdir(parents=True, exist_ok=True)
                (reports / "quickstep-anr.xml").write_text(adb("shell", "cat", "/sdcard/update-window.xml"))
                capture = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=True, timeout=20)
                (reports / "quickstep-anr.png").write_bytes(capture.stdout)
                _launcher_recovery_used = True
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", launcher_close.get("bounds", "")))
                print("E2E INFRA: closing Quickstep launcher ANR once; M-POS assertions remain mandatory", flush=True)
                adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                continue
            preferred = [n for n in nodes if preferred_resource_id and n.get("resource-id") == preferred_resource_id]
            # If the modern control exists but is disabled, never click a legacy text elsewhere.
            for node in preferred or nodes:
                matches = bool(preferred) or re.search(pattern, (node.get("text", "") or node.get("content-desc", "")), re.I)
                if matches and node.get("enabled", "true") == "true":
                    if click:
                        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
                        adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    return node.get("text") or node.get("content-desc")
        except (ET.ParseError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError(f"UI control not found: {preferred_resource_id or pattern}")


def wait_navigation(route, timeout=75, click=True):
    # Stable IDs for the new header, exact legacy labels for APKs published before NAV01.
    legacy = {"workspace": r"^(Рабочая зона|M POS)$", "shift": r"^Смена$", "settings": r"^Настройки$"}
    return wait_text(legacy[route], timeout=timeout, click=click, preferred_resource_id="topNav-"+route)


def scroll_catalog_to_product(name):
    """A new seed can push the preserved row below the fold; absence must still fail."""
    pattern = "^" + re.escape(name) + "$"
    wait_text("^Товары$")
    for _ in range(12):
        nodes = ui_nodes()
        for node in nodes:
            if re.search(pattern, node.get("text", "")):
                return node.get("text") or node.get("content-desc")
        lists = [n for n in nodes if n.get("package") == PACKAGE and n.get("scrollable") == "true" and n.get("resource-id") == "catalogProducts"]
        assert len(lists) == 1, "Expected one catalog list before scrolling to preserved product"
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", lists[0].get("bounds", "")))
        assert x2 > x1 and y2 > y1, "Invalid catalog list bounds"
        x = (x1 + x2) // 2
        inset = (y2 - y1) // 6
        adb("shell", "input", "swipe", str(x), str(y2-inset), str(x), str(y1+inset), "400")
        time.sleep(0.5)
    raise AssertionError(f"Preserved product not found after catalog scroll: {name}")


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
            wait_navigation("settings", timeout=12)
            wait_text("^Обновления приложения$", timeout=12)
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
    expected_product = None
    expected_home = None
    expected_shift = None
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
                        "versionName": "0.0.2-e2e-debug", "minSdk": 36, "apkUrl": f"http://10.0.2.2:{PORT}/mpos-native.apk",
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
                # Gradle UTP uninstalls target/test apps after its suite. Seed via the real UI
                # under an explicit instrumentation lifetime, then keep the target for replacement.
                test_apk = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
                adb("install", "-r", str(test_apk))
                # UTP removes apps after connected tests. Capture scale evidence in an explicit
                # instrumentation lifetime, just like the existing persistent update seed.
                scale = adb("shell", "am", "instrument", "-w", "-e", "class", "com.mendelev.mpos.WorkspaceScaleUiTest",
                            PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=180)
                assert "OK (2 tests)" in scale and "FAILURES" not in scale, scale[-4000:]
                reports = ROOT / "app/build/reports/update-e2e"
                reports.mkdir(parents=True, exist_ok=True)
                for name in ("performance.json", "workspace-portrait150.png", "workspace-landscape150.png"):
                    captured = subprocess.run(["adb", "exec-out", "run-as", PACKAGE, "cat", f"files/qa/{name}"], capture_output=True, timeout=20, check=True)
                    if name.endswith(".json"):
                        assert json.loads(captured.stdout)["products"] == 2000
                    else:
                        assert captured.stdout.startswith(b"\x89PNG"), "Invalid adaptive screenshot"
                    (reports / name).write_bytes(captured.stdout)
                # Keep updater fixtures independent of the performance/rotation fixture data.
                adb("uninstall", PACKAGE)
                adb("install", str(base))
                adb("install", "-r", str(test_apk))
                financial = adb("shell", "am", "instrument", "-w", "-e", "class", "com.mendelev.mpos.SaleUpgradeTest,com.mendelev.mpos.SplitUpgradeTest,com.mendelev.mpos.RefundUpgradeTest,com.mendelev.mpos.SupplierUpgradeTest,com.mendelev.mpos.PurchaseUpgradeTest,com.mendelev.mpos.PurchaseOrderUpgradeTest,com.mendelev.mpos.PurchaseOrderMutationUpgradeTest,com.mendelev.mpos.ReceivingUpgradeTest,com.mendelev.mpos.ReceivingPostingUpgradeTest,com.mendelev.mpos.StandaloneReceivingUpgradeTest",
                                PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=60)
                assert "OK (10 tests)" in financial and "FAILURES" not in financial, financial[-4000:]
                # Recover only the already-supported, exact Quickstep infrastructure ANR before UI captures.
                # Unknown/system/M-POS dialogs are not dismissed by wait_text.
                adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.mendelev.mpos.MainActivity")
                wait_navigation("workspace", click=False)
                result = adb("shell", "am", "instrument", "-w", "-e", "class", "com.mendelev.mpos.RefundUiTest,com.mendelev.mpos.SplitSaleUiTest,com.mendelev.mpos.SaleUiTest,com.mendelev.mpos.CatalogExtrasUiTest,com.mendelev.mpos.LaunchTest,com.mendelev.mpos.ParkedOrdersUiTest,com.mendelev.mpos.SupplierUiTest,com.mendelev.mpos.PurchaseUiTest,com.mendelev.mpos.PurchaseOrderUiTest,com.mendelev.mpos.PurchaseOrderMutationUiTest,com.mendelev.mpos.ReceivingUiTest,com.mendelev.mpos.ReceivingPostingUiTest,com.mendelev.mpos.StandaloneReceivingUiTest,com.mendelev.mpos.WorkspaceUiTest,com.mendelev.mpos.ShiftUiTest",
                             PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=180)
                assert "OK (15 tests)" in result and "FAILURES" not in result, result[-4000:]
                reports = ROOT / "app/build/reports/update-e2e"
                reports.mkdir(parents=True, exist_ok=True)
                expected_shift = adb("shell", "run-as", PACKAGE, "cat", "files/qa/shiftId.txt").strip()
                assert expected_shift, "UI setup must preserve the active shift"
                for name in ("shift-open.png", "shift-closed.png"):
                    captured = subprocess.run(["adb", "exec-out", "run-as", PACKAGE, "cat", f"files/qa/{name}"], capture_output=True, timeout=20, check=True)
                    assert captured.stdout.startswith(b"\x89PNG")
                    (reports / name).write_bytes(captured.stdout)
                expected_home = adb("shell", "run-as", PACKAGE, "cat", "files/qa/homeTileName.txt").strip()
                assert expected_home, "UI setup must preserve a custom home tile"
                expected_product = adb("shell", "run-as", PACKAGE, "cat", "files/qa/productName.txt").strip()
                assert expected_product, "UI setup must create a real catalog record"
                expected_workspace_line = adb("shell", "run-as", PACKAGE, "cat", "files/qa/workspaceLineName.txt").strip()
                assert expected_workspace_line, "UI setup must create a real persistent current cart"
                expected_parked_label = adb("shell", "run-as", PACKAGE, "cat", "files/qa/parkedLabel.txt").strip()
                assert expected_parked_label, "UI setup must create a real parked order"
                for name in ("refund-confirm", "refund-result", "refund-returned-receipt", "split-confirm", "split-progress", "sale-preview", "sale-receipt", "sale-history", "catalog", "product-editor", "shared-dialog", "launcher-icon", "extras-editor", "category-editor", "workspace", "workspace-folder", "workspace-configuration", "workspace-home", "order-details", "parked-swap", "parked-orders", "supplier-card", "supplier-list", "purchase-draft", "purchase-order", "purchase-history", "purchase-order-edit", "purchase-order-edited", "purchase-order-cancelled", "receiving-draft", "receiving-preview", "receiving-post-confirm", "receiving-posted", "standalone-draft", "standalone-posted"):
                    captured = subprocess.run(["adb", "exec-out", "run-as", PACKAGE, "cat", f"files/qa/{name}.png"], capture_output=True, timeout=20, check=True)
                    assert captured.stdout.startswith(b"\x89PNG"), "Invalid screenshot artifact"
                    (reports / f"{name}.png").write_bytes(captured.stdout)
                adb("uninstall", PACKAGE + ".test")
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
            if not production:
                wait_text("^Назад$", click=True)
                scroll_catalog_to_product(expected_product)
                wait_text("^8 шт$")
                (reports / "updated-catalog.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20, check=True).stdout)
                print("CATALOG UPDATE PASSED: Room product and stock=8 survived actual package replacement", flush=True)
                wait_navigation("workspace")
                wait_text(r"(?:^|\n)" + re.escape(expected_workspace_line) + r"(?:$|\n)")
                wait_text("^15,00 BYN$")
                wait_text(r"(?:^|\n)3,00 BYN / шт · ×5(?:$|\n)")
                (reports / "updated-workspace.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20, check=True).stdout)
                print("WORKSPACE UPDATE PASSED: current cart configuration, quantity=5 and total=15 survived package replacement", flush=True)
                wait_text("^Отложенные$", click=True, preferred_resource_id="workspaceParked")
                wait_text("^" + re.escape(expected_parked_label) + "$")
                wait_text("^Доставка$")
                wait_text("^5 BYN$")
                (reports / "updated-parked.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20, check=True).stdout)
                print("PARKED UPDATE PASSED: delivery order label, type and total5 survived package replacement", flush=True)
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                wait_text(re.escape(expected_home))
                wait_text("^Раскладка$", click=True, preferred_resource_id="workspaceEdit")
                wait_text(r"(?:^|\n)Место 8(?:$|\n)")
                (reports / "updated-home.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20, check=True).stdout)
                print("HOME UPDATE PASSED: custom product tile and slot8 survived package replacement", flush=True)
                wait_navigation("shift")
                wait_text("^Смена открыта$")
                wait_text(re.escape(expected_shift))
                wait_text("^Наличные при открытии: 140,00 BYN$")
                (reports / "updated-shift.png").write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=20, check=True).stdout)
                print("SHIFT UPDATE PASSED: active shift identity and opening140 BYN survived package replacement", flush=True)
                adb("install", "-r", str(test_apk))
                financial = adb("shell", "am", "instrument", "-w", "-e", "class", "com.mendelev.mpos.SaleUpgradeTest,com.mendelev.mpos.SplitUpgradeTest,com.mendelev.mpos.RefundUpgradeTest,com.mendelev.mpos.SupplierUpgradeTest,com.mendelev.mpos.PurchaseUpgradeTest,com.mendelev.mpos.PurchaseOrderUpgradeTest,com.mendelev.mpos.PurchaseOrderMutationUpgradeTest,com.mendelev.mpos.ReceivingUpgradeTest,com.mendelev.mpos.ReceivingPostingUpgradeTest,com.mendelev.mpos.StandaloneReceivingUpgradeTest",
                                "-e", "saleUpgradeMode", "verify", PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=60)
                assert "OK (10 tests)" in financial and "FAILURES" not in financial, financial[-4000:]
                adb("uninstall", PACKAGE + ".test")
                print("STANDALONE RECEIVING UPDATE PASSED: independent invoice/slot, posted stock/cost, legacy history and original replays survived APK replacement", flush=True)
                print("RECEIVING POST UPDATE PASSED: immutable invoice, stock/cost, terminal order, tombstone and command replay survived APK replacement", flush=True)
                print("RECEIVING DRAFT UPDATE PASSED: invoice facts, baselines, preview and replay preserved without stock or order posting", flush=True)
                print("PURCHASE ORDER MUTATION UPDATE PASSED: previous request history, cancellation actor/status, no stock writes and old edit replay preserved", flush=True)
                print("PURCHASE ORDER UPDATE PASSED: pending snapshots, raw quantities, cleared draft and replay survived APK replacement", flush=True)
                print("SUPPLIER UPDATE PASSED: registry, product links, tombstones, audit and replay survived APK replacement", flush=True)
                print("SPLIT UPDATE PASSED: dedicated partial progress2, stable IDs, unpaid tender and replay survived APK replacement")
                print("REFUND UPDATE PASSED: dedicated full delivery refund, exact historical restock, closed shift and replay survived APK replacement")
                print("SALE UPDATE PASSED: dedicated Room receipt snapshot, ledger, result, shift cash and cleared draft survived APK replacement; replay did not duplicate sale", flush=True)

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
        for name, command in {
            "last-anr.txt": ("shell", "dumpsys", "activity", "lastanr"),
            "cpuinfo.txt": ("shell", "dumpsys", "cpuinfo"),
            "meminfo.txt": ("shell", "dumpsys", "meminfo"),
            "events.txt": ("logcat", "-b", "events", "-d"),
            "display.txt": ("shell", "dumpsys", "display"),
        }.items():
            try:
                (reports / name).write_text(adb(*command, check=False, timeout=20))
            except Exception as error:
                (reports / name).write_text(str(error))
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
