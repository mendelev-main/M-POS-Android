import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("update_e2e", Path(__file__).resolve().parents[1] / "ci/update-e2e.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EmulatorGuardTests(unittest.TestCase):
    def test_destructive_upgrade_test_rejects_physical_and_ambiguous_devices(self):
        module.require_emulator("emulator-5554")
        for serial in ["R58N123456", "192.168.1.50:5555", "", "unknown", "emulator-5554\nR58N123456"]:
            with self.assertRaises(module.UnsafeDeviceError):
                module.require_emulator(serial)
