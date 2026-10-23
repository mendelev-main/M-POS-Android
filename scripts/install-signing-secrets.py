#!/usr/bin/env python3
"""Install the existing native key bundle using an authenticated GitHub CLI."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from release import CONFIG, SECRET_NAMES

parser = argparse.ArgumentParser()
parser.add_argument("--source", type=Path, required=True)
args = parser.parse_args()
creds = json.loads(args.source.read_text())
if set(creds) != set(SECRET_NAMES) or not all(isinstance(v, str) and v for v in creds.values()):
    parser.error("Invalid credentials bundle")
env = dict(os.environ, **creds)
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / "key.jks"
    path.write_bytes(base64.b64decode(creds["MPOS_KEYSTORE_BASE64"], validate=True))
    path.chmod(0o600)
    result = subprocess.run(["keytool", "-exportcert", "-keystore", str(path), "-storepass:env", "MPOS_KEYSTORE_PASSWORD",
        "-alias", creds["MPOS_KEY_ALIAS"]], env=env, capture_output=True, check=True)
    if hashlib.sha256(result.stdout).hexdigest() != json.loads(CONFIG.read_text())["certificateSha256"]:
        parser.error("Bundle is not the pinned native key")
subprocess.run(["gh", "auth", "status"], check=True)
for name in SECRET_NAMES:
    subprocess.run(["gh", "secret", "set", name, "--repo", "mendelev-main/M-POS-Android"], input=creds[name], text=True, check=True)
print("Four native signing secrets installed; private values suppressed")
