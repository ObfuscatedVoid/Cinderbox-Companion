# R43-B1: tested, isolated update-swap core (not enabled)

Baseline: Cinderbox Companion `master` at `5324b2ac6fc5a70f710a586994f83f796fee9826`, after R43-A PR #44.

## What is implemented

`R43BUpdateSwap.kt` is an internal, **not live-wired** primitive for a same-filesystem directory swap of an independently verified staging directory. It uses an app-private, atomically replaced + fsynced journal and exclusive token-bound sibling directory identities. The operations are rename-only; **there are no recursive copy/delete operations**. A prior GameFiles installation is retained as a sibling backup until explicitly reconciled. A rollback preserves the replacement as another sibling rather than deleting it. Tests cover interrupted renames, conflicts, symlinks, invalid ownership markers, preserving Saves, Mods and smapi-internal, and duplicate journals.

The `GameFiles` target is `/storage/emulated/0/StardewValley/desktop/GameFiles`. `Saves`, `Mods`, and `smapi-internal` are outside the rename domain, and are not to be modified. Backup folders can contain user-controlled content, so there is **no automatic cleanup**.

## Strictly NOT claimed

- No real game update or installation was carried out.
- The engine is **not connected to M24's verified Steam download path**. No action on an installed game is enabled by this phase.
- A local ownership marker and non-empty stage do **not** prove Steam entitlement, file authenticity, manifest identity, or safety of replacing modified/user-owned files. B2 must bind verified M24 depot proof and explicitly classify retained user content before invoking this engine. This code must not be called based on a UI Boolean or user-supplied folder path.
- The engine is not a complete disaster-recovery mechanism against kernel crashes, concurrent malicious root process, or storage failure; explicit backup and recovery status must be surfaced to the user.
- For B2, add crash-ordering tests for every journal write and both `ATOMIC_MOVE` operations, concurrent writer exclusion, backup retention policy, post-promotion file verification, and a real-device read-only acceptance gate before permitting an actual update.
- This is a source/JUnit test candidate, not device or release acceptance. R40/R41 evidence remains unchanged.

## Validation

Run host-only tests:

```sh
python3 -B -m unittest discover -s scripts/devtool -p 'test_r43b_swap_gate.py' -v
python3 -B scripts/devtool/r43b_swap_gate.py
```

Android Kotlin compilation and unit tests run through GitHub Actions; never claim host Python static gates alone qualify Kotlin/device behavior.
