from pathlib import Path
import json
p=Path(__file__).resolve().parent
j=json.loads((p/'mod.json').read_text())
assert not j['hooks']['points']
s=(p/'src/kr/ioniq5/warmerhook/WarmerHook.java').read_text()
assert 'Bridge.hookMethod(' in s and 'mod.hook(' not in s and 'Points.method(' not in s
assert 'APK_SHA.equals(digest(APK))&&ODEX_SHA.equals(digest(ODEX))' in s
assert 'hooks.containsKey(open)' in s and 'mod.enabled()' in s
assert 'asyncBackend.disable()' in s and 'ui.disable()' in s and 'onSettingsChanged' in s
print('Mapless, fingerprint, prior-hook and disable source checks passed')
