# R42 — Developer feature boundaries and production-safe presentation

Source base: `Mikeyphw/Cinderbox-Companion` `master` at `b4d8e2e0c085906cc54d5cbb47cf7fee4fe92984`.

R42 is **not** R43. It does not change the Cinderbox download destination, upgrade an existing game, authorize a filesystem write, verify a real Steam download or qualify a new release. Cinderbox's existing M23/M24 protections remain enforced in the ViewModel, Manager, and Service.

## Implemented

- `DeveloperFeaturePolicy` uses the existing seven-tap About unlock for diagnostics. Direct navigation to the Steam diagnostics route is denied unless unlocked.
- Alternative destination selection and manual path editing are exposed **only when both `BuildConfig.DEBUG` and the persistent developer unlock are true**. The ViewModel independently refuses both setter and picker actions if the guard fails. A debug-only display override never weakens Manager or Service destination protections.
- Production download UI is honest about the **temporary separate destination** in R42. R43 must migrate the final target to Cinderbox's `desktop/GameFiles` using a reviewed, rollback-safe transaction rather than changing a string constant.
- Read-only status and errors use user-friendly text, while preserving the existing integrity warnings and required verification.
- New `r42-check` Devtool workflow and separate GitHub source-contract workflow use host Python, not Gradle. Existing GitHub Android build workflows remain the only place for Gradle.

## Release and historical evidence

R40 signing run `41502904211` and R41 release-device preparation run `41525142590` are historical sources; all R40/R41 scripts, policies, workflows and acceptance receipts remain untouched. R41 installed APK `0.1.1` (14) does NOT qualify R42. No application installation, APK signing, automatic publication, game download, SMAPI changes, save/mod writes or device mutation are part of this overlay.

## Acceptance

1. Apply only to the exact R41-merged base, with a clean worktree. No `--force`, no overlay rebase.
2. `python3 -B -m unittest discover -s scripts/devtool -p 'test_r42_product_policy.py' -v` and `python3 -B scripts/devtool/r42_product_gate.py` must pass.
3. `./devtoolw r42-check` runs only source gates.
4. GitHub Actions must compile/test the changed Kotlin source via the existing debug APK workflow before claiming Android correctness. Runtime UI testing and release signing require new evidence.
5. R43 must resolve the current separate Downloads path, existing M24 journals and installed Cinderbox game updates without silent overwrite.
