#!/usr/bin/env python3
"""Atomic, exact-blob R43-B4 consolidated patch (no GitHub/Android/Gradle execution)."""
from __future__ import annotations
import argparse, hashlib, os, subprocess, sys, tempfile, tomllib
from pathlib import Path
BASE = '8445f23467e2233a04fb174219814b5681359117'
TREE = 'c33e83bbc81e294f53aa400ff66e45027fdb09f5'
BLOBS = {
 '.devtool.toml': 'fb493e491f8be331001343d6e14021e32add55f9',
 'app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt': '1bbb1c2255575d4c986261738c9a2d0166df0af4',
 'app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt': 'fd875edb93c36bd8ec8ef75a4611eb350d4e4460',
 'app/src/main/java/com/sdvsync/download/GameDownloadManager.kt': '64a4b6aa957c8352f4c15c8cc1fed4af2b811fd1',
 'app/src/main/java/com/sdvsync/download/GameDownloadService.kt': 'a4537d07174aa2d210aed6649e5fcbe692f6d2c7',
 'app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt': '0c16a213ec4b33cf0bb625e5f45b10edcf1650ab',
 'app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt': '523f8e30bef8cbe33a9fa231cf25ffc601ea8fd7',
}

def replace_once(src: str, old: str, new: str, name: str) -> str:
    count=src.count(old)
    if count!=1:
        raise RuntimeError(f'R43B4_SOURCE_ANCHOR_CHANGED:{name}:matches={count}')
    return src.replace(old,new,1)

