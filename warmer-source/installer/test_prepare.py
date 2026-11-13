from pathlib import Path
import tempfile,shutil,subprocess,json
p=Path(__file__).resolve().parent
root=p.parents[4]
jdk=root/'.tools/java-build/jdk-17.0.21+9/Contents/Home/bin'
base=root/'neev-home-icons/v1.0.0/vehicle-20261110/before-export/map'
star=root/'neev-star-options/v0.1.0/source/payload/maps'
with tempfile.TemporaryDirectory() as t:
 t=Path(t).resolve(); m=t/'map';shutil.copytree(base,m);car=json.loads((m/'car.json').read_text());st=json.loads((star/'car.json').read_text());car['apps'].append('buttonsettings');car['app_files']['buttonsettings']=st['app_files']['buttonsettings'];car['files'].update(st['files']);
 for name in ('/system/app/HKMC_Climate.odex','/system/framework/automotive.odex','/system/framework/com.broadcom.bt.jar','/system/framework/com.telechips.android.tcciap.odex','/system/framework/com.tcc.iap2.odex'):
  if name in car['files']:car['files'][name]='d41d8cd98f00b204e9800998ecf8427e'
 (m/'car.json').write_text(json.dumps(car));shutil.copy(star/'buttonsettings.json',m/'buttonsettings.json')
 (t/'check.json').write_text(json.dumps({'ok':True,'product':'NEEV.KOR.0000.V039.001.260902','folder':'hyundai/neev/kor/260902','points':406,'files':car['files']}));(t/'root').mkdir();out=t/'classes';out.mkdir()
 subprocess.run([jdk/'javac','--release','8','-d',out,root/'neev-star-options/v0.1.0/source/vendor/hmgapi/engine/Json.java',p/'src/kr/ioniq5/warmerinstall/PrepareMap.java',p/'tests/kr/ioniq5/warmerinstall/PrepareMapTest.java'],check=True)
 subprocess.run([jdk/'java','-cp',out,'kr.ioniq5.warmerinstall.PrepareMapTest',t,p.parent/'climate-points.json'],check=True)
