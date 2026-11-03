# R43-B4 consolidated candidate: owned Steam update, publication and rollback

Base: **PR #47 head 8445f23467e2233a04fb174219814b5681359117**.
All work is intended to update PR #47 as a single consolidated candidate, not create a chain of follow-on PRs.

## Architecture

- R43-B2 still refuses updates when the installed game has modified/untracked bytes, an incomplete journal, or known user-managed directories inside GameFiles.
- R43-B3 records an M24 **update-mode** staging session, retains the occupied original, and independently verifies the staged files against a fresh authenticated Steam PICS selection before writing the swap-preparation journal.
- R43-B4 adds a source-integrated manager entry, foreground service update branch, and explicit consent UI. All remain gated by `R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED=false` and `DEVICE_ACCEPTED=false`.
- `R43BLedgerArchive` copies the pre-update local SHA-256 baseline into an app-private, owner-token-bound witness with fsync and atomic rename. It verifies the actual preserved original against that receipt after the update is published.
- `R43BUpdateCoordinator` owns preparation -> snapshot -> final original check -> atomic backup/promotion -> authenticated post-publication recheck -> new installed integrity receipt -> M24 completion.
- If post-publication verification fails, it validates the original against the witness and requests an atomic rollback. The replacement survives as a displaced sibling; the original receipt is restored.
- The B1 swap state handles interrupted renames and foreign target conflicts without destructive overwrite. Uncertain published recovery is *not* presented as successfully finalized without the matching M24 completion and receipts.
- The production manager/service cannot start the workflow until **both** acceptance gates are deliberately enabled in a future reviewed change. Test/fixture paths use temporary directories.

## Important limitations

1. This is **not** physical-device acceptance, nor signed manifest verification by Valve. A cached SDK depot file SHA-1 plus live PICS identity checks is not an independent Valve signature.
2. This does not determine reliably whether the game is open; users must close Cinderbox before a future enabled update. This must be addressed or explicitly tested before flipping the flags.
3. Local stage and original may require substantial free storage. Storage-budget and space exhaustion scenarios need device-level tests.
4. An app reinstall/clear-data can remove private rollback journals even if the external backup exists. Do not advertise rollback as guaranteed across app-data erasure.
5. Existing-game updates and their UI button are intentionally unavailable while either gate is false. A successful debug build alone is insufficient to enable them.
6. If crash recovery reports `MANUAL_REVIEW`, do not auto-download, overwrite, or delete anything. Preserve the original/backup/stage and seek explicit recovery acceptance.

## CI acceptance

The consolidated source workflow checks exact source/Devtool gates plus earlier R43-A/B1/B2/B3 invariants. The already-existing hosted Android workflow must pass JUnit, lint, debug APK build, and signer checks. Synthetic JVM tests exercise 13 expected variants of publication, rollback, identity drift, symlinks, protected user content, and journal conflicts. A device-level instrumentation lane for storage aliases and interrupted service/process scenarios remains a hard prerequisite for enabling live updates.

## Audited corrections to the initial consolidated overlay

- The swap's **both renames** are now inside the coordinator's exception recovery boundary. When the original was moved but the stage rename fails, the existing B1 engine restores the original only to an empty slot; foreign target conflicts remain manual-review only.
- The installed-file integrity checker now refuses **unknown empty directories**, not just untracked/modified files. User-managed empty folders can otherwise disappear from the published install despite passing a file-only checksum ledger.
- The UI adds separate **explicitly confirmed rollback and interrupted-update recovery controls**, backed by a read-only journal status inspection and an idle-transfer guard. The controls remain hidden while both device acceptance switches are false.
- This candidate does **not** supply a reliable Android game-running proof, guaranteed free-space calculation, or real-device proof of the Cinderbox storage rename semantics. These are still hard blockers to enabling live game replacement.
- The older R43-B1/B2/B3 gates still enforce that their low-level swap or verified bridge is **not called directly** by the manager/service. The coordinator is the only indirect integration, and both live acceptance switches remain false.
