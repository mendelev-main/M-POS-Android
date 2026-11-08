#!/usr/bin/env python3
"""Provision the pinned wrapper before emulator boot; retry only download failures."""
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]


def bootstrap(run=subprocess.run, sleep=time.sleep):
    for attempt in range(1, 4):
        result = run([str(ROOT / 'gradlew'), '--no-daemon', '--version'],
                     cwd=ROOT, capture_output=True, text=True)
        output = result.stdout + result.stderr
        print(output, end='', flush=True)
        if result.returncode == 0:
            return 0
        # Never retry a checksum/validation failure or a Gradle build/test task.
        transient = any(marker in output for marker in (
            'Server returned HTTP response code: 502',
            'Server returned HTTP response code: 503',
            'Server returned HTTP response code: 504',
            'java.net.SocketTimeoutException',
            'java.net.ConnectException',
            'java.net.UnknownHostException',
        ))
        if not transient or 'Verification of Gradle distribution failed' in output or attempt == 3:
            return result.returncode
        print(f'Gradle download network failure; retry {attempt + 1}/3', flush=True)
        sleep(attempt * 10)
    raise AssertionError('unreachable')


if __name__ == '__main__':
    sys.exit(bootstrap())
