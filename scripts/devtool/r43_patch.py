#!/usr/bin/env python3
"""R43-A source-pinned, fail-closed Devtool finalize patch. No device operations."""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys

BASE = "47dc6c1a3cd8659b6fe969d6dd8faad910a77cf5"
SOURCE_BLOBS = {
    ".devtool.toml": "27bed38fa7c714e09217f3dd12f1888224691187",
    "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt": "4156d6f8de80dca79e8201dc7b98b8bec26f004f",
    "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt": "a1744160642b8398b6503817487346537dd065e7",
    "app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt": "652fe65baf4b6d849a0a8f9f76443741a0a8a4ea",
    "app/src/main/java/com/sdvsync/download/GameDownloadManager.kt": "1662434bf6c37a082d6e9d05ae602f60c7a6f367",
    "app/src/main/java/com/sdvsync/download/GameDownloadService.kt": "60752f667ea4ca56d1f573a26a7909bf9611c244",
}


def replace_once(source: str, before: str, after: str, label: str) -> str:
    n = source.count(before)
    if n != 1:
        raise RuntimeError(f"R43_ANCHOR_MISMATCH:{label}:count={n}")
    return source.replace(before, after, 1)


def transform(rel: str, s: str) -> str:
    if rel == ".devtool.toml":
        s = replace_once(s, "[wrapper.commands.m23-check]\n",
            '''[wrapper.commands.r43-check]
description = "R43 Cinderbox-only production destination source guard (host-only)"
workflow = "r43-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''', "wrapper-r43")
        s = replace_once(s, "[targets.cinderbox.workflows]\n",
            '''[targets.cinderbox.jobs.r43-cinderbox-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43_policy_gate.py"]
inputs = ["scripts/devtool/r43_*.py", "app/src/main/java/com/sdvsync/local/CinderboxDownloadDestination.kt", "app/src/main/java/com/sdvsync/download/GameDownloadManager.kt", "app/src/main/java/com/sdvsync/download/GameDownloadService.kt", "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt", "app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt", ".devtool.toml"]
metadata = { read_only = true, area = "r43-cinderbox-only", root_required = false, device_required = false }

[targets.cinderbox.workflows]
r43-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r42", ref = "job:r42-product-source", depends_on = ["security"] },
  { id = "r43", ref = "job:r43-cinderbox-source", depends_on = ["r42"] },
]
''', "devtool-r43-job")
        return s

    if rel.endswith("/GameDownloadViewModel.kt"):
        s = replace_once(s, "import com.sdvsync.local.StoragePreflight\n",
            "import com.sdvsync.local.StoragePreflight\nimport com.sdvsync.local.CinderboxDownloadDestination\n",
            "vm-policy-import")
        s = replace_once(s, "    init {\n",
            '''    private fun isDebugAlternative(path: String): Boolean =
        DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context) &&
            !CinderboxDownloadDestination.isExactDestination(path)

    private fun destinationDetail(path: String): String =
        if (isDebugAlternative(path)) {
            "Debug-only custom folder. Actual write access will be tested when downloading."
        } else {
            CinderboxDownloadDestination.inspect(File(path)).explanation
        }

    init {
''', "vm-readonly-status-helper")
        s = replace_once(s,
            "        val selectedDir = pending?.directory ?: savedDir\n",
            '''        val selectedDir = if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {
            pending?.directory ?: savedDir
        } else {
            // Old M4 preferences and separate-folder checkpoints cannot redirect release downloads.
            CinderboxPaths.GAME_FILES_DIR
        }
''', "vm-managed-default")
        s = replace_once(s,
            '            storageDetail = "Write access will be tested only when you start a download.",\n',
            '            storageDetail = destinationDetail(selectedDir),\n', "vm-initial-status")
        old_status = '            storageDetail = "Write access will be tested only when you start a download."\n'
        if s.count(old_status) != 2:
            raise RuntimeError("R43_ANCHOR_MISMATCH:vm-two-readonly-status-sites")
        s = s.replace(old_status, '            storageDetail = destinationDetail(path)\n')
        s = replace_once(s,
            '''    fun refreshSelectedDirectoryHealth() {
        val path = _state.value.installDirectory
        _state.value = _state.value.copy(
''',
            '''    fun refreshSelectedDirectoryHealth() {
        val path = if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context)) {
            _state.value.installDirectory
        } else {
            CinderboxPaths.GAME_FILES_DIR
        }
        _state.value = _state.value.copy(
            installDirectory = path,
''', "vm-refresh-managed-path")
        s = replace_once(s,
            '''        // A checkpoint never licenses writing inside the original Cinderbox tree.
        // For new downloads, the *selected* destination must be empty and separate.
        if ((!resume && !destination.separateEmptyDestination) ||
            destination.code == M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE ||
            destination.code == M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION ||
            destination.code == M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
''',
            '''        val debugAlternative = isDebugAlternative(current.installDirectory)
        if (!debugAlternative) {
            val assessment = CinderboxDownloadDestination.inspect(File(current.installDirectory))
            val ownedPromotion = resume &&
                downloadManager.isPublishedRecovery(File(current.installDirectory))
            if (!assessment.canStartNewDownload &&
                !(ownedPromotion && assessment.code ==
                    CinderboxDownloadDestination.Code.EXISTING_GAME_PROTECTED)) {
                _state.value = current.copy(error = assessment.explanation,
                    storageDetail = assessment.explanation)
                return
            }
        }
        // Legacy M23 independent empty-directory policy is retained only for
        // explicitly unlocked debug overrides. Production cannot select it.
        if (debugAlternative && (
            (!resume && !destination.separateEmptyDestination) ||
            destination.code == M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE ||
            destination.code == M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION ||
            destination.code == M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE)) {
''', "vm-managed-vs-debug-authorization")
        s = replace_once(s,
            '''    fun onWizardChooseOther() {
        pendingSetupType = SETUP_TYPE_OTHER
''',
            '''    fun onWizardChooseOther() {
        if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context) == false) {
            _wizardState.value = CinderboxWizardState()
            _state.update { it.copy(error =
                "Alternative download locations are available only in unlocked debug builds.") }
            return
        }
        pendingSetupType = SETUP_TYPE_OTHER
''', "vm-wizard-other-guard")
        s = replace_once(s,
            '''    fun copyCinderbox() {
        downloadManager.resetCopyState()
''',
            '''    fun copyCinderbox() {
        if (!isDebugAlternative(_state.value.installDirectory)) {
            _state.update { it.copy(error =
                "Game files install directly into Cinderbox. No separate copy is needed.") }
            return
        }
        downloadManager.resetCopyState()
''', "vm-hide-copy-operation")
        return s

    if rel.endswith("/GameDownloadManager.kt"):
        s = replace_once(s,
            "import com.sdvsync.local.M23CinderboxFirstPolicy\n",
            "import com.sdvsync.local.M23CinderboxFirstPolicy\nimport com.sdvsync.local.CinderboxDownloadDestination\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\n",
            "manager-imports")
        s = replace_once(s,
            '''    fun canResume(directory: File, branch: String, os: String, verify: Boolean): Boolean =
        recovery.pending()?.matches(directory, branch, os, verify) == true
''',
            '''    fun canResume(directory: File, branch: String, os: String, verify: Boolean): Boolean =
        recovery.pending()?.matches(directory, branch, os, verify) == true

    /** Read-only view of journal-owned promotion; not standalone write authority. */
    fun isPublishedRecovery(directory: File): Boolean =
        runCatching { recovery.isPromotedStage(directory) }.getOrDefault(false)
''', "manager-own-promoted-seam")
        s = replace_once(s,
            '''        // Recheck in the service entry path; UI preflight alone is not a guard.
        val scope = M23CinderboxFirstPolicy.assess(directory, File(CinderboxPaths.ROOT_DIR))
        check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&
            scope.code != M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION &&
            scope.code != M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
            "Destination overlaps Cinderbox or cannot be safely inspected"
        }
''',
            '''        // UI preflight does not authorize writing. Recheck the fixed
        // production destination even for callers that bypass the ViewModel.
        val debugAlternative = DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context) &&
            !CinderboxDownloadDestination.isExactDestination(directory.path)
        val scope = M23CinderboxFirstPolicy.assess(directory, File(CinderboxPaths.ROOT_DIR))
        if (debugAlternative) {
            check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&
                scope.code != M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION &&
                scope.code != M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
                "Debug destination is protected, unsafe or unreadable"
            }
        } else {
            CinderboxDownloadDestination.requireWritableForInstall(
                directory, isPublishedRecovery(directory))
        }
''', "manager-layer-defense")
        s = replace_once(s,
            '''        if (checkpoint == null) {
            check(scope.separateEmptyDestination) {
                "Selected folder is not an independent empty download destination"
            }
''',
            '''        if (checkpoint == null) {
            if (debugAlternative) {
                check(scope.separateEmptyDestination) {
                    "Debug destination must be an independent empty folder"
                }
            } else {
                check(CinderboxDownloadDestination.inspect(directory).canStartNewDownload) {
                    "Cinderbox's GameFiles destination is no longer available for a new download"
                }
            }
''', "manager-new-install-state")
        return s

    if rel.endswith("/GameDownloadService.kt"):
        s = replace_once(s,
            "import com.sdvsync.local.M23CinderboxFirstPolicy\n",
            "import com.sdvsync.local.M23CinderboxFirstPolicy\nimport com.sdvsync.local.CinderboxDownloadDestination\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\n",
            "service-imports")
        s = replace_once(s,
            '''            // Defense in depth for restarted/explicit service intents, even if the
            // UI and GameDownloadManager path gates were bypassed.
            val scope = M23CinderboxFirstPolicy.assess(installRoot, File(CinderboxPaths.ROOT_DIR))
            check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&
                scope.code != M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION &&
                scope.code != M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
                "Protected Cinderbox game tree or unsafe destination"
            }
            StoragePreflight.requireWritable(installRoot)
            // Only a checkpoint written by our manager authorizes touching partial files.
            // This also rejects intents manually dispatched to the service.
            GameDownloadRecovery(filesDir).requireMatching(installRoot, branch, os, verify)
            val recovery = GameDownloadRecovery(filesDir)
''',
            '''            // Recheck the production destination independently of the UI and
            // manager. A direct service intent is never an installation authority.
            val recovery = GameDownloadRecovery(filesDir)
            val debugAlternative = DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(this) &&
                !CinderboxDownloadDestination.isExactDestination(installRoot.path)
            val scope = M23CinderboxFirstPolicy.assess(installRoot, File(CinderboxPaths.ROOT_DIR))
            if (debugAlternative) {
                check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&
                    scope.code != M23CinderboxFirstPolicy.Code.UNSAFE_OR_NONABSOLUTE_DESTINATION &&
                    scope.code != M23CinderboxFirstPolicy.Code.DESTINATION_UNREADABLE) {
                    "Debug destination is protected, unsafe or unreadable"
                }
            } else {
                val promoted = runCatching { recovery.isPromotedStage(installRoot) }.getOrDefault(false)
                CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)
            }
            StoragePreflight.requireWritable(installRoot)
            // Only the manager's checkpoint, matching target, Steam options and
            // ownership marker authorize resuming this exact download.
            recovery.requireMatching(installRoot, branch, os, verify)
''', "service-last-gate")
        return s

    if rel.endswith("/GameDownloadScreen.kt"):
        s = replace_once(s,
            '''            } else {
                Text(
                    "This version downloads Steam game files to a separate folder. " +
                        "It will not replace the existing Cinderbox game automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
''',
            '''            } else {
                StardewCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Game destination · Cinderbox",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Game files are downloaded only to Cinderbox's GameFiles folder. " +
                            "Existing installations are protected until safe updates are supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
''', "screen-managed-destination")
        s = replace_once(s,
            '''            // Read-only assessment of the selected path, not a hash verification.
            state.selectedInstallHealth?.let { health ->
''',
            '''            // Alternative path inspector belongs to unlocked debug mode only.
            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                state.installDirectory != CinderboxPaths.GAME_FILES_DIR) {
            state.selectedInstallHealth?.let { health ->
''', "screen-debug-health-start")
        s = replace_once(s,
            '''                Spacer(Modifier.height(12.dp))
            }
            Text(state.storageDetail, style = MaterialTheme.typography.bodySmall)
            if (state.destinationCode != M23CinderboxFirstPolicy.Code.EMPTY_SEPARATE_DESTINATION) {
''',
            '''                Spacer(Modifier.height(12.dp))
            }
            }
            Text(state.storageDetail, style = MaterialTheme.typography.bodySmall)
            if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                state.installDirectory != CinderboxPaths.GAME_FILES_DIR &&
                state.destinationCode != M23CinderboxFirstPolicy.Code.EMPTY_SEPARATE_DESTINATION) {
''', "screen-debug-health-end")
        s = replace_once(s,
            '''                    val showCopyButton = state.isCinderboxSetup && !copySucceeded
''',
            '''                    val showCopyButton = state.isCinderboxSetup && !copySucceeded &&
                        DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current) &&
                        state.installDirectory != CinderboxPaths.GAME_FILES_DIR
''', "screen-no-copy-managed")
        return s

    if rel.endswith("/CinderboxWizardDialog.kt"):
        s = replace_once(s,
            "import com.sdvsync.fileaccess.AllFilesAccess\n",
            "import com.sdvsync.fileaccess.AllFilesAccess\nimport com.sdvsync.ui.policy.DeveloperFeaturePolicy\n",
            "wizard-policy-import")
        s = replace_once(s,
            '''        Spacer(Modifier.height(12.dp))

        // Other option — outlined card
''',
            '''        // The alternate-app/destination wizard is an unlocked debug tool.
        if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)) {
        Spacer(Modifier.height(12.dp))

        // Other option — outlined card
''', "wizard-other-hidden")
        s = replace_once(s,
            '''            }
        }
    }
}

@Composable
private fun PermissionsStep(
''',
            '''            }
        }
        }
    }
}

@Composable
private fun PermissionsStep(
''', "wizard-other-close")
        return s

    raise RuntimeError(f"R43_UNEXPECTED_FILE:{rel}")


