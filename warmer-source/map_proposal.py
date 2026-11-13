#!/usr/bin/env python3
"""Create a local-only climate callback map proposal, never touch a car."""
import importlib.util
import json
import shutil
from pathlib import Path

HERE=Path(__file__).resolve().parent
source=HERE.parent/'observer/research-merge/propose.py'
spec=importlib.util.spec_from_file_location('warmer_research_merge',source)
base=importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
base.CLIMATE=json.loads((HERE/'climate-points.json').read_text(encoding='utf-8'))
base.FRAMEWORK={}

def propose(source_dir,destination):
    src,dst=Path(source_dir),Path(destination)
    if dst.exists() or dst.is_symlink():raise ValueError('destination must not exist')
    car,maps=base.merge(src)
    if src.is_symlink() or any(p.is_symlink() for p in src.rglob('*')):
        raise ValueError('symlink in source tree')
    if src.resolve() in dst.resolve().parents:raise ValueError('output inside source')
    shutil.copytree(src,dst)
    # Do not rewrite framework, star, Dipo or any existing owner's file.
    for name,obj in [('car',car),('climate',maps['climate'])]:
        (dst/(name+'.json')).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return car,maps

if __name__=='__main__':
    raise SystemExit('Local test library only; not a vehicle installer')
