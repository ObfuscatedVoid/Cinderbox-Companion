#!/usr/bin/env python3
"""Exact Git-blob, atomic R43-B2 preflight and M24 integrity baseline patch.

Only three existing files are modified; all source anchors must be unique.
Never touches device data, GitHub, APKs, or earlier release evidence.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys

BASE = '9ad42db2c48e83cddd4ce07d4d165761b149f8a8'
BLOBS = {
    '.devtool.toml': 'bd9b44b24841eab3dd9c33ef671252b2bfe8be12',
    'app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt': '6844f2bc5672805c46a2f9fe53cb248a8a9e1240',
    'app/src/main/java/com/sdvsync/download/GameDownloadService.kt': 'ca398a42ed92fca347f6d77d5af83dbba7dbfd8f',
}

WRAPPER = '''[wrapper.commands.r43b2-check]
description = "R43-B2 read-only installed-game admission and staged-ledger isolation"
workflow = "r43b2-check"
target = "cinderbox"

'''
JOB = '''[targets.cinderbox.jobs.r43b2-admission-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43b2_gate.py"]
inputs = ["scripts/devtool/r43b2_*.py", "app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt", "app/src/test/java/com/sdvsync/download/R43BUpdateAdmissionTest.kt", "app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt", "app/src/main/java/com/sdvsync/download/GameDownloadService.kt", ".devtool.toml"]
metadata = { read_only = true, area = "r43b2-preflight-unwired", root_required = false, device_required = false }

'''
FLOW = '''r43b2-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43a", ref = "job:r43-cinderbox-source", depends_on = ["security"] },
  { id = "r43b1", ref = "job:r43b-swap-source", depends_on = ["r43a"] },
  { id = "admission", ref = "job:r43b2-admission-source", depends_on = ["r43b1"] },
]
'''

def replace_once(source: str, old: str, new: str, scope: str) -> str:
    if source.count(old) != 1:
        raise RuntimeError(f'R43B2_ANCHOR_CHANGED:{scope}:matches={source.count(old)}')
    return source.replace(old, new, 1)

def transforms(rel: str, source: str) -> str:
    if rel == '.devtool.toml':
        if '[wrapper.commands.r43b2-check]' in source:
            raise RuntimeError('R43B2_ALREADY_REGISTERED')
        source = replace_once(source, '[wrapper.commands.m23-check]\n',
                              WRAPPER + '[wrapper.commands.m23-check]\n', 'wrapper')
        source = replace_once(source, '[targets.cinderbox.workflows]\n',
                              JOB + '[targets.cinderbox.workflows]\n' + FLOW, 'workflow')
        return source
    if rel.endswith('GameFileIntegrity.kt'):
        source = replace_once(source,
            'phaseProgress: (String, Int, Int, String) -> Unit = { _, _, _, _ -> }): GameIntegrityResult {',
            'phaseProgress: (String, Int, Int, String) -> Unit = { _, _, _, _ -> },\n'
            '                        persistLedger: Boolean = true): GameIntegrityResult {',
            'ledger-parameter')
        source = replace_once(source,
            '        if (errors.isNotEmpty()) return GameIntegrityResult(second.size, errors)\n'
            '        check(privateRoot.isDirectory || privateRoot.mkdirs())',
            '        if (errors.isNotEmpty()) return GameIntegrityResult(second.size, errors)\n'
            '        // A verified stage must not overwrite the last published installation\'s\n'
            '        // private SHA-256 baseline. The ledger is committed after publication.\n'
            '        if (!persistLedger) return GameIntegrityResult(second.size, emptyList())\n'
            '        check(privateRoot.isDirectory || privateRoot.mkdirs())',
            'ledger-write-gate')
        return source
    if rel.endswith('GameDownloadService.kt'):
        source = replace_once(source,
            '                verifyingStage("Local integrity: $phase", done, total, name)\n'
            '            })\n'
            '        if (!report.passed)',
            '                verifyingStage("Local integrity: $phase", done, total, name)\n'
            '            }, persistLedger = false)\n'
            '        if (!report.passed)',
            'm24-staging-does-not-overwrite-original-ledger')
        return source
    raise RuntimeError('R43B2_UNEXPECTED_MODIFICATION:' + rel)

def blob_sha(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--repo', type=Path, default=Path.cwd())
    args = parser.parse_args()
    root = args.repo.resolve()
    files = {k: root / k for k in BLOBS}
    if args.check:
        config = files['.devtool.toml'].read_text()
        integrity = files['app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt'].read_text()
        service = files['app/src/main/java/com/sdvsync/download/GameDownloadService.kt'].read_text()
        for needle in (WRAPPER.strip(), JOB.strip(), FLOW.strip()):
            if config.count(needle) != 1:
                raise RuntimeError('R43B2_CONFIG_POSTCONDITION')
        if ('persistLedger: Boolean = true' not in integrity or
            'if (!persistLedger) return GameIntegrityResult' not in integrity or
            '}, persistLedger = false)' not in service):
            raise RuntimeError('R43B2_LEDGER_ISOLATION_POSTCONDITION')
        print('R43B2_EXACT_SOURCE_CONTRACT_PASS')
        return
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if head != BASE:
        raise RuntimeError('R43B2_BASE_CHANGED:' + head)
    out = {}
    for rel, path in files.items():
        if not path.is_file() or path.is_symlink():
            raise RuntimeError('R43B2_SOURCE_MISSING_OR_LINKED:' + rel)
        original = path.read_bytes()
        actual = blob_sha(original)
        if actual != BLOBS[rel]:
            raise RuntimeError('R43B2_SOURCE_BLOB_CHANGED:' + rel + ':' + actual)
        out[rel] = transforms(rel, original.decode('utf-8')).encode('utf-8')
    for rel, data in out.items():
        path = files[rel]
        tmp = path.with_name(path.name + '.r43b2-tmp')
        if tmp.exists() or tmp.is_symlink():
            raise RuntimeError('R43B2_TEMP_CONFLICT:' + rel)
        try:
            with tmp.open('xb') as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(tmp, path.stat().st_mode & 0o777)
            os.replace(tmp, path)
        finally:
            if tmp.exists():
                tmp.unlink()
    print('R43B2_SOURCE_PATCH_APPLIED')

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError, UnicodeError) as error:
        print('R43B2_SOURCE_REFUSED:', error, file=sys.stderr)
        sys.exit(2)
