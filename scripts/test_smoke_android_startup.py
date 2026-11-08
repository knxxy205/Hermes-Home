import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest.mock import patch

from smoke_android_startup import exercise, find_tabs, find_unlock_button


class StartupNavigationLabelsTest(unittest.TestCase):
    def test_old_and_new_tabs_choose_navigation_instead_of_repeated_heading(self):
        for settings, files in [('Settings', 'Files'), ('Pengaturan', 'Berkas')]:
            tree = ET.fromstring(f'''<hierarchy>
                <node text="Hermes" bounds="[0,20][300,70]" />
                <node text="{settings}" bounds="[0,20][300,70]" />
                <node text="Terminal" bounds="[0,720][90,780]" />
                <node text="{files}" bounds="[90,720][180,780]" />
                <node text="Hermes" bounds="[180,720][270,780]" />
                <node text="{settings}" bounds="[270,720][360,780]" />
            </hierarchy>''')
            tabs = find_tabs(tree)
            self.assertEqual({'Settings', 'Files', 'Hermes', 'Terminal'}, set(tabs))
            self.assertEqual('[180,720][270,780]', tabs['Hermes'].get('bounds'))
            self.assertEqual('[270,720][360,780]', tabs['Settings'].get('bounds'))

    def test_manual_unlock_recognizes_updated_button_and_pin_alternative(self):
        for label in ['Buka Hermes Home', 'Open Hermes Home', 'Buka Homeserver', 'Use PIN', 'Use screen lock']:
            node = ET.Element('node', {'text': label})
            self.assertIs(node, find_unlock_button([ET.Element('node', {'text': 'Hermes Home'}), node]))
        self.assertIsNone(find_unlock_button([ET.Element('node', {'text': 'Hermes Home'})]))

    def test_startup_enters_pin_again_when_unlock_screen_returns(self):
        lock = ET.fromstring('<hierarchy><node text="Buka Hermes Home" bounds="[0,20][300,70]" /></hierarchy>')
        challenge = ET.fromstring('<hierarchy><node class="android.widget.EditText" bounds="[0,20][300,70]" /></hierarchy>')
        tabs = ET.fromstring('''<hierarchy>
            <node text="Terminal" bounds="[0,720][90,780]" />
            <node text="Berkas" bounds="[90,720][180,780]" />
            <node text="Hermes" bounds="[180,720][270,780]" />
            <node text="Pengaturan" bounds="[270,720][360,780]" />
        </hierarchy>''')
        screens = [lock, challenge, lock, challenge] + [tabs] * 20
        with TemporaryDirectory() as directory, \
                patch('smoke_android_startup.hierarchy', side_effect=screens), \
                patch('smoke_android_startup.time.sleep'), \
                patch('smoke_android_startup.adb', return_value=SimpleNamespace(stdout='1234', stderr='')) as adb:
            exercise(Path(directory))
            pin_entries = [call for call in adb.call_args_list if call.args[:3] == ('shell', 'input', 'text')]
            self.assertEqual(2, len(pin_entries))

    def test_lock_screen_during_tab_navigation_retries(self):
        lock = ET.fromstring('<hierarchy><node text="Buka Hermes Home" bounds="[0,20][300,70]" /></hierarchy>')
        challenge = ET.fromstring('<hierarchy><node class="android.widget.EditText" bounds="[0,20][300,70]" /></hierarchy>')
        tabs = ET.fromstring('''<hierarchy>
            <node text="Terminal" bounds="[0,720][90,780]" />
            <node text="Berkas" bounds="[90,720][180,780]" />
            <node text="Hermes" bounds="[180,720][270,780]" />
            <node text="Pengaturan" bounds="[270,720][360,780]" />
        </hierarchy>''')
        # First unlock works, then navigating to Settings triggers lock again
        screens = [lock, challenge] + [tabs] * 2 + [lock, challenge] + [tabs] * 20
        with TemporaryDirectory() as directory, \
                patch('smoke_android_startup.hierarchy', side_effect=screens), \
                patch('smoke_android_startup.time.sleep'), \
                patch('smoke_android_startup.adb', return_value=SimpleNamespace(stdout='1234', stderr='')) as adb:
            exercise(Path(directory))
            pin_entries = [call for call in adb.call_args_list if call.args[:3] == ('shell', 'input', 'text')]
            self.assertEqual(2, len(pin_entries))


if __name__ == '__main__':
    unittest.main()
