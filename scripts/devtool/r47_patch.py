#!/usr/bin/env python3
"""Exact-tree R47 Devtool registry patch; no device, Gradle or network actions."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tomllib

BASE='a45b120aaf824042cab3a942f7a6427ef1df818c'
TREE='3753b85ef56af1080c2db929203708000a193e55'
CONFIG='.devtool.toml'
BLOB='f4e9882700edb9dcdea3c25af983a582501c9661'
POST_BLOB='c031c11569f5e4b8aea02604035ad625c9c97064'
ROOT=Path(__file__).resolve().parents[2]

def git(*args:str)->str:
    return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def sha_blob(raw:bytes)->str:
    return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()

def once(text:str, old:str, new:str)->str:
    assert text.count(old)==1, 'R47_SOURCE_ANCHOR_DRIFT:'+old[:60]
    return text.replace(old,new,1)

def transform(s:str)->str:
    s=once(s,'[wrapper.commands.host-check]\n',
"""[wrapper.commands.r47-check]
description = "R47 read-only synthetic UI and accessibility source contracts (no Gradle/device)"
workflow = "r47-check"
target = "cinderbox"

[wrapper.commands.host-check]
""")
    s=once(s,'[targets.cinderbox.workflows]\n',
"""[targets.cinderbox.workflows]
r47-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r44b", ref = "job:r44b-experience-source", depends_on = ["security"] },
  { id = "r45", ref = "job:r45r2-audit-source", depends_on = ["r44b"] },
  { id = "r47", ref = "job:r47-ui-source", depends_on = ["r45"] },
]
""")
    s+="""
[targets.cinderbox.jobs.r47-ui-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r47_gate.py"]
inputs = ["scripts/devtool/r47_*.py", "scripts/devtool/r47_emulator_ci.sh", "scripts/devtool/test_r47_gate.py", "app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt", ".github/workflows/r47-ui-acceptance.yml", "docs/security/M3_RELEASE_POLICY.json", ".devtool.toml"]
metadata = { read_only = true, device_required = false, root_required = false, live_update_enabled = false, release_enabled = false }
"""
    tomllib.loads(s)
    return s

def check(root:Path=ROOT)->None:
    # GitHub PR checkouts can be shallow merge commits without the original base.
    # Bind validation to the exact committed post-image instead of git history.
    path=root/CONFIG
    assert path.is_file() and not path.is_symlink(),'R47_CONFIG_MISSING_OR_LINKED'
    raw=path.read_bytes()
    assert sha_blob(raw)==POST_BLOB,'R47_CONFIG_POST_BLOB_DRIFT'
    c=tomllib.loads(raw.decode('utf-8'))
    assert c['wrapper']['commands']['r47-check']['workflow']=='r47-check'
    assert [x['id'] for x in c['targets']['cinderbox']['workflows']['r47-check']]==['security','r44b','r45','r47']
    job=c['targets']['cinderbox']['jobs']['r47-ui-source']
    assert job['metadata']['read_only'] is True and job['metadata']['device_required'] is False
    print('R47_DEVTOOL_POST_PASS')

def apply(root:Path=ROOT)->None:
    assert git('rev-parse','HEAD')==BASE,'R47_BASE_DRIFT'
    assert git('rev-parse','HEAD^{tree}')==TREE,'R47_TREE_DRIFT'
    assert git('diff','--name-only','--',CONFIG)=='','R47_PREEXISTING_CONFIG_CHANGE'
    original=subprocess.check_output(['git','show',BASE+':'+CONFIG],cwd=root)
    path=root/CONFIG
    assert path.is_file() and not path.is_symlink()
    assert sha_blob(path.read_bytes())==BLOB,'R47_CONFIG_PIN_DRIFT'
    output=transform(original.decode())
    tmp=root/(CONFIG+'.r47-tmp')
    assert not tmp.exists() and not tmp.is_symlink(),'R47_TEMP_COLLISION'
    try:
        with tmp.open('x',encoding='utf-8') as f:
            f.write(output); f.flush(); os.fsync(f.fileno())
        os.chmod(tmp,path.stat().st_mode&0o777)
        os.replace(tmp,path)
    finally:
        if tmp.exists():tmp.unlink()
    check(root)

if __name__=='__main__':
    ap=argparse.ArgumentParser(); choice=ap.add_mutually_exclusive_group(required=True)
    choice.add_argument('--apply',action='store_true'); choice.add_argument('--check',action='store_true')
    opts=ap.parse_args(); (apply if opts.apply else check)()
