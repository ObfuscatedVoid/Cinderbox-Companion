#!/usr/bin/env python3
"""R45/R46 R3: source-pinned Kotlin namespace correction, no Android I/O."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess

BASE = '366330319719a4230f99459c28b5bcfec1d90331'
TREE = 'adad614f6ebbc8c48b6403db10f3e2dd452d4de0'
SCREENS = {
    'app/src/main/java/com/sdvsync/ui/screens/DashboardScreen.kt': '0be6dabbc02a5032bc2b84dd4f7edf30dba1ac27',
    'app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt': '46af973c89755e79c3d477aa906600442e070db3',
    'app/src/main/java/com/sdvsync/ui/screens/ModManagerScreen.kt': 'bdb139671b752cbe527ca06cc1f5fdc665bfa73a',
    'app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt': '4f4fc0efea7ddca5f86e6430f4f3d416ff38db1b',
}
GATE = 'scripts/devtool/r45r2_gate.py'
GATE_BLOB = 'db6f7727c20eb5c2b03abddff1b333b01f212e0c'
WRONG = 'import com.sdvsync.ui.components.R45GuideArea\n'
RIGHT = 'import com.sdvsync.ui.policy.R45GuideArea\n'
GATE_OLD = "    assert 'R45GuidePolicy.forArea(area)' in panel\n"
GATE_NEW = ("    for screen in (games, save, mods, read('app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt')):\n"
            "        assert 'import com.sdvsync.ui.policy.R45GuideArea' in screen, 'R45R3_MISSING_POLICY_IMPORT'\n"
            "        assert 'import com.sdvsync.ui.components.R45GuideArea' not in screen, 'R45R3_WRONG_COMPONENT_IMPORT'\n"
            + GATE_OLD)

def git_blob(data:bytes)->str:
    return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def replace_once(source:str,old:str,new:str,label:str)->str:
    assert source.count(old)==1, f'R45R3_ANCHOR_DRIFT:{label}:{source.count(old)}'
    assert new not in source, f'R45R3_ALREADY_PRESENT:{label}'
    return source.replace(old,new,1)

def transformed(rel:str,source:str)->str:
    if rel in SCREENS:
        return replace_once(source,WRONG,RIGHT,rel)
    assert rel==GATE, 'R45R3_UNKNOWN_PATH:'+rel
    return replace_once(source,GATE_OLD,GATE_NEW,rel)

def check(root:Path)->None:
    for rel in SCREENS:
        path=root/rel
        assert path.is_file() and not path.is_symlink(), 'R45R3_MISSING_OR_LINK:'+rel
        text=path.read_text(encoding='utf-8')
        assert text.count(RIGHT)==1 and WRONG not in text, 'R45R3_BAD_IMPORT:'+rel
        assert 'R45AreaGuide(R45GuideArea.' in text, 'R45R3_GUIDE_MISSING:'+rel
    assert (root/'app/src/main/java/com/sdvsync/ui/policy/R45GuidePolicy.kt').read_text().count('enum class R45GuideArea')==1
    gate=(root/GATE).read_text(encoding='utf-8')
    assert gate.count('R45R3_MISSING_POLICY_IMPORT')==1
    assert gate.count('R45R3_WRONG_COMPONENT_IMPORT')==1
    print('R45_R46_R3_KOTLIN_IMPORT_POSTCONDITION_PASS')

def apply(root:Path)->None:
    def git(*args:str)->str:
        return subprocess.check_output(['git',*args],cwd=root,text=True).strip()
    assert git('rev-parse','HEAD')==BASE, 'R45R3_COMMIT_PIN_MISMATCH'
    assert git('rev-parse','HEAD^{tree}')==TREE, 'R45R3_TREE_PIN_MISMATCH'
    assert git('diff','--name-only','--',*SCREENS.keys(),GATE)=='', 'R45R3_TOUCHED_SOURCE_DIRTY'
    originals={}
    for rel,sha in {**SCREENS,GATE:GATE_BLOB}.items():
        p=root/rel
        assert p.is_file() and not p.is_symlink(), 'R45R3_MISSING_OR_LINK:'+rel
        raw=p.read_bytes()
        assert git_blob(raw)==sha, 'R45R3_BLOB_DRIFT:'+rel
        originals[rel]=raw.decode('utf-8')
    outputs={rel:transformed(rel,src) for rel,src in originals.items()}
    temps={rel:root/(rel+'.r45r3-tmp') for rel in outputs}
    for rel,tmp in temps.items():
        assert not tmp.exists() and not tmp.is_symlink(), 'R45R3_TEMP_COLLISION:'+rel
    try:
        for rel,source in outputs.items():
            tmp=temps[rel]
            with tmp.open('x',encoding='utf-8') as f:
                f.write(source);f.flush();os.fsync(f.fileno())
            os.chmod(tmp,(root/rel).stat().st_mode&0o777)
        for rel,tmp in temps.items():os.replace(tmp,root/rel)
    finally:
        for tmp in temps.values():
            if tmp.exists():tmp.unlink()
    check(root)

if __name__=='__main__':
    ap=argparse.ArgumentParser()
    group=ap.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply',action='store_true')
    group.add_argument('--check',action='store_true')
    a=ap.parse_args()
    (apply if a.apply else check)(Path.cwd().resolve())
