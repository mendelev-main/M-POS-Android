#!/usr/bin/env python3
"""Copy the reviewed iPad web runtime without changing business code."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path, help="Path to prilavok-pos-ipad repository")
args = parser.parse_args()
source = args.source.resolve()
root = Path(__file__).resolve().parents[1]
target = root / "app/src/main/assets/pos"
ios = source / "PrilavokPOS"

if not (ios / "pos.html").is_file():
    raise SystemExit(f"iPad POS not found at {source}")

source_html = (ios / "pos.html").read_text()
anchors = ['<script src="Web/js/core/storage.js"></script>',
           '<script src="network-printer.js"></script>',
           '<script>loadAll().then(()=>startAvailabilityRecovery());</script>']
if any(source_html.count(anchor) != 1 for anchor in anchors):
    raise SystemExit("Reviewed source initialization changed; inspect Android adapters before synchronization")
product_file = "Web/js/features/product-persistence.js"
product_legacy = "await window.PrilavokCore.Storage.set('products',nextProducts);"
product_hook = "await (window.MPosCore?.ProductEditorCommands?.enabled() ? window.MPosCore.ProductEditorCommands.commit({version:1,editingId:editingId??null,expected:storageSnapshot(state.products),nextProducts,grants:window.MPosCore.EditorAuthorization?.grants()||{}}) : window.PrilavokCore.Storage.set('products',nextProducts));window.MPosCore?.EditorAuthorization?.clear();"
product_timeout = "    if(String(e?.message).includes('commit status is uncertain')){criticalStorageRecoveryPending=true;markStorageBroken(e);}" + "\n"
product_source = (ios / product_file).read_text()
if product_source.count(product_legacy) != 1 or product_hook in product_source:
    raise SystemExit("Reviewed product persistence changed; inspect native editor boundary before synchronization")
if source_html.count("if(!saveNetworkSettings()) return;") != 2:
    raise SystemExit("Reviewed network save callers changed; inspect native settings boundary")
adapters = ["android-bridge.js", "native-storage-shadow.js", "native-catalog-cutover.js",
            "native-network-shadow.js", "native-settings.js", "native-platform-settings.js", "native-settings-ui.js", "native-connection-tests.js", "native-workspace.js", "native-workspace-legacy.js", "native-workspace-header.js", "native-layout-ui.js", "native-overlay-lifecycle.js", "native-print-jobs.js", "native-availability.js", "native-receipts-history.js", "native-payment-command.js", "native-configured-prices.js", "native-payment-preflight.js", "native-cart-totals.js", "native-cart-preview.js", "native-order-context-queue.js", "native-delivery.js", "native-order-settings.js", "native-parked-command.js", "native-catalog-edit.js", "native-recipe-edit.js", "native-navigation.js","native-workspace-navigation.js", "native-system-back.js", "native-employee-command.js","native-company-command.js","native-catalog-delete.js","native-editor-authorization.js","native-product-web.js","native-admin-settings.js","native-admin-access.js","native-loyalty-authorization.js", "native-customer-command.js", "native-loyalty-guard.js", "native-loyalty-outbox.js", "native-web-journal.js", "native-web-sse.js", "native-catalog-transport.js", "native-supplier-command.js", "native-purchase-command.js", "native-receiving-command.js", "native-receiving-draft.js", "native-inventory-command.js", "native-warehouse.js", "native-analytics.js", "native-hall-command.js", "native-split-count.js", "native-split-amount.js", "native-split-recovery.js", "native-shift-accounting.js", "native-return-command.js", "native-cash-movement-command.js", "native-shift-lifecycle-command.js", "native-shift-reports.js", "native-shift-screen.js", "native-storage-warning.js", "native-cash-forms.js", "native-close-form.js", "native-open-form.js", "native-card-confirmation.js", "native-split-cash.js", "native-cash-payment.js"]
if any(not (target / name).is_file() for name in adapters):
    raise SystemExit("Required Android adapter is missing; no source files were changed")

bridge = (target / "android-bridge.js").read_bytes() if (target / "android-bridge.js").exists() else b""
if (target / "Web").exists():
    shutil.rmtree(target / "Web")
shutil.copytree(ios / "Web", target / "Web", ignore=shutil.ignore_patterns(".DS_Store"))
(target / product_file).write_text(product_source.replace(product_legacy, product_hook).replace("  }catch(e){\n    if(btn)", "  }catch(e){\n" + product_timeout + "    if(btn)"))
shutil.copy2(ios / "network-printer.js", target / "network-printer.js")
shutil.copy2(ios / "notification-native.js", target / "notification-native.js")

android_html = source_html.replace(
    '<script src="Web/js/core/storage.js"></script>',
    '<script src="android-bridge.js"></script>\n<script src="Web/js/core/storage.js"></script>\n'
    '<script src="native-storage-shadow.js"></script>\n<script src="native-catalog-cutover.js"></script>\n'
    '<script src="native-network-shadow.js"></script>',
).replace(
    '<script src="network-printer.js"></script>',
    '<script src="network-printer.js"></script>\n<script src="native-settings.js"></script>\n<script src="native-platform-settings.js"></script>\n<script src="native-print-jobs.js"></script>\n'
    '<script src="notification-native.js"></script>\n<script src="native-connection-tests.js"></script>\n<script src="native-settings-ui.js"></script>\n<script src="native-shift-reports.js"></script>\n<script src="native-shift-screen.js"></script>\n<script src="native-cash-forms.js"></script>\n<script src="native-close-form.js"></script>\n<script src="native-open-form.js"></script>\n<script src="native-card-confirmation.js"></script>\n<script src="native-split-cash.js"></script>\n<script src="native-cash-payment.js"></script>\n<script src="native-workspace-legacy.js"></script>\n<script src="native-workspace.js"></script>\n<script src="native-workspace-header.js"></script>\n<script src="native-layout-ui.js"></script>\n<script src="native-overlay-lifecycle.js"></script>',
).replace(
    '<script>loadAll().then(()=>startAvailabilityRecovery());</script>',
    '<script src="native-shift-accounting.js"></script>\n<script src="native-payment-command.js"></script>\n<script src="native-configured-prices.js"></script>\n<script src="native-cart-totals.js"></script>\n<script src="native-cart-preview.js"></script>\n<script src="native-order-context-queue.js"></script>\n<script src="native-delivery.js"></script>\n<script src="native-order-settings.js"></script>\n<script src="native-parked-command.js"></script>\n<script src="native-catalog-edit.js"></script>\n<script src="native-recipe-edit.js"></script>\n<script src="native-navigation.js"></script>\n<script src="native-workspace-navigation.js"></script>\n<script src="native-system-back.js"></script>\n<script src="native-employee-command.js"></script>\n<script src="native-company-command.js"></script>\n<script src="native-catalog-delete.js"></script>\n<script src="native-editor-authorization.js"></script>\n<script src="native-product-web.js"></script>\n<script src="native-admin-settings.js"></script>\n<script src="native-admin-access.js"></script>\n<script src="native-loyalty-authorization.js"></script>\n<script src="native-customer-command.js"></script>\n<script src="native-loyalty-guard.js"></script>\n<script src="native-loyalty-outbox.js"></script>\n<script src="native-web-journal.js"></script>\n<script src="native-web-sse.js"></script>\n<script src="native-catalog-transport.js"></script>\n<script src="native-supplier-command.js"></script>\n<script src="native-purchase-command.js"></script>\n<script src="native-receiving-command.js"></script>\n<script src="native-receiving-draft.js"></script>\n<script src="native-inventory-command.js"></script>\n<script src="native-warehouse.js"></script>\n<script src="native-analytics.js"></script>\n<script src="native-hall-command.js"></script>\n<script src="native-split-count.js"></script>\n<script src="native-split-amount.js"></script>\n<script src="native-split-recovery.js"></script>\n<script src="native-payment-preflight.js"></script>\n<script src="native-return-command.js"></script>\n<script src="native-cash-movement-command.js"></script>\n<script src="native-shift-lifecycle-command.js"></script>\n<script src="native-receipts-history.js"></script>\n<script src="native-availability.js"></script>\n<script src="native-storage-warning.js"></script>\n<script>loadAll().then(()=>startAvailabilityRecovery());</script>',
)
android_html = android_html.replace("if(!saveNetworkSettings()) return;", "if(!await saveNetworkSettings()) return;")
# All settings authority and UI adapters must be installed before the first loadAll call.
bootstrap = '<script>loadAll().then(()=>startAvailabilityRecovery());</script>'
android_html = android_html.replace(bootstrap + "\n", "").replace("</body>", bootstrap + "\n</body>")
(target / "pos.html").write_text(android_html)
(target / "android-bridge.js").write_bytes(bridge)

files = [ios / "pos.html", ios / "network-printer.js", ios / "notification-native.js"]
files += sorted(path for path in (ios / "Web").rglob("*") if path.is_file() and path.name != ".DS_Store")
commit = subprocess.check_output(["git", "-C", source, "rev-parse", "HEAD"], text=True).strip()
manifest = {
    "sourceRepository": "mendelev-main/prilavok-pos-ipad",
    "sourceCommit": commit,
    "files": {str(path.relative_to(ios)): sha(path) for path in files},
}
(root / "web-source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
print(f"Synced {len(files)} source files from {commit}")
