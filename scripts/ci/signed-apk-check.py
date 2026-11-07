#!/usr/bin/env python3
"""Install the verified signed candidate BEFORE publication, on a disposable emulator only."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
REPO='mendelev-main/M-POS-Android'
sys.path.insert(0,str(ROOT/'scripts'))
from release import validate_apk_outputs

def load_driver():
    spec=importlib.util.spec_from_file_location('update_driver',ROOT/'scripts/ci/update-e2e.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def check_asset(folder,config,allow_older_minimum=False):
    manifest=json.loads((folder/'update.json').read_text());apk=folder/'mpos-native.apk'
    if manifest['applicationId']!=config['applicationId'] or manifest['certificateSha256']!=config['certificateSha256']:
        raise ValueError('Release identity differs from pinned native config')
    if not 1<=apk.stat().st_size<=200*1024*1024:raise ValueError('APK size is outside release limits')
    if apk.stat().st_size!=manifest['apkBytes'] or hashlib.sha256(apk.read_bytes()).hexdigest()!=manifest['sha256']:
        raise ValueError('Signed artifact differs from its verified manifest')
    tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
    badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
    signature=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
    expected=dict(config)
    if allow_older_minimum:
        minimum=manifest.get('minSdk')
        if type(minimum) is not int or not 1<=minimum<=config['minSdk']:raise ValueError('Invalid baseline minimum SDK')
        expected['minSdk']=minimum
    validate_apk_outputs(badging,signature,expected,manifest['versionCode'],manifest['versionName'])
    return manifest

def baseline(folder,requested):
    if requested and not re.fullmatch(r'v[0-9][0-9A-Za-z._-]{0,79}',requested):raise ValueError('Invalid baseline release tag')
    if not requested:
        found=subprocess.run(['gh','api',f'repos/{REPO}/releases/latest'],capture_output=True,text=True,check=False)
        if found.returncode:
            if '404' in found.stderr:return None
            raise RuntimeError('Cannot inspect the previous stable release')
        requested=json.loads(found.stdout)['tag_name']
    if not re.fullmatch(r'v[0-9][0-9A-Za-z._-]{0,79}',requested):raise ValueError('Invalid published baseline tag')
    folder.mkdir(parents=True,exist_ok=True)
    subprocess.run(['gh','release','download',requested,'--repo',REPO,'--pattern','mpos-native.apk','--pattern','update.json','--dir',str(folder)],check=True)
    return requested

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--assets',type=Path,required=True);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('CI')!='true':raise RuntimeError('Signed install smoke is CI-emulator-only')
    d=load_driver();d.require_emulator(d.adb('get-serialno').strip());d.PACKAGE='com.mendelev.mpos'
    if d.adb('shell','getprop','ro.build.version.sdk').strip()!='36' or d.adb('shell','getprop','ro.hardware').strip() not in {'ranchu','goldfish'}:
        raise RuntimeError('Signed smoke requires the disposable API36 emulator')
    config=json.loads((ROOT/'config/android-release.json').read_text());candidate=check_asset(args.assets,config)
    if candidate['commit']!=os.environ['GITHUB_SHA']:raise ValueError('Candidate belongs to another commit')
    base_folder=ROOT/'build/upgrade-base';tag=baseline(base_folder,os.environ.get('BASE_TAG','').strip())
    if tag:
        old=check_asset(base_folder,config,allow_older_minimum=True)
        if old['versionCode']>=candidate['versionCode']:raise ValueError('Candidate must be newer than baseline')
    # Root is only used to seed/read a non-financial marker in this disposable release sandbox.
    # No instrumentation build or temporary signing key substitutes for the release APK.
    d.adb('root')
    for _ in range(30):
        try:
            if d.adb('shell','id','-u').strip()=='0':break
        except subprocess.SubprocessError:pass
        time.sleep(1)
    else:raise RuntimeError('Disposable AOSP emulator root did not become ready')
    d.adb('uninstall',d.PACKAGE,check=False)
    marker='/data/user/0/com.mendelev.mpos/files/ci-signed-update-sentinel'
    uid=None
    if tag:
        d.adb('install',str(base_folder/'mpos-native.apk'));d.launch(fixture=False)
        uid=d.adb('shell','stat','-c','%u','/data/user/0/com.mendelev.mpos').strip()
        if not uid.isdecimal():raise RuntimeError('Invalid native app UID')
        d.adb('shell',"printf '%s' mpos-ci-signed-preserved > "+marker)
        d.adb('shell','chown',uid,marker);d.adb('shell','chmod','600',marker)
        d.adb('shell','am','force-stop',d.PACKAGE)
    d.adb('install','-r',str(args.assets/'mpos-native.apk'))
    if tag:
        if d.adb('shell','cat',marker).strip()!='mpos-ci-signed-preserved':raise RuntimeError('Release update lost private data marker')
        if d.adb('shell','stat','-c','%u','/data/user/0/com.mendelev.mpos').strip()!=uid:raise RuntimeError('Release update changed the app UID')
    d.launch(fixture=False)
    d.wait_text('Установлена версия '+re.escape(candidate['versionName']))
    # Force a fresh process, so a successful replacement cannot hide a cold-start failure.
    d.adb('shell','am','force-stop',d.PACKAGE);d.launch(fixture=False)
    d.wait_text('Установлена версия '+re.escape(candidate['versionName']))
    reports=ROOT/'app/build/reports/signed-install';reports.mkdir(parents=True,exist_ok=True)
    screenshot=subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=20)
    if not screenshot.startswith(b'\x89PNG'):raise RuntimeError('Invalid signed-install screenshot')
    (reports/'signed-settings.png').write_bytes(screenshot)
    result={'commit':candidate['commit'],'versionName':candidate['versionName'],'versionCode':candidate['versionCode'],'baseline':tag,'privateMarkerPreserved':bool(tag),'coldStart':True,'published':False}
    (reports/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print('SIGNED APK INSTALL PASSED: '+candidate['versionName']+'; '+('previous signed release replaced, UID/private marker retained' if tag else 'first signed installation')+'; cold start passed; not yet published',flush=True)
if __name__=='__main__':main()
