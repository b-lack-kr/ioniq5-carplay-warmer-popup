import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
FIXTURE=ROOT/'neev-home-icons/v1.0.0/vehicle-20261110/before-export/map'
STAR=ROOT/'neev-star-options/v0.1.0/source/payload/maps'
spec=importlib.util.spec_from_file_location('proposal',HERE/'map_proposal.py')
proposal=importlib.util.module_from_spec(spec);spec.loader.exec_module(proposal)

class MapProposalTest(unittest.TestCase):
    def test_406_map_preserves_every_existing_app_file_and_adds_exact_points(self):
        with tempfile.TemporaryDirectory(prefix='warmer-map-') as td:
            src=Path(td)/'source';out=Path(td)/'output';shutil.copytree(FIXTURE,src)
            car=json.loads((src/'car.json').read_text());star=json.loads((STAR/'car.json').read_text())
            car['apps'].append('buttonsettings');car['app_files']['buttonsettings']=star['app_files']['buttonsettings'];car['files'].update(star['files'])
            (src/'car.json').write_text(json.dumps(car));shutil.copy2(STAR/'buttonsettings.json',src/'buttonsettings.json')
            before={p.name:p.read_bytes() for p in src.iterdir() if p.is_file()}
            prior=sum(len(json.loads(before[a+'.json'])) for a in car['apps']);self.assertEqual(prior,406)
            proposed,maps=proposal.propose(src,out)
            self.assertEqual(sum(map(len,maps.values())),prior+6)
            self.assertEqual(set(maps['climate']),set(json.loads((HERE/'climate-points.json').read_text())))
            for app in car['apps']:self.assertEqual((out/(app+'.json')).read_bytes(),before[app+'.json'])
            self.assertEqual({p.name:p.read_bytes() for p in src.iterdir() if p.is_file()},before)
            self.assertEqual(proposed['app_files']['buttonsettings'],car['app_files']['buttonsettings'])

    def test_conflicting_point_fails_without_output(self):
        with tempfile.TemporaryDirectory(prefix='warmer-map-') as td:
            src=Path(td)/'source';out=Path(td)/'output';shutil.copytree(FIXTURE,src)
            p=src/'dipo.json';data=json.loads(p.read_text());data['climate.listener']={'class':'example.Other'};p.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError,'ownership conflict'):proposal.propose(src,out)
            self.assertFalse(out.exists())

    def test_signatures_are_in_captured_odex_smali(self):
        base=HERE.parent/'capture-stock-20261110/smali/com/hkmc/telematics/service/apps/climate'
        manager=(base/'ClimateManager.smali').read_text();listener=(base/'j.smali').read_text()
        self.assertIn('.method public mD()V',manager)
        self.assertIn('.method public onFrontSeatHeatStatusReceived(IIII)V',listener)
        self.assertIn('.method public onSWRCStatusReceived(IIII)V',listener)
        self.assertIn('.field final synthetic l:Lcom/hkmc/telematics/service/apps/climate/ClimateManager;',listener)

if __name__=='__main__':unittest.main()
