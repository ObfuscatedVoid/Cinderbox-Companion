#!/usr/bin/env python3
"""Read-only R43-A source and CI contract; never runs Gradle/ADB/network."""
from __future__ import annotations

import json
from pathlib import Path
import sys

REPO = Path(__file__).resolve().parents[2]
FILES = {
    "policy": "app/src/main/java/com/sdvsync/local/CinderboxDownloadDestination.kt",
    "path": "app/src/main/java/com/sdvsync/cinderbox/CinderboxPaths.kt",
    "viewmodel": "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt",
    "manager": "app/src/main/java/com/sdvsync/download/GameDownloadManager.kt",
    "service": "app/src/main/java/com/sdvsync/download/GameDownloadService.kt",
    "screen": "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt",
    "wizard": "app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt",
    "recovery": "app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt",
    "devtool": ".devtool.toml",
    "workflow": ".github/workflows/r43-cinderbox-destination.yml",
    "kotlin_test": "app/src/test/java/com/sdvsync/local/CinderboxDownloadDestinationTest.kt",
}


def read(repo: Path, rel: str) -> str:
    file = repo / rel
    if not file.is_file() or file.is_symlink():
        raise AssertionError(f"R43_MISSING_OR_SYMLINK:{rel}")
    return file.read_text(encoding="utf-8")


def check(repo: Path = REPO) -> dict:
    source = {key: read(repo, path) for key, path in FILES.items()}
    guard = source["policy"]
    paths = source["path"]
    vm = source["viewmodel"]
    manager = source["manager"]
    service = source["service"]
    screen = source["screen"]
    wizard = source["wizard"]
    recovery = source["recovery"]
    cfg = source["devtool"]
    workflow = source["workflow"]
    test = source["kotlin_test"]
    assert 'const val GAME_FILES_DIR = "$DESKTOP_DIR/GameFiles"' in paths
    assert 'const val ROOT_DIR = "/storage/emulated/0/StardewValley"' in paths
    for piece in (
        'selected.path != gameFiles.absolutePath', 'PlatformStoragePaths.inspect(selected)',
        'if (!root.isDirectory)', 'if (!desktop.isDirectory)',
        'EMPTY_GAME_DIRECTORY_PRESENT', 'EXISTING_GAME_PROTECTED',
        'StoragePreflight.requireWritable(selected)', 'Code.READY_NEW_INSTALL',
    ):
        assert piece in guard, f"R43_GUARD_MISSING:{piece}"
    assert "CinderboxPaths.GAME_FILES_DIR\n        }" in vm, "R43_PRODUCTION_DEFAULT_MISSING"
    assert "val debugAlternative = isDebugAlternative(current.installDirectory)" in vm
    assert "if (debugAlternative && (" in vm, "R43_LEGACY_M23_NOT_ISOLATED"
    assert "if (!debugAlternative) {" in vm
    assert "downloadManager.isPublishedRecovery(File(current.installDirectory))" in vm
    assert 'if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {' in vm
    assert 'if (!isDebugAlternative(_state.value.installDirectory)) {' in vm
    assert "CinderboxDownloadDestination.requireWritableForInstall(" in manager
    assert "CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)" in service
    assert "recovery.requireMatching(installRoot, branch, os, verify)" in service
    assert "GameDownloadRecovery.safeDestination(directory)" in manager
    assert "recovery.beginStaged(directory, branch, os, verifyAfterDownload)" in manager
    assert "recovery.requireMatching(directory, branch, os, verifyAfterDownload)" in manager
    assert "M23CinderboxFirstPolicy.assess" in manager and "M23CinderboxFirstPolicy.assess" in service
    assert 'if (debugAlternative) {' in manager and 'if (debugAlternative) {' in service
    assert 'DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(this)' in service
    assert "if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)) {" in wizard
    assert "Game destination · Cinderbox" in screen
    assert "state.installDirectory != CinderboxPaths.GAME_FILES_DIR" in screen
    assert "val showCopyButton = state.isCinderboxSetup && !copySucceeded &&" in screen
    assert "fun promoteStaged(destination: File)" in recovery
    assert 'check(!safe.exists()) { "Target appeared during staged transfer; no overwrite" }' in recovery
    assert "StandardCopyOption.ATOMIC_MOVE" in recovery
    assert "[wrapper.commands.r43-check]" in cfg
    assert "[targets.cinderbox.jobs.r43-cinderbox-source]" in cfg
    assert "r43-check = [" in cfg
    assert "test_r43_policy.py" in workflow and "r42_product_gate.py" in workflow
    assert "testDebugUnitTest" not in workflow, "R43_WRONG_RUNNER:must be hosted via existing build workflow"
    assert not any(word in workflow for word in ("adb ", "gh release", "gradle ", "release publish"))
    assert test.count("@Test fun ") >= 6, "R43_EXPECTED_KOTLIN_NEGATIVE_TESTS"
    assert "EXISTING_GAME_PROTECTED" in test and "WRONG_DESTINATION" in test
    return {
        "overall": "R43_A_HOST_SOURCE_CONTRACT_PASS",
        "base": "47dc6c1a3cd8659b6fe969d6dd8faad910a77cf5",
        "managed_destination": "/storage/emulated/0/StardewValley/desktop/GameFiles",
        "production_alternative_destination": False,
        "existing_game_mutation": False,
        "device_tested": False,
        "release_qualified": False,
    }


if __name__ == "__main__":
    try:
        print(json.dumps(check(), sort_keys=True))
    except (AssertionError, OSError, UnicodeError, ValueError) as exc:
        print(f"R43_SOURCE_REFUSED:{exc}", file=sys.stderr)
        sys.exit(2)
