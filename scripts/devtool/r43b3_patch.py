#!/usr/bin/env python3
"""R43-B3 exact-blob Devtool finalize. Refuses all unexpected base sources."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys

BASE = '01487cb72a664b380b9ea076d56e51c5df04874b'
BLOBS = {
 '.devtool.toml': 'f631d6fb6dcfc903ace3bc13ffdef56799cddda3',
 'app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt': '7022169bc1613c7d16bd3f1fcc1138219079c845',
 'app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt': '51f7c709664931fd7e88fa168ccb11bab1fdfc5c',
}
WRAPPER = '''[wrapper.commands.r43b3-check]
description = "R43-B3 owned update staging and verified-preparation bridge (live update disabled)"
workflow = "r43b3-check"
target = "cinderbox"

'''
JOB = '''[targets.cinderbox.jobs.r43b3-handoff-source]
runner = "command"
execution_environment = "host"
command = ["python3", "-B", "scripts/devtool/r43b3_gate.py"]
inputs = ["scripts/devtool/r43b3_*.py", "app/src/main/java/com/sdvsync/download/R43BVerifiedStageBridge.kt", "app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt", "app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt", "app/src/test/java/com/sdvsync/download/R43BUpdateStageSessionTest.kt", ".devtool.toml"]
metadata = { read_only = true, area = "r43b3-owned-stage-unwired", root_required = false, device_required = false }

'''
FLOW = '''r43b3-check = [
  { id = "security", ref = "job:security-contract", depends_on = [] },
  { id = "r43b2", ref = "job:r43b2-admission-source", depends_on = ["security"] },
  { id = "handoff", ref = "job:r43b3-handoff-source", depends_on = ["r43b2"] },
]
'''

def replace_once(s,a,b,label):
    n=s.count(a)
    if n!=1: raise RuntimeError(f'R43B3_ANCHOR_MISMATCH:{label}:{n}')
    return s.replace(a,b,1)

def transform(path, s):
    if path == '.devtool.toml':
        if '[wrapper.commands.r43b3-check]' in s: raise RuntimeError('R43B3_ALREADY_INSTALLED')
        s=replace_once(s,'[wrapper.commands.m23-check]\n',WRAPPER+'[wrapper.commands.m23-check]\n','wrapper')
        return replace_once(s,'[targets.cinderbox.workflows]\n',JOB+'[targets.cinderbox.workflows]\n'+FLOW,'flow')
    if path.endswith('GameDownloadRecovery.kt'):
        s=replace_once(s,'import com.sdvsync.local.PlatformStoragePaths\n',
            'import com.sdvsync.local.PlatformStoragePaths\nimport com.sdvsync.cinderbox.CinderboxPaths\n','import')
        s=replace_once(s,'    val depotManifestFingerprint: String? = null\n)',
            '    val depotManifestFingerprint: String? = null,\n    val updateMode: Boolean = false\n)','checkpoint')
        s=replace_once(s,
            '        if (stage != null && stage.exists() == safe.exists()) return false\n',
            '        if (stage != null) {\n'
            '            if (updateMode) {\n'
            '                // PRE-PROMOTION ONLY. Recovery after a swap belongs to R43-B1,\n'
            '                // never the old M24 absent-target path.\n'
            '                if (!stage.isDirectory || !safe.isDirectory) return false\n'
            '            } else if (stage.exists() == safe.exists()) return false\n'
            '        }\n', 'matches')
        s=replace_once(s,'        val manifestHash = props.getProperty("pics_depot_manifest_fingerprint")\n',
            '        val updateMode = when (props.getProperty("update_mode")) {\n'
            '            null, "false" -> false\n'
            '            "true" -> true\n'
            '            else -> error("Invalid update-mode checkpoint flag")\n'
            '        }\n'
            '        check(!updateMode || (stagePath != null && verify)) {\n'
            '            "Update mode requires verified sibling staging"\n'
            '        }\n'
            '        val manifestHash = props.getProperty("pics_depot_manifest_fingerprint")\n', 'parse-mode')
        s=replace_once(s,
            '        return GameDownloadCheckpoint(directory, branch, os, verify, token, buildId, stagePath, manifestHash)\n',
            '        return GameDownloadCheckpoint(directory, branch, os, verify, token, buildId, stagePath, manifestHash, updateMode)\n','construct')
        s=replace_once(s,
            '    @Synchronized fun beginStaged(directory: File, branch: String, os: String, verify: Boolean) =\n'
            '        beginOwned(directory, branch, os, verify, true)\n',
            '    @Synchronized fun beginStaged(directory: File, branch: String, os: String, verify: Boolean) =\n'
            '        beginOwned(directory, branch, os, verify, true)\n\n'
            '    /** R43-B3: owned stage beside a still-occupied game; NOT called by live UI/service.\n'
            '     * The old installation and its integrity ledger must pass read-only admission\n'
            '     * before any owned stage can be created. Publication remains separate.\n'
            '     */\n'
            '    @Synchronized fun beginStagedUpdate(directory: File, branch: String, os: String, verify: Boolean) {\n'
            '        check(verify && directory.path == CinderboxPaths.GAME_FILES_DIR) {\n'
            '            "Update staging is restricted to verified Cinderbox GameFiles"\n'
            '        }\n'
            '        check(R43BUpdateAdmission.inspect(privateRoot).eligible) {\n'
            '            "Installed game is not eligible for protected staging"\n'
            '        }\n'
            '        beginOwned(directory, branch, os, verify, true, updateMode = true)\n'
            '    }\n', 'begin-staged-update')
        s=replace_once(s,
            '    private fun beginOwned(directory: File, branch: String, os: String, verify: Boolean, staged: Boolean) {\n',
            '    private fun beginOwned(directory: File, branch: String, os: String, verify: Boolean,\n'
            '                           staged: Boolean, updateMode: Boolean = false) {\n'
            '        check(!updateMode || staged) { "Update requires sibling staging" }\n', 'begin-signature')
        s=replace_once(s,
            '            check(!safe.exists() && safe.parentFile?.isDirectory == true) {\n'
            '                "Staged download requires an absent target and existing parent"\n'
            '            }\n',
            '            if (updateMode) {\n'
            '                check(safe.isDirectory && safe.parentFile?.isDirectory == true &&\n'
            '                    R43BUpdateAdmission.inspect(privateRoot).eligible) {\n'
            '                    "Existing GameFiles no longer satisfies read-only update admission"\n'
            '                }\n'
            '            } else {\n'
            '                check(!safe.exists() && safe.parentFile?.isDirectory == true) {\n'
            '                    "Staged download requires an absent target and existing parent"\n'
            '                }\n'
            '            }\n', 'begin-preflight')
        s=replace_once(s,
            '            if (staged) setProperty("staging_directory", stage.path)\n',
            '            if (staged) setProperty("staging_directory", stage.path)\n'
            '            if (updateMode) setProperty("update_mode", "true")\n','journal-flag')
        s=replace_once(s,
            '        check(stage.isDirectory && !destination.exists()) {\n',
            '        check(stage.isDirectory && (if (current.updateMode) destination.isDirectory\n'
            '            else !destination.exists())) {\n','active-stage')
        s=replace_once(s,
            '        if (current.stagingDirectory == null || current.steamBuildId <= 0L ||\n',
            '        if (current.updateMode || current.stagingDirectory == null || current.steamBuildId <= 0L ||\n','no-false-promotion')
        s=replace_once(s,
            '            if (current.stagingDirectory != null) {\n',
            '            check(!current.updateMode) {\n'
            '                "M24 completion cannot finalize an occupied-target update; use R43 recovery"\n'
            '            }\n'
            '            if (current.stagingDirectory != null) {\n', 'completion-not-allowed')
        s=replace_once(s,
            '        if (current != null) {\n            val path = current.stagingDirectory?.let { File(it) }\n',
            '        if (current != null) {\n'
            '            check(!current.updateMode ||\n'
            '                !File(privateRoot, "r43b-update-swap.properties").exists()) {\n'
            '                "Prepared update swap cannot be discarded before recovery review"\n'
            '            }\n'
            '            val path = current.stagingDirectory?.let { File(it) }\n', 'discard-prepared-swap')
        return s
    if path.endswith('R43BUpdateAdmission.kt'):
        s=replace_once(s,
            '    internal fun inspectFixture(privateRoot: File, gameFiles: File): Assessment =\n'
            '        inspectCore(privateRoot, gameFiles, fixture = true)\n',
            '    internal fun inspectFixture(privateRoot: File, gameFiles: File): Assessment =\n'
            '        inspectCore(privateRoot, gameFiles, fixture = true)\n\n'
            '    /** Only an exact existing M24 update journal can bypass the pending-download\n'
            '     * block for READ-ONLY baseline rechecks; not an installation authority.\n'
            '     */\n'
            '    fun inspectOwnedStage(privateRoot: File, checkpoint: GameDownloadCheckpoint): Assessment =\n'
            '        inspectOwnedCore(privateRoot, File(CinderboxPaths.GAME_FILES_DIR), checkpoint, false)\n\n'
            '    internal fun inspectOwnedStageFixture(privateRoot: File, gameFiles: File,\n'
            '                                          checkpoint: GameDownloadCheckpoint): Assessment =\n'
            '        inspectOwnedCore(privateRoot, gameFiles, checkpoint, true)\n\n'
            '    private fun inspectOwnedCore(privateRoot: File, gameFiles: File,\n'
            '                                 checkpoint: GameDownloadCheckpoint, fixture: Boolean): Assessment {\n'
            '        if (!checkpoint.updateMode || !checkpoint.verify || checkpoint.stagingDirectory == null ||\n'
            '            checkpoint.directory != gameFiles.canonicalPath) {\n'
            '            return refusal(Code.RECOVERY_REVIEW_REQUIRED, "No verified owned update checkpoint")\n'
            '        }\n'
            '        val owned = runCatching {\n'
            '            val current = GameDownloadRecovery(privateRoot).pending()\n'
            '            current == checkpoint && checkpoint.matches(gameFiles,\n'
            '                checkpoint.branch, checkpoint.os, true)\n'
            '        }.getOrDefault(false)\n'
            '        if (!owned) return refusal(Code.RECOVERY_REVIEW_REQUIRED,\n'
            '            "M24 checkpoint and sibling stage ownership could not be confirmed")\n'
            '        return inspectCore(privateRoot, gameFiles, fixture, ownedUpdateJournal = true)\n'
            '    }\n', 'owned-admission')
        s=replace_once(s,
            '    private fun inspectCore(privateRoot: File, gameFiles: File, fixture: Boolean): Assessment {\n',
            '    private fun inspectCore(privateRoot: File, gameFiles: File, fixture: Boolean,\n'
            '                            ownedUpdateJournal: Boolean = false): Assessment {\n', 'admission-signature')
        s=replace_once(s,
            '            downloadJournal.exists() || swapJournal.exists()) {\n',
            '            (downloadJournal.exists() && !ownedUpdateJournal) || swapJournal.exists()) {\n', 'pending-journal')
        return s
    raise RuntimeError('R43B3_UNKNOWN_MODIFIED_FILE:'+path)

def blob(data):
    return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def main():
    a=argparse.ArgumentParser()
    g=a.add_mutually_exclusive_group(required=True)
    g.add_argument('--apply',action='store_true');g.add_argument('--check',action='store_true')
    a.add_argument('--repo',type=Path,default=Path.cwd())
    args=a.parse_args();root=args.repo.resolve()
    if args.check:
        policy=(root/'app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt').read_text()
        assert 'fun beginStagedUpdate(' in policy and 'update_mode' in policy
        assert 'check(!safe.exists())' in policy and 'check(!current.updateMode)' in policy
        cfg=(root/'.devtool.toml').read_text();assert '[wrapper.commands.r43b3-check]' in cfg
        assert 'fun inspectOwnedStage(' in (root/'app/src/main/java/com/sdvsync/download/R43BUpdateAdmission.kt').read_text()
        print('R43B3_PATCH_ALREADY_APPLIED_AND_CHECKED');return
    sha=subprocess.run(['git','rev-parse','HEAD'],cwd=root,capture_output=True,text=True,check=True).stdout.strip()
    if sha!=BASE: raise RuntimeError('R43B3_WRONG_BASE:'+sha)
    changes={}
    for rel,expected in BLOBS.items():
        p=root/rel
        if p.is_symlink() or not p.is_file(): raise RuntimeError('R43B3_SOURCE_MISSING_OR_LINKED:'+rel)
        data=p.read_bytes()
        if blob(data)!=expected: raise RuntimeError('R43B3_BASE_BLOB_MISMATCH:'+rel)
        content=transform(rel,data.decode())
        changes[rel]=content.encode()
    for rel,data in changes.items():
        p=root/rel; tmp=p.with_name(p.name+'.r43b3-tmp')
        tmp.write_bytes(data);tmp.replace(p)
    print('R43B3_EXACT_BLOB_PATCH_APPLIED',len(changes))

if __name__=='__main__':
    try: main()
    except (AssertionError, OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as exc:
        print('R43B3_PATCH_REFUSED:'+str(exc),file=sys.stderr);sys.exit(1)
