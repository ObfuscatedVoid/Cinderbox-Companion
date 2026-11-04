#!/usr/bin/env python3
"""R47 host-only source, privacy and hosted-emulator workflow contract."""
from __future__ import annotations
from pathlib import Path
import json
import re
import sys
import tomllib
import r47_patch
import r47_mmrl_emulator_fix
ROOT=Path(__file__).resolve().parents[2]

TEST='app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt'
CAPTURE='scripts/devtool/r47_capture.py'
SCRIPT='scripts/devtool/r47_emulator_ci.sh'
WORKFLOW='.github/workflows/r47-ui-acceptance.yml'
POLICY='docs/security/M3_RELEASE_POLICY.json'

def read(p:str)->str:
    file=ROOT/p
    assert file.is_file() and not file.is_symlink(),'R47_MISSING_OR_SYMLINK:'+p
    return file.read_text(encoding='utf-8')

def check()->dict:
    r47_patch.check(ROOT)
    test=read(TEST);cap=read(CAPTURE);script=read(SCRIPT);workflow=read(WORKFLOW)
    security=json.loads(read(POLICY))
    cfg=tomllib.loads(read('.devtool.toml'))
    for key in ('release_enabled','publication_enabled','automatic_device_install','device_qualification_complete'):
        assert security[key] is False,'R47_RELEASE_POLICY_CHANGED:'+key
    coordinator=read('app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt')
    assert 'const val LIVE_UPDATES_ENABLED: Boolean = false' in coordinator
    assert 'const val DEVICE_ACCEPTED: Boolean = false' in coordinator
    assert 'class R47SyntheticExperienceDeviceTest' in test
    assert test.count('@Test fun ')==11,'R47_TEST_CASE_COUNT'
    for expected in ('allFiveTabsHaveCorrectSelectionSemantics','homeShortcutInvokesOnlyItsProvidedCallback',
                     'errorPanelNeverRetriesBeforeExplicitTap','staticErrorCannotExposeAnUnwiredAction',
                     'fontScale = 1.6f','AppearanceStyle.CLASSIC','AppearanceStyle.EARTHY',
                     'captureToImage().asAndroidBitmap()', 'Context.MODE_PRIVATE',
                     '"Open operation center" to "operations"','assertIsSelected()'):
        assert expected in test,'R47_MISSING_UI_CHECK:'+expected
    # R2: check behavior-focused instrumentation, not only @Test name counts.
    for expected in ('mutableStateListOf<String>()',
                     'Open Game', 'Open Saves', 'Open Mods', 'Open More',
                     'View installation health', 'Open operation center',
                     'calls.isEmpty()', 'shortcuts.forEachIndexed',
                     'SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)', 'assertIsNotSelected()',
                     'StardewBottomBar(tab, onTabSelected = {})',
                     'verticalScroll(rememberScrollState())',
                     'performScrollTo().assertIsDisplayed()',
                     'R46UiErrorPolicy.forArea(error).title'):
        assert expected in test, 'R47_INTERACTION_OR_REFLOW_NOT_TESTED:'+expected
    verifier=read('scripts/devtool/r47_verify.py')
    for required in ('PASS_SYNTHETIC_EMULATOR','png_dimensions(content)',
                     "('physical_device_qualified',False)",
                     'sha256(content).hexdigest()', 'set(NAMES)'):
        assert required in verifier, 'R47_VERIFIER_CONTRACT:'+required
    for required in ('INSTRUMENTATION_CODE: -1', 'MAX_PNG_BYTES',
                     'truncated PNG chunk', 'PNG chunk CRC', 'PNG pixel data',
                     'len(decoded)!=expected', 'for name in NAMES:',
                     'hashlib.sha256(data).hexdigest()', 'os.replace(tmp,args.output_dir/name)'):
        assert required in cap, 'R47_SCREENSHOT_VALIDATION_MISSING:'+required
    r47_mmrl_emulator_fix.validate_workflow(workflow)
    assert 'scripts/devtool/r47_verify.py' in workflow
    assert '--source-sha "$GITHUB_SHA" --run-id "$GITHUB_RUN_ID"' in workflow
    assert "'app/src/main/java/com/sdvsync/ui/**'" in workflow
    for area in ('GAME','SAVES','MODS','MORE'):
        assert 'R45GuideArea.'+area in test
    assert 'R46ErrorPanel(' in test and 'R45AreaGuide(area)' in test
    assert 'EXPECTED_TESTS=11' in cap
    assert 'from r47_diag import summarize_instrumentation' in cap
    assert "result['instrumentation_summary']=summary" in cap
    assert "or summary['failed_tests']" in cap
    diagnostics=read('scripts/devtool/r47_diag.py')
    assert 'ALLOWED_TESTS' in diagnostics and 'raw_output_retained' in diagnostics
    assert "test_r47_diag.py' -v" in workflow
    assert "if args.serial!='emulator-5554'" in cap
    assert "'getprop','ro.kernel.qemu'" in cap
    assert "'run-as','com.sdvsync'" in cap
    assert 'png_dimensions(data)' in cap and 'hashlib.sha256(data).hexdigest()' in cap
    assert "'physical_device_qualified':False" in cap and "'release_qualified':False" in cap
    assert 'getprop ro.kernel.qemu' in script and 'R47_PHYSICAL_DEVICE_REFUSED' in script
    assert ':app:assembleDebug :app:assembleDebugAndroidTest' in workflow
    assert 'ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d' in workflow
    assert 'scripts/devtool/r47_emulator_ci.sh' in workflow
    assert 'app/src/androidTest/java/com/sdvsync/qualification/R47SyntheticExperienceDeviceTest.kt' in workflow
    assert 'if-no-files-found: error' in workflow and 'R47_SYNTHETIC_EVIDENCE.json' in cap
    assert "[targets.cinderbox.jobs.r47-ui-source]" in read('.devtool.toml')
    assert cfg['targets']['cinderbox']['jobs']['r47-ui-source']['metadata']['release_enabled'] is False
    for forbidden in ('pm clear','grantPermission','Environment.getExternalStorageDirectory',
                      'startDownload(', 'forceSync(', 'deleteRecursively(', 'shell su ', 'reboot'):
        assert forbidden not in test and forbidden not in cap and forbidden not in script,'R47_UNSAFE_DEVICE_OPERATION:'+forbidden
    return {'result':'R47_SYNTHETIC_ACCEPTANCE_SOURCE_PASS','source_only':True,
            'instrumentation_declared':11,'synthetic_screenshot_variants':7,
            'emulator_executed':False,'physical_device_qualified':False,
            'release_enabled':False,'live_update_enabled':False}

if __name__=='__main__':
    try:print(json.dumps(check(),sort_keys=True))
    except (AssertionError,ValueError,KeyError,OSError) as e:
        print('R47_SOURCE_REFUSED:'+str(e),file=sys.stderr);raise SystemExit(1)
