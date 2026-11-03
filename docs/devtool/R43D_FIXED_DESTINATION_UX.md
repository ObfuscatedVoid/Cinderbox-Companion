# R43-D — Cinderbox-only destination UX, not a write authorization

**Source base:** `Mikeyphw/Cinderbox-Companion` `master` `a307c2d26828c86cd2544a3c0debb58871abaf09`, tree `783322acca7759c3ba3819eca0611f98d3234c48`. Work follows R43-C merged PR #48. No historical R40/R41 evidence is changed.

## Shipped in this source candidate

- **One fixed-location status** on the normal download screen, driven by `CinderboxDownloadDestination.inspect` in the ViewModel on initial load and resume. The UI maps policy codes to safe, actionable copy (available for further checks, setup needed, access blocked, existing protected game, manual review), never to download permission.
- **No public absolute `GameFiles` path** in the normal status card. The legacy enum-code display is replaced by human descriptions. Steam integrity is explicitly *unverified* during installation discovery; local status is not a signed Steam manifest.
- **Developer-only alternative path** and setup-wizard “Other” option remain behind `BuildConfig.DEBUG && explicit developer unlock`. They do not grant write privileges and remain subject to Manager/Service and M24 gates. Technical selected-path details appear only under that gate.
- **Protected existing game** and typed recovery/discovery warning take priority over superficial “ready” status. Existing game replacement remains disabled; no new file mutation, arbitrary folder picker, journal mutation or Steam operation was introduced.
- **Tests and Devtool**: 7 Kotlin/JUnit presentation tests, source gate + 3 host Python checks, pinned two Kotlin file changes and `.devtool.toml`, new `r43d-check` DAG and GitHub source workflow preserving R42/R43A/B4/C regressions. Existing GitHub Android build workflow compiles JVM tests and APK; no local Gradle permitted.

## Deliberate limits

- Public error mapping across all app screens belongs to **R46**. This phase removes a known direct GameFiles path and technical enum status from the download card, and hides debug-specific folder text. It does not certify *all* exception copies elsewhere in the app.
- Recovery strings are accepted only as **read-only warnings**, not as authoritative entitlement to restore or update. Future typed recovery states should replace text matching. Existing backend write guards are unchanged.
- This overlay does not implement an authoritative game stop/launch fence, trusted Steam expected-size budget, production ENOSPC fault handling or full reboot matrix. R43-C production updates remain intentionally blocked.
- Human visual/accessible screenshot acceptance, Android smoke testing and BKX-like Material 3 polish follow in R44/R45. This phase does not claim R44 redesign or M25/M26 cloud/mod functionality.
- `docs/security/M3_RELEASE_POLICY.json` remains all false; `R43BLiveUpdatePolicy` booleans stay false. No automatic device install, release signing or publishing.

## Acceptance evidence required

1. Devtool applies the single schema-v2 exact-tree overlay and creates one automatic Git commit, no manual `git commit` necessary.
2. Host source/legacy gates pass; new PR triggers hosted **Build Debug APK** with Android Kotlin/JVM compilation, lint, signed debug APK and instrumentation APK compilation.
3. Review UI appearance on supported phone and adjust in R44/R45; do not run real game update or alter saved games for visual acceptance.
4. Merge only after hosted PR checks. A green source gate does not mean physical-device acceptance.
