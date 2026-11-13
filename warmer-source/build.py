from pathlib import Path
import shutil
import subprocess

here = Path(__file__).resolve().parent
root = here.parents[3]
java = root / '.tools/java-build/jdk-17.0.21+9/Contents/Home/bin'
android = root / '.tools/android34/android.jar'
api = root / 'neev-carplay/improvements/20261110-lane-match/build-release/mod/hmg-api'
stubs = root / 'neev-carplay/improvements/20261110-lane-match/build-release/mod/hmg-stubs'
build = here / 'build'
classes, payload = build / 'classes', build / 'payload'
for path in (classes, payload):
    if path.exists(): shutil.rmtree(path)
    path.mkdir(parents=True)
def run(args): subprocess.run([str(x) for x in args], check=True)
run(['python3', here / 'test.py'])
run(['python3', here / 'test_mapless.py'])
run(['python3', here / 'test_async_overlay.py'])
run(['python3', here / 'async-window/run_tests.py'])
sources = sorted((here/'src').rglob('*.java'))
run([java/'javac', '--release', '8', '-encoding', 'UTF-8', '-cp', ':'.join(map(str, (android,api,stubs))), '-d', classes, *sources])
shutil.copyfile(here/'mod.json', payload/'mod.json')
shutil.copyfile(here/'settings.json', payload/'settings.json')
run([java/'java', '-cp', root/'.tools/java-build/r8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '19',
     '--lib', android, '--classpath', api, '--classpath', stubs, '--output', payload/'hooks.jar', *sorted(classes.rglob('*.class'))])
key = build/'research-author.key'
if not key.exists():
    run([java/'java', '-cp', api, 'hmgapi.engine.Signing', 'keygen', key])
    key.chmod(0o600)
run([java/'java', '-cp', api, 'hmgapi.engine.Signing', 'sign', payload, key])
run([java/'java', '-cp', api, 'hmgapi.engine.Signing', 'verify', payload])
print('Experimental payload built; setting defaults OFF; no vehicle installer.')
