#!/usr/bin/env python3
"""R47 R3: source-pinned correction for shallow GitHub pull-request checkouts."""
from __future__ import annotations
from pathlib import Path
import argparse
import hashlib
import os
import subprocess

BASE='90f36468ece5ed26c735f98ecacb6fb1ded81cdf'
TREE='6c228e28aa2cd706154906d0ac28c6aefcf89002'
SOURCE={
    'scripts/devtool/r47_patch.py': '92d17844a23a0c9b65043af28a65c30cd6f1dde1',
    'scripts/devtool/test_r47_gate.py': 'e5b6232b5f52138914b342c81c4292232b8c2f99',
    'app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt': '653e54daed26e1bf46037bf86a42dfe7f9a0c378',
}
POST_BLOB='c031c11569f5e4b8aea02604035ad625c9c97064'
ROOT=Path(__file__).resolve().parents[2]

def sha_blob(b:bytes)->str:
    return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

def git(*args:str)->str:
    return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def once(source:str,old:str,new:str)->str:
    assert source.count(old)==1, 'R47_R3_SOURCE_ANCHOR_DRIFT:'+old[:80]
    return source.replace(old,new,1)

def transform(rel:str,s:str)->str:
    if rel=='scripts/devtool/r47_patch.py':
        s=once(s,"BLOB='f4e9882700edb9dcdea3c25af983a582501c9661'\n",
            "BLOB='f4e9882700edb9dcdea3c25af983a582501c9661'\nPOST_BLOB='"+POST_BLOB+"'\n")
        s=once(s,'''def check(root:Path=ROOT)->None:
    original=subprocess.check_output(['git','show',BASE+':'+CONFIG],cwd=root)
    assert sha_blob(original)==BLOB,'R47_BASE_CONFIG_BLOB_DRIFT'
    path=root/CONFIG
    assert path.is_file() and not path.is_symlink(),'R47_CONFIG_MISSING_OR_LINKED'
    assert path.read_text(encoding='utf-8')==transform(original.decode()),'R47_CONFIG_POST_DRIFT'
    c=tomllib.loads(path.read_text())
''','''def check(root:Path=ROOT)->None:
    # GitHub PR checkouts can be shallow merge commits without the original base.
    # Bind validation to the exact committed post-image instead of git history.
    path=root/CONFIG
    assert path.is_file() and not path.is_symlink(),'R47_CONFIG_MISSING_OR_LINKED'
    raw=path.read_bytes()
    assert sha_blob(raw)==POST_BLOB,'R47_CONFIG_POST_BLOB_DRIFT'
    c=tomllib.loads(raw.decode('utf-8'))
''')
    elif rel=='scripts/devtool/test_r47_gate.py':
        s=once(s,'import r47_gate\n','import r47_gate\nimport r47_patch\n')
        s=once(s,'''class R47Contracts(unittest.TestCase):
    def test_exact_source_gate(self):
''','''class R47Contracts(unittest.TestCase):
    def test_shallow_checkout_source_check_requires_no_history(self):
        # PR merged-ref checkout defaults to depth=1; historical BASE is absent.
        with patch.object(r47_patch.subprocess, 'check_output',
                          side_effect=AssertionError('historical git fetch forbidden')):
            r47_patch.check(r47_gate.ROOT)

    def test_compose_interaction_methods_not_imported_as_top_level_functions(self):
        source = (r47_gate.ROOT / 'app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt').read_text()
        for member in ('assertDoesNotExist', 'assertExists', 'onNode'):
            self.assertNotIn('import androidx.compose.ui.test.' + member + '\\n', source)

    def test_exact_source_gate(self):
''')
    elif rel=='app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt':
        # The three names are methods of Compose interactions/rule, not top-level exports.
        for member in ('assertDoesNotExist', 'assertExists', 'onNode'):
            s=once(s, 'import androidx.compose.ui.test.'+member+'\n', '')
    else: raise AssertionError('R47_R3_UNEXPECTED_PATH:'+rel)
    return s

def check()->None:
    c=(ROOT/'scripts/devtool/r47_patch.py').read_text(encoding='utf8')
    test=(ROOT/'scripts/devtool/test_r47_gate.py').read_text(encoding='utf8')
    block=c.split('def check(root:Path=ROOT)->None:',1)[1].split('def apply(root:Path=ROOT)->None:',1)[0]
    assert 'subprocess.check_output' not in block,'R47_R3_GIT_HISTORY_REGRESSION'
    assert 'sha_blob(raw)==POST_BLOB' in block
    assert "POST_BLOB='"+POST_BLOB+"'" in c
    assert 'test_shallow_checkout_source_check_requires_no_history' in test
    assert 'patch.object(r47_patch.subprocess' in test
    src=(ROOT/'app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt').read_text()
    for name in ('assertDoesNotExist', 'assertExists', 'onNode'):
        assert 'import androidx.compose.ui.test.'+name+'\n' not in src, 'R47_R3_INVALID_COMPOSE_IMPORT:'+name
    assert 'test_compose_interaction_methods_not_imported_as_top_level_functions' in test
    print('R47_R3_SHALLOW_PR_CHECKOUT_POST_PASS')

def apply()->None:
    assert git('rev-parse','HEAD')==BASE,'R47_R3_BASE_COMMIT_DRIFT'
    assert git('rev-parse','HEAD^{tree}')==TREE,'R47_R3_BASE_TREE_DRIFT'
    assert git('diff','--name-only','--',*SOURCE)=='' ,'R47_R3_PREEXISTING_CHANGES'
    edits={}
    for rel, sha in SOURCE.items():
        p=ROOT/rel
        assert p.is_file() and not p.is_symlink(),'R47_R3_SOURCE_MISSING_OR_SYMLINK:'+rel
        raw=p.read_bytes()
        assert sha_blob(raw)==sha,'R47_R3_SOURCE_PIN_DRIFT:'+rel
        edits[rel]=transform(rel,raw.decode('utf8'))
    temps={rel: ROOT/(rel+'.r47r3-tmp') for rel in edits}
    for tmp in temps.values(): assert not tmp.exists() and not tmp.is_symlink(),'R47_R3_TEMP_COLLISION'
    try:
        for rel,src in edits.items():
            tmp=temps[rel];dst=ROOT/rel
            with tmp.open('x',encoding='utf8') as f:
                f.write(src); f.flush(); os.fsync(f.fileno())
            os.chmod(tmp,dst.stat().st_mode & 0o777)
        for rel,tmp in temps.items():os.replace(tmp,ROOT/rel)
    finally:
        for tmp in temps.values():
            if tmp.exists(): tmp.unlink()
    check()

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    g=parser.add_mutually_exclusive_group(required=True)
    g.add_argument('--apply',action='store_true');g.add_argument('--check',action='store_true')
    args=parser.parse_args()
    (apply if args.apply else check)()
