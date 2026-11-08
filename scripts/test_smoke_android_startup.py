import unittest
import xml.etree.ElementTree as ET

from smoke_android_startup import find_tabs, find_unlock_button


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


if __name__ == '__main__':
    unittest.main()
