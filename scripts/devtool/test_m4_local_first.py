"""Source-bound M4 regressions; Gradle Android tests must also run."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(path): return (ROOT / path).read_text(encoding='utf-8')

class M4Contracts(unittest.TestCase):
    def test_offline_is_explicit(self):
        login = read('app/src/main/java/com/sdvsync/ui/screens/LoginScreen.kt')
        nav = read('app/src/main/java/com/sdvsync/MainActivity.kt')
        assert 'Continue offline' in login
        assert 'onContinueOffline' in login
        assert 'navController.navigate("main")' in nav

    def test_local_saves_do_not_wait_for_steam(self):
        src = read('app/src/main/java/com/sdvsync/ui/viewmodels/DashboardViewModel.kt')
        block = src.split('fun refresh(isUserRefresh:', 1)[1].split('private suspend fun ensureSteamReady', 1)[0]
        assert 'clientManager.isLoggedIn' in block
        assert 'saveFileManager.listLocalSaves()' in block
        assert 'if (steamReady)' in block
        assert 'if (!ensureSteamReady())' not in block

    def test_install_health_cannot_claim_crypto_hashes(self):
        src = read('app/src/main/java/com/sdvsync/local/InstallationScanner.kt')
        assert 'STRUCTURALLY_VALID' in src
        assert 'INACCESSIBLE' in src
        assert 'not performed' in src
        assert 'Some required DLLs' in src
        assert 'smapi(' in src

    def test_storage_gates_are_at_manager_and_service(self):
        for p in ('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt',
                  'app/src/main/java/com/sdvsync/download/GameDownloadService.kt',
                  'app/src/main/java/com/sdvsync/mods/ModDownloadManager.kt',
                  'app/src/main/java/com/sdvsync/mods/ModDownloadService.kt',
                  'app/src/main/java/com/sdvsync/download/AppUpdateManager.kt'):
            assert 'StoragePreflight.requireWritable' in read(p), p

    def test_wizard_and_selected_dir_never_overwrite_game(self):
        # M4 forbids overwriting someone else's game files. M6 adds an explicitly
        # owned resume exception: new downloads are checked by the manager,
        # while the service must reject any call without a matching checkpoint.
        manager = read('app/src/main/java/com/sdvsync/download/GameDownloadManager.kt')
        start = manager.split('    fun startDownload(', 1)[1].split('    fun cancelDownload()', 1)[0]
        assert 'StoragePreflight.requireWritable(directory)' in start
        assert 'InstallationScanner.game(directory).health' in start
        for health in ('INCOMPLETE', 'INACCESSIBLE', 'DETECTED', 'STRUCTURALLY_VALID'):
            assert 'InstallHealth.' + health in start
        assert 'if (checkpoint == null)' in start
        assert 'recovery.beginStaged(directory, branch, os, verifyAfterDownload)' in start
        assert 'recovery.requireMatching(directory, branch, os, verifyAfterDownload)' in start
        assert (start.index('InstallationScanner.game(directory).health') <
                start.index('recovery.beginStaged(directory, branch, os, verifyAfterDownload)'))

        service = read('app/src/main/java/com/sdvsync/download/GameDownloadService.kt')
        run = service.split('    private suspend fun runDownload(', 1)[1]
        # R43 uses one private recovery instance for the ownership proof and
        # subsequent Steam binding. Keep the M4 fail-closed ordering contract:
        # untrusted service intents cannot reach a downloader without a match.
        recovery_init = 'val recovery = GameDownloadRecovery(filesDir)'
        gate = 'recovery.requireMatching(installRoot, branch, os, verify)'
        assert 'StoragePreflight.requireWritable(installRoot)' in run
        assert recovery_init in run
        assert gate in run
        assert run.index(recovery_init) < run.index(gate)
        assert run.index('StoragePreflight.requireWritable(installRoot)') < run.index(gate)
        assert run.index(gate) < run.index('recovery.requireSteamBuildId(expectedBuildId)')
        assert run.index(gate) < run.index('depotDownloader = downloader')

        recovery = read('app/src/main/java/com/sdvsync/download/GameDownloadRecovery.kt')
        assert 'this.directory != safe.path' in recovery
        assert 'marker.readText() == token' in recovery
        assert 'check(pending()?.matches(directory, branch, os, verify) == true)' in recovery

    def test_health_ui_and_offline_navigation(self):
        nav = read('app/src/main/java/com/sdvsync/MainActivity.kt')
        assert 'InstallationHealthScreen(' in nav
        assert 'composable("installation_health")' in nav
        src = read('app/src/main/java/com/sdvsync/ui/screens/InstallationHealthScreen.kt')
        assert 'InstallationScanner.scan' in src
        assert 'Read-only checks' in src

    def test_no_forced_privilege_or_background_download(self):
        for p in ('app/src/main/java/com/sdvsync/local/InstallationScanner.kt',
                  'app/src/main/java/com/sdvsync/local/StoragePreflight.kt'):
            src=read(p)
            for phrase in ('su -c','chmod','deleteRecursively','requestPermissions'):
                assert phrase not in src

if __name__ == '__main__': unittest.main()
