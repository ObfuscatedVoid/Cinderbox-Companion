#!/usr/bin/env python3
"""Exact-blob Devtool wiring for isolated R43-B1 swap core; no app mutations."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys

BASE='5324b2ac6fc5a70f710a586994f83f796fee9826'
CONFIG_BLOB='7b8be867b0ca4938ac263c6ecdfb692e5cb468b5'
PATH='.devtool.toml'

WRAPPER='''[wrapper.commands.r43b-check]
description = "R43-B1 isolated update transaction source tests (host only, no device writes)"
workflow = "r43b-check"
target = "cinderbox"

'''
JOB='''[targets.cinderbox.jobs.r43b-swap-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43b_swap_gate.py"]
inputs = ["scripts/devtool/r43b_*.py", "app/src/main/java/com/sdvsync/download/R43BUpdateSwap.kt", "app/src/test/java/com/sdvsync/download/R43BUpdateSwapTest.kt", ".devtool.toml"]
metadata = { read_only = true, area = "r43b-swap-unwired", root_required = false, device_required = false }

'''
WORKFLOW='''r43b-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43a", ref = "job:r43-cinderbox-source", depends_on = ["security"] },
  { id = "r43b", ref = "job:r43b-swap-source", depends_on = ["r43a"] },
]
'''

def transform(src):
    for anchor in ('[wrapper.commands.m23-check]\n',
                   '[targets.cinderbox.workflows]\n'):
        if src.count(anchor) != 1: raise RuntimeError('R43B_CONFIG_ANCHOR_MISMATCH:'+anchor)
    if '[wrapper.commands.r43b-check]' in src or '[targets.cinderbox.jobs.r43b-swap-source]' in src:
        raise RuntimeError('R43B_ALREADY_CONFIGURED')
    src=src.replace('[wrapper.commands.m23-check]\n',WRAPPER+'[wrapper.commands.m23-check]\n',1)
    src=src.replace('[targets.cinderbox.workflows]\n',JOB+'[targets.cinderbox.workflows]\n'+WORKFLOW,1)
    return src

def main():
    ap=argparse.ArgumentParser()
    m=ap.add_mutually_exclusive_group(required=True)
    m.add_argument('--apply',action='store_true')
    m.add_argument('--check',action='store_true')
    ap.add_argument('--repo',type=Path,default=Path.cwd())
    args=ap.parse_args()
    root=args.repo.resolve()
    cfg=root/PATH
    if not cfg.is_file() or cfg.is_symlink(): raise RuntimeError('CONFIG_MISSING_OR_LINKED')
    if args.check:
        s=cfg.read_text()
        for x in (WRAPPER.strip(), JOB.strip(), WORKFLOW.strip()):
            if s.count(x)!=1: raise RuntimeError('R43B_CONFIG_POSTCONDITION_FAILED')
        print('R43B_DEVTOOL_CONFIG_PASS')
        return
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    if commit!=BASE: raise RuntimeError('R43B_BASE_COMMIT_CHANGED:'+commit)
    content=cfg.read_bytes()
    git_blob=hashlib.sha1(b'blob '+str(len(content)).encode()+b'\0'+content).hexdigest()
    if git_blob!=CONFIG_BLOB: raise RuntimeError('R43B_CONFIG_SOURCE_CHANGED:'+git_blob)
    new=transform(content.decode('utf-8')).encode('utf-8')
    tmp=cfg.with_name(cfg.name+'.r43b-tmp')
    if tmp.exists() or tmp.is_symlink(): raise RuntimeError('R43B_CONFIG_TEMP_CONFLICT')
    try:
        with tmp.open('xb') as f:
            f.write(new); f.flush(); os.fsync(f.fileno())
        os.chmod(tmp,cfg.stat().st_mode&0o777)
        os.replace(tmp,cfg)
    finally: tmp.unlink(missing_ok=True)
    print('R43B_DEVTOOL_CONFIG_APPLIED')

if __name__=='__main__':
    try:main()
    except (OSError,RuntimeError,subprocess.CalledProcessError) as e:
        print('R43B_DEVTOOL_REFUSED:',str(e),file=sys.stderr)
        sys.exit(2)
