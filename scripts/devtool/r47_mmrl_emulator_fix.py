#!/usr/bin/env python3
"""R47 R5: source-pinned migration to MMRL-LSPosed's proven Linux/KVM AVD.

This helper only edits GitHub Actions source and the R47 source gate. No Android
runtime, SDK, Gradle, network, user data, or real/virtual devices are touched.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

BASE = 'c5e2917de7f59694f8b56b98c0ce80849ce63d80'
TREE = 'd86153ee2849fc6656586283fa6d49adcbecbcf4'
ROOT = Path(__file__).resolve().parents[2]
WF = '.github/workflows/r47-ui-acceptance.yml'
GATE = 'scripts/devtool/r47_gate.py'
SOURCE = {
    WF: '3ee8388fc27e543ad36025561993b330da051cd8',
    GATE: '818c0437b7dc961796802baedb941e656e624648',
}
# Populated during the source-pinned overlay build; do not modify at runtime.
POST = {
    WF: '449a663351e016a93cb3960166158c39a42dbd30',
    GATE: '17220e442b35f65d941baba06eabb3a8dce75768',
}


def blob(raw: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()


def once(s: str, old: str, new: str, label: str) -> str:
    actual = s.count(old)
    assert actual == 1, f'R47_R5_ANCHOR_DRIFT:{label}:{actual}'
    return s.replace(old, new, 1)


def transform(rel: str, source: str) -> str:
    if rel == WF:
        s = once(source,
            '  emulator-ui:\n    needs: [host-source]\n    runs-on: macos-15-intel\n',
            '  emulator-ui:\n    needs: [host-source]\n    runs-on: ubuntu-24.04\n', 'runner')
        s = once(s, '          test "$RUNNER_OS" = macOS\n',
            '          test "$RUNNER_OS" = Linux\n', 'os')
        s = once(s, "        run: sdkmanager --install 'platforms;android-35' 'build-tools;35.0.0'\n",
            "        run: sdkmanager --install 'platforms;android-35' 'platforms;android-36' 'build-tools;35.0.0'\n", 'sdk')
        before = '''      - name: Run 11 offline synthetic UI cases and collect seven private previews
        uses: ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
        with:
          api-level: 35
          arch: x86_64
          target: google_apis
          profile: pixel_4
          disable-animations: true
          script: bash scripts/devtool/r47_emulator_ci.sh
'''
        after = '''      # Same AVD configuration as MMRL-LSPosed S09 (successful run 41759587780).
      # This permits only the disposable GitHub-hosted Linux emulator to use KVM.
      - name: Enable KVM on Linux hosted runner
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
      - name: Run 11 offline synthetic UI cases and collect seven private previews
        uses: ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d
        with:
          api-level: 36
          target: google_apis
          arch: x86_64
          profile: pixel_6
          cores: 2
          ram-size: 2048M
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -camera-front none
          script: bash scripts/devtool/r47_emulator_ci.sh
'''
        s = once(s, before, after, 'avd')
        s = once(s,
            "          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_diag.py' -v\n",
            "          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_diag.py' -v\n"
            "          python3 -B -m unittest discover -s scripts/devtool -p 'test_r47_mmrl_emulator.py' -v\n", 'tests')
        return s
    if rel == GATE:
        assert 'import r47_mmrl_emulator_fix' not in source, 'R47_R5_ALREADY_PATCHED'
        s = once(source, "import r47_patch\n", "import r47_patch\nimport r47_mmrl_emulator_fix\n", 'import')
        s = once(s,
            "    assert 'scripts/devtool/r47_verify.py' in workflow\n",
            "    r47_mmrl_emulator_fix.validate_workflow(workflow)\n"
            "    assert 'scripts/devtool/r47_verify.py' in workflow\n", 'gate')
        return s
    raise AssertionError('R47_R5_UNEXPECTED_TARGET:'+rel)


def validate_workflow(text: str) -> None:
    """Fail closed on runner drift, denied KVM access or dropped evidence gates."""
    match = re.search(r'(?ms)^  emulator-ui:\n(.*?)(?=^  [a-zA-Z][\w-]*:\n|\Z)', text)
    assert match, 'R47_R5_EMULATOR_JOB_MISSING'
    job = match.group(1)
    header = '''    needs: [host-source]
    runs-on: ubuntu-24.04
'''
    assert header in job, 'R47_R5_MMRL_HOST_REQUIRED'
    assert 'test "$RUNNER_OS" = Linux' in job and 'test "$(uname -m)" = x86_64' in job
    assert 'api-level: 35' not in job and 'macos-15-intel' not in job and 'pixel_4' not in job
    config = {
        'api-level': '36',
        'target': 'google_apis',
        'arch': 'x86_64',
        'profile': 'pixel_6',
        'cores': '2',
        'ram-size': '2048M',
        'disable-animations': 'true',
        'emulator-options': '-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -camera-front none',
        'script': 'bash scripts/devtool/r47_emulator_ci.sh',
    }
    for key, value in config.items():
        assert re.search(r'^          '+re.escape(key)+r': '+re.escape(value)+r'$', job, re.M), \
            'R47_R5_MMRL_AVD_DRIFT:'+key
    action='ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d'
    assert job.count(action)==1, 'R47_R5_EMULATOR_ACTION_DRIFT'
    kvm = (
        '- name: Enable KVM on Linux hosted runner',
        'if [[ ! -c /dev/kvm ]]; then',
        'R47_KVM_DEVICE_UNAVAILABLE',
        'if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then',
        'sudo chmod a+rw /dev/kvm',
        'R47_KVM_PERMISSION_CHANGE_FAILED',
        'R47_KVM_NOT_ACCESSIBLE',
        'echo R47_KVM_READY',
    )
    for required in kvm:
        assert required in job,'R47_R5_KVM_CHECK_MISSING:'+required
    assert 'sudo udevadm' not in job and '/etc/udev/rules.d/' not in job, 'R47_R6_NO_UDEV_SERVICE_DEPENDENCY'
    assert '-accel off' not in job and '-no-accel' not in job, 'R47_R6_KVM_REQUIRED'
    assert job.index('Enable KVM on Linux hosted runner') < job.index(action), 'R47_R5_KVM_ORDER'
    assert "sdkmanager --install 'platforms;android-35' 'platforms;android-36' 'build-tools;35.0.0'" in job
    assert ':app:assembleDebug :app:assembleDebugAndroidTest' in job, 'R47_R5_HOSTED_BUILD_REQUIRED'
    assert job.index('Compile disposable app and instrumentation APKs') < job.index('Enable KVM on Linux hosted runner')
    assert 'scripts/devtool/r47_verify.py' in job
    assert job.index(action)<job.index('scripts/devtool/r47_verify.py'), 'R47_R5_EVIDENCE_GATE_ORDER'
    assert "if: always()\n        uses: actions/upload-artifact@" in job, 'R47_R5_FAIL_EVIDENCE_UPLOAD'
    assert 'if-no-files-found: error' in job and 'R47_SYNTHETIC_EVIDENCE.json' in job
    assert 'secrets.' not in job and 'pm clear' not in job and 'sudo adb' not in job
    assert "test_r47_mmrl_emulator.py' -v" in text, 'R47_R5_HOST_TEST_NOT_WIRED'


def check(root: Path = ROOT) -> None:
    for rel, expected in POST.items():
        path = root / rel
        assert path.is_file() and not path.is_symlink(), 'R47_R5_POST_MISSING:'+rel
        assert blob(path.read_bytes()) == expected, 'R47_R5_POST_BLOB_DRIFT:'+rel
    validate_workflow((root/WF).read_text(encoding='utf-8'))
    gate = (root/GATE).read_text(encoding='utf-8')
    assert 'r47_mmrl_emulator_fix.validate_workflow(workflow)' in gate, 'R47_R5_GATE_BYPASS'
    print('R47_R5_MMRL_API36_KVM_SOURCE_PASS')


def apply(root: Path = ROOT) -> None:
    def git(*args: str) -> str:
        return subprocess.check_output(['git',*args],cwd=root,text=True).strip()
    assert git('rev-parse','HEAD')==BASE, 'R47_R5_COMMIT_DRIFT'
    assert git('rev-parse','HEAD^{tree}')==TREE, 'R47_R5_TREE_DRIFT'
    # Devtool has already extracted newly added overlay files at this phase.
    # Check only pre-existing tracked sources; global cleanliness was validated by Devtool before extraction.
    assert git('diff','--name-only','--',WF,GATE)=='', 'R47_R5_PREEXISTING_SOURCE_CHANGES'
    originals={}
    for rel, expected in SOURCE.items():
        path=root/rel
        assert path.is_file() and not path.is_symlink(), 'R47_R5_SOURCE_MISSING:'+rel
        raw=path.read_bytes()
        assert blob(raw)==expected, 'R47_R5_SOURCE_BLOB_DRIFT:'+rel
        originals[rel]=raw.decode('utf-8')
    updates={rel:transform(rel,source) for rel,source in originals.items()}
    for rel,updated in updates.items():
        assert blob(updated.encode())==POST[rel], 'R47_R5_TRANSFORM_DRIFT:'+rel
    validate_workflow(updates[WF])
    paths={rel:root/(rel+'.r47r5-tmp') for rel in updates}
    for temp in paths.values():
        assert not temp.exists() and not temp.is_symlink(), 'R47_R5_TMP_COLLISION'
    try:
        for rel,source in updates.items():
            path=root/rel
            temp=paths[rel]
            with temp.open('x',encoding='utf-8') as stream:
                stream.write(source);stream.flush();os.fsync(stream.fileno())
            os.chmod(temp,path.stat().st_mode&0o777)
        for rel,temp in paths.items():
            os.replace(temp,root/rel)
    finally:
        for temp in paths.values():
            if temp.exists():temp.unlink()
    check(root)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    modes=parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--check',action='store_true')
    modes.add_argument('--apply',action='store_true')
    options=parser.parse_args()
    (apply if options.apply else check)()
