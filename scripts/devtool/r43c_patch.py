#!/usr/bin/env python3
"""Strict two-file R43-C migration; exact current GitHub blobs, no silent rebase."""
from __future__ import annotations
import argparse
import hashlib
import os
import pathlib
import subprocess
import tomllib

BASE = '9c6485521f6da05b0a033fc838461a643e8977f7'
TREE = '06cc91fe19f2effdc9d0cee41ebb1befda89932a'
PINNED = {
    '.devtool.toml': '1d6d0471f7c655b0271e7f056dfc931d5717e155',
    'app/src/main/java/com/sdvsync/download/GameDownloadService.kt':
        'a7b9bb920a427b0e2196a6f404f80cbd94dd29cb',
}

def blob_sha(b: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()

def once(s: str, old: str, new: str) -> str:
    assert s.count(old) == 1, f'R43C_ANCHOR_DRIFT:{old[:70]} count={s.count(old)}'
    return s.replace(old, new, 1)

def migrate(rel: str, source: str) -> str:
    if rel.endswith('GameDownloadService.kt'):
        return once(source, '''                    "Unqualified or redirected update service intent"
                }
                check(R43BUpdateAdmission.inspectOwnedStage''', '''                    "Unqualified or redirected update service intent"
                }
                // Service intents do not bypass the R43-C game-launch interlock.
                // No device-qualified exclusion protocol exists: this fails closed.
                R43CUpdateSafety.requireProductionQuiescence()
                check(R43BUpdateAdmission.inspectOwnedStage''')
    if rel == '.devtool.toml':
        s = once(source, '[wrapper.commands.m23-check]\n', '''[wrapper.commands.r43c-check]
description = "R43-C production interlock, storage and cold-recovery source gates (no Gradle)"
workflow = "r43c-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''')
        return once(s, '[targets.cinderbox.workflows]\n', '''[targets.cinderbox.jobs.r43c-protected-safety-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43c_gate.py"]
inputs = ["scripts/devtool/r43c_*.py", "scripts/devtool/test_r43c_gate.py", "app/src/main/java/com/sdvsync/download/R43C*.kt", "app/src/main/java/com/sdvsync/download/R43BUpdateSwap.kt", "app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt", "app/src/main/java/com/sdvsync/download/GameDownloadService.kt", "app/src/test/java/com/sdvsync/download/R43C*.kt", "app/src/androidTest/java/com/sdvsync/qualification/R43C*.kt", "docs/security/M3_RELEASE_POLICY.json", ".devtool.toml"]
metadata = { read_only = true, area = "r43c-fail-closed-update-prequalification", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r43c-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43b3", ref = "job:r43b3-handoff-source", depends_on = ["security"] },
  { id = "r43b4", ref = "job:r43b4-coordinator-source", depends_on = ["r43b3"] },
  { id = "r43c", ref = "job:r43c-protected-safety-source", depends_on = ["r43b4"] },
]
''')
    raise AssertionError('R43C_UNEXPECTED_PATH:' + rel)

def git(root: pathlib.Path, *args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()

def check(root: pathlib.Path) -> None:
    config = (root / '.devtool.toml').read_text()
    service = (root / 'app/src/main/java/com/sdvsync/download/GameDownloadService.kt').read_text()
    assert service.count('R43CUpdateSafety.requireProductionQuiescence()') >= 1
    assert service.index('R43CUpdateSafety.requireProductionQuiescence()') < service.index(
        'R43BUpdateAdmission.inspectOwnedStage(filesDir, ownedUpdate).eligible')
    cfg = tomllib.loads(config)
    assert cfg['wrapper']['commands']['r43c-check']['workflow'] == 'r43c-check'
    wf = cfg['targets']['cinderbox']['workflows']
    assert [step['id'] for step in wf['r43c-check']] == ['security','r43b3','r43b4','r43c']
    assert [step['id'] for step in wf['r43b4-check']] == ['security','r43b3','coordinator']
    job = cfg['targets']['cinderbox']['jobs']['r43c-protected-safety-source']
    assert job['runner'] == 'command' and job['metadata']['read_only'] is True
    print('R43C_PATCH_POSTCONDITION_PASS')

def apply(root: pathlib.Path) -> None:
    assert git(root, 'rev-parse', 'HEAD') == BASE, 'R43C_BASE_DRIFT'
    assert git(root, 'rev-parse', 'HEAD^{tree}') == TREE, 'R43C_TREE_DRIFT'
    assert git(root, 'diff', '--name-only', '--', *PINNED) == '', 'R43C_PREEXISTING_TRACKED_WORKTREE_DIFF'
    result = {}
    for rel, sha in PINNED.items():
        p = root / rel
        assert p.is_file() and not p.is_symlink(), 'R43C_MISSING_OR_LINKED:' + rel
        raw = p.read_bytes()
        assert blob_sha(raw) == sha, 'R43C_SOURCE_PIN_DRIFT:' + rel
        result[rel] = migrate(rel, raw.decode('utf-8'))
    assert tomllib.loads(result['.devtool.toml'])
    # No writes until ALL preimage, anchor, and TOML checks have succeeded.
    temps = {rel: root / (rel + '.r43c-tmp') for rel in result}
    for rel, path in temps.items():
        assert not path.exists() and not path.is_symlink(), 'R43C_TEMP_COLLISION:' + rel
    try:
        for rel, content in result.items():
            p = root / rel
            path = temps[rel]
            with path.open('x', encoding='utf-8') as f:
                f.write(content)
                f.flush()
                os.fsync(f.fileno())
            os.chmod(path, p.stat().st_mode & 0o777)
        for rel, path in temps.items():
            os.replace(path, root / rel)
    finally:
        for path in temps.values():
            if path.exists(): path.unlink()
    check(root)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    exclusive = parser.add_mutually_exclusive_group(required=True)
    exclusive.add_argument('--apply', action='store_true')
    exclusive.add_argument('--check', action='store_true')
    parser.add_argument('--repo', type=pathlib.Path, default=pathlib.Path.cwd())
    args = parser.parse_args()
    (apply if args.apply else check)(args.repo.resolve())
