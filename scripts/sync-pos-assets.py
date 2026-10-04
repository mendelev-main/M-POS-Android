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

bridge = (target / "android-bridge.js").read_bytes() if (target / "android-bridge.js").exists() else b""
if (target / "Web").exists():
    shutil.rmtree(target / "Web")
shutil.copytree(ios / "Web", target / "Web", ignore=shutil.ignore_patterns(".DS_Store"))
shutil.copy2(ios / "network-printer.js", target / "network-printer.js")
shutil.copy2(ios / "notification-native.js", target / "notification-native.js")

source_html = (ios / "pos.html").read_text()
android_html = source_html.replace(
    '<script src="Web/js/core/storage.js"></script>',
    '<script src="android-bridge.js"></script>\n<script src="Web/js/core/storage.js"></script>',
).replace(
    '<script src="network-printer.js"></script>',
    '<script src="network-printer.js"></script>\n<script src="notification-native.js"></script>',
)
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

