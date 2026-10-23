#!/usr/bin/env python3
"""Repository preflight and reproducible Android checks; no third-party Python deps."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def preflight(root=ROOT):
    errors = []
    required = ["AGENTS.md", "GOALS.md", "PLANS.md", "PROMPTS.md", "README.md",
                "docs/SOURCE.md", "docs/DATA.md", "docs/PARITY.md", "docs/RELEASE.md",
                "harness/build-log.md", "config/reference.json", "config/android-release.json",
                "gradle/wrapper/gradle-wrapper.jar", ".github/workflows/android.yml"]
    for name in required:
        if not (root / name).is_file():
            errors.append(f"Missing: {name}")
    phases = list((root / "harness/build").glob("[0-9][0-9]-*.md"))
    if sorted(p.name[:2] for p in phases) != [f"{i:02}" for i in range(10)]:
        errors.append("Expected exactly phases 00–09")
    for path in root.rglob("*.md"):
        if any(p in {".git", "build", ".gradle"} for p in path.parts):
            continue
        for target in re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", path.read_text()):
            if "://" in target or target.startswith("#"):
                continue
            if not (path.parent / target.split("#")[0]).exists():
                errors.append(f"Broken link {path.relative_to(root)} → {target}")
    for name in ("mpos-phase", "mpos-domain", "mpos-release"):
        path = root / f".agents/skills/{name}/SKILL.md"
        if not path.is_file():
            errors.append(f"Missing skill {name}")
            continue
        text = path.read_text()
        header = text.split("---", 2)
        if len(header) != 3 or f"name: {name}" not in header[1] or "description:" not in header[1]:
            errors.append(f"Invalid skill metadata {name}")
    try:
        cfg = json.loads((root / "config/android-release.json").read_text())
        if cfg["applicationId"] != "com.mendelev.mpos" or cfg["applicationId"] == cfg["webApplicationId"]:
            errors.append("Native/Web application identity collision")
        if not re.fullmatch(r"[0-9a-f]{64}", cfg["certificateSha256"]):
            errors.append("Release certificate is not pinned")
        gradle = (root / "app/build.gradle.kts").read_text()
        if f'applicationId = "{cfg["applicationId"]}"' not in gradle:
            errors.append("Gradle/config applicationId mismatch")
        ET.parse(root / "app/src/main/AndroidManifest.xml")
        for path in (root / "app/src/main").rglob("*"):
            if path.is_file() and (path.suffix in {".html", ".js"} or
               path.suffix == ".kt" and re.search(r"\bWebView\b|evaluateJavascript", path.read_text())):
                errors.append(f"Unexpected Web runtime: {path.relative_to(root)}")
        ref = json.loads((root / "config/reference.json").read_text())
        if not re.fullmatch(r"[0-9a-f]{40}", ref["commit"]):
            errors.append("Reference commit must be pinned")
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        errors.append(str(error))
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["preflight", "android"])
    args = parser.parse_args()
    errors = preflight()
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("Harness preflight passed")
    if args.command == "android":
        return subprocess.call([str(ROOT / "gradlew"), "--no-daemon", "testDebugUnitTest", "lintDebug", "assembleDebug"], cwd=ROOT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
