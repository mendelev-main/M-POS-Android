#!/usr/bin/env python3
"""One-time native key creation. Never invoke to repair missing CI secrets."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, required=True, help="NEW private directory OUTSIDE the repository")
args = parser.parse_args()
folder = args.output.resolve()
if folder == ROOT or ROOT in folder.parents:
    parser.error("Private key must be outside repository")
folder.mkdir(mode=0o700, parents=True, exist_ok=False)
password = secrets.token_urlsafe(36)
env = dict(os.environ, MPOS_NEW_PASSWORD=password)
key = folder / "mpos-native-release.jks"
subprocess.run(["keytool", "-genkeypair", "-keystore", str(key), "-storetype", "JKS", "-alias", "mpos-native",
    "-storepass:env", "MPOS_NEW_PASSWORD", "-keypass:env", "MPOS_NEW_PASSWORD", "-keyalg", "RSA",
    "-keysize", "3072", "-validity", "10000", "-dname", "CN=M-POS Native, OU=Android, O=Mendelev"], env=env, check=True, capture_output=True)
key.chmod(0o600)
cert = subprocess.check_output(["keytool", "-exportcert", "-keystore", str(key), "-storepass:env", "MPOS_NEW_PASSWORD", "-alias", "mpos-native"], env=env, stderr=subprocess.DEVNULL)
sha = hashlib.sha256(cert).hexdigest()
creds = {"MPOS_KEYSTORE_BASE64": base64.b64encode(key.read_bytes()).decode(), "MPOS_KEYSTORE_PASSWORD": password,
         "MPOS_KEY_ALIAS": "mpos-native", "MPOS_KEY_PASSWORD": password}
credentials = folder / "credentials.json"
credentials.write_text(json.dumps(creds, indent=2) + "\n")
credentials.chmod(0o600)
(folder / "certificate-sha256.txt").write_text(sha + "\n")
(folder / "README.txt").write_text("PRIVATE: сохранить keystore и credentials.json в защищённой резервной копии. Не коммитить/не отправлять в чат.\n"
    "Установить secrets: python3 scripts/install-signing-secrets.py --source /absolute/path/credentials.json\n"
    "Нужен авторизованный gh с доступом к mendelev-main/M-POS-Android и правом на Actions secrets.\n"
    "В config/android-release.json хранится только публичный SHA-256 сертификата. Не генерировать другой ключ при пропаже CI secrets.\n")
print(f"Created private native signing bundle at {folder}")
print(f"Public certificate SHA-256: {sha}")
