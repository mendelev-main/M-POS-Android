import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
from types import SimpleNamespace

spec=importlib.util.spec_from_file_location('signed_check',Path(__file__).resolve().parents[1]/'ci/signed-apk-check.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class SignedCandidateTests(unittest.TestCase):
    def test_untrusted_identity_and_changed_bytes_are_refused_before_platform_tools(self):
        config={'applicationId':'com.mendelev.mpos','certificateSha256':'a'*64}
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp);(folder/'mpos-native.apk').write_bytes(b'candidate')
            for change in [{'applicationId':'com.mendelev.mpos.web'},{'certificateSha256':'b'*64},{'sha256':'0'*64}]:
                manifest={**config,'apkBytes':9,'sha256':'0'*64,**change};(folder/'update.json').write_text(json.dumps(manifest))
                with patch.object(module.subprocess,'check_output') as command:
                    with self.assertRaises(ValueError):module.check_asset(folder,config)
                    command.assert_not_called()
    def test_baseline_tag_cannot_become_a_shell_or_cli_option(self):
        for tag in ['--latest','v1;curl example.invalid','v1\nmore','../v1']:
            with patch.object(module.subprocess,'run') as command:
                with self.assertRaises(ValueError):module.baseline(Path('unused'),tag)
                command.assert_not_called()


class SignedProcessSmokeTests(unittest.TestCase):
    package='com.mendelev.mpos'
    def driver(self, pid_after='123'):
        def adb(*args, **kwargs):
            if args[:3]==('shell','am','start'): return 'Status: ok\n'
            if args[:2]==('shell','pidof'):
                self.reads+=1
                return '123' if self.reads==1 else pid_after
            if args[:3]==('shell','dumpsys','activity'):
                return 'topResumedActivity: ActivityRecord{ com.mendelev.mpos/.MainActivity }'
            raise AssertionError(args)
        self.reads=0
        return SimpleNamespace(PACKAGE=self.package,adb=Mock(side_effect=adb))
    def test_startup_uses_activity_and_stable_process_without_ui_selectors(self):
        driver=self.driver()
        with patch.object(module.time,'sleep'):
            self.assertIn('Status: ok',module.launch_signed(driver))
        self.assertEqual(2,self.reads)
    def test_dead_process_is_not_accepted_as_successful_startup(self):
        with patch.object(module.time,'sleep'):
            with self.assertRaisesRegex(RuntimeError,'process died'):
                module.launch_signed(self.driver(pid_after=''))
    def test_failed_activity_start_is_rejected(self):
        driver=SimpleNamespace(PACKAGE=self.package,adb=Mock(return_value='Error: Activity class does not exist'))
        with self.assertRaisesRegex(RuntimeError,'failed to start'):module.launch_signed(driver)
    def test_package_version_is_checked_exactly_without_settings_screen(self):
        expected={'versionCode':123,'versionName':'0.1.23.1'}
        for code,name in [(123,'0.1.23.1'),(122,'0.1.23.1'),(123,'0.1.23.10')]:
            d=SimpleNamespace(PACKAGE=self.package,adb=Mock(return_value=f'versionCode={code} minSdk=36\n versionName={name}\n'))
            if (code,name)==(123,'0.1.23.1'):module.verify_installed_version(d,expected)
            else:
                with self.assertRaisesRegex(RuntimeError,'version differs'):module.verify_installed_version(d,expected)
