#!/usr/bin/env python3
"""Version generation, private signing setup and post-build APK verification."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config/android-release.json"
SECRET_NAMES = ("MPOS_KEYSTORE_BASE64", "MPOS_KEYSTORE_PASSWORD", "MPOS_KEY_ALIAS", "MPOS_KEY_PASSWORD")


def version(run, attempt):
    if not 1 <= run <= 20_000_000 or not 1 <= attempt <= 99:
        raise ValueError("run must be 1..20000000; attempt 1..99")
    return 1000 + run * 100 + attempt, f"0.1.{run}.{attempt}"


def validate_apk_outputs(badging, signature, config, expected_code, expected_name):
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != (config["applicationId"], str(expected_code), expected_name):
        raise ValueError("APK package/version differs from release config")
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", signature, re.M)
    if len(fingerprints) != 1 or fingerprints[0].lower() != config["certificateSha256"].lower():
        raise ValueError("APK signing certificate differs from pinned native certificate")


def signing_setup(directory):
    missing = [key for key in SECRET_NAMES if not os.environ.get(key)]
    if missing:
        raise ValueError("Missing GitHub Actions secrets: " + ", ".join(missing))
    cfg = json.loads(CONFIG.read_text())
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    key = directory / "release.jks"
    fd = os.open(key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(base64.b64decode(os.environ["MPOS_KEYSTORE_BASE64"], validate=True))
        result = subprocess.run(["keytool", "-exportcert", "-keystore", str(key),
            "-storepass:env", "MPOS_KEYSTORE_PASSWORD", "-alias", os.environ["MPOS_KEY_ALIAS"]], capture_output=True)
        if result.returncode or hashlib.sha256(result.stdout).hexdigest() != cfg["certificateSha256"]:
            raise ValueError("Keystore/alias/password or certificate mismatch")
        # CSR generation proves private-key recovery (exportcert alone checks only store password).
        result = subprocess.run(["keytool", "-certreq", "-keystore", str(key),
            "-storepass:env", "MPOS_KEYSTORE_PASSWORD", "-keypass:env", "MPOS_KEY_PASSWORD",
            "-alias", os.environ["MPOS_KEY_ALIAS"]], capture_output=True)
        if result.returncode:
            raise ValueError("Cannot access private signing key")
    except Exception:
        key.unlink(missing_ok=True)
        raise
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        with open(github_env, "a") as output:
            output.write(f"MPOS_KEYSTORE_PATH={key.resolve()}\n")
    print("Native signing key validated (private values suppressed)")


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    v = commands.add_parser("version")
    v.add_argument("--run", type=int, required=True)
    v.add_argument("--attempt", type=int, required=True)
    s = commands.add_parser("signing-setup")
    s.add_argument("--directory", type=Path, required=True)
    a = commands.add_parser("verify-apk")
    a.add_argument("apk", type=Path)
    a.add_argument("--build-tools", type=Path, required=True)
    a.add_argument("--code", type=int, required=True)
    a.add_argument("--name", required=True)
    args = parser.parse_args()
    if args.command == "version":
        code, name = version(args.run, args.attempt)
        content = f"MPOS_VERSION_CODE={code}\nMPOS_VERSION_NAME={name}\n"
        if os.environ.get("GITHUB_ENV"):
            with open(os.environ["GITHUB_ENV"], "a") as output:
                output.write(content)
        print(content, end="")
    elif args.command == "signing-setup":
        signing_setup(args.directory)
    else:
        badging = subprocess.check_output([str(args.build_tools / "aapt"), "dump", "badging", str(args.apk)], text=True)
        signature = subprocess.check_output([str(args.build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(args.apk)], text=True)
        validate_apk_outputs(badging, signature, json.loads(CONFIG.read_text()), args.code, args.name)
        print("APK identity, version and pinned native signature verified")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
