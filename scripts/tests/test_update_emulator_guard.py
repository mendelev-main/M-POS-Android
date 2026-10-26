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


class CatalogScrollTests(unittest.TestCase):
    def test_preserved_product_below_fold_is_found_in_xml_after_scrolling_inside_list(self):
        from unittest.mock import patch
        import xml.etree.ElementTree as ET
        viewport=ET.Element("node", {"package":module.PACKAGE,"scrollable":"true","resource-id":"catalogProducts","bounds":"[30,325][1570,1030]"})
        unrelated=ET.Element("node", {"text":"Чай extra"})
        target=ET.Element("node", {"text":"Чай [42]"})
        with patch.object(module,"wait_text"), patch.object(module,"ui_nodes",side_effect=[[viewport,unrelated],[viewport,target]]), patch.object(module,"adb") as adb, patch.object(module.time,"sleep"):
            self.assertEqual("Чай [42]",module.scroll_catalog_to_product("Чай [42]"))
        self.assertEqual(1,adb.call_count)
        command=adb.call_args.args
        self.assertEqual(("shell","input","swipe"),command[:3])
        x1,y1,x2,y2=map(int,command[3:7])
        self.assertTrue(30<x1<1570 and 30<x2<1570)
        self.assertTrue(325<y2<y1<1030)

    def test_missing_product_fails_after_bounded_scroll_instead_of_accepting_similar_text(self):
        from unittest.mock import patch
        import xml.etree.ElementTree as ET
        viewport=ET.Element("node", {"package":module.PACKAGE,"scrollable":"true","resource-id":"catalogProducts","bounds":"[30,325][1570,1030]"})
        wrong=ET.Element("node", {"text":"Чай 42 extra"})
        with patch.object(module,"wait_text"), patch.object(module,"ui_nodes",return_value=[viewport,wrong]), patch.object(module,"adb") as adb, patch.object(module.time,"sleep"):
            with self.assertRaisesRegex(AssertionError,"Preserved product not found"):
                module.scroll_catalog_to_product("Чай 42")
        self.assertEqual(12,adb.call_count)
