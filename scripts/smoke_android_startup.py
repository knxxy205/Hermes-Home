"""Install and exercise a signed APK on a disposable Android emulator only."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

APP_ID = 'com.aistudio.homeserver.thns'
ACTIVITY = APP_ID + '/com.example.MainActivity'
EMULATOR_PIN = '246810'
TAB_LABELS = {
    'Settings': ('Settings', 'Pengaturan'),
    'Files': ('Files', 'Berkas'),
    'Hermes': ('Hermes',),
    'Terminal': ('Terminal',),
}


def find_tabs(tree):
    """Prefer the bottom navigation when a heading repeats a tab's label."""
    tabs = {}
    for title, labels in TAB_LABELS.items():
        matches = [node for node in tree.iter('node') if node.get('text', '').strip() in labels]
        if matches:
            def top(node):
                bounds = re.findall(r'\d+', node.get('bounds', ''))
                return int(bounds[1]) if len(bounds) == 4 else -1
            tabs[title] = max(matches, key=top)
    return tabs


def find_unlock_button(nodes):
    labels = ('use pin', 'use screen lock', 'buka homeserver', 'buka hermes home', 'open hermes home',
              'gunakan pin', 'gunakan kunci layar')
    return next((node for node in nodes if node.get('text', '').strip().lower() in labels), None)


def adb(*args, check=True):
    return subprocess.run(['adb', '-e', *args], text=True, capture_output=True,
                          timeout=60, check=check)


def hierarchy(output, name):
    result = adb('shell', 'uiautomator', 'dump', '/sdcard/hermes-startup.xml', check=False)
    if result.returncode:
        return None
    result = adb('shell', 'cat', '/sdcard/hermes-startup.xml', check=False)
    if result.returncode:
        return None
    (output / (name + '.xml')).write_text(result.stdout)
    try:
        return ET.fromstring(result.stdout)
    except ET.ParseError:
        return None


def tap(node):
    points = re.findall(r'\d+', node.get('bounds', ''))
    if len(points) != 4:
        raise RuntimeError('Cannot locate UI element: ' + str(node.attrib))
    left, top, right, bottom = map(int, points)
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def assert_running():
    if not adb('shell', 'pidof', APP_ID, check=False).stdout.strip():
        raise RuntimeError('App process exited during startup; inspect crash-logcat.txt')


def exercise(output):
    launch = adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    (output / 'launch.txt').write_text(launch.stdout + launch.stderr)
    time.sleep(5)
    assert_running()
    entered_pin = False
    for attempt in range(12):
        tree = hierarchy(output, 'screen-' + str(attempt))
        assert_running()
        if tree is not None:
            nodes = list(tree.iter('node'))
            if len(find_tabs(tree)) == len(TAB_LABELS):
                # Check all four screens after real device-credential authentication.
                for title in ('Settings', 'Files', 'Hermes', 'Terminal'):
                    tree = hierarchy(output, 'before-' + title.lower())
                    tabs_now = find_tabs(tree) if tree is not None else {}
                    if title not in tabs_now:
                        # Activity recreation or lock screen returned; re-authenticate.
                        entered_pin = False
                        break
                    tap(tabs_now[title])
                    time.sleep(2)
                    assert_running()
                    tree = hierarchy(output, 'after-' + title.lower())
                    # If lock screen returned after navigating, restart the loop.
                    if tree is not None and len(find_tabs(tree)) < len(TAB_LABELS):
                        nodes_after = list(tree.iter('node'))
                        if find_unlock_button(nodes_after) is not None:
                            entered_pin = False
                            break
                else:
                    return
            pin_field = next((n for n in nodes if n.get('class') == 'android.widget.EditText'
                               or n.get('resource-id', '').endswith('/pinEntry')), None)
            if pin_field is None:
                # Activity recreation can return to the lock screen and start a new challenge.
                entered_pin = False
            if pin_field is not None and not entered_pin:
                tap(pin_field)
                adb('shell', 'input', 'text', EMULATOR_PIN)
                adb('shell', 'input', 'keyevent', '66')
                entered_pin = True
            elif pin_field is None:
                button = find_unlock_button(nodes)
                if button is not None:
                    tap(button)
        time.sleep(2)
    raise RuntimeError('Did not reach the four app tabs after device-credential authentication')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != '1':
        raise RuntimeError('This test may only clear data on a disposable emulator')
    adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
    # Fixed test PIN belongs only to this disposable emulator, never to an app profile.
    pin = adb('shell', 'locksettings', 'set-pin', EMULATOR_PIN, check=False)
    if pin.returncode and 'already' not in pin.stdout.lower():
        # A second invocation reuses the same emulator PIN.
        adb('shell', 'locksettings', 'verify', '--old', EMULATOR_PIN)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard', check=False)
    adb('install', '-r', str(args.apk))
    adb('shell', 'pm', 'clear', APP_ID)
    adb('logcat', '-c')
    try:
        exercise(args.output)
        print('PASS: fresh install, launch, device-credential unlock, and four tabs')
    finally:
        (args.output / 'crash-logcat.txt').write_text(adb('logcat', '-b', 'crash', '-d').stdout)
        (args.output / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '2000').stdout)
        screenshot = subprocess.run(['adb', '-e', 'exec-out', 'screencap', '-p'],
                                    capture_output=True, timeout=30)
        if screenshot.returncode == 0:
            (args.output / 'screen.png').write_bytes(screenshot.stdout)
        adb('shell', 'am', 'force-stop', APP_ID, check=False)


if __name__ == '__main__':
    main()
