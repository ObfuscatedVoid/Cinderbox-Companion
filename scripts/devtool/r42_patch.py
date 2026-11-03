#!/usr/bin/env python3
"""R42 exact-source, fail-closed Devtool finalizer; invoked only after payload extraction.

This is a UI-only patch. It does not write to Android or game/storage directories.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys

BASE = "b4d8e2e0c085906cc54d5cbb47cf7fee4fe92984"
# Git blob OIDs fetched from the exact public GitHub master tree, not historical overlays.
SOURCE_BLOBS = {
    ".devtool.toml": "c0b8d16e500ff089ade7724917accfb31004c68d",
    "app/src/main/java/com/sdvsync/MainActivity.kt": "439dc9c167d211c55b9de1095dcece912a8ffc64",
    "app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt": "90b5995db491f6dbe3dd7bc06b5123f986b983d0",
    "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt": "471a6383da793d1bc5d754444e2d1ef54efcf925",
    "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt": "cc5ebc91150379baf55afa7fa92a273755f50de5",
    "app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt": "9ce20ae98334072c541680b3f3f66b8b874cabcf",
}


def git(repo: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(repo), *args], text=True).strip()


def replace_once(source: str, before: str, after: str, label: str) -> str:
    count = source.count(before)
    if count != 1:
        raise RuntimeError(f"R42_SOURCE_ANCHOR_REFUSED:{label}:count={count}")
    return source.replace(before, after, 1)


def transform(path: str, s: str) -> str:
    if path.endswith("/MainActivity.kt"):
        s = replace_once(s, "import androidx.compose.ui.Modifier\n",
            "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\n", "main-context-import")
        s = replace_once(s, "import com.sdvsync.ui.screens.SteamDiagnosticsScreen\n",
            "import com.sdvsync.ui.screens.SteamDiagnosticsScreen\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\n", "main-policy-import")
        s = replace_once(s, '''        composable("steam_diagnostics") {
            SteamDiagnosticsScreen(onBack = { navController.popBackStack() })
        }''',
            '''        composable("steam_diagnostics") {
            if (DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)) {
                SteamDiagnosticsScreen(onBack = { navController.popBackStack() })
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }''', "main-route-guard")
    elif path.endswith("/SettingsScreen.kt"):
        s = replace_once(s, "import com.sdvsync.logging.AppLogger\n",
            "import com.sdvsync.logging.AppLogger\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\n", "settings-policy-import")
        s = replace_once(s, "            if (developerUnlocked) {\n",
            "            if (developerUnlocked && DeveloperFeaturePolicy.canOpenDiagnostics(context)) {\n",
            "settings-diagnostics-guard")
    elif path.endswith("/GameDownloadScreen.kt"):
        s = replace_once(s, "import com.sdvsync.cinderbox.CinderboxPaths\n",
            "import com.sdvsync.cinderbox.CinderboxPaths\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\nimport com.sdvsync.ui.policy.DownloadPresentation\n", "download-policy-imports")
        s = replace_once(s,
            "            // A separate optional Steam download destination; not the imported game.\n",
            "            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)) {\n" +
            "                // Debug-only folder override; M23/M24 safety gates still apply.\n",
            "download-advanced-start")
        s = replace_once(s,
            "            // Read-only assessment of the selected path, not a hash verification.\n",
            """            } else {
                Text(
                    "This version downloads Steam game files to a separate folder. " +
                        "It will not replace the existing Cinderbox game automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Read-only assessment of the selected path, not a hash verification.
""", "download-advanced-end")
        s = replace_once(s,
            '''Text("Destination review: ${state.destinationCode.name.replace('_', ' ')}",''',
            "Text(DownloadPresentation.destinationStatus(state.destinationCode),",
            "download-readable-status")
        s = replace_once(s,
            '"The final Steam manifest source is not independently signed-verified."',
            '"This does not provide independent signed proof of the Steam files."',
            "download-proof-copy")
    elif path.endswith("/GameDownloadViewModel.kt"):
        s = replace_once(s, "import com.sdvsync.logging.AppLogger\n",
            "import com.sdvsync.logging.AppLogger\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\nimport com.sdvsync.ui.policy.DownloadPresentation\n", "download-vm-policy-import")
        s = replace_once(s,
            "    fun updateInstallDirectory(path: String) {\n",
            '''    fun updateInstallDirectory(path: String) {
        if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {
            _state.update { it.copy(error = "Custom download folders are available only in developer debug builds.") }
            return
        }
''', "download-vm-manual-gate")
        s = replace_once(s,
            "    fun chooseAndroidFolder(uri: Uri) {\n",
            '''    fun chooseAndroidFolder(uri: Uri) {
        if (!DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {
            _state.update { it.copy(error = "Custom download folders are available only in developer debug builds.") }
            return
        }
''', "download-vm-picker-gate")
    elif path.endswith("/InstallationHealthScreen.kt"):
        s = replace_once(s, 'Text("M23 · Read-only installation evidence",',
            'Text("Game installation details",', "health-public-heading")
    elif path == ".devtool.toml":
        s = replace_once(s, "[wrapper.commands.m23-check]\n",
            '''[wrapper.commands.r42-check]
description = "R42 user-facing policy and developer-feature source contracts (host-only)"
workflow = "r42-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''', "devtool-wrapper")
        s = replace_once(s, "[targets.cinderbox.workflows]\n",
            '''[targets.cinderbox.jobs.r42-product-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r42_product_gate.py"]
inputs = ["scripts/devtool/r42_*.py", "app/src/main/java/com/sdvsync/MainActivity.kt", "app/src/main/java/com/sdvsync/ui/policy/**", "app/src/main/java/com/sdvsync/ui/screens/*.kt", "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt", ".devtool.toml"]
metadata = { read_only = true, area = "r42-ui-policy", root_required = false, device_required = false }

[targets.cinderbox.workflows]
r42-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "policy", ref = "job:r42-product-source", depends_on = ["security"] },
]
''', "devtool-job-and-workflow")
    else:
        raise RuntimeError(f"R42_UNEXPECTED_PATCH_PATH:{path}")
    return s


def expected_post_markers() -> dict[str, tuple[str, ...]]:
    return {
        ".devtool.toml": ("[wrapper.commands.r42-check]", "[targets.cinderbox.jobs.r42-product-source]", "r42-check = ["),
        "app/src/main/java/com/sdvsync/MainActivity.kt": ("DeveloperFeaturePolicy.canOpenDiagnostics(LocalContext.current)",),
        "app/src/main/java/com/sdvsync/ui/screens/SettingsScreen.kt": ("developerUnlocked && DeveloperFeaturePolicy.canOpenDiagnostics(context)",),
        "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt": (
            "DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)",
            "DownloadPresentation.destinationStatus(state.destinationCode)"),
        "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt": (
            "DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)",),
        "app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt": ("Game installation details",),
    }


def run(repo: Path, *, check_only: bool) -> None:
    if check_only:
        for rel, markers in expected_post_markers().items():
            text = (repo / rel).read_text(encoding="utf-8")
            for marker in markers:
                if marker not in text:
                    raise RuntimeError(f"R42_EXPECTED_POSTCONDITION_MISSING:{rel}:{marker}")
        print("R42_PATCHED_SOURCE_CHECK_PASS")
        return

    if git(repo, "rev-parse", "HEAD") != BASE:
        raise RuntimeError("R42_WRONG_BASE_COMMIT")
    planned: dict[Path, bytes] = {}
    for rel, blob in SOURCE_BLOBS.items():
        path = repo / rel
        if not path.is_file() or path.is_symlink():
            raise RuntimeError(f"R42_SOURCE_MISSING_OR_SYMLINK:{rel}")
        original = path.read_bytes()
        oid = hashlib.sha1(b"blob " + str(len(original)).encode() + b"\0" + original).hexdigest()
        if oid != blob:
            raise RuntimeError(f"R42_SOURCE_BLOB_MISMATCH:{rel}:{oid}")
        text = original.decode("utf-8")
        rewritten = transform(rel, text).encode("utf-8")
        if original == rewritten:
            raise RuntimeError(f"R42_NOOP_REFUSED:{rel}")
        planned[path] = rewritten

    # Only after ALL anchors and blob preimages match may this hook write anything.
    for path, blob in planned.items():
        tmp = path.with_name(path.name + ".r42-tmp")
        if tmp.exists() or tmp.is_symlink():
            raise RuntimeError(f"R42_UNSAFE_TEMP_PATH:{tmp}")
        try:
            with tmp.open("xb") as f:
                f.write(blob)
                f.flush()
                os.fsync(f.fileno())
            os.chmod(tmp, path.stat().st_mode & 0o777)
            os.replace(tmp, path)
        finally:
            tmp.unlink(missing_ok=True)
    run(repo, check_only=True)
    print("R42_SOURCE_BOUND_PRODUCT_PATCH_APPLIED")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--apply", action="store_true")
    g.add_argument("--check", action="store_true")
    p.add_argument("--repo", type=Path, default=Path.cwd())
    args = p.parse_args(argv)
    try:
        run(args.repo.resolve(), check_only=args.check)
    except (OSError, ValueError, UnicodeError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
