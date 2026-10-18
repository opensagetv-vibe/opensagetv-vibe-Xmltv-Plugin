#!/usr/bin/env python3
"""Integrity manifest for tracked source only, never server data or dependencies."""
import argparse
import hashlib
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
def render():
    names=subprocess.check_output(['git','ls-files','-z'],cwd=ROOT).decode().split('\0')
    result=[]
    for name in sorted(filter(None,names)):
        if name=='PROJECT_MANIFEST.sha256': continue
        path=Path(name)
        if path.is_absolute() or '..' in path.parts or set(path.parts)&{'.deps','output','.git','__pycache__'}:
            raise ValueError('Private/generated source entry')
        if path.parts[0]=='artifacts' and name!='artifacts/downloads/.gitkeep': raise ValueError('Private artifact')
        if (ROOT/path).is_symlink(): raise ValueError('Symlink in source')
        data=(ROOT/path).read_bytes()
        if b'\0' not in data:
            try: data.decode('utf-8'); data=data.replace(b'\r\n',b'\n')
            except UnicodeDecodeError: pass
        result.append(hashlib.sha256(data).hexdigest()+'  '+name+'\n')
    return ''.join(result)
if __name__=='__main__':
    p=argparse.ArgumentParser(); g=p.add_mutually_exclusive_group(required=True)
    g.add_argument('--write',action='store_true');g.add_argument('--check',action='store_true');a=p.parse_args()
    text=render(); path=ROOT/'PROJECT_MANIFEST.sha256'
    if a.write: path.write_text(text,encoding='utf-8',newline='\n')
    elif not path.is_file() or path.read_text()!=text: raise SystemExit('FAIL: stale source manifest')
    print('PASS: source manifest')
