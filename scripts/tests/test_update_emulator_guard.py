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

class AccessibleNavigationTests(unittest.TestCase):
    def test_settings_gear_is_found_by_accessible_name_and_clicked_inside_bounds(self):
        from unittest.mock import patch
        import xml.etree.ElementTree as ET
        gear=ET.Element("node", {"text":"", "content-desc":"Настройки", "enabled":"true", "bounds":"[1520,40][1575,95]"})
        with patch.object(module,"ui_nodes",return_value=[gear]), patch.object(module,"adb") as adb:
            self.assertEqual("Настройки",module.wait_text("^Настройки$",click=True))
        self.assertEqual(("shell","input","tap","1547","67"),adb.call_args.args)

    def test_shift_uses_resource_id_despite_dynamic_employee_label(self):
        from unittest.mock import patch
        import xml.etree.ElementTree as ET
        node=ET.Element("node", {"text":"Иванов И.И.", "resource-id":"topNav-shift", "enabled":"true", "bounds":"[1200,40][1400,95]"})
        with patch.object(module,"ui_nodes",return_value=[node]), patch.object(module,"adb") as adb:
            self.assertEqual("Иванов И.И.",module.wait_navigation("shift"))
        self.assertEqual(("shell","input","tap","1300","67"),adb.call_args.args)

    def test_old_release_without_ids_still_navigates_by_legacy_label(self):
        from unittest.mock import patch
        import xml.etree.ElementTree as ET
        node=ET.Element("node", {"text":"Смена", "enabled":"true", "bounds":"[1200,40][1400,95]"})
        with patch.object(module,"ui_nodes",return_value=[node]), patch.object(module,"adb") as adb:
            self.assertEqual("Смена",module.wait_navigation("shift"))
        self.assertEqual(1,adb.call_count)

    def test_disabled_modern_navigation_cannot_fall_back_to_a_similarly_named_text(self):
        from unittest.mock import patch
        import xml.etree.ElementTree as ET
        locked=ET.Element("node", {"text":"Иванов И.И.", "resource-id":"topNav-shift", "enabled":"false", "bounds":"[1200,40][1400,95]"})
        heading=ET.Element("node", {"text":"Смена", "enabled":"true", "bounds":"[20,140][200,195]"})
        with patch.object(module,"ui_nodes",return_value=[locked,heading]), patch.object(module,"adb") as adb, patch.object(module.time,"monotonic",side_effect=[0,0,2]), patch.object(module.time,"sleep"):
            with self.assertRaisesRegex(AssertionError,"topNav-shift"):
                module.wait_navigation("shift",timeout=1)
        adb.assert_not_called()