def transform(rel: str, s: str) -> str:
    if rel=='.devtool.toml':
        s=replace_once(s, '[wrapper.commands.m23-check]\n', '''[wrapper.commands.r43b4-check]
description = "R43 consolidated update transaction, two-journal recovery and acceptance policy"
workflow = "r43b4-check"
target = "cinderbox"

[wrapper.commands.m23-check]
''', 'devtool-wrapper')
        s=replace_once(s,'[targets.cinderbox.workflows]\n','''[targets.cinderbox.jobs.r43b4-coordinator-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43b4_gate.py"]
inputs = ["scripts/devtool/r43b4_*.py", "scripts/devtool/test_r43b4_gate.py", "app/src/main/java/com/sdvsync/download/R43BLedgerArchive.kt", "app/src/main/java/com/sdvsync/download/R43BUpdateCoordinator.kt", "app/src/test/java/com/sdvsync/download/R43BUpdateCoordinatorTest.kt", "app/src/main/java/com/sdvsync/download/GameDownloadService.kt", "app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt", "app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt", "app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt", ".devtool.toml"]
metadata = { read_only = true, area = "r43b4-coordinated-fail-closed-candidate", root_required = false, device_required = false, live_update_enabled = false }

[targets.cinderbox.workflows]
r43b4-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43b3", ref = "job:r43b3-handoff-source", depends_on = ["security"] },
  { id = "coordinator", ref = "job:r43b4-coordinator-source", depends_on = ["r43b3"] },
]
''','devtool-dag')
        return s
    if rel.endswith('GameFileIntegrity.kt'):
        s=replace_once(s,
          '    fun verifyRecorded(root: File): GameIntegrityResult {\n'
          '        check(ledger.isFile && ledger.length() in 1..10485760) { "No saved game integrity record" }\n'
          '        val props = Properties().apply { ledger.inputStream().use { load(it) } }\n',
          '    fun verifyRecorded(root: File, recordedDestination: File = root,\n'
          '                       receiptFile: File = ledger): GameIntegrityResult {\n'
          '        check(receiptFile.isFile && !Files.isSymbolicLink(receiptFile.toPath()) &&\n'
          '            receiptFile.length() in 1..10485760) { "No safe game integrity record" }\n'
          '        val props = Properties().apply { receiptFile.inputStream().use { load(it) } }\n',
          'backup-verification-signature')
        s=replace_once(s,
          'props.getProperty("directory") == GameDownloadRecovery.safeDestination(root).path) {',
          'props.getProperty("directory") == GameDownloadRecovery.safeDestination(recordedDestination).path) {',
          'backup-verification-path')
        # The legacy ledger tracks files, not empty directories. An unknown
        # empty directory can hold user-owned structure even if the file hash
        # set is unchanged. Reject it, rather than silently dropping it.
        s=replace_once(s,
          '        val actual = files(root)\n',
          '''        val expectedDirectories = buildSet<String> {
            for (relative in expected.keys) {
                val segments = relative.split('/')
                for (depth in 1 until segments.size) {
                    add(segments.take(depth).joinToString("/"))
                }
            }
        }
        val actual = files(root)
''','untracked-empty-directory-index')
        s=replace_once(s,
          '        for (missing in expected.keys - actualPaths) errors.add(missing)\n',
          '''        for (missing in expected.keys - actualPaths) errors.add(missing)
        // Fail closed for untracked empty folders and symlinks. The Steam SDK
        // metadata tree is excluded from game-content tracking by design.
        root.walkTopDown().onEnter { dir ->
            // Reject a linked directory without traversing into its target.
            val linked = Files.isSymbolicLink(dir.toPath())
            if (linked) errors.add("Linked directory: ${dir.relativeTo(root).invariantSeparatorsPath}")
            !linked
        }.forEach { node ->
            val relative = node.relativeTo(root).invariantSeparatorsPath
            if (Files.isSymbolicLink(node.toPath())) errors.add("Linked path: $relative")
            if (node != root && node.isDirectory &&
                relative != ".DepotDownloader" &&
                !relative.startsWith(".DepotDownloader/") &&
                relative !in expectedDirectories) {
                errors.add("Untracked directory: $relative")
            }
        }
''','untracked-empty-directory-verification')
        return s
    if rel.endswith('GameDownloadRecovery.kt'):
        return replace_once(s,
          '    /** Explicit user discard; forgets the checkpoint but keeps game bytes. */\n',
          '''    /** R43: the verified replacement keeps its ownership marker while an
     * independent R43 rollback journal and original backup still exist.
     * Never allow the old M24 completed() path to remove that marker.
     */
    @Synchronized fun completeUpdateJournal(token: String) {
        val current = pending() ?: error("M24 update checkpoint missing")
        check(current.updateMode && current.verify && current.token == token &&
            current.stagingDirectory != null && current.steamBuildId > 0L &&
            current.depotManifestFingerprint != null) {
            "Unbound update checkpoint cannot be finalized"
        }
        val original = safeDestination(File(current.directory))
        val stage = safeDestination(File(current.stagingDirectory))
        check(!stage.exists() && original.isDirectory) {
            "Update was not atomically published"
        }
        val marker = File(original, ".cinderbox-download.session")
        check(marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
            marker.readText() == token) { "Published update owner changed" }
        val transaction = File(privateRoot, "r43b-update-swap.properties")
        check(transaction.isFile && !Files.isSymbolicLink(transaction.toPath())) {
            "R43 rollback journal required before closing M24"
        }
        check(journal.delete()) { "Could not finalize M24 update checkpoint" }
    }

    /** Rollback preserves the new bytes in the owned displaced sibling and
     * closes M24 only after the original is back in its exact GameFiles path.
     */
    @Synchronized fun completeRolledBackUpdate(token: String) {
        val current = pending() ?: return
        check(current.updateMode && current.token == token &&
            current.stagingDirectory != null) { "Rollback journal owner changed" }
        val original = safeDestination(File(current.directory))
        val stage = safeDestination(File(current.stagingDirectory))
        val displaced = safeDestination(File(original.parentFile,
            ".GameFiles.cinderbox-r43-rolledback-$token"))
        val marker = File(displaced, ".cinderbox-download.session")
        check(original.isDirectory && !stage.exists() && displaced.isDirectory &&
            marker.isFile && !Files.isSymbolicLink(marker.toPath()) &&
            marker.readText() == token) { "Rollback has not preserved both game versions" }
        check(journal.delete()) { "Could not close the rolled-back M24 journal" }
    }

    /** Explicit user discard; forgets the checkpoint but keeps game bytes. */
''','m24-update-completion')
    if rel.endswith('GameDownloadManager.kt'):
        s=replace_once(s,
          '    fun cancelDownload() {\n        GameDownloadService.stop(context)\n    }',
          '''    /** Explicit existing-game update. No debug path or accidental resume authority.
     * Both feature gates remain OFF until hosted and physical-device acceptance.
     */
    fun startProtectedUpdate(branch: String, branchPassword: String?, os: String,
                             verifyAfterDownload: Boolean, branchBuildId: Long) {
        check(R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED && R43BLiveUpdatePolicy.DEVICE_ACCEPTED) {
            "Protected updates are disabled pending device acceptance"
        }
        require(verifyAfterDownload && branchBuildId > 0L) {
            "Verified Steam build is required for updating"
        }
        check(_progress.value.state !in setOf(DownloadState.PREPARING,
            DownloadState.DOWNLOADING, DownloadState.VERIFYING, DownloadState.COPYING)) {
            "Another game operation is already active"
        }
        val destination = File(CinderboxPaths.GAME_FILES_DIR)
        check(recovery.pending() == null &&
            R43BUpdateAdmission.inspect(context.filesDir).eligible) {
            "Existing game changed, has user files, or needs recovery review"
        }
        StoragePreflight.requireWritable(destination)
        // The coordinator repeats admission before creating a private journal.
        R43BUpdateCoordinator.forProduction(context.filesDir)
            .beginOwnedUpdate(branch, os, verifyAfterDownload)
        recovery.bindSteamBuildId(branchBuildId)
        GameDownloadService.start(context, branch, branchPassword,
            destination.path, os, verifyAfterDownload, branchBuildId)
    }

    fun cancelDownload() {
        GameDownloadService.stop(context)
    }''','manager-existing-update-entry')
        s=replace_once(s,
          'errorMessage = "Interrupted Steam download detected. Sign in and choose Resume interrupted download."',
          '''errorMessage = if (recovery.pending()?.updateMode == true)
                    "Interrupted protected game update: review recovery before resuming."
                else "Interrupted Steam download detected. Sign in and choose Resume interrupted download."''',
          'manager-update-recovery-message')
        s=replace_once(s,
          '    /** Explicit discard ONLY forgets metadata, never deletes downloaded game bytes. */',
          '''    /** Read-only inspection. Does not reconcile or rename files on UI refresh. */
    data class ProtectedUpdateStatus(
        val description: String?, val canRollback: Boolean, val canRecover: Boolean
    )

    fun protectedUpdateStatus(): ProtectedUpdateStatus {
        // The manager knows the coordinator, NOT the raw swap engine. This
        // preserves the older no-direct-swap safety contracts.
        val inspected = R43BUpdateCoordinator.forProduction(context.filesDir).inspectReadOnly()
        return ProtectedUpdateStatus(inspected.description,
            inspected.canRollback, inspected.canRecover)
    }

    private fun requireNoConcurrentGameTransfer() {
        check(_progress.value.state !in setOf(DownloadState.PREPARING,
            DownloadState.DOWNLOADING, DownloadState.VERIFYING, DownloadState.COPYING)) {
            "Cannot modify game directories while a transfer is active"
        }
    }

    /** Explicit user action only; errors never trigger unverified deletion. */
    fun reconcileProtectedUpdate(): String {
        requireNoConcurrentGameTransfer()
        return R43BUpdateCoordinator.forProduction(context.filesDir).reconcileWithoutSteam().name
    }

    /** Explicit user action; coordinator verifies original backup before rename. */
    fun rollbackProtectedUpdate(): String {
        requireNoConcurrentGameTransfer()
        return R43BUpdateCoordinator.forProduction(context.filesDir).rollbackPublished().name
    }

    /** Explicit discard ONLY forgets metadata, never deletes downloaded game bytes. */''',
          'manager-rollback-and-recovery-controls')
        return s
    if rel.endswith('GameDownloadService.kt'):
        s=replace_once(s,
          '''            if (debugAlternative) {
                check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&''',
          '''            val ownedUpdate = recovery.pending()?.takeIf { it.updateMode }
            if (ownedUpdate != null) {
                check(R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
                    R43BLiveUpdatePolicy.DEVICE_ACCEPTED && !debugAlternative &&
                    installRoot.path == CinderboxPaths.GAME_FILES_DIR) {
                    "Unqualified or redirected update service intent"
                }
                check(R43BUpdateAdmission.inspectOwnedStage(filesDir, ownedUpdate).eligible) {
                    "Existing GameFiles changed after update session creation"
                }
            } else {
                if (debugAlternative) {
                    check(scope.code != M23CinderboxFirstPolicy.Code.PROTECTED_CINDERBOX_TREE &&''',
          'service-early-update-auth')
        s=replace_once(s,
          '''                CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)
            }
            StoragePreflight.requireWritable(installRoot)''',
          '''                CinderboxDownloadDestination.requireWritableForInstall(installRoot, promoted)
                }
            }
            StoragePreflight.requireWritable(installRoot)''',
          'service-preserve-legacy-debug-gate')
        s=replace_once(s,
          '''            verifyFiles(installDir, stageRoot, completedFiles, ::recheckBranch,
                checkNotNull(sessionManifestFingerprint), checkNotNull(sessionDepotManifestIds), completedDepots.toSet())''',
          '''            verifyFiles(installDir, stageRoot, completedFiles, ::recheckBranch,
                checkNotNull(sessionManifestFingerprint), checkNotNull(sessionDepotManifestIds),
                completedDepots.toSet(), refreshSelection = {
                    recheckBranch()
                    R43BUpdateCoordinator.Selection(
                        branch, os, expectedBuildId,
                        checkNotNull(sessionManifestFingerprint),
                        checkNotNull(sessionDepotManifestIds), completedDepots.toSet())
                })''','service-authenticated-select')
        s=replace_once(s,
          '''        expectedManifestFingerprint: String, depotIds: Map<Int, String>,
        completedDepots: Set<Int>) {''',
          '''        expectedManifestFingerprint: String, depotIds: Map<Int, String>,
        completedDepots: Set<Int>,
        refreshSelection: suspend () -> R43BUpdateCoordinator.Selection) {''',
          'service-verifier-signature')
        s=replace_once(s,
          '''        GameDownloadRecovery(filesDir).promoteStaged(File(installDir))
        verifyPromotedFiles(File(installDir), depotIds, completedDepots)
        updateNotification(getString(R.string.download_complete))''',
          '''        val checkpoint = GameDownloadRecovery(filesDir).pending()
        if (checkpoint?.updateMode == true) {
            check(R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
                R43BLiveUpdatePolicy.DEVICE_ACCEPTED) {
                "Live update remains disabled pending device acceptance"
            }
            val selection = R43BUpdateCoordinator.Selection(
                checkpoint.branch, checkpoint.os, checkpoint.steamBuildId,
                expectedManifestFingerprint, depotIds, completedDepots)
            R43BUpdateCoordinator.forProduction(filesDir)
                .publishFromSteam(selection, refreshSelection)
            val installed = GameFileIntegrity(filesDir).verifyRecorded(File(installDir))
            check(installed.passed && installed.files > 0) {
                "Published update receipt did not verify"
            }
            GameDownloadManager._progress.value = DownloadProgress(
                state = DownloadState.COMPLETED, overallPercent = 1f,
                verifiedFiles = installed.files, totalFilesToVerify = installed.files,
                verificationPassed = true,
                integrityScope = "PICS_BOUND_DEPOT_SHA1_UNSIGNED")
        } else {
            GameDownloadRecovery(filesDir).promoteStaged(File(installDir))
            verifyPromotedFiles(File(installDir), depotIds, completedDepots)
        }
        updateNotification(getString(R.string.download_complete))''',
          'service-commit-branch')
        return s
    if rel.endswith('GameDownloadViewModel.kt'):
        s=replace_once(s,
          '    val error: String? = null,',
          '''    val protectedUpdateStatus: String? = null,
    val canRollbackProtectedUpdate: Boolean = false,
    val canRecoverProtectedUpdate: Boolean = false,
    val error: String? = null,''',
          'viewmodel-protected-transaction-state')
        return replace_once(s,
          '''    fun cancelDownload() {
        downloadManager.cancelDownload()
    }''',
          '''    /** Only reached through a separate, explicit update confirmation. */
    fun startProtectedUpdateConfirmed() {
        if (!com.sdvsync.download.R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED ||
            !com.sdvsync.download.R43BLiveUpdatePolicy.DEVICE_ACCEPTED) {
            _state.update { it.copy(error = "Existing-game updates are awaiting device acceptance.") }
            return
        }
        if (!steamClientManager.isLoggedIn) {
            _state.update { it.copy(error = "Steam sign-in required for a protected update.") }
            return
        }
        val current = _state.value
        val info = current.branches.firstOrNull { it.name == current.selectedBranch }
        if (info == null || info.buildId <= 0L || !current.verifyAfterDownload) {
            _state.update { it.copy(error = "Refresh Steam branch and build information first.") }
            return
        }
        try {
            downloadManager.startProtectedUpdate(
                current.selectedBranch, current.branchPassword.ifBlank { null },
                current.selectedOs, current.verifyAfterDownload, info.buildId)
            _state.update { it.copy(error = null) }
        } catch (failure: Exception) {
            _state.update { it.copy(error = failure.message ?: "Protected update preflight failed") }
        }
    }

    /** Read-only UI status, rechecked after resume and explicit actions. */
    fun refreshProtectedUpdateStatus() {
        runCatching { downloadManager.protectedUpdateStatus() }
            .onSuccess { status -> _state.update {
                it.copy(protectedUpdateStatus = status.description,
                    canRollbackProtectedUpdate = status.canRollback,
                    canRecoverProtectedUpdate = status.canRecover)
            } }
            .onFailure { failure -> _state.update {
                it.copy(protectedUpdateStatus =
                    "Protected update journal requires review: ${failure.message}",
                    canRollbackProtectedUpdate = false,
                    canRecoverProtectedUpdate = false)
            } }
    }

    fun rollbackProtectedUpdateConfirmed() {
        runCatching { downloadManager.rollbackProtectedUpdate() }
            .onSuccess { result ->
                refreshProtectedUpdateStatus()
                _state.update { it.copy(error = "Protected update rollback: $result") }
            }
            .onFailure { error ->
                refreshProtectedUpdateStatus()
                _state.update { it.copy(error = error.message ?: "Rollback needs manual review") }
            }
    }

    fun recoverProtectedUpdateConfirmed() {
        runCatching { downloadManager.reconcileProtectedUpdate() }
            .onSuccess { result ->
                refreshProtectedUpdateStatus()
                _state.update { it.copy(error = "Protected update recovery: $result") }
            }
            .onFailure { error ->
                refreshProtectedUpdateStatus()
                _state.update { it.copy(error = error.message ?: "Recovery needs manual review") }
            }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }''','viewmodel-consent-entry')
    if rel.endswith('GameDownloadScreen.kt'):
        s=replace_once(s,
          'import com.sdvsync.download.DownloadState\n',
          'import com.sdvsync.download.DownloadState\nimport com.sdvsync.download.R43BLiveUpdatePolicy\n',
          'screen-import-gate')
        s=replace_once(s,
          '''    val state by viewModel.state.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()''',
          '''    val state by viewModel.state.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    var confirmProtectedGameUpdate by remember { mutableStateOf(false) }
    var confirmProtectedRollback by remember { mutableStateOf(false) }
    var confirmProtectedRecovery by remember { mutableStateOf(false) }
    if (confirmProtectedGameUpdate) {
        AlertDialog(
            onDismissRequest = { confirmProtectedGameUpdate = false },
            title = { Text("Update existing game?") },
            text = { Text("Close Cinderbox before proceeding. The existing GameFiles " +
                "must be unchanged and have an app-owned integrity receipt. A new Steam " +
                "stage is verified before an atomic swap; both versions are retained. " +
                "If verification or recovery is uncertain, the operation stops for review. " +
                "Saves, Mods and SMAPI must remain protected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmProtectedGameUpdate = false
                    viewModel.startProtectedUpdateConfirmed()
                }) { Text("Confirm protected update") }
            },
            dismissButton = {
                TextButton(onClick = { confirmProtectedGameUpdate = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (confirmProtectedRollback) {
        AlertDialog(
            onDismissRequest = { confirmProtectedRollback = false },
            title = { Text("Restore original game?") },
            text = { Text("Only the verified owned backup can be restored. " +
                "Both versions are preserved; conflicts require manual review.") },
            confirmButton = { TextButton(onClick = {
                confirmProtectedRollback = false
                viewModel.rollbackProtectedUpdateConfirmed()
            }) { Text("Restore original") } },
            dismissButton = { TextButton(onClick = {
                confirmProtectedRollback = false
            }) { Text("Cancel") } }
        )
    }
    if (confirmProtectedRecovery) {
        AlertDialog(
            onDismissRequest = { confirmProtectedRecovery = false },
            title = { Text("Review interrupted update?") },
            text = { Text("Recovery only restores an owned original into an empty slot. " +
                "It will not overwrite unknown game data.") },
            confirmButton = { TextButton(onClick = {
                confirmProtectedRecovery = false
                viewModel.recoverProtectedUpdateConfirmed()
            }) { Text("Run safe recovery") } },
            dismissButton = { TextButton(onClick = {
                confirmProtectedRecovery = false
            }) { Text("Cancel") } }
        )
    }''','screen-explicit-consent')
        s=replace_once(s,
          '        viewModel.refreshSelectedDirectoryHealth()\n',
          '        viewModel.refreshSelectedDirectoryHealth()\n        viewModel.refreshProtectedUpdateStatus()\n',
          'screen-refresh-update-recovery-state')
        s=replace_once(s,
          '''                        Text("Game files are downloaded only to Cinderbox's GameFiles folder. " +
                            "Existing installations are protected until safe updates are supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)''',
          '''                        Text("Game files are downloaded only to Cinderbox's GameFiles folder. " +
                            "Existing installations are protected until safe updates are supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        if (R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED &&
                            R43BLiveUpdatePolicy.DEVICE_ACCEPTED) {
                            StardewOutlinedButton(onClick = {
                                confirmProtectedGameUpdate = true
                            }, modifier = Modifier.fillMaxWidth(),
                                enabled = state.branches.isNotEmpty() && state.steamLoggedIn &&
                                    !state.isLoadingBranches &&
                                    state.downloadProgress.state == DownloadState.IDLE) {
                                Text("Update existing game (verified + rollback)")
                            }
                            state.protectedUpdateStatus?.let { status ->
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.canRollbackProtectedUpdate) {
                                StardewOutlinedButton(onClick = {
                                    confirmProtectedRollback = true
                                }, modifier = Modifier.fillMaxWidth(),
                                    enabled = state.downloadProgress.state !in setOf(
                                        DownloadState.PREPARING, DownloadState.DOWNLOADING,
                                        DownloadState.VERIFYING, DownloadState.COPYING)) {
                                    Text("Restore original game from backup")
                                }
                            }
                            if (state.canRecoverProtectedUpdate) {
                                StardewOutlinedButton(onClick = {
                                    confirmProtectedRecovery = true
                                }, modifier = Modifier.fillMaxWidth(),
                                    enabled = state.downloadProgress.state !in setOf(
                                        DownloadState.PREPARING, DownloadState.DOWNLOADING,
                                        DownloadState.VERIFYING, DownloadState.COPYING)) {
                                    Text("Review interrupted game update")
                                }
                            }
                        } else {
                            Text("Protected updates: transaction and rollback candidate ready. " +
                                "Live replacement remains disabled until device acceptance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }''','screen-upgrade-preview')
        return s
    raise RuntimeError('R43B4_UNEXPECTED_EXISTING_FILE:'+rel)

