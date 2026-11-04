#!/usr/bin/env python3
"""R47 R4: exact-commit, exact-tree, source-blob pinned diagnostic-only fix.

Applied atomically under Devtool. No Gradle, physical device, credentials, or network.
No runtime behavior change except saving a sanitized failure summary when R47 fails.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess

BASE='a9eeb8d8940a1a58bf3841e03cb1648b6d465b9d'
TREE='4021fef4174a36ea98f8b97f9732bdd9e83c3e84'
ROOT=Path(__file__).resolve().parents[2]
CAP='scripts/devtool/r47_capture.py'
GATE='scripts/devtool/r47_gate.py'
WF='.github/workflows/r47-ui-acceptance.yml'
SOURCE={
    CAP:'e08d2815f7a2046c23e6bb8f0706b64018951d18',
    GATE:'097f2d5743438bb214a015740b7a399c847e118d',
    WF:'cd9d0fd2f4b890959c8215c44b9534324ce27b21',
}
POST={'scripts/devtool/r47_capture.py': '331086595acd14335c48435704a1c9188ae029f2', 'scripts/devtool/r47_gate.py': '818c0437b7dc961796802baedb941e656e624648', '.github/workflows/r47-ui-acceptance.yml': '3ee8388fc27e543ad36025561993b330da051cd8'}


def git(*args:str)->str:
    return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def sha_blob(b:bytes)->str:
    return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

def once(s:str,old:str,new:str)->str:
    assert s.count(old)==1, 'R47_R4_ANCHOR_DRIFT:'+old[:85]
    return s.replace(old,new,1)

def transform(rel:str,source:str)->str:
    s=source
    if rel==CAP:
        s=once(s,'import zlib\n', 'import zlib\nfrom r47_diag import summarize_instrumentation\n')
        s=once(s, '''        instrument=run('shell','am','instrument','-w','-r','-e','class',CLASS,RUNNER,timeout=420)
        if not parse_result(instrument.decode('utf-8','replace')):
            raise RuntimeError('all eleven synthetic instrumentation cases did not pass')
''','''        # Preserve allowlisted test results even if adb exits nonzero after a test crash.
        # No raw stacktrace, exception message, path or log is persisted or uploaded.
        completed=subprocess.run(adb+['shell','am','instrument','-w','-r','-e',
                                   'class',CLASS,RUNNER],capture_output=True,timeout=420)
        output=completed.stdout.decode('utf-8','replace')
        summary=summarize_instrumentation(output)
        result['instrumentation_summary']=summary
        result['instrumentation_exit_success']=(completed.returncode==0)
        if completed.returncode!=0 or not parse_result(output) or summary['failed_tests']:
            raise RuntimeError('all eleven synthetic instrumentation cases did not pass')
''')
    elif rel==GATE:
        s=once(s, '''    assert 'EXPECTED_TESTS=11' in cap
''','''    assert 'EXPECTED_TESTS=11' in cap
    assert 'from r47_diag import summarize_instrumentation' in cap
    assert "result['instrumentation_summary']=summary" in cap
    assert "or summary['failed_tests']" in cap
    diagnostics=read('scripts/devtool/r47_diag.py')
    assert 'ALLOWED_TESTS' in diagnostics and 'raw_output_retained' in diagnostics
    assert "test_r47_diag.py' -v" in workflow
''')
    elif rel==WF:
        s=once(s, '''          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_gate.py' -v
''', '''          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_gate.py' -v
          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_diag.py' -v
''')
    else: raise AssertionError('R47_R4_UNKNOWN_PATH:'+rel)
    return s

def check(root:Path=ROOT)->None:
    for rel,expected in POST.items():
        path=root/rel
        assert path.is_file() and not path.is_symlink(),'R47_R4_MISSING:'+rel
        assert sha_blob(path.read_bytes())==expected,'R47_R4_POST_DRIFT:'+rel
    assert len(POST)==len(SOURCE),'R47_R4_INCOMPLETE_POST_PIN'
    cap=(root/CAP).read_text()
    assert "result['instrumentation_summary']=summary" in cap
    assert "or summary['failed_tests']" in cap
    wf=(root/WF).read_text()
    assert "test_r47_diag.py' -v" in wf
    print('R47_R4_SANITIZED_INSTRUMENTATION_POST_PASS')

def apply(root:Path=ROOT)->None:
    assert git('rev-parse','HEAD')==BASE,'R47_R4_BASE_COMMIT_DRIFT'
    assert git('rev-parse','HEAD^{tree}')==TREE,'R47_R4_BASE_TREE_DRIFT'
    assert git('diff','--name-only','--',*SOURCE)=='' ,'R47_R4_PREEXISTING_CHANGES'
    changes={}
    for rel,expected in SOURCE.items():
        path=root/rel
        assert path.is_file() and not path.is_symlink(),'R47_R4_MISSING_SOURCE:'+rel
        raw=path.read_bytes()
        assert sha_blob(raw)==expected,'R47_R4_SOURCE_PIN_DRIFT:'+rel
        changes[rel]=transform(rel,raw.decode('utf-8'))
        assert sha_blob(changes[rel].encode())==POST[rel],'R47_R4_GENERATED_DRIFT:'+rel
    temps={rel:root/(rel+'.r47r4-tmp') for rel in changes}
    for tmp in temps.values():assert not tmp.exists() and not tmp.is_symlink(),'R47_R4_TEMP_COLLISION'
    try:
        for rel,data in changes.items():
            tmp=temps[rel];src=root/rel
            with tmp.open('x',encoding='utf-8') as f:
                f.write(data);f.flush();os.fsync(f.fileno())
            os.chmod(tmp,src.stat().st_mode&0o777)
        for rel,tmp in temps.items():os.replace(tmp,root/rel)
    finally:
        for tmp in temps.values():
            if tmp.exists():tmp.unlink()
    check(root)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply',action='store_true')
    group.add_argument('--check',action='store_true')
    args=parser.parse_args()
    (apply if args.apply else check)()
