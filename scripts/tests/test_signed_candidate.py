import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

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
