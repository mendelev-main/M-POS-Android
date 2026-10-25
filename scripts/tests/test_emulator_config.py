import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("emulator_config", Path(__file__).resolve().parents[1] / "ci/configure-emulator.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EmulatorConfigTests(unittest.TestCase):
    def test_display_reduction_preserves_tablet_geometry_and_other_avd_settings(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "config.ini"
            path.write_text("hw.lcd.width=2560\nhw.lcd.height=1800\nhw.lcd.density=320\nhw.cpu.ncore=2\nimage.sysdir.1=system-images/android-36/default/x86_64/\n")
            module.configure(path)
            values = dict(line.split("=", 1) for line in path.read_text().splitlines())
            self.assertEqual(1280, int(values["hw.lcd.width"]) * 160 / int(values["hw.lcd.density"]))
            self.assertEqual(900, int(values["hw.lcd.height"]) * 160 / int(values["hw.lcd.density"]))
            self.assertEqual("2", values["hw.cpu.ncore"])
            self.assertIn("android-36/", values["image.sysdir.1"])
            module.configure(path)
            self.assertEqual(values, dict(line.split("=", 1) for line in path.read_text().splitlines()))
