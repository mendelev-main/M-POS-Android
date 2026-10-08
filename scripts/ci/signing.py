"""Decode only the existing repository secret; never create a replacement CI key."""
import base64
import os
from pathlib import Path

names = ('MPOS_KEYSTORE_BASE64', 'MPOS_KEYSTORE_PASSWORD', 'MPOS_KEY_ALIAS', 'MPOS_KEY_PASSWORD')
missing = [name for name in names if not os.environ.get(name)]
if missing:
    raise SystemExit('Configure repository Actions secrets: ' + ', '.join(missing))
key = Path(os.environ['RUNNER_TEMP']) / 'mpos-release.keystore'
key.write_bytes(base64.b64decode(os.environ['MPOS_KEYSTORE_BASE64'], validate=True))
key.chmod(0o600)
with open(os.environ['GITHUB_ENV'], 'a') as target:
    target.write(f'MPOS_KEYSTORE_PATH={key}\n')
