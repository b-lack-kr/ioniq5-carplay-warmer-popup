"""Host-only shell integration tests, no ADB and no access to the vehicle."""
from pathlib import Path
import os, shutil, subprocess, tempfile, unittest

HERE = Path(__file__).resolve().parent

class InstallerTest(unittest.TestCase):
    def run_case(self, product='NEEV.KOR.0000.V039.001.260902', legacy=False, fail=''):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            usb, system, data, bins = [root / x for x in ('usb', 'system', 'data', 'bin')]
            for p in (usb, system/'bin', system/'framework', data/'nemesis', data/'hmgapi/mods', bins):
                p.mkdir(parents=True)
            shared = data/'hmgapi/maps/vehicle/map.json'; shared.parent.mkdir(parents=True)
            shared.write_text('star-owned map unchanged')
            (system/'framework/hmgapi-hook.jar').write_text('stub')
            if legacy: (data/'hmgapi/mods/neev-warmer-async-research').mkdir()
            payload = usb/'WARMER_MAPLESS/payload'; payload.parent.mkdir()
            shutil.copytree(HERE/'build/payload', payload)
            for name, source in {
                'id': '#!/bin/sh\necho uid=0\n',
                'getprop': '#!/bin/sh\ncase "$1" in ro.build.product) echo "$TEST_PRODUCT";; ro.board.platform) echo tcc803x;; esac\n',
            }.items():
                p=bins/name; p.write_text(source); p.chmod(0o755)
            runner = system/'bin/app_process.hmgapi-stock'
            runner.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$TEST_CALLS"\ncase "$*" in *"$TEST_FAIL"*) [ -z "$TEST_FAIL" ] || exit 7;; esac\nexit 0\n')
            runner.chmod(0o755)
            script=(HERE/'NEMESIS.sh').read_text().replace('/system',str(system)).replace('/data',str(data))
            script=script.replace('export PATH='+str(system)+'/bin:'+str(system)+'/xbin:'+str(data)+'/nemesis/bin',f'export PATH={bins}:/usr/bin:/bin')
            (usb/'NEMESIS.sh').write_text(script)
            calls=root/'calls'
            env=os.environ.copy(); env.update(TEST_PRODUCT=product,TEST_FAIL=fail,TEST_CALLS=str(calls),BOOTCLASSPATH='host-test')
            result=subprocess.run(['/bin/sh',str(usb/'NEMESIS.sh')],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            status=next(usb.glob('WARMER_MAPLESS_RESULT_*/STATUS.txt')).read_text().strip()
            self.assertEqual(shared.read_text(),'star-owned map unchanged')
            return result.returncode,status,calls.read_text() if calls.exists() else ''

    def test_success_calls_signature_then_install(self):
        code,status,calls=self.run_case()
        self.assertEqual((code,status),(0,'INSTALLED_EXPERIMENT_OFF_REBOOT_REQUIRED'))
        self.assertIn('hmgapi.engine.Signing verify',calls)
        self.assertIn('hmgapi.engine.Engine install',calls)
        self.assertLess(calls.index('Signing verify'),calls.index('Engine install'))
        self.assertNotIn(' remove ',calls)
    def test_wrong_vehicle(self):
        code,status,calls=self.run_case(product='different')
        self.assertNotEqual(code,0); self.assertEqual(status,'UNSUPPORTED_PRODUCT'); self.assertEqual(calls,'')
    def test_legacy_candidate_stops(self):
        code,status,calls=self.run_case(legacy=True)
        self.assertNotEqual(code,0); self.assertEqual(status,'OTHER_WARMER_RESEARCH_INSTALLED'); self.assertEqual(calls,'')
    def test_bad_signature_stops(self):
        code,status,calls=self.run_case(fail='Signing verify')
        self.assertNotEqual(code,0); self.assertEqual(status,'BAD_SIGNATURE'); self.assertNotIn('Engine install',calls)
    def test_engine_failure_reports(self):
        code,status,calls=self.run_case(fail='Engine install')
        self.assertNotEqual(code,0); self.assertEqual(status,'INSTALL_FAILED')

if __name__ == '__main__': unittest.main(verbosity=2)