def blob_sha(data: bytes) -> str:
    return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def run_git(root: Path, *args: str) -> str:
    return subprocess.run(['git',*args],cwd=root,check=True,capture_output=True,text=True).stdout.strip()

def validate_post(root: Path) -> None:
    s={p:(root/p).read_text(encoding='utf-8') for p in BLOBS}
    assert '[wrapper.commands.r43b4-check]' in s['.devtool.toml']
    cfg=tomllib.loads(s['.devtool.toml'])
    assert [x['id'] for x in cfg['targets']['cinderbox']['workflows']['r43b4-check']] == ['security','r43b3','coordinator']
    assert 'fun completeUpdateJournal(token: String)' in s['app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt']
    assert 'receiptFile: File = ledger' in s['app/src/main/java/com/sdvsync/download/GameFileIntegrity.kt']
    assert 'R43BUpdateCoordinator.forProduction' in s['app/src/main/java/com/sdvsync/download/GameDownloadManager.kt']
    assert 'R43BUpdateCoordinator.forProduction' in s['app/src/main/java/com/sdvsync/download/GameDownloadService.kt']
    assert 'refreshSelection()' not in s['app/src/main/java/com/sdvsync/download/GameDownloadService.kt']
    assert 'fun startProtectedUpdateConfirmed()' in s['app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt']
    assert 'confirmProtectedGameUpdate' in s['app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt']

