"""Expose compiler/test failures in Checks when artifact downloads are unavailable."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def error(message):
    escaped = str(message).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{escaped}')


log = Path(os.environ['RUNNER_TEMP']) / 'mpos-kotlin-checks.log'
if log.exists():
    for line in log.read_text(errors='replace').splitlines():
        if line.startswith('e: '):
            error(line)
for report in Path('app/build/test-results').glob('**/TEST-*.xml'):
    root = ET.parse(report).getroot()
    for case in root.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            error(f"{case.get('classname')}.{case.get('name')}: {failure.get('message', '')}\n{(failure.text or '')[:2500]}")
