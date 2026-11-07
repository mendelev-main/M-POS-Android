import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('ci_emulator',Path(__file__).resolve().parents[1]/'ci/emulator.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class BootReadinessTests(unittest.TestCase):
    def test_boot_completed_without_focus_does_not_admit_tests_or_send_keys(self):
        events=[];ticks=[0]
        def call(*args):
            events.append(args)
            if args[-1]=='sys.boot_completed':return '1'
            return 'mCurrentFocus=null\nmFocusedApp=ActivityRecord{launcher}'
        def sleep(seconds):ticks[0]+=seconds
        with self.assertRaises(TimeoutError):module.wait_ready(call,lambda:ticks[0],sleep,timeout=5)
        self.assertFalse(any('input' in e for e in events))
    def test_focus_must_be_stable_before_tests_can_start(self):
        ticks=[0];focus=iter(['mCurrentFocus=null','mCurrentFocus=Window{a launcher}','mCurrentFocus=null','mCurrentFocus=Window{b launcher}','mCurrentFocus=Window{b launcher}']);events=[]
        def call(*args):
            events.append(args)
            return '1' if args[-1]=='sys.boot_completed' else next(focus) if args[-1]=='window' else ''
        def sleep(seconds):ticks[0]+=seconds
        module.wait_ready(call,lambda:ticks[0],sleep,timeout=30)
        self.assertEqual(8,ticks[0]);self.assertFalse(any('input' in e for e in events))
    def test_boot_anr_is_never_dismissed_or_ignored(self):
        for package in ['systemui','launcher3']:
            with self.assertRaises(RuntimeError):module.assert_clean_boot('E/ActivityManager: ANR in com.android.'+package+' (app)')
        module.assert_clean_boot('I/ActivityManager: focused launcher ready')
    def test_launcher_refuses_non_ci_machine(self):
        from unittest.mock import patch
        with patch.dict('os.environ',{},clear=True):
            with self.assertRaises(RuntimeError):module.ci_only()
