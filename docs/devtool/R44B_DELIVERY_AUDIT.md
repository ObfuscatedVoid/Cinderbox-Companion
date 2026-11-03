# R44-B delivery audit

- Source pin: merged R44-A commit `47886857021dc395810989fb486c02937a9432a0`; exact Git tree `2493fed063830e65639513625ce1c38cd3215087`.
- Overlay: Devtool schema v2, exact-tree, clean-worktree admission, conflict fail, rollback on validation failure, auto-commit enabled, no automatic remote push.
- New production files: `R44BLayoutPolicy.kt`, `R44BExperience.kt`, `R44BExperiencePreview.kt`.
- Updated production files via `before_validation`: `StardewDialog.kt`, `EmptyState.kt`, `OperationCenterScreen.kt`, `InstallationHealthScreen.kt`.
- New qualification: host source gate, host unit tests, Kotlin policy unit tests, GitHub Actions source workflow, Devtool TOML host DAG.
- Unchanged: GameDownloadScreen, GameDownloadViewModel, all game save/mod/SMAPI/download/updater engines, docs/security/M3_RELEASE_POLICY.json.
- Source-only assertions and unit mock compilation are not Android/device certification. Full hosted Gradle build, lint and UI screenshots must be verified on PR.
