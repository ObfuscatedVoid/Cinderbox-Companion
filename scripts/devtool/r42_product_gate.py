#!/usr/bin/env python3
"""Read-only R42 product-boundary source gate (no build, ADB, or game access)."""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys

REPO = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    p = REPO / rel
    if not p.is_file() or p.is_symlink():
        raise AssertionError(f"R42_SOURCE_MISSING:{rel}")
    return p.read_text(encoding="utf-8")


def assert_contract() -> dict:
    policy = read("app/src/main/java/com/sdvsync/ui/policy/DeveloperFeaturePolicy.kt")
    presentation = read("app/src/main/java/com/sdvsync/ui/policy/DownloadPresentation.kt")
    activity = read("app/src/main/java/com/sdvsync/MainActivity.kt")
    settings = read("app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt")
    screen = read("app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt")
    vm = read("app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt")
    config = read(".devtool.toml")
    assert "debugBuild && unlocked" in policy, "R42_DEBUG_GATE_MISSING"
    assert "BuildConfig.DEBUG" in policy, "R42_RELEASE_FLAG_MISSING"
    assert "steam_diagnostics_enabled" in policy, "R42_LEGACY_UNLOCK_MISSING"
    assert "DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)" in activity, "R42_DIAG_ROUTE_UNGUARDED"
    assert "developerUnlocked && DeveloperFeaturePolicy.canOpenDiagnostics(context)" in settings, "R42_DIAG_ENTRY_UNGUARDED"
    assert "DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)" in screen, "R42_PATH_UI_UNGUARDED"
    assert vm.count("if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context))") == 2, "R42_PATH_VM_UNGUARDED"
    assert "DownloadPresentation.destinationStatus(state.destinationCode)" in screen, "R42_STATUS_PRESENTATION_MISSING"
    assert "PROTECTED_CINDERBOX_TREE ->" in presentation and "DESTINATION_UNREADABLE ->" in presentation
    assert "[wrapper.commands.r42-check]" in config and "[targets.cinderbox.jobs.r42-product-source]" in config
    assert 'Text("M23 · Read-only installation evidence"' not in read("app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt")
    assert 'Destination review: ${state.destinationCode.name' not in screen
    for protected in (
        "app/src/main/java/com/sdvsync/download/GameDownloadManager.kt",
        "app/src/main/java/com/sdvsync/download/GameDownloadService.kt",
        "app/src/main/java/com/sdvsync/local/M23CinderboxFirstPolicy.kt",
    ):
        content = read(protected)
        assert "M23CinderboxFirstPolicy" in content or "PROTECTED_CINDERBOX_TREE" in content
    # R40/R41 producer scripts and approved release signing workflow are never touched.
    workflow = read(".github/workflows/r42-product-policy.yml")
    assert "gradle " not in workflow and "adb " not in workflow and "gh release" not in workflow
    assert "test_r42_product_policy.py" in workflow
    return {"overall": "R42_SOURCE_BOUND_PRODUCT_GATES_PASS", "device_tested": False,
            "gradle_executed": False, "release_qualified": False}


if __name__ == "__main__":
    try:
        print(json.dumps(assert_contract(), sort_keys=True))
    except (OSError, ValueError, AssertionError) as exc:
        print(f"R42_SOURCE_GATE_BLOCKED:{exc}", file=sys.stderr)
        sys.exit(2)
