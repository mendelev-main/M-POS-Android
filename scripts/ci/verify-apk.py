"""Verify actual APK identity, certificate and version before publishing it."""
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

config = json.loads(Path('config/android-release.json').read_text())
apk = Path('app/build/outputs/apk/release/app-release.apk')
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
build_tools = sdk / 'build-tools/35.0.0'
cert = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
fingerprints = re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]+)', cert)
if fingerprints != [config['certificateSha256']]:
    raise SystemExit('APK signer does not match the permanent release certificate')
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
expected = (config['applicationId'], os.environ['MPOS_VERSION_CODE'], os.environ['MPOS_VERSION_NAME'])
if not package or package.groups() != expected or 'application-debuggable' in badging:
    raise SystemExit('APK package/version/debuggable validation failed')
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
apk.with_suffix('.sha256').write_text(f'{sha}  {apk.name}\n')
apk.with_suffix('.json').write_text(json.dumps(dict(applicationId=expected[0], versionCode=int(expected[1]), versionName=expected[2], certificateSha256=fingerprints[0], apkSha256=sha, commit=os.environ['GITHUB_SHA']), indent=2)+'\n')