def patched_markers() -> dict[str, tuple[str, ...]]:
    return {
        ".devtool.toml": ("[wrapper.commands.r43-check]", "[targets.cinderbox.jobs.r43-cinderbox-source]", "r43-check = ["),
        "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt": (
            "Game destination · Cinderbox", "Existing installations are protected",
            "val showCopyButton = state.isCinderboxSetup && !copySucceeded &&"),
        "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt": (
            "CinderboxPaths.GAME_FILES_DIR\n        }", "val debugAlternative = isDebugAlternative(current.installDirectory)",
            "downloadManager.isPublishedRecovery(File(current.installDirectory))"),
        "app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt": (
            "if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current))" ,),
        "app/src/main/java/com/sdvsync/download/GameDownloadManager.kt": (
            "CinderboxDownloadDestination.requireWritableForInstall(", "fun isPublishedRecovery("),
        "app/src/main/java/com/sdvsync/download/GameDownloadService.kt": (
            "CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)",
            "recovery.requireMatching(installRoot, branch, os, verify)"),
    }


def git(repo: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(repo), *args], text=True).strip()


def run(repo: Path, check_only: bool) -> None:
    if check_only:
        for rel, markers in patched_markers().items():
            src = (repo / rel).read_text(encoding="utf-8")
            for marker in markers:
                if marker not in src:
                    raise RuntimeError(f"R43_POSTCONDITION_MISSING:{rel}:{marker[:70]}")
        print("R43_PATCHED_SOURCE_CHECK_PASS")
        return

    if git(repo, "rev-parse", "HEAD") != BASE:
        raise RuntimeError("R43_BASE_COMMIT_CHANGED")
    planned: dict[Path, bytes] = {}
    for rel, git_oid in SOURCE_BLOBS.items():
        path = repo / rel
        if not path.is_file() or path.is_symlink():
            raise RuntimeError(f"R43_SOURCE_FILE_MISSING:{rel}")
        old = path.read_bytes()
        actual_oid = hashlib.sha1(b"blob " + str(len(old)).encode() + b"\0" + old).hexdigest()
        if actual_oid != git_oid:
            raise RuntimeError(f"R43_PREIMAGE_BLOB_MISMATCH:{rel}:{actual_oid}")
        changed = transform(rel, old.decode("utf-8")).encode("utf-8")
        if old == changed:
            raise RuntimeError(f"R43_NOOP_REFUSED:{rel}")
        planned[path] = changed

    # No files are written until every source blob and text anchor has matched.
    for path, blob in planned.items():
        tmp = path.with_name(path.name + ".r43-tmp")
        if tmp.exists() or tmp.is_symlink():
            raise RuntimeError(f"R43_TEMP_FILE_CONFLICT:{tmp}")
        try:
            with tmp.open("xb") as stream:
                stream.write(blob)
                stream.flush()
                os.fsync(stream.fileno())
            os.chmod(tmp, path.stat().st_mode & 0o777)
            os.replace(tmp, path)
        finally:
            tmp.unlink(missing_ok=True)
    run(repo, check_only=True)
    print("R43_A_SOURCE_PINNED_POLICY_INSTALLED")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    kind = parser.add_mutually_exclusive_group(required=True)
    kind.add_argument("--apply", action="store_true")
    kind.add_argument("--check", action="store_true")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    args = parser.parse_args(argv)
    try:
        run(args.repo.resolve(), check_only=args.check)
    except (OSError, UnicodeError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"R43_FAIL_CLOSED:{exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
