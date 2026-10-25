#!/usr/bin/env python3
"""Prepare the stable-channel assets only AFTER release.py verify-apk succeeds."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "mendelev-main/M-POS-Android"


def prepare(apk, folder, code, name, notes):
    if not 1 <= code <= 2_100_000_000 or not re.fullmatch(r"[0-9][0-9A-Za-z._-]{0,79}", name):
        raise ValueError("Invalid release version")
    if not notes.strip() or len(notes) > 8000:
        raise ValueError("Release notes must contain 1..8000 characters")
    config = json.loads((ROOT / "config/android-release.json").read_text())
    size = apk.stat().st_size
    if not 1 <= size <= 200 * 1024 * 1024:
        raise ValueError("Invalid APK size")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    manifest = {"schemaVersion": 1, "channel": "stable", "applicationId": config["applicationId"],
        "versionCode": code, "versionName": name, "minSdk": config["minSdk"],
        "apkUrl": f"https://github.com/{REPOSITORY}/releases/download/v{name}/mpos-native.apk",
        "apkBytes": size, "sha256": digest, "certificateSha256": config["certificateSha256"],
        "notes": notes, "commit": os.environ.get("GITHUB_SHA", "local")}
    folder.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, folder / "mpos-native.apk")
    (folder / "update.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    (folder / "mpos-native.apk.sha256").write_text(digest + "  mpos-native.apk\n")
    (folder / "RELEASE_NOTES.md").write_text(notes + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()
    prepare(args.apk, args.output, args.code, args.name,
            os.environ.get("RELEASE_NOTES", "Нативный M-POS: исправления и улучшения."))
    print("Stable update assets prepared")
