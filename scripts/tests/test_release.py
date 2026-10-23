import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release import validate_apk_outputs, version


class ReleaseTests(unittest.TestCase):
    def test_version_strictly_increases_for_run_and_retry(self):
        self.assertLess(version(1, 1)[0], version(1, 2)[0])
        self.assertLess(version(1, 99)[0], version(2, 1)[0])
        self.assertLess(version(20_000_000, 99)[0], 2_100_000_000)
        for run, attempt in [(0, 1), (1, 0), (1, 100), (20_000_001, 1)]:
            with self.assertRaises(ValueError):
                version(run, attempt)

    def test_only_expected_native_package_version_and_single_certificate_pass(self):
        cfg = {"applicationId": "com.mendelev.mpos", "certificateSha256": "ab" * 32}
        badging = "package: name='com.mendelev.mpos' versionCode='1101' versionName='0.1.1.1' platformBuildVersionName='16'"
        signature = "Signer #1 certificate SHA-256 digest: " + cfg["certificateSha256"] + "\n"
        validate_apk_outputs(badging, signature, cfg, 1101, "0.1.1.1")
        for changed in [badging.replace("com.mendelev.mpos'", "com.mendelev.mpos.web'"),
                        badging.replace("1101", "1102"), badging.replace("0.1.1.1", "0.1.1.2"), "invalid"]:
            with self.assertRaises(ValueError):
                validate_apk_outputs(changed, signature, cfg, 1101, "0.1.1.1")
        for changed in [signature.replace("ab", "cd"), "", signature + signature.replace("#1", "#2")]:
            with self.assertRaises(ValueError):
                validate_apk_outputs(badging, changed, cfg, 1101, "0.1.1.1")


if __name__ == "__main__":
    unittest.main()
