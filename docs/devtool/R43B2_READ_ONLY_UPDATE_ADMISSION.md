# R43-B2: safe update eligibility + staging-ledger isolation

## Baseline and scope

Exact source: `Mikeyphw/Cinderbox-Companion` `master` commit
`9ad42db2c48e83cddd4ce07d4d165761b149f8a8` (R43-B1 merged).

This step does **not** activate automatic replacement, build an APK locally,
query Steam, or touch the real Cinderbox installation. It is a small, explicitly
non-destructive prerequisite for a future verified-stage integration.

## Corrected M24 ledger lifetime

Previously `GameDownloadService.verifyFiles()` called
`GameFileIntegrity.verifyAndRecord(stageRoot)`, updating the global app-private
integrity ledger to the **temporary staging directory** before the game was
published. Any existing installation's last-known SHA-256 baseline would thus
be overwritten prematurely. That is unsafe for update admission and for
reconciliation after a failed staged download.

`GameFileIntegrity.verifyAndRecord(..., persistLedger = false)` now verifies
both hashing passes and emits progress without writing the ledger. Only
`verifyPromotedFiles(destination)` records the final, published GameFiles
baseline. Default `persistLedger = true` preserves prior API behavior elsewhere.

The ledger checks local file changes. They do not establish signed Steam file
provenance, source authenticity, account entitlement, or complete depot scope.

## Admission rules

`R43BUpdateAdmission.inspect(privateRoot)` is strictly read-only, with a
synthetic fixture seam for unit tests. It requires:

- Exact Cinderbox `desktop/GameFiles` production destination and safe path.
- Existing readable and writable GameFiles and accessible parent (no probe writes).
- No active M24 download journal, pending R43 swap journal, session owner
  marker, or symlinked private receipts.
- No user-owned `Mods`, `Saves`, `smapi-internal`, SMAPI, saved backups,
  screenshots, configuration or error-log trees **inside** GameFiles.
- A bounded, valid, app-private post-publication SHA-256 ledger bound to this
  directory, and unchanged files with no unknown extras or missing entries.
- No writes, directory creation, marker creation, backup moves, or stage cleanup.

Saves/Mods outside GameFiles and `smapi-internal` are not scanned or changed.
A previously imported Cinderbox installation without a matching app-private
receipt is *not* eligible. A user-modified installation is *not* eligible.

## Pending R43-B2 integration / release block

This is **not** a swap authorization. The live update pathway remains blocked
by R43-A and M24 and cannot replace an installed game. A subsequent phase must:

1. Start a journal-owned update stage while preserving the occupied original.
2. Bind Steam PICS build, branch, depot identities, and completed file hashes to
   the exact stage and the original's still-current checksum baseline.
3. Recheck both before invoking `R43BUpdateSwap.recordVerifiedStage()`.
4. Handle interruption across all rename and post-publication receipt states.
5. Keep old GameFiles backup and never move `desktop/Saves`, `desktop/Mods`,
   `smapi-internal`, or user-modified content without explicit review.
6. Complete GitHub Android tests and real-device evidence before enabling any
   production update control or describing the update as fully working.

## Validation

- R43-B2 host regression and source gate.
- Existing R43-B1 swap, R43-A, R42, R40 and M24 gates unchanged.
- Android Kotlin tests, lint, debug APK and signing via existing GitHub Actions.
- No claims of real Android device or authenticated Steam transfer acceptance.
