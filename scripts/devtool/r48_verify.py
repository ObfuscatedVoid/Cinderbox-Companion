#!/usr/bin/env python3
"""Independent host-only verifier for R48 real-Activity emulator receipts and PNGs."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import zlib
from r47_capture import png_dimensions
from r48_capture import NAMES, TESTS


def verify(folder: Path, source_sha: str, run_id: str) -> dict:
    assert re.fullmatch(r'[0-9a-f]{40}', source_sha), 'invalid expected SHA'
    assert re.fullmatch(r'[0-9]+', run_id), 'invalid expected run'
    receipt = folder / 'R48_REAL_ACTIVITY_EVIDENCE.json'
    assert receipt.is_file() and not receipt.is_symlink(), 'missing receipt'
    assert receipt.stat().st_size < 64000, 'oversize receipt'
    data = json.loads(receipt.read_bytes())
    fields = {
        'schema_version': 1, 'producer': 'cinderbox-r48-real-activity-hosted-emulator',
        'overall': 'PASS_REAL_ACTIVITY_HOSTED_EMULATOR',
        'source_sha': source_sha, 'run_id': run_id,
        'real_main_activity': True, 'hosted_emulator_only': True, 'fresh_no_account_fixture': True,
        'physical_device_qualified': False, 'talkback_qualified': False,
        'real_steam_qualified': False, 'real_game_data_qualified': False,
        'release_qualified': False, 'publication_allowed': False,
        'automatic_game_update': False, 'test_count': len(TESTS),
        'screenshot_count': len(NAMES),
    }
    for key, expected in fields.items():
        assert data.get(key) == expected, 'invalid receipt field: ' + key
    s = data.get('instrumentation_summary')
    assert isinstance(s, dict), 'missing instrumentation summary'
    assert s.get('expected_tests') == list(TESTS), 'test roster mismatch'
    assert s.get('started_tests') == list(TESTS), 'missing test starts'
    assert s.get('passed_tests') == list(TESTS), 'missing passes'
    assert s.get('failed_tests') == [], 'reported failed tests'
    assert s.get('reported_ok') is True and s.get('terminal_success') is True, 'instrumentation not OK'
    assert s.get('error_categories') == [] and s.get('raw_logs_retained') is False, 'unsafe test logs'
    identity_path = folder / 'APK_IDENTITY.json'
    assert identity_path.is_file() and not identity_path.is_symlink(), 'missing APK identity'
    assert identity_path.stat().st_size < 10000, 'oversized identity'
    identity = json.loads(identity_path.read_bytes())
    assert identity.get('schema_version') == 1 and identity.get('source_sha') == source_sha, 'APK source mismatch'
    assert identity.get('package') == 'com.sdvsync' and identity.get('app_is_debug') is True, 'APK package/type invalid'
    for key in ('app_apk_sha256', 'android_test_apk_sha256', 'installed_base_apk_sha256',
                'debug_signing_certificate_sha256'):
        assert re.fullmatch(r'[0-9a-f]{64}', identity.get(key, '')), 'invalid APK hash'
    assert identity['app_apk_sha256'] == identity['installed_base_apk_sha256'], 'installed APK bytes mismatch'
    assert identity.get('physical_device_qualified') is False and identity.get('release_qualified') is False, 'invalid APK scope'
    records = data.get('screenshots')
    assert isinstance(records, list) and len(records) == len(NAMES), 'screenshot receipt count'
    assert set(p.name for p in folder.glob('*.png')) == set(NAMES), 'unexpected screenshot'
    assert not list(folder.glob('*.tmp')), 'temporary screenshot remains'
    seen = set()
    for filename, record in zip(NAMES, records):
        assert isinstance(record, dict) and record.get('name') == filename, 'unexpected screenshot ordering'
        path = folder / filename
        assert path.is_file() and not path.is_symlink(), 'screenshot missing/linked'
        b = path.read_bytes()
        width, height = png_dimensions(b)
        digest = hashlib.sha256(b).hexdigest()
        assert digest not in seen and digest == record.get('sha256'), 'invalid PNG hash/duplicate'
        assert record.get('width_px') == width and record.get('height_px') == height, 'invalid dimensions'
        seen.add(digest)
    return {'overall': 'PASS_R48_REAL_ACTIVITY_EMULATOR_EVIDENCE_VERIFIED',
            'source_sha': source_sha, 'run_id': run_id, 'screenshots_verified': len(NAMES),
            'tests_verified': len(TESTS), 'physical_device_qualified': False,
            'talkback_qualified': False, 'release_qualified': False}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output-dir', required=True, type=Path)
    p.add_argument('--source-sha', required=True)
    p.add_argument('--run-id', required=True)
    a = p.parse_args()
    try:
        print(json.dumps(verify(a.output_dir, a.source_sha, a.run_id), sort_keys=True))
        return 0
    except (AssertionError, OSError, ValueError, KeyError, TypeError, zlib.error) as err:
        print('R48_EVIDENCE_REFUSED: ' + str(err)[:130], file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
