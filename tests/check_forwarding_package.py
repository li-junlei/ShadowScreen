from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
with ZipFile(root / 'build/app.apk') as apk:
    dex = apk.read('classes.dex')
    assert b'GamepadClient' in dex and b'GamepadActivity' in dex
    for removed in (b'GamepadView', b'GamepadDeviceService', b'GkmeDevice', b'com/zyz4/gkme', b'kotlin/'):
        assert removed not in dex, removed
    assert not any(name.startswith('lib/') for name in apk.namelist())
xml = (root / 'res/layout/activity_main.xml').read_text(encoding='utf-8')
assert '@+id/btn_gamepad_forward' in xml
assert '@+id/btn_gamepad"' not in xml
print('Forwarding retained; built-in classes, Kotlin and native libraries excluded')
