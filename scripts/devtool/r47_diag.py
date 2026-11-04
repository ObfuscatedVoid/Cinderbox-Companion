#!/usr/bin/env python3
"""Summarize the *known synthetic* instrumentation cases without raw logs.

Never write/upload instrumentation stdout, stack traces, arbitrary identifiers,
paths, emulator state, or error messages. Outputs use finite allowlists only.
"""
from __future__ import annotations
import re

ALLOWED_TESTS = (
    'allFiveTabsHaveCorrectSelectionSemantics',
    'homeShortcutInvokesOnlyItsProvidedCallback',
    'errorPanelNeverRetriesBeforeExplicitTap',
    'staticErrorCannotExposeAnUnwiredAction',
    'earthyHomeDay320Font160',
    'earthyHomeNight320Font160',
    'classicHomeDay320Font160',
    'earthyGame320Font160',
    'earthySaves320Font160',
    'earthyMods320Font160',
    'earthyMore320Font160',
)
_ALLOWED = frozenset(ALLOWED_TESTS)
_CATEGORIES = (
    ('assertion', ('AssertionError', 'AssertionFailedError', 'ComparisonFailure')),
    ('compose_timeout', ('ComposeTimeoutException', 'ComposeNotIdleException')),
    ('test_timeout', ('TestTimedOutException', 'TestTimeoutException')),
    ('instrumentation_crash', ('Process crashed', 'INSTRUMENTATION_RESULT: shortMsg=', 'INSTRUMENTATION_FAILED')),
    ('instrumentation_aborted', ('INSTRUMENTATION_ABORTED',)),
)


def summarize_instrumentation(output: str) -> dict:
    """Project raw, untrusted test output into a privacy-safe bounded schema.

    A case is reported only if its exact method name appears in ALLOWED_TESTS.
    AndroidJUnitRunner per-test status codes: 1=start, 0=pass, -1/-2=error/fail.
    This report is diagnostic only and NEVER substitutes for the 11-test gate.
    """
    started: set[str] = set()
    passed: set[str] = set()
    failed: set[str] = set()
    active: str | None = None
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: test='):
            name = line.partition('=')[2].strip()
            active = name if name in _ALLOWED else None
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = line.partition(': ')[2].strip()
            if active:
                if code == '1':
                    started.add(active)
                elif code == '0':
                    passed.add(active)
                elif code in ('-1', '-2'):
                    failed.add(active)
            active = None
    # Never portray a case with a reported failure as a pass.
    passed -= failed
    terminal = re.search(r'(?m)^INSTRUMENTATION_CODE: (-?\d+)\s*$', output)
    terminal_code = terminal.group(1) if terminal else None
    marker_ok = bool(re.search(r'(?m)^OK \(11 tests\)\s*$', output))
    return {
        'schema_version': 1,
        'diagnostics_only': True,
        'expected_tests': len(ALLOWED_TESTS),
        'started_tests': [x for x in ALLOWED_TESTS if x in started],
        'passed_tests': [x for x in ALLOWED_TESTS if x in passed],
        'failed_tests': [x for x in ALLOWED_TESTS if x in failed],
        'reported_ok_11': marker_ok,
        'terminal_success_code': terminal_code == '-1',
        'error_categories': [name for name, needles in _CATEGORIES
                             if any(n in output for n in needles)],
        'raw_output_retained': False,
    }