def main():
    p=argparse.ArgumentParser()
    mx=p.add_mutually_exclusive_group(required=True)
    mx.add_argument('--apply',action='store_true')
    mx.add_argument('--check',action='store_true')
    p.add_argument('--repo',type=Path,default=Path.cwd())
    args=p.parse_args()
    root=args.repo.resolve()
    if args.check:
        validate_post(root)
        print('PASS_R43B4_EXACT_SOURCE_PATCH_POSTCONDITION')
        return
    if run_git(root,'rev-parse','HEAD')!=BASE or run_git(root,'rev-parse','HEAD^{tree}')!=TREE:
        raise RuntimeError('R43B4_BASE_COMMIT_OR_TREE_CHANGED')
    originals={}
    for rel,sha in BLOBS.items():
        path=root/rel
        if path.is_symlink() or not path.is_file():
            raise RuntimeError('R43B4_MISSING_OR_LINKED:'+rel)
        value=path.read_bytes()
        if blob_sha(value)!=sha:
            raise RuntimeError('R43B4_GIT_BLOB_CHANGED:'+rel)
        originals[rel]=value.decode('utf-8')
    changed={rel:transform(rel,txt) for rel,txt in originals.items()}
    # All transforms complete before writing ANY existing file.
    for rel,contents in changed.items():
        dest=root/rel
        tmp=dest.with_name(dest.name+'.r43b4-tmp')
        if tmp.exists() or tmp.is_symlink():
            raise RuntimeError('R43B4_TEMP_COLLISION:'+str(tmp))
    try:
        temps={}
        for rel,contents in changed.items():
            dest=root/rel
            tmp=dest.with_name(dest.name+'.r43b4-tmp')
            with tmp.open('xb') as f:
                f.write(contents.encode());f.flush();os.fsync(f.fileno())
            temps[rel]=tmp
        for rel,tmp in temps.items():
            os.replace(tmp,root/rel)
    finally:
        for rel in changed:
            tmp=(root/rel).with_name(Path(rel).name+'.r43b4-tmp')
            if tmp.exists():tmp.unlink()
    validate_post(root)
    print('PASS_R43B4_EXACT_SOURCE_PATCH_APPLIED')

if __name__=='__main__':
    try:main()
    except (AssertionError,OSError,RuntimeError,ValueError,subprocess.CalledProcessError) as exc:
        print('R43B4_SOURCE_REFUSED:'+str(exc),file=sys.stderr)
        sys.exit(1)
