# R43-A — Cinderbox-only destination, fail-closed new installations

**Base:** merged R42 `master`, `47dc6c1a3cd8659b6fe969d6dd8faad910a77cf5`.

This overlay implements **R43-A** (fixed production destination and safe blocking),
not the R43-B existing-game replacement transaction.

## Product behavior

- The only normal/release destination for a Steam game is
  `/storage/emulated/0/StardewValley/desktop/GameFiles`, taken from
  `CinderboxPaths.GAME_FILES_DIR`. Saved M4 alternative folder preferences and
  old checkpoint paths do not silently override the production destination.
- All destination entry points—ViewModel, manager and foreground service—check
  the fixed identity and actual storage. A service intent cannot bypass the UI.
- If the Cinderbox root or `desktop` directory is missing, permissions are
  unavailable, path is unsafe, or a game installation exists, download is
  blocked with a user-readable explanation. Nothing is deleted or replaced.
- The existing M24 same-filesystem sibling staging, checkpoint token, depot
  identity/file hashes, and absent-target-only atomic publication remain intact.
- An interrupted download to the **exact same GameFiles destination** can
  resume under M24's original ownership and Steam build rules. A promotion
  already completed but awaiting verification may be reconciled only when
  `GameDownloadRecovery.isPromotedStage` and `requireMatching` confirm ownership.
- Existing game, SMAPI, mods and saves are never overwritten. R43-B must add a
  separate rollback-capable, crash-safe, source-bound update transaction before
  existing installations may be updated.
- Debug-only alternative destinations remain gated by both `BuildConfig.DEBUG`
  and the deliberate developer unlock. Normal release builds never expose the
  picker or use saved alternative paths. No developer UI gate authorizes a
  filesystem write without the M23/M24 checks.
- Wizard's alternative-app choice is hidden in production. A direct call to its
  ViewModel handler is also rejected outside unlocked debug builds.

## Acceptance boundary

Source gate + Kotlin unit tests + Android compilation are necessary, not enough
for device qualification. **No real Steam transfer, filesystem mutation on a
user device, existing-game upgrade, R41-release reuse, or release publishing**
is authorized by this overlay. R40/R41 release receipts and scripts are unchanged.

## R43-B and future work

1. Explicit existing-game update consent and game-not-running detection.
2. Size budgeting for staged new game + protected existing game + rollback.
3. Same-volume atomic old/new swap with durable journal and staged proof.
4. Tested failure recovery across crashes at every rename boundary.
5. Scoped rollback only when ownership and destination identity remain valid.
6. Physical-device tests using a new GitHub-signed candidate.

Keep R44–R47 UI redesign and release qualification on separate reviewed patches.
