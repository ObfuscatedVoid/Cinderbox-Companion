#!/usr/bin/env python3
"""R41 source-bound release candidate kit + explicit opt-in physical-device install.

Prepare is GitHub-hosted and never connects to a device. Plan is read-only.
Install requires a freshly generated, artifact/device/plan-bound approval phrase.
No uninstall, -r replacement, data clear, reboot, root, Steam or game-file writes.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

REPO = 'Mikeyphw/Cinderbox-Companion'
SOURCE = 'ea79d4044764f4f3c5827a05dd32a821d1aec20c'
RELEASE_CERT = '1a97bfe73fccba607b7816e50db0fa1c16850b3036c4bb156efe047e70046ca0'
PKG = 'com.sdvsync'
TEST_PKG = 'com.sdvsync.test'
APK_NAME = 'CinderboxCompanion-release-candidate.apk'
WORKFLOW = '.github/workflows/cinderbox-r40-qualification.yml'
R41_WORKFLOW = '.github/workflows/cinderbox-r41-release-device.yml'
HEX40 = re.compile(r'[0-9a-f]{40}\Z')
HEX64 = re.compile(r'[0-9a-f]{64}\Z')
SERIAL = re.compile(r'[A-Za-z0-9_.:-]{1,128}\Z')
TOKEN = re.compile(r'R41_INSTALL_[0-9a-f]{24}\Z')
MAX_JSON = 256 * 1024


class Refused(Exception):
    pass


def require(ok, why):
    if not ok:
        raise Refused(why)


def load(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size < MAX_JSON, 'R41_UNSAFE_OR_MISSING_JSON')
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
    except (ValueError, UnicodeError) as exc:
        raise Refused('R41_MALFORMED_JSON') from exc
    require(isinstance(value, dict), 'R41_JSON_NOT_OBJECT')
    return value


def sha256(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), 'R41_UNSAFE_OR_MISSING_APK')
    h = hashlib.sha256()
    with path.open('rb') as f:
        for data in iter(lambda: f.read(1024 * 1024), b''):
            h.update(data)
    return h.hexdigest()


def command(argv: list[str], *, timeout=180) -> str:
    try:
        p = subprocess.run(argv, text=True, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise Refused('R41_COMMAND_UNAVAILABLE_OR_TIMEOUT:' + argv[0]) from exc
    require(p.returncode == 0, f'R41_COMMAND_FAILED:{argv[0]}:exit_{p.returncode}')
    return p.stdout.strip()


def verified_run(run_id: int, source: str, workflow: str) -> dict:
    require(isinstance(run_id, int) and run_id > 0 and HEX40.fullmatch(source), 'R41_RUN_OR_SOURCE_INVALID')
    try:
        obj = json.loads(command(['gh', 'api', f'repos/{REPO}/actions/runs/{run_id}'], timeout=90))
    except ValueError as exc:
        raise Refused('R41_REMOTE_RUN_JSON_INVALID') from exc
    require(isinstance(obj, dict) and obj.get('id') == run_id and
            obj.get('repository', {}).get('full_name') == REPO and
            obj.get('head_sha') == source and obj.get('head_branch') == 'master' and
            obj.get('event') == 'workflow_dispatch' and obj.get('status') == 'completed' and
            obj.get('conclusion') == 'success' and
            obj.get('path', '').split('@', 1)[0] == workflow,
            'R41_REMOTE_WORKFLOW_IDENTITY_FAILED')
    return obj


def verify_attestation(apk: Path, source: str) -> str:
    output = command(['gh', 'attestation', 'verify', str(apk), '--repo', REPO,
                      '--source-ref', 'refs/heads/master', '--source-digest', source,
                      '--signer-workflow', f'{REPO}/{WORKFLOW}', '--format', 'json'], timeout=180)
    try:
        parsed = json.loads(output)
    except ValueError as exc:
        raise Refused('R41_ATTESTATION_JSON_INVALID') from exc
    require(bool(parsed) and isinstance(parsed, (dict, list)), 'R41_ATTESTATION_EMPTY')
    return hashlib.sha256(output.encode()).hexdigest()


def verify_kit(folder: Path, r40_run: int, source: str, *, online: bool) -> dict:
    require(folder.is_dir() and not folder.is_symlink(), 'R41_KIT_NOT_DIRECTORY')
    require(source == SOURCE, 'R41_SOURCE_PIN_MISMATCH')
    apk = folder / APK_NAME
    digest = sha256(apk)
    candidate = load(folder / 'M21_RELEASE_CANDIDATE.json')
    accepted = load(folder / 'R40_SIGNED_BUILD_ACCEPTANCE.json')
    sums = folder / 'SHA256SUMS'
    require(sums.is_file() and not sums.is_symlink() and
            sums.read_text(encoding='utf-8') == f'{digest}  {APK_NAME}\n', 'R41_APK_SUMS_MISMATCH')
    expected = dict(repository=REPO, source_commit=source, package=PKG,
                    variant='release', workflow_run_id=r40_run, release_signed=True,
                    signature_verified=True, debuggable=False, installed=False,
                    device_qualified=False, release_qualified=False,
                    publication_allowed=False, automatic_publication=False)
    require(all(type(candidate.get(k)) is type(v) and candidate[k] == v for k, v in expected.items()),
            'R41_RELEASE_RECEIPT_NOT_CANDIDATE')
    require(candidate.get('schema_version') == 1 and
            candidate.get('producer') == 'cinderbox-m21-manual-release-candidate' and
            candidate.get('release_certificate_sha256') == RELEASE_CERT and
            candidate.get('debug_certificate_sha256') != RELEASE_CERT and
            isinstance(candidate.get('debug_certificate_sha256'), str) and
            HEX64.fullmatch(candidate['debug_certificate_sha256']) and
            candidate.get('apk_sha256') == digest and
            type(candidate.get('version_code')) is int and candidate['version_code'] > 0 and
            isinstance(candidate.get('version_name'), str) and candidate['version_name'],
            'R41_SIGNED_APK_OR_CERTIFICATE_MISMATCH')
    expected_accept = dict(repository=REPO, source_commit=source, run_id=r40_run,
                           kind='r40-verified-signed-build', schema_version=1,
                           overall='PASS_R40_SIGNING_AND_REMOTE_ATTESTATION_ONLY',
                           release_apk_sha256=digest, signing_certificate_sha256=RELEASE_CERT,
                           physical_device_qualified=False, emulator_instrumentation_qualified=False,
                           release_qualified=False, publication_allowed=False)
    require(all(type(accepted.get(k)) is type(v) and accepted[k] == v for k, v in expected_accept.items()),
            'R41_INDEPENDENT_SIGNING_RECEIPT_MISMATCH')
    require(accepted.get('junit_tests', 0) > 0 and accepted.get('junit_reports', 0) > 0,
            'R41_MISSING_JUNIT_ACCEPTANCE')
    attestation_hash = None
    if online:
        verified_run(r40_run, source, WORKFLOW)
        attestation_hash = verify_attestation(apk, source)
    return dict(apk_sha256=digest, apk_size=apk.stat().st_size,
                certificate_sha256=RELEASE_CERT, r40_run_id=r40_run,
                source_commit=source, attestation_verified=online,
                attestation_output_sha256=attestation_hash, version_name=candidate['version_name'],
                version_code=candidate['version_code'])


def save(path: Path, value: dict) -> None:
    require(not path.is_symlink(), 'R41_OUTPUT_SYMLINK_REFUSED')
    path.parent.mkdir(parents=True, exist_ok=True)
    require(not path.exists(), 'R41_OUTPUT_EXISTS_REFUSED')
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    path.chmod(0o600)


def adb(serial: str, *tokens: str) -> str:
    require(bool(SERIAL.fullmatch(serial)), 'R41_ADB_SERIAL_INVALID')
    return command(['adb', '-s', serial, *tokens], timeout=120)


def device_state(serial: str) -> dict:
    require(adb(serial, 'get-state') == 'device', 'R41_DEVICE_OFFLINE')
    fingerprint = adb(serial, 'shell', 'getprop', 'ro.build.fingerprint').strip()
    require(5 < len(fingerprint) < 300, 'R41_DEVICE_FINGERPRINT_INVALID')
    # A build fingerprint alone is shared by many phones. Pair it with the
    # selected Android user's ID; store only the combined SHA-256, never raw ID.
    android_id = adb(serial, 'shell', 'settings', 'get', 'secure', 'android_id').strip()
    require(bool(re.fullmatch(r'[a-fA-F0-9]{16}', android_id)), 'R41_ANDROID_ID_UNAVAILABLE')
    installed = {}
    for package in (PKG, TEST_PKG):
        # `pm path` returns 1 for some absent packages. Inspect output and require
        # agreement with `pm list packages` to prevent permission-failure-as-absent.
        listing = adb(serial, 'shell', 'pm', 'list', 'packages', package).splitlines()
        exact = f'package:{package}'
        require(all(line.startswith('package:') for line in listing), 'R41_PACKAGES_LIST_INVALID')
        installed[package] = exact in listing
    return dict(serial=serial, device_identity_sha256=hashlib.sha256((fingerprint + '\x00' + android_id).encode()).hexdigest(),
                installed=installed)


def approval(plan: dict) -> str:
    payload = {k:plan[k] for k in ('source_commit', 'apk_sha256', 'r40_run_id',
                                  'r41_run_id', 'r41_source_commit', 'serial', 'device_identity_sha256',
                                  'created_at_utc', 'nonce')}
    h = hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return 'R41_INSTALL_' + h[:24]


def base_receipt(kind: str, evidence: dict, *, serial: str | None = None) -> dict:
    return dict(schema_version=1, kind=kind, repository=REPO, **evidence,
                device_serial=serial, automatic_install=False,
                automatic_uninstall=False, automatic_data_clear=False,
                real_game_modified=False, steam_transfer_performed=False,
                physical_device_tested=False, device_qualified=False,
                release_qualified=False, publication_allowed=False)


def prepare(a) -> None:
    evidence = verify_kit(a.kit, a.r40_run_id, a.source, online=True)
    require(HEX40.fullmatch(a.r41_source) and command(['git', 'rev-parse', 'HEAD']) == a.r41_source,
            'R41_REVIEWED_WORKFLOW_SOURCE_INVALID')
    doc = base_receipt('r41-host-release-device-kit', evidence)
    doc.update(r41_run_id=a.r41_run_id, r41_source_commit=a.r41_source,
               overall='PASS_R41_VERIFIED_KIT_NO_DEVICE',
               installation_approved=False)
    save(a.output, doc)
    print('PASS_R41_HOST_KIT_VERIFIED_NO_INSTALL')


def check_host(folder: Path, evidence: dict, r41_run_id: int) -> str:
    host = load(folder / 'R41_HOST_KIT.json')
    require(host.get('kind') == 'r41-host-release-device-kit' and
            host.get('overall') == 'PASS_R41_VERIFIED_KIT_NO_DEVICE' and
            host.get('installation_approved') is False and
            host.get('r41_run_id') == r41_run_id and
            host.get('apk_sha256') == evidence['apk_sha256'] and
            host.get('source_commit') == evidence['source_commit'] and
            host.get('r40_run_id') == evidence['r40_run_id'] and
            host.get('certificate_sha256') == RELEASE_CERT and
            host.get('attestation_verified') is True and
            host.get('publication_allowed') is False,
            'R41_HOST_KIT_RECEIPT_INVALID')
    r41_source = host.get('r41_source_commit')
    require(isinstance(r41_source, str) and HEX40.fullmatch(r41_source),
            'R41_WORKFLOW_SOURCE_MISSING')
    verified_run(r41_run_id, r41_source, R41_WORKFLOW)
    return r41_source


def plan(a) -> None:
    evidence = verify_kit(a.kit, a.r40_run_id, a.source, online=True)
    r41_source = check_host(a.kit, evidence, a.r41_run_id)
    require(command(['git', 'rev-parse', 'HEAD']) == r41_source, 'R41_LOCAL_SOURCE_DIFFERS')
    state = device_state(a.serial)
    clean = not any(state['installed'].values())
    doc = base_receipt('r41-device-install-plan', evidence, serial=a.serial)
    doc.update(r41_run_id=a.r41_run_id, r41_source_commit=r41_source, serial=a.serial,
               device_identity_sha256=state['device_identity_sha256'],
               installed_packages=state['installed'],
               nonce=secrets.token_hex(16),
               created_at_utc=datetime.now(timezone.utc).isoformat(),
               overall='READY_FOR_MANUAL_APPROVAL' if clean else 'BLOCKED_EXISTING_INSTALLATION',
               installation_approved=False)
    save(a.output, doc)
    print('Plan:', a.output)
    print('Status:', doc['overall'])
    if clean:
        print('Approval phrase (copy only after reviewing the plan):', approval(doc))
    else:
        print('An existing Cinderbox app or test package is installed.')
        print('No uninstall/replacement is permitted. Existing app data is preserved.')
        print('Only you can decide whether to back up and manually uninstall first.')


def install(a) -> None:
    plan_doc = load(a.plan)
    require(plan_doc.get('kind') == 'r41-device-install-plan' and
            plan_doc.get('overall') == 'READY_FOR_MANUAL_APPROVAL' and
            plan_doc.get('installation_approved') is False and
            plan_doc.get('serial') == a.serial and
            plan_doc.get('source_commit') == a.source and
            plan_doc.get('r40_run_id') == a.r40_run_id and
            plan_doc.get('r41_run_id') == a.r41_run_id and
            isinstance(plan_doc.get('r41_source_commit'), str) and
            HEX40.fullmatch(plan_doc['r41_source_commit']) and
            plan_doc.get('certificate_sha256') == RELEASE_CERT and
            isinstance(plan_doc.get('nonce'), str) and re.fullmatch('[0-9a-f]{32}', plan_doc['nonce']) and
            isinstance(plan_doc.get('device_identity_sha256'), str) and
            HEX64.fullmatch(plan_doc['device_identity_sha256']) and
            plan_doc.get('installed_packages') == {PKG: False, TEST_PKG: False},
            'R41_PLAN_NOT_APPROVABLE')
    require(isinstance(a.approve_install, str) and TOKEN.fullmatch(a.approve_install) and
            a.approve_install == approval(plan_doc), 'R41_EXPLICIT_INSTALL_APPROVAL_REQUIRED')
    try:
        age = datetime.now(timezone.utc) - datetime.fromisoformat(plan_doc['created_at_utc'])
    except (ValueError, KeyError, TypeError) as exc:
        raise Refused('R41_PLAN_TIMESTAMP_INVALID') from exc
    require(timedelta(0) <= age < timedelta(minutes=20), 'R41_PLAN_EXPIRED')
    evidence = verify_kit(a.kit, a.r40_run_id, a.source, online=True)
    r41_source = check_host(a.kit, evidence, a.r41_run_id)
    require(evidence['apk_sha256'] == plan_doc['apk_sha256'] and
            r41_source == plan_doc['r41_source_commit'] and
            command(['git', 'rev-parse', 'HEAD']) == r41_source,
            'R41_KIT_OR_SOURCE_CHANGED')
    current = device_state(a.serial)
    require(current['device_identity_sha256'] == plan_doc['device_identity_sha256'], 'R41_DEVICE_CHANGED')
    require(current['installed'] == {PKG: False, TEST_PKG: False}, 'R41_ALREADY_INSTALLED_REFUSED')
    # Write intent before making any Android change, so failure leaves an audit.
    doc = base_receipt('r41-physical-release-install', evidence, serial=a.serial)
    doc.update(r41_run_id=a.r41_run_id, r41_source_commit=r41_source,
               device_identity_sha256=current['device_identity_sha256'],
               installation_approved=True, install_attempted=False,
               installed_apk_verified=False, activity_launch='NOT_RUN',
               overall='FAILED_OR_BLOCKED')
    # Consume approval atomically before the Android side effect; even if install fails,
    # a fresh plan and fresh operator approval are required for another attempt.
    marker = a.plan.with_name(a.plan.name + '.consumed')
    require(not marker.is_symlink(), 'R41_APPROVAL_CONSUMED_MARKER_UNSAFE')
    try:
        fd = os.open(marker, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError as exc:
        raise Refused('R41_APPROVAL_ALREADY_USED') from exc
    except OSError as exc:
        raise Refused('R41_APPROVAL_CONSUMPTION_UNAVAILABLE') from exc
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(hashlib.sha256(a.approve_install.encode()).hexdigest() + '\n')
    try:
        doc['install_attempted'] = True
        # No -r, -d, uninstall, pm clear, root, silent install, or use of ADB aliases.
        command(['adb', '-s', a.serial, 'install', str(a.kit / APK_NAME)], timeout=300)
        installed = adb(a.serial, 'shell', 'pm', 'path', PKG).splitlines()
        paths = [l.removeprefix('package:').strip() for l in installed if l.startswith('package:')]
        require(len(paths) == 1 and paths[0].startswith('/data/app/') and
                paths[0].endswith('/base.apk') and
                re.fullmatch(r'/[A-Za-z0-9_+./=~:@-]+', paths[0]) and '/../' not in paths[0],
                'R41_INSTALLED_BASE_APK_UNREADABLE')
        remote = adb(a.serial, 'shell', 'sha256sum', paths[0])
        require(remote.split() and remote.split()[0].lower() == evidence['apk_sha256'],
                'R41_INSTALLED_APK_DIGEST_MISMATCH')
        doc['installed_apk_verified'] = True
        if a.approve_launch:
            stdout = adb(a.serial, 'shell', 'am', 'start', '-W', '-n', f'{PKG}/.MainActivity')
            require('Status: ok' in stdout and 'Error:' not in stdout,
                    'R41_ACTIVITY_START_NOT_CONFIRMED')
            doc['activity_launch'] = 'PASS_ACTIVITY_START_COMMAND'
        doc['overall'] = 'PASS_RELEASE_APK_INSTALL_AND_HASH' if not a.approve_launch else 'PASS_RELEASE_INSTALL_HASH_AND_ACTIVITY_START'
        doc['physical_device_tested'] = True
    except Refused as exc:
        doc['failure_code'] = str(exc)
        raise
    finally:
        save(a.output, doc)
        print('R41 local receipt:', a.output)
    print(doc['overall'])


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='mode', required=True)
    for mode in ('prepare', 'plan', 'install'):
        sp = sub.add_parser(mode)
        sp.add_argument('--kit', type=Path, required=True)
        sp.add_argument('--source', default=SOURCE)
        sp.add_argument('--r40-run-id', type=int, required=True)
        sp.add_argument('--r41-run-id', type=int, required=True)
        sp.add_argument('--output', type=Path, required=True)
        if mode == 'prepare':
            sp.add_argument('--r41-source', required=True)
        if mode != 'prepare':
            sp.add_argument('--serial', required=True)
        if mode == 'install':
            sp.add_argument('--plan', type=Path, required=True)
            sp.add_argument('--approve-install', required=True)
            sp.add_argument('--approve-launch', action='store_true')
    a = p.parse_args(argv)
    try:
        {'prepare':prepare, 'plan':plan, 'install':install}[a.mode](a)
    except Refused as exc:
        print('BLOCKED:', exc, file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
