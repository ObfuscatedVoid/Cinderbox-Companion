#!/usr/bin/env python3
"""R43-B3 M24 fixture correction; source-bound, test-only, no app changes."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys

BASE = 'b32757f69a0982f03895e96d9168f775cde09211'
TARGET = 'scripts/devtool/test_m24_staged_gate.py'
ORIGINAL_BLOB = '0383cafbc137f0eb7b3c5a8755788b1fc5465e6e'
OLD = """            build=subprocess.run(['kotlinc', str(ROOT/'app/src/main/java/com/sdvsync/local/PlatformStoragePaths.kt'), str(REC), str(harness), '-d', str(jar)],capture_output=True,text=True,timeout=60)"""
NEW = """            # Compile the real R43-B3 recovery dependencies. The only test stub
            # replaces CinderboxLayout's Android-dependent isInstalled() method;
            # production CinderboxPaths, admission and SHA-256 integrity are real.
            layout_stub = root / 'CinderboxLayoutStub.kt'
            layout_stub.write_text(
                'package com.sdvsync.cinderbox\\n'
                'class CinderboxLayout { fun isInstalled(): Boolean = false }\\n'
            )
            dependencies = (
                'app/src/main/java/com/sdvsync/local/PlatformStoragePaths.kt',
                'app/src/main/java/com/sdvsync/cinderbox/CinderboxPaths.kt',
                'app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt',
                'app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt',
            )
            build = subprocess.run(
                ['kotlinc', *(str(ROOT / p) for p in dependencies),
                 str(REC), str(layout_stub), str(harness), '-d', str(jar)],
                capture_output=True, text=True, timeout=60
            )"""

def blob_sha(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\x00' + data).hexdigest()

def main() -> None:
    p = argparse.ArgumentParser()
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--apply', action='store_true')
    p.add_argument('--repo', type=Path, default=Path.cwd())
    args = p.parse_args()
    root = args.repo.resolve()
    target = root / TARGET
    if target.is_symlink() or not target.is_file():
        raise RuntimeError('Missing or linked M24 test file')
    source = target.read_bytes()
    if args.check:
        text = source.decode('utf-8')
        assert OLD not in text and text.count(NEW) == 1
        assert 'class CinderboxLayout { fun isInstalled(): Boolean = false }' in text
        assert 'R43BUpdateAdmission.kt' in text
        print('R43B3_M24_REAL_DEPENDENCIES_COMPILE_FIX_PASS')
        return
    sha = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=root, check=True,
        capture_output=True, text=True).stdout.strip()
    if sha != BASE:
        raise RuntimeError('Wrong exact base: ' + sha)
    if blob_sha(source) != ORIGINAL_BLOB:
        raise RuntimeError('M24 test blob differs from PR #47 source')
    text = source.decode('utf-8')
    if text.count(OLD) != 1:
        raise RuntimeError('M24 compiler invocation anchor changed')
    updated = text.replace(OLD, NEW, 1)
    temp = target.with_name(target.name + '.r43b3-ci-tmp')
    temp.write_text(updated, encoding='utf-8')
    temp.replace(target)
    print('R43B3_M24_ISOLATED_KOTLIN_FIX_APPLIED')

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, AssertionError, OSError, subprocess.CalledProcessError) as e:
        print('R43B3_M24_CI_FIX_REFUSED:' + str(e), file=sys.stderr)
        sys.exit(1)
