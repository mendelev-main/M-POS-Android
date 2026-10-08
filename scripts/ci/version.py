"""Stable workflow counter plus rerun attempt; no timestamps or random versions."""
import json
import os
from pathlib import Path


def version(config, run_number, attempt):
    if run_number < 1 or not 1 <= attempt <= 999:
        raise ValueError('Invalid workflow run number or attempt')
    code = config['versionCodeBase'] + run_number * 1000 + attempt
    if not 1 <= code <= 2100000000:
        raise ValueError('Android versionCode out of range')
    return code, f"{config['versionNameBase']}.{run_number}-{attempt}"


if __name__ == '__main__':
    config = json.loads(Path('config/android-release.json').read_text())
    code, name = version(config, int(os.environ['GITHUB_RUN_NUMBER']), int(os.environ['GITHUB_RUN_ATTEMPT']))
    with open(os.environ['GITHUB_ENV'], 'a') as target:
        target.write(f'MPOS_VERSION_CODE={code}\nMPOS_VERSION_NAME={name}\n')
