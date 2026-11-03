# R43-B3: owned update stage + verified preparation (not activated)

Base: `01487cb72a664b380b9ea076d56e51c5df04874b` (R43-B2 merged).

## Scope

* M24 recovery can represent a **separate update-mode** stage beside an existing
  `desktop/GameFiles` without accepting the occupied target in the normal
  new-install path. Checkpoints without `update_mode=true` keep the old
  absent-target invariant. Invalid flags fail closed.
* `beginStagedUpdate()` is bound to the production Cinderbox path and performs
  an installed-game checksum baseline + protected-user-content admission before
  any stage is made. It is intentionally **not called** by the app.
* `R43BUpdateAdmission.inspectOwnedStage` checks private journal identity and the
  ownership marker before allowing a **read-only** recheck while an M24 update
  stage is in progress. A swap journal, changed original, Mods, and unknown files
  block the handoff.
* `R43BVerifiedStageBridge.prepareForProduction` consumes an M24 build/PICS-bound
  update checkpoint, performs two local staged-file hashes **without replacing the
  original ledger**, checks every completed depot against PICS-bound local depot
  manifests, requires an exact equality match against a fresh authenticated PICS selection, rechecks
  the unchanged original, and records a prepared swap journal. It does **not**
  rename or publish any game files.
* Existing `GameDownloadService`, `GameDownloadManager`, and ViewModel are
  unchanged. No code path can initiate `beginStagedUpdate` or invoke this bridge
  in a production download. APK builds, Kotlin JUnit tests and lint are delegated
  to GitHub Actions.

## Remaining before activation (R43-B4)

1. A lifecycle-bound coordinator that explicitly requests an update and binds
   authenticated PICS build+depot IDs before creating the occupied-target stage.
2. Consistent two-journal crash recovery for every swap window, including
   post-promotion checks and rollback without source-tree loss.
3. New published-file SHA-256 receipt, retention or manual rollback of old
   installation, and explicit finalization of both journals.
4. Device tests on disposable Cinderbox fixtures (including process death,
   low-storage, symlink/mount changes, modified files, and no silent deletion).
5. Explicit user consent and UI copy; never claim signed Valve provenance from
   JavaSteam's local cached depot manifest.

The previous release/qualification policy remains unchanged. This is **not** a
real-device qualification receipt or authorization to overwrite a game.
