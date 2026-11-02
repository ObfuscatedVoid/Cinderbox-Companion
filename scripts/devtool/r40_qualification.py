#!/usr/bin/env python3
"""R40: fail-closed JUnit, ephemeral Android emulator and source-bound evidence.

All APK builds and instrumented Android operations run exclusively on GitHub Actions.
No keys, passwords, account data or unfiltered Android logs enter public receipts.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO = 'Mikeyphw/Cinderbox-Companion'
SOURCE = re.compile(r'[a-f0-9]{40}\Z')
HEX = re.compile(r'[a-f0-9]{64}\Z')
PKG = 'com.sdvsync'
MAX_FILE = 8 * 1024 * 1024

class Refused(ValueError):
    pass

def insist(value: bool, message: str) -> None:
    if not value:
        raise Refused(message)

def put(file: Path, payload: dict) -> None:
    insist(not file.exists() and not file.is_symlink(), 'R40_RECEIPT_OVERWRITE')
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(payload, sort_keys=True, indent=2) + '\n', encoding='utf-8')

def get(file: Path) -> dict:
    insist(file.is_file() and not file.is_symlink() and file.stat().st_size < MAX_FILE, 'R40_RECORD_INVALID')
    obj = json.loads(file.read_text(encoding='utf-8'))
    insist(type(obj) is dict, 'R40_RECORD_WRONG_TYPE')
    return obj

def sha256(file: Path) -> str:
    insist(file.is_file() and not file.is_symlink() and 0 < file.stat().st_size < 200 * 1024 * 1024, 'R40_FILE_INVALID')
    h = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def identity(source: str, run: str, attempt: str) -> dict:
    insist(bool(SOURCE.fullmatch(source)), 'R40_SOURCE_INVALID')
    insist(run.isascii() and run.isdecimal() and 0 < int(run) < 10**20, 'R40_RUN_INVALID')
    insist(attempt.isascii() and attempt.isdecimal() and 0 < int(attempt) <= 1000, 'R40_ATTEMPT_INVALID')
    return dict(repository=REPO, source_commit=source, run_id=int(run), attempt=int(attempt))

def junit(path: Path, *, source: str, run: str, attempt: str) -> dict:
    ident = identity(source, run, attempt)
    files = sorted(path.glob('TEST-*.xml'))
    insist(0 < len(files) <= 512, 'R40_JUNIT_REPORTS_MISSING_OR_EXCESSIVE')
    cases = set()
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for file in files:
        insist(file.is_file() and not file.is_symlink() and 0 < file.stat().st_size <= MAX_FILE, 'R40_JUNIT_FILE_INVALID')
        root = ET.parse(file).getroot()
        suites = [root] if root.tag == 'testsuite' else root.findall('testsuite') if root.tag == 'testsuites' else []
        insist(bool(suites), 'R40_JUNIT_SUITE_INVALID')
        for suite in suites:
            records = suite.findall('testcase')
            expected = int(suite.attrib.get('tests', '-1'))
            insist(expected == len(records), 'R40_JUNIT_TEST_COUNT_MISMATCH')
            counts = dict(tests=len(records), failures=0, errors=0, skipped=0)
            for record in records:
                ident_case = (record.attrib.get('classname'), record.attrib.get('name'))
                insist(all(ident_case) and ident_case not in cases, 'R40_JUNIT_DUPLICATE_OR_UNNAMED_TEST')
                cases.add(ident_case)
                for name, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
                    counts[name] += int(record.find(tag) is not None)
            for key in counts:
                insist(int(suite.attrib.get(key, '0')) == counts[key] if key != 'tests' else True,
                       'R40_JUNIT_SUITE_SUMMARY_MISMATCH')
                totals[key] += counts[key]
    insist(totals['tests'] > 0 and totals['failures'] == 0 and totals['errors'] == 0 and totals['skipped'] == 0,
           'R40_JUNIT_NOT_ALL_PASSED')
    return dict(schema_version=1, kind='r40-parsed-junit', **ident, status='PASS',
                report_files=len(files), **totals, release_qualified=False)

def instrument(path: Path, *, source: str, run: str, attempt: str, expected: int) -> dict:
    ident = identity(source, run, attempt)
    insist(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_FILE, 'R40_INSTRUMENT_FILE_INVALID')
    output = path.read_text(encoding='utf-8', errors='replace')
    oks = [int(x) for x in re.findall(r'(?m)^OK \((\d+) tests?\)\s*$', output)]
    insist(len(oks) == 1 and oks[0] == expected and expected >= 1, 'R40_INSTRUMENT_TEST_COUNT_INVALID')
    insist(bool(re.search(r'(?m)^INSTRUMENTATION_CODE: -1\s*$', output)), 'R40_INSTRUMENT_RUN_FAILED')
    insist('FAILURES!!!' not in output and 'INSTRUMENTATION_ABORTED' not in output and
           'shortMsg=' not in output and 'Process crashed.' not in output,
           'R40_INSTRUMENT_REPORTED_FAILURE')
    return dict(schema_version=1, kind='r40-emulator-instrumentation', **ident,
                status='PASS', tests=oks[0], device_scope='github-ephemeral-android-emulator',
                physical_device_qualified=False, release_qualified=False)

def installed(apk: Path, *, source: str, run: str, attempt: str, serial: str) -> dict:
    ident = identity(source, run, attempt)
    insist(bool(re.fullmatch(r'emulator-\d{4,5}', serial)), 'R40_NON_EMULATOR_REFUSED')
    expected = sha256(apk)
    def adb(*args):
        try:
            p = subprocess.run(['adb', '-s', serial, *args], text=True,
                               capture_output=True, timeout=80, check=False)
        except (OSError, subprocess.TimeoutExpired):
            raise Refused('R40_ADB_UNAVAILABLE') from None
        insist(p.returncode == 0, 'R40_ADB_COMMAND_FAILED')
        return p.stdout.strip()
    devices = adb('get-state')
    insist(devices == 'device', 'R40_EMULATOR_OFFLINE')
    paths = [s[len('package:'):] for s in adb('shell', 'pm', 'path', PKG).splitlines() if s.startswith('package:')]
    insist(len(paths) == 1 and paths[0].startswith('/data/app/') and paths[0].endswith('/base.apk') and
           not any(c in paths[0] for c in (' ', '\n', '\r', '\t', "'", '"', ';')), 'R40_BASE_APK_PATH_UNSAFE')
    remote = adb('shell', 'sha256sum', paths[0]).split()
    insist(len(remote) == 2 and remote[0] == expected, 'R40_INSTALLED_APK_DIGEST_MISMATCH')
    return dict(schema_version=1, kind='r40-emulator-signed-candidate-install', **ident,
                status='PASS', package=PKG, apk_sha256=expected,
                device_scope='github-ephemeral-android-emulator', physical_device_qualified=False,
                release_qualified=False)

def finalize(folder: Path, *, source: str, run: str, attempt: str) -> dict:
    ident = identity(source, run, attempt)
    u = get(folder/'unit/R40_JUNIT.json')
    e = get(folder/'emulator/R40_EMULATOR.json')
    s = get(folder/'signed/M21_RELEASE_CANDIDATE.json')
    d = get(folder/'signed-emulator/R40_SIGNED_EMULATOR.json')
    for obj,kind in ((u,'r40-parsed-junit'),(e,'r40-emulator-instrumentation'),
                     (d,'r40-emulator-signed-candidate-install')):
        insist(obj.get('schema_version') == 1 and obj.get('kind') == kind, 'R40_CROSS_JOB_RECORD_KIND_INVALID')
        insist(all(obj.get(k) == v for k,v in ident.items()), 'R40_CROSS_JOB_IDENTITY_MISMATCH')
        insist(obj.get('status') == 'PASS', 'R40_CROSS_JOB_NONPASS')
    xml = junit(folder/'unit', source=source, run=run, attempt=attempt)
    insist(all(u.get(k) == xml[k] for k in ('tests','failures','errors','skipped','report_files')),
           'R40_JUNIT_RECEIPT_DIFFERS_FROM_ORIGINAL_XML')
    insist(s.get('schema_version') == 1 and s.get('producer') == 'cinderbox-m21-manual-release-candidate' and
           s.get('variant') == 'release' and s.get('package') == PKG and
           s.get('installed') is False and s.get('automatic_publication') is False and
           isinstance(s.get('version_code'),int) and s['version_code'] > 0 and
           s.get('repository') == REPO and s.get('source_commit') == source and
           s.get('workflow_run_id') == int(run) and s.get('release_signed') is True and
           s.get('signature_verified') is True and s.get('publication_allowed') is False and
           s.get('release_qualified') is False, 'R40_RELEASE_RECEIPT_INVALID')
    insist(s.get('apk_sha256') == d.get('apk_sha256') == sha256(folder/'signed/CinderboxCompanion-release-candidate.apk'),
           'R40_RELEASE_DIGEST_MISMATCH')
    sums = (folder/'signed/SHA256SUMS').read_text('utf-8')
    insist(sums == f"{s['apk_sha256']}  CinderboxCompanion-release-candidate.apk\n", 'R40_RELEASE_SUMS_MISMATCH')
    insist(u['tests'] > 0 and u['failures'] == 0 and u['errors'] == 0 and u['skipped'] == 0 and
           e['tests'] >= 1 and e['device_scope'] == d['device_scope'] == 'github-ephemeral-android-emulator',
           'R40_TEST_OR_DEVICE_SCOPE_INVALID')
    insist(s.get('release_certificate_sha256') != s.get('debug_certificate_sha256') and
           bool(HEX.fullmatch(str(s.get('release_certificate_sha256','')))), 'R40_SIGNER_PIN_INVALID')
    return dict(schema_version=1, kind='r40-candidate-evidence-crosscheck', **ident,
                overall='PASS_R40_HOSTED_CANDIDATE_EVIDENCE', unit_tests=u['tests'],
                emulator_tests=e['tests'], release_apk_sha256=s['apk_sha256'],
                signing_cert_sha256=s['release_certificate_sha256'],
                github_attestation_verified_separately=True,
                remote_authenticated_by='github-actions-oidc-sigstore',
                device_scope='ephemeral-emulator-only', physical_device_qualified=False,
                release_qualified=False, publication_allowed=False)

def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    cmd = p.add_subparsers(dest='command', required=True)
    for name in ['junit','instrument','installed','finalize']:
        t = cmd.add_parser(name)
        t.add_argument('--source', required=True)
        t.add_argument('--run', required=True)
        t.add_argument('--attempt', required=True)
        t.add_argument('--output', required=True, type=Path)
        if name == 'junit':t.add_argument('--reports', required=True, type=Path)
        if name == 'instrument':
            t.add_argument('--input', required=True, type=Path)
            t.add_argument('--expected', type=int, required=True)
        if name == 'installed':
            t.add_argument('--apk', required=True, type=Path)
            t.add_argument('--serial', required=True)
        if name == 'finalize':t.add_argument('--folder', required=True, type=Path)
    a = p.parse_args(argv)
    try:
        args = dict(source=a.source, run=a.run, attempt=a.attempt)
        if a.command == 'junit': doc = junit(a.reports, **args)
        elif a.command == 'instrument': doc = instrument(a.input, expected=a.expected, **args)
        elif a.command == 'installed': doc = installed(a.apk, serial=a.serial, **args)
        else: doc = finalize(a.folder, **args)
        put(a.output, doc)
        print(doc.get('overall', doc['status'] if 'status' in doc else 'PASS_R40'))
        return 0
    except (Refused, OSError, ValueError, ET.ParseError, json.JSONDecodeError, OverflowError) as exc:
        print('R40_REFUSED: ' + str(exc)[:250], file=sys.stderr)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
