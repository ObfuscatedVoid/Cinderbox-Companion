#!/usr/bin/env python3
"""Collect bounded, privacy-safe REAL MainActivity emulator evidence (never device)."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zlib
from r47_capture import png_dimensions

CLASS = 'com.sdvsync.qualification.R48RealActivityDeviceTest'
RUNNER = 'com.sdvsync.test/androidx.test.runner.AndroidJUnitRunner'
TESTS = (
    'realOfflineFiveTabNavigationAndPrivatePreviews',
    'realHomeShortcutsAndNestedBackNavigation',
    'realPreLoginSettingsBackRequiresNoSteam',
    'realOperationCenterReadOnlyReturn',
)
NAMES = tuple(f'r48-real-{name}-320dp-fs160.png' for name in ('home', 'game', 'saves', 'mods', 'more'))


def summarize(output: str) -> dict:
    """Read only allowlisted test method identifiers, never raw device traces."""
    known = set(TESTS)
    started, passed, failed = set(), set(), set()
    active = None
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: test='):
            name = line.partition('=')[2].strip()
            active = name if name in known else None
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = line.partition(': ')[2].strip()
            if active:
                if code == '1': started.add(active)
                elif code == '0': passed.add(active)
                elif code in ('-1', '-2'): failed.add(active)
            active = None
    passed -= failed
    categories = {
        'assertion': ('AssertionError', 'AssertionFailedError', 'ComparisonFailure'),
        'compose_timeout': ('ComposeTimeoutException', 'ComposeNotIdleException'),
        'instrumentation_crash': ('Process crashed', 'INSTRUMENTATION_RESULT: shortMsg=', 'INSTRUMENTATION_FAILED'),
        'instrumentation_abort': ('INSTRUMENTATION_ABORTED',),
    }
    return {
        'expected_tests': list(TESTS),
        'started_tests': [x for x in TESTS if x in started],
        'passed_tests': [x for x in TESTS if x in passed],
        'failed_tests': [x for x in TESTS if x in failed],
        'reported_ok': bool(re.search(r'(?m)^OK \(4 tests\)\s*$', output)),
        'terminal_success': bool(re.search(r'(?m)^INSTRUMENTATION_CODE: -1\s*$', output)),
        'error_categories': [k for k, needles in categories.items() if any(n in output for n in needles)],
        'raw_logs_retained': False,
    }


def _run(adb: list[str], *args: str, timeout: int = 60) -> bytes:
    ret = subprocess.run(adb + list(args), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if ret.returncode:
        raise RuntimeError('adb command refused')
    return ret.stdout


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', required=True)
    p.add_argument('--output-dir', required=True, type=Path)
    p.add_argument('--source-sha', required=True)
    p.add_argument('--run-id', required=True)
    a = p.parse_args()
    a.output_dir.mkdir(parents=True, exist_ok=True)
    evidence = {
        'schema_version': 1,
        'producer': 'cinderbox-r48-real-activity-hosted-emulator',
        'overall': 'REFUSED',
        'source_sha': a.source_sha,
        'run_id': a.run_id,
        'real_main_activity': True,
        'hosted_emulator_only': True,
        'fresh_no_account_fixture': True,
        'physical_device_qualified': False,
        'talkback_qualified': False,
        'real_steam_qualified': False,
        'real_game_data_qualified': False,
        'release_qualified': False,
        'publication_allowed': False,
        'automatic_game_update': False,
    }
    for f in NAMES:
        (a.output_dir / f).unlink(missing_ok=True)
        (a.output_dir / (f + '.tmp')).unlink(missing_ok=True)
    adb = ['adb', '-s', a.serial]
    try:
        if a.serial != 'emulator-5554':
            raise RuntimeError('unapproved emulator serial')
        if not re.fullmatch(r'[0-9a-f]{40}', a.source_sha) or not re.fullmatch(r'[0-9]+', a.run_id):
            raise ValueError('invalid source/run identity')
        git = subprocess.run(['git', 'rev-parse', 'HEAD'], capture_output=True, text=True, timeout=10)
        if git.returncode or git.stdout.strip() != a.source_sha:
            raise ValueError('source commit mismatch')
        if _run(adb, 'get-state').strip() != b'device':
            raise RuntimeError('emulator not connected')
        if _run(adb, 'shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
            raise RuntimeError('physical device refused')
        if b'/base.apk' not in _run(adb, 'shell', 'pm', 'path', 'com.sdvsync'):
            raise RuntimeError('target APK missing')
        _run(adb, 'shell', 'run-as', 'com.sdvsync', 'id')
        ret = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', CLASS, RUNNER],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=600)
        summary = summarize(ret.stdout.decode('utf-8', 'replace'))
        evidence['instrumentation_summary'] = summary
        if (ret.returncode != 0 or not summary['reported_ok'] or not summary['terminal_success']
                or summary['error_categories'] or summary['failed_tests']
                or len(summary['started_tests']) != len(TESTS)
                or len(summary['passed_tests']) != len(TESTS)):
            raise RuntimeError('real activity instrumentation did not pass all four tests')
        screenshots = []
        unique_hashes = set()
        buffers = []
        for filename in NAMES:
            raw = _run(adb, 'exec-out', 'run-as', 'com.sdvsync', 'cat', 'files/' + filename)
            width, height = png_dimensions(raw)
            digest = hashlib.sha256(raw).hexdigest()
            if digest in unique_hashes:
                raise RuntimeError('duplicate real activity screenshot')
            unique_hashes.add(digest)
            buffers.append((filename, raw))
            screenshots.append({'name': filename, 'width_px': width, 'height_px': height, 'sha256': digest})
        for filename, raw in buffers:
            temp = a.output_dir / (filename + '.tmp')
            temp.write_bytes(raw)
            os.replace(temp, a.output_dir / filename)
        evidence.update(overall='PASS_REAL_ACTIVITY_HOSTED_EMULATOR', test_count=len(TESTS),
                        screenshot_count=len(NAMES), screenshots=screenshots)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired, zlib.error) as err:
        for f in NAMES:
            (a.output_dir / f).unlink(missing_ok=True)
            (a.output_dir / (f + '.tmp')).unlink(missing_ok=True)
        # Do not write raw adb output, account identifiers or exception strings to evidence.
        evidence['failure_category'] = type(err).__name__
    (a.output_dir / 'R48_REAL_ACTIVITY_EVIDENCE.json').write_text(
        json.dumps(evidence, sort_keys=True, indent=2) + '\n', encoding='utf-8'
    )
    print(json.dumps({k: v for k, v in evidence.items() if k != 'screenshots'}, sort_keys=True))
    return 0 if evidence['overall'] == 'PASS_REAL_ACTIVITY_HOSTED_EMULATOR' else 1


if __name__ == '__main__':
    raise SystemExit(main())
