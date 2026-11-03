# R43-C — Consolidated production-safety candidate (fail closed)

**Base**: `Mikeyphw/Cinderbox-Companion` `master` commit `9c6485521f6da05b0a033fc838461a643e8977f7`, tree `06cc91fe19f2effdc9d0cee41ebb1befda89932a`. No history rewrite. This is a **source candidate**, not an activated/accepted production update.

## What is in the consolidated overlay

1. **Cinderbox process/launch exclusion (not yet qualified).** `R43CUpdateSafety` defines a testable STOPPED/RUNNING/UNKNOWN witness that requires *positive* stop AND launch-exclusion proof, not absence from `ps` or a Companion-only lock. Production currently uses `UNKNOWN`: it **always refuses**. `R43BUpdateCoordinator` rejects admission/publication/finalization/rollback/recovery without proof. `R43BUpdateSwap` also checks before each critical rename and in mutating reconcile. The foreground service independently rejects update-mode invocations. These checks do not kill or launch anything.
2. **Storage accounting.** Pure overflow-safe budget distinguishes stage download (extra stage bytes are needed), publication (the already-complete stage must **not** be double-counted), private journal capacity and shared storage reserve. It refuses unknown sizes, unknown/free=0 storage, foreign filesystems, linked/unsupported stage nodes. App-installed game and sibling Saves/Mods/SMAPI are never deleted for room. Point-in-time probes cannot guarantee future FUSE/quota free space; ENOSPC, revocation and volume changes require device fault qualification.
3. **Read-only cold-start recovery status.** `R43CRecoveryReadOnly` detects orphaned stage/backup siblings, absent witness/receipt, conflicting target/link, incomplete transaction or missing app-private data as **manual review**. It does not call `swap.reconcile()` (which mutates). Rollback is only advertised after verifying the retained original witness, ownership and recorded published content. There is no unattended boot restore or file cleanup.
4. **Host and Android synthetic coverage.** JVM fixtures cover game activity, unknown permission, race observations, low/unknown/exact-margin storage, overflow, sibling/symlink interference, stale/orphaned recovery and retained bytes. Android instrumented class runs only in temporary app-private data; it does not open `/storage/emulated/0/StardewValley/desktop/GameFiles`. Hosted GitHub Actions build already runs JVM tests, lint, debug APK, `assembleDebugAndroidTest`, APK signer and provenance validation; R43-C adds a separate no-Gradle source workflow and Devtool `r43c-check` DAG preserving R43B4/B3 source checks.
5. **Strict Devtool contract.** One schema-v2 overlay, source SHA-256 for direct modifications, Git blob preimages for two finish-time source changes, exact `HEAD`+tree, clean-tree requirement, atomic Devtool application, local host-only validation, and a manifest-driven automatic Git commit after validation (no automatic push or release).

## Explicit remaining *blocking* qualifications

- **Authoritative game stop and launch lock**: identify real Cinderbox Android processes/package and supported launch control; demonstrate a fence spanning BOTH directory renames and rollback; distinguish stopped from unobservable. No such proof exists yet. Turning the policy booleans on today still cannot enable a production update, because the witness refuses and the early verified Steam size is unprovided.
- **Real Steam stage byte budget**: integrate a trustworthy size from authenticated selected PICS/depot manifests (including shared depots, compression and temporary overhead), prove quota/volume semantics and handle changing free space and ENOSPC at every journal/rename boundary. Pure-test estimates are not Steam authorization.
- **End-to-end crash matrix**: app-private faults and physical-device same-filesystem acceptance across every M24 journal, R43 swap, ledger/fsync boundary, second rename, post-publish verify, rollback and device reboot. Interrupted or ambiguous real filesystem state must be reviewed, never auto-deleted. A powered-off / app-cleared device has no presumed ledger.
- **Manager/service/Android UX**: review update eligibility/user errors for 1st-run, inaccessible destination, branch changes, interrupted stage, manual review; R43-D still requires a separate path-leakage audit. Production screen must not expose hidden diagnostics or alternative writable locations.
- **Steam proof, device provenance and release**: authenticated entitlement, live depot completion, actual game closed/locked, same-source debug/test APK installed identity, synthetic/app-private device cases, authorized disposable-target test *separate* from an existing real user install. CI builds and historical 5/5 R43B4 synthetic passes are insufficient.

## Required CI after application

On the same PR-head commit: run `r43c-protected-safety.yml` (source) and existing `build.yml` (GitHub Gradle JVM tests, lint, app APK, instrumented APK compile, signer/provenance). Inspect older R42/R43 A/B1/B2/B3/B4 checks as regression gates. Do not claim Android test *execution* because CI builds only the instrumentation APK: select the ADB serial and install/execute from the **same run**, retaining hashes/signer records; do not uninstall user app or clear data without explicit permission. The next physical-device run uses app-private fixtures only.

## Security and roadmap constraints

`R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED=false`, `DEVICE_ACCEPTED=false`. All four `M3_RELEASE_POLICY.json` booleans remain false. Production destination is **only** Cinderbox `desktop/GameFiles`. No automatic live Steam update, game modification, release signing, installed-game experiment, or publication.

R43-D (destination UX) follows; R44 design, R45 Home/Game/Saves/Mods/More, R46 human errors, R47 qualification remain active. Original M4–M29 is preserved: M25 mod lifecycle, M26 real Cloud saves, M27 Android resilience, M28 upgrade/signer and M29 explicit release decision are *not canceled* by R43-C or visual redesign. Preserve prior historical evidence without re-labeling as current-pass.

### Honest completion classification

- Source: consolidated guard/capacity/read-only recovery candidate implemented, subject to repository CI.
- Host: Python source/manifest/static tests and `kotlinc` source compiles can be performed without Android Gradle.
- Device: **not yet performed for this candidate**.
- Real protected update: **blocked and unqualified**.
- Release: **disabled**.
