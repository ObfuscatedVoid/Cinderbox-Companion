# R48A — Real MainActivity hosted-emulator navigation qualification

## Scope

This is the **first real-Activity emulator step after R47 synthetic UI acceptance**. It launches the actual `MainActivity`, `SdvSyncNavGraph`, and `MainScreen` in a freshly provisioned GitHub Actions API36 emulator with an intentionally empty account/game fixture. It exercises the real offline entry point, all five primary tab routes and selection semantics, Home → Game → Back, Home → Installation Health → Back, pre-login Settings → Back, and Operation Center → Back. It tests a real 320dp Android window at 160% font scale (not just `CompositionLocalProvider` synthetic density), and captures five real app screens from the debug target app's private files.

Host CI: exact source validation, existing R43C/R47 gates and R48 negative tests. Hosted Android CI: app/debug-test builds, emulator identity `ro.kernel.qemu=1`, fresh package requirement, radio disable, APK signer and installed package byte digest verification, four allowlisted instrumentation cases and five unique complete PNG captures. A separate host verifier checks the full allowlisted inventory, PNG checksums/decompression and evidence/provenance claims.

The app may perform its normal read-only background checks even on this empty emulator; this is **not an assertion of fully disconnected network behavior**. No test creates Steam credentials, grants root/storage permissions, imports mods/saves, changes game files or approves downloads. No production source changes or authorization flips are included.

## Evidence and limits

- Primary workflow: `.github/workflows/r48-real-activity-acceptance.yml`.
- Receipt: `R48_REAL_ACTIVITY_EVIDENCE.json` with `overall=PASS_REAL_ACTIVITY_HOSTED_EMULATOR` only if all four tests pass and all five screenshots verify.
- APK identity: `APK_IDENTITY.json` records source SHA, APK/debug certificate digest, APK/test APK SHA-256 and installed base APK SHA-256. This is a debug CI identity receipt, not release signing acceptance.
- No raw instrumentation logs, Steam tokens, file paths or game data are persisted in the artifact. The screenshots are still actual application UI: they are allowed only in a guaranteed fresh, empty CI emulator fixture, never a user account/device.
- No claim of physical hardware, TalkBack speech/focus traversal, switch access, landscape, 200% text, full online Steam, storage revocation, persistent data/reboot resilience, update interlock, production signing, release or publication.
- R48B requires reviewing these real screenshots and failed CI details to identify and correct actual UX problems. R48C requires separately authorized physical-device accessibility tests with explicit user consent and non-destructive installation/rollback identity proof.

## Safety boundaries

`R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED` and `DEVICE_ACCEPTED` stay false. Every flag in `docs/security/M3_RELEASE_POLICY.json` remains disabled. Device acceptance **cannot** be inferred from a green CI emulator. No `pm clear`, uninstall, root, reboot, actual GameFiles action or release key is used. Future changes must not silently promote this CI result into live update or release readiness.
