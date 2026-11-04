#!/usr/bin/env python3
"""R47 R6: source-pinned GitHub Ubuntu KVM initialization repair.

The patch changes only the hosted CI preflight and its source-contract tests.
No Gradle, emulator, root, or device operation is run in Termux.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess

BASE = 'b19df0062f4df9815b9c40a84eb9fbcd35c995a4'
TREE = 'f8dba28b744de132c5bbadb27b98e4b782b387f3'
ROOT = Path(__file__).resolve().parents[2]
WF = '.github/workflows/r47-ui-acceptance.yml'
HELPER = 'scripts/devtool/r47_mmrl_emulator_fix.py'
TEST = 'scripts/devtool/test_r47_mmrl_emulator.py'
DOC = 'docs/devtool/R47_R5_MMRL_EMULATOR.md'
SOURCE = {
    WF: 'cd9dfb5054912603cc39ff933ece3408edaceffd',
    HELPER: 'f3b1f4f0d99f4800603cdd102560752ca7bbe61f',
    TEST: 'cfa8e898d329ecc5be68db69195b3a369f86b929',
    DOC: '5d2a6aa435cfb9d1ce37867dfa52d5778b020bfd',
}
POST = {'.github/workflows/r47-ui-acceptance.yml': '449a663351e016a93cb3960166158c39a42dbd30', 'scripts/devtool/r47_mmrl_emulator_fix.py': '47f6e9198d12d43e6a3f0f3eb4b2e151c6246bac', 'scripts/devtool/test_r47_mmrl_emulator.py': '875eab6593734f26fa04ca9388fa730d3477854a', 'docs/devtool/R47_R5_MMRL_EMULATOR.md': '65db76306fdbbfb5c55866c34100a32ac99d9f0c'}

OLD_KVM = '''      - name: Enable KVM on Linux hosted runner
        shell: bash
        run: |
          set -euo pipefail
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
          test -c /dev/kvm
          test -r /dev/kvm && test -w /dev/kvm
'''
NEW_KVM = '''      - name: Enable KVM on Linux hosted runner
        shell: bash
        run: |
          set -euo pipefail
          # Avoid relying on the presence of a running udev daemon.
          # Never create a KVM device or silently downgrade to software emulation.
          if [[ ! -c /dev/kvm ]]; then
            echo R47_KVM_DEVICE_UNAVAILABLE >&2
            exit 1
          fi
          if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
            sudo chmod a+rw /dev/kvm || {
              echo R47_KVM_PERMISSION_CHANGE_FAILED >&2
              exit 1
            }
          fi
          if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
            echo R47_KVM_NOT_ACCESSIBLE >&2
            exit 1
          fi
          echo R47_KVM_READY
'''
NEW_KVM_REQUIREMENTS = '''    kvm = (
        '- name: Enable KVM on Linux hosted runner',
        'if [[ ! -c /dev/kvm ]]; then',
        'R47_KVM_DEVICE_UNAVAILABLE',
        'if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then',
        'sudo chmod a+rw /dev/kvm',
        'R47_KVM_PERMISSION_CHANGE_FAILED',
        'R47_KVM_NOT_ACCESSIBLE',
        'echo R47_KVM_READY',
    )
'''
OLD_TEST_KVM = '''        for key in ('Enable KVM on Linux hosted runner',
                    'sudo udevadm control --reload-rules',
                    'sudo udevadm trigger --name-match=kvm',
                    'test -c /dev/kvm',
                    'test -r /dev/kvm && test -w /dev/kvm'):
'''
NEW_TEST_KVM = '''        for key in ('Enable KVM on Linux hosted runner',
                    'if [[ ! -c /dev/kvm ]]; then',
                    'R47_KVM_DEVICE_UNAVAILABLE',
                    'sudo chmod a+rw /dev/kvm',
                    'R47_KVM_PERMISSION_CHANGE_FAILED',
                    'R47_KVM_NOT_ACCESSIBLE',
                    'R47_KVM_READY'):
'''

def blob(data: bytes)->str:
    return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def once(source: str, before: str, after: str, label: str)->str:
    assert source.count(before)==1, 'R47_R6_ANCHOR_DRIFT:'+label
    return source.replace(before,after,1)

def transform(rel: str, original: str)->str:
    if rel==DOC:
        return once(original,
            '- GitHub-hosted Ubuntu 24.04 `x86_64`, KVM access enabled by udev with a\n'
            '  fail-closed device check *before* starting the emulator.\n',
            '- GitHub-hosted Ubuntu 24.04 `x86_64`, KVM on a pre-existing\n'
            '  `/dev/kvm` character device with fail-closed accessibility checks.\n'
            '  R6 initializes permissions directly if needed, without a udev daemon.\n',
            'doc_kvm')
    if rel==WF:
        s=once(original,OLD_KVM,NEW_KVM,'workflow')
        return once(s,
            "          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_mmrl_emulator.py' -v\n",
            "          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_mmrl_emulator.py' -v\n"
            "          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_r6_kvm.py' -v\n",'host_test_wiring')
    if rel==HELPER:
        s=once(original,OLD_KVM,NEW_KVM,'history_transformation')
        start=s.index('    kvm = (\n')
        end=s.index('    for required in kvm:\n',start)
        s=s[:start]+NEW_KVM_REQUIREMENTS+s[end:]
        s=once(s,
            "    assert job.index('Enable KVM on Linux hosted runner') < job.index(action), 'R47_R5_KVM_ORDER'\n",
            "    assert 'sudo udevadm' not in job and '/etc/udev/rules.d/' not in job, 'R47_R6_NO_UDEV_SERVICE_DEPENDENCY'\n"
            "    assert '-accel off' not in job and '-no-accel' not in job, 'R47_R6_KVM_REQUIRED'\n"
            "    assert job.index('Enable KVM on Linux hosted runner') < job.index(action), 'R47_R5_KVM_ORDER'\n",'validate')
        return once(s,
            "    WF: 'cd9dfb5054912603cc39ff933ece3408edaceffd',\n    GATE: '17220e442b35f65d941baba06eabb3a8dce75768',\n",
            "    WF: '"+POST[WF]+"',\n    GATE: '17220e442b35f65d941baba06eabb3a8dce75768',\n",'post_pin')
    if rel==TEST:
        s=once(original,OLD_TEST_KVM,NEW_TEST_KVM,'test_requirements')
        return once(s,
            "        self.assertIn('test \"$(uname -m)\" = x86_64',self.source)\n",
            "        self.assertIn('test \"$(uname -m)\" = x86_64',self.source)\n"
            "\n    def test_kvm_preflight_does_not_depend_on_udev_or_fallback(self):\n"
            "        for forbidden in ('sudo udevadm', '/etc/udev/rules.d/', '-accel off', '-no-accel'):\n"
            "            self.assertNotIn(forbidden,self.source)\n"
            "        for marker in ('R47_KVM_DEVICE_UNAVAILABLE', 'R47_KVM_PERMISSION_CHANGE_FAILED',\n"
            "                       'R47_KVM_NOT_ACCESSIBLE', 'R47_KVM_READY'):\n"
            "            self.assertIn(marker,self.source)\n",'test_no_udev')
    raise AssertionError('R47_R6_UNAPPROVED_PATH:'+rel)

def check(root:Path=ROOT)->None:
    import sys
    sys.path.insert(0,str(root/'scripts/devtool'))
    import r47_mmrl_emulator_fix as prior
    for rel,expected in POST.items():
        p=root/rel
        assert p.is_file() and not p.is_symlink(),'R47_R6_MISSING:'+rel
        assert blob(p.read_bytes())==expected,'R47_R6_POST_BLOB_DRIFT:'+rel
    prior.check(root)
    print('R47_R6_UBUNTU_KVM_SOURCE_PASS')

def apply(root:Path=ROOT)->None:
    def git(*args)->str:
        return subprocess.check_output(['git',*args],cwd=root,text=True).strip()
    assert git('rev-parse','HEAD')==BASE,'R47_R6_BASE_DRIFT'
    assert git('rev-parse','HEAD^{tree}')==TREE,'R47_R6_TREE_DRIFT'
    assert git('diff','--name-only','--',*SOURCE)=='','R47_R6_PREEXISTING_SOURCE_CHANGE'
    outputs={}
    for rel,expected in SOURCE.items():
        p=root/rel
        assert p.is_file() and not p.is_symlink(),'R47_R6_SOURCE_MISSING:'+rel
        raw=p.read_bytes()
        assert blob(raw)==expected,'R47_R6_SOURCE_BLOB_DRIFT:'+rel
        output=transform(rel,raw.decode())
        assert blob(output.encode())==POST[rel],'R47_R6_TRANSFORM_DRIFT:'+rel
        outputs[rel]=output
    temporary={rel:root/(rel+'.r47r6-tmp') for rel in outputs}
    for p in temporary.values():
        assert not p.exists() and not p.is_symlink(),'R47_R6_TMP_COLLISION'
    try:
        for rel,updated in outputs.items():
            original=root/rel;temp=temporary[rel]
            with temp.open('x',encoding='utf-8') as stream:
                stream.write(updated);stream.flush();os.fsync(stream.fileno())
            os.chmod(temp,original.stat().st_mode&0o777)
        for rel,tmp in temporary.items():os.replace(tmp,root/rel)
    finally:
        for tmp in temporary.values():
            if tmp.exists():tmp.unlink()
    check(root)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply',action='store_true')
    group.add_argument('--check',action='store_true')
    options=parser.parse_args()
    (apply if options.apply else check)()
