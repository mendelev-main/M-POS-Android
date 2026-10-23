#!/usr/bin/env python3
"""Publish complete immutable assets; draft first, stable pointer LAST. Requires contents:write."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

REPOSITORY = "mendelev-main/M-POS-Android"


def gh(*args):
    return subprocess.check_output(["gh", *args, "--repo", REPOSITORY], text=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, required=True)
    args = parser.parse_args()
    folder = args.assets
    manifest = json.loads((folder / "update.json").read_text())
    apk = folder / "mpos-native.apk"
    if hashlib.sha256(apk.read_bytes()).hexdigest() != manifest["sha256"] or apk.stat().st_size != manifest["apkBytes"]:
        raise ValueError("Release APK changed after verification")
    tag = "v" + manifest["versionName"]
    if manifest["commit"] != os.environ["GITHUB_SHA"]:
        raise ValueError("Artifact belongs to another commit")
    # Concurrent/stale publish may never move stable backwards. 404 means the first release.
    result = subprocess.run(["gh", "api", f"repos/{REPOSITORY}/releases/latest"], capture_output=True, text=True)
    if result.returncode == 0:
        current = json.loads(result.stdout)
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            gh("release", "download", current["tag_name"], "--pattern", "update.json", "--dir", tmp)
            previous = json.loads((Path(tmp) / "update.json").read_text())
            if previous["versionCode"] >= manifest["versionCode"]:
                raise ValueError("Refusing to replace stable with an equal or older version")
    elif "404" not in result.stderr:
        raise RuntimeError("Cannot inspect the current stable release")
    existing = subprocess.run(["gh", "release", "view", tag, "--repo", REPOSITORY], capture_output=True)
    if existing.returncode == 0:
        raise ValueError("Release tag already exists; rerun workflow for a new version, do not overwrite")
    assets = [str(folder / name) for name in ["mpos-native.apk", "update.json", "mpos-native.apk.sha256"]]
    gh("release", "create", tag, *assets, "--draft", "--target", os.environ["GITHUB_SHA"],
       "--title", "M-POS " + manifest["versionName"], "--notes-file", str(folder / "RELEASE_NOTES.md"))
    # All assets are uploaded before making this release discoverable to installed clients.
    print(gh("release", "edit", tag, "--draft=false", "--latest"))
    print("Published stable M-POS " + manifest["versionName"])


if __name__ == "__main__":
    main()
