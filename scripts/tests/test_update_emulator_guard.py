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

    def test_only_identified_system_quickstep_anr_can_be_closed(self):
        import xml.etree.ElementTree as ET
        title=ET.Element("node", {"package":"android", "resource-id":"android:id/alertTitle", "text":"Quickstep isn't responding"})
        button=ET.Element("node", {"package":"android", "resource-id":"android:id/aerr_close", "text":"Close app", "enabled":"true"})
        self.assertIs(button,module.launcher_anr_close([title,button]))
        for text in ["M-POS isn't responding", "System UI isn't responding", "", "Quickstep keeps stopping"]:
            title.set("text",text)
            self.assertIsNone(module.launcher_anr_close([title,button]))
        title.set("text","Quickstep isn't responding");title.set("package","com.mendelev.mpos.debug")
        self.assertIsNone(module.launcher_anr_close([title,button]))
        title.set("package","android");button.set("enabled","false")
        self.assertIsNone(module.launcher_anr_close([title,button]))
