#!/usr/bin/env python3
"""Own the disposable API36 CI emulator; never inject keys before a focused window exists."""
import argparse
import importlib.util
import os
from pathlib import Path
import re
import signal
import subprocess
import time

ROOT=Path(__file__).resolve().parents[2]
REPORTS=ROOT/'app/build/reports/emulator-diagnostics'
SERIAL='emulator-5554'
AVD='mpos-ci-api36'
ANR=re.compile(r'ANR in com\.android\.(?:systemui|launcher3)(?:[^a-zA-Z0-9_.]|$)')

def ci_only():
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('CI')!='true':
        raise RuntimeError('This launcher is only for disposable GitHub Actions runners')

def adb(*args,check=True):
    return subprocess.run(['adb','-s',SERIAL,*args],check=check,capture_output=True,text=True,timeout=20).stdout

def focused_window(raw):
    return bool(re.search(r'mCurrentFocus=Window\{[^\n}]+\}',raw))

def assert_clean_boot(log):
    if ANR.search(log):
        raise RuntimeError('CI emulator boot failed: SystemUI/launcher ANR before M-POS installation')

def wait_ready(call=adb,clock=time.monotonic,sleep=time.sleep,timeout=600,alive=lambda:True):
    deadline=clock()+timeout;stable=0;dismissed=False
    while clock()<deadline:
        if not alive():raise RuntimeError('Emulator exited during boot; see emulator-process.log')
        try:
            booted=call('shell','getprop','sys.boot_completed').strip()=='1'
            if booted and not dismissed:
                # Binder command, not an input key targeted at an unfocused launcher.
                call('shell','wm','dismiss-keyguard');dismissed=True
            focus=booted and focused_window(call('shell','dumpsys','window'))
        except (subprocess.SubprocessError,OSError):focus=False
        stable=stable+1 if focus else 0
        if stable>=2:return
        sleep(2)
    raise TimeoutError('Android boot/focused window was not ready within the CI timeout')

def diagnostics():
    REPORTS.mkdir(parents=True,exist_ok=True)
    for name,args in [('logcat.txt',('logcat','-d','-v','threadtime')),('last-anr.txt',('shell','dumpsys','activity','lastanr'))]:
        try:(REPORTS/name).write_text(adb(*args,check=False))
        except (OSError,subprocess.SubprocessError):pass

def assert_ready():
    REPORTS.mkdir(parents=True,exist_ok=True)
    log=adb('logcat','-d','-v','brief');(REPORTS/'boot-logcat.txt').write_text(log)
    assert_clean_boot(log)
    if not focused_window(adb('shell','dumpsys','window')):
        raise RuntimeError('No focused Android window; refusing to start tests')
    print('API36 READY: boot completed, focused window observed, no boot ANR',flush=True)

def start():
    sdk=Path(os.environ['ANDROID_HOME'])
    manager=sdk/'cmdline-tools/latest/bin/avdmanager'
    subprocess.run([str(manager),'create','avd','--force','--name',AVD,'--package','system-images;android-36;default;x86_64','--device','pixel_c'],input='no\n',text=True,check=True)
    home=Path(os.environ.get('ANDROID_AVD_HOME',str(Path.home()/'.android/avd')))
    config=home/(AVD+'.avd/config.ini')
    spec=importlib.util.spec_from_file_location('mpos_avd',ROOT/'scripts/ci/configure-emulator.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    module.configure(config)
    with config.open('a') as f:f.write('hw.cpu.ncore=4\nhw.ramSize=3072M\nhw.keyboard=yes\n')
    REPORTS.mkdir(parents=True,exist_ok=True)
    with (REPORTS/'emulator-process.log').open('w') as log:
        process=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',AVD,'-port','5554','-no-window','-gpu','software','-no-snapshot','-noaudio','-no-boot-anim'],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    (Path(os.environ['RUNNER_TEMP'])/'mpos-emulator.pid').write_text(str(process.pid))
    wait_ready(alive=lambda:process.poll() is None)
    for name in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
        adb('shell','settings','put','global',name,'0')
    adb('shell','settings','put','secure','show_ime_with_hard_keyboard','0')
    adb('shell','settings','put','system','screen_off_timeout','2147483647')
    assert_ready()

def stop():
    diagnostics()
    try:adb('emu','kill',check=False)
    except (OSError,subprocess.SubprocessError):pass
    pidfile=Path(os.environ['RUNNER_TEMP'])/'mpos-emulator.pid'
    if pidfile.is_file():
        try:os.kill(int(pidfile.read_text()),signal.SIGTERM)
        except (ProcessLookupError,PermissionError):pass

def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['start','assert-ready','stop']);args=p.parse_args();ci_only()
    try:globals()[args.action.replace('-','_')]()
    except BaseException:
        diagnostics();raise
if __name__=='__main__':main()
