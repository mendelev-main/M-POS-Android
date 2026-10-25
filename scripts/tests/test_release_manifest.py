import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("prepare_release", Path(__file__).resolve().parents[1] / "prepare-release.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ManifestTests(unittest.TestCase):
    def test_manifest_and_checksum_match_exact_artifact(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "source.apk"
            apk.write_bytes(b"verified-test-apk")
            output = root / "release"
            result = module.prepare(apk, output, 1401, "0.1.4.1", "Описание выпуска")
            self.assertEqual(result["sha256"], hashlib.sha256(apk.read_bytes()).hexdigest())
            self.assertEqual(result["apkBytes"], apk.stat().st_size)
            self.assertEqual(result["minSdk"],36)
            self.assertEqual(result["applicationId"], "com.mendelev.mpos")
            self.assertIn("/releases/download/v0.1.4.1/mpos-native.apk", result["apkUrl"])
            self.assertEqual(json.loads((output / "update.json").read_text()), result)
            self.assertEqual((output / "mpos-native.apk").read_bytes(), apk.read_bytes())
            self.assertTrue((output / "mpos-native.apk.sha256").read_text().endswith("  mpos-native.apk\n"))
            for name in ["../bad", "bad/name", "name?token", ""]:
                with self.assertRaises(ValueError):
                    module.prepare(apk, output, 1401, name, "Notes")
