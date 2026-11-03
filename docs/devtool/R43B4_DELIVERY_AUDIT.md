# Consolidated R43-B4 delivery audit — repaired candidate

**Base:** PR #47 branch `feature/r43b3-verified-stage`, commit `8445f23467e2233a04fb174219814b5681359117`, tree `c33e83bbc81e294f53aa400ff66e45027fdb09f5`.

This report distinguishes source implementation from actual Android/device acceptance.

| Promised area | Source delivery | Qualification / limitation |
|---|---|---|
| Authenticated Steam update staging | New update-mode manager/service path calls the existing verified handoff through `R43BUpdateCoordinator` | Gates OFF; live Steam entitlement/depot proof and device download NOT tested |
| Read-only installed-game admission | Installed SHA-256 baseline, protected folders and owned session checks; repaired to reject untracked **empty directories** too | Fail-closed; an old installation without an app-generated baseline is ineligible |
| Atomic backup and publication | Same-volume directory renames only; original backup retained; both renames inside coordinator error recovery | Filesystem-specific behavior and concurrent-game races require device acceptance |
| Rollback | Dedicated original checksum witness; verified original backup restored; new game preserved in displaced sibling | No backup guarantee after clearing app-private data |
| Interrupted transaction recovery | `R43BUpdateSwap.reconcile()` restores original into an empty slot, rejects foreign targets; explicit UI recovery action | Does not auto-run in the background; unknown state requires manual review |
| User consent and UI | Update confirmation, explicit rollback and recovery confirmations, read-only journal status | UI controls inaccessible while acceptance gates are OFF |
| Devtool and GitHub workflows | Exact-base ZIP, `r43b4-check`, hosted source gate, prior R43 contract checks, existing hosted Gradle build | Android CI must still run after push; device instrumentation source included but execution NOT proven |
| Local tests | 13 synthetic Kotlin coordinator scenarios using temporary directories + a **test adapter**; 5 Android instrumentation source scenarios | Synthetic adapter is not production Android/Steam testing |
| Game-running detection | NOT implemented as a reliable Android check | **Hard blocker** before activating real updates |
| Free-space / exhaustion | Existing write preflight used, but **no guaranteed stage-size budget** | **Hard blocker** before activating real updates |
| Valve-signed proof | Not implemented; PICS identity + cached depot SHA-1 are not Valve signatures | Do not claim independently authenticated file provenance |

## Additional corrections from initial ZIP

1. Exception handling now encloses **both** atomic renames; failed second moves can request safe original restoration without overwriting foreign files.
2. Untracked empty user-created folders inside `GameFiles` now fail admission.
3. Read-only transaction status, separately confirmed rollback and recovery controls are routed through the coordinator; raw swap is never invoked directly by manager/service.
4. Interrupted update status is labeled separately from ordinary resumable Steam downloads.
5. Source gates check the new directory and coordinator boundaries; device acceptance flags stay `false`.

## Validation performed locally

- ZIP manifest, member hashes and shell-script hashes: checked after packaging.
- Python source compiles (`py_compile`); patch anchors checked against the target source and exact source blobs.
- `kotlinc` synthetic coordinator compilation + all 13 fixture cases: passed with test-only source adapters.
- **Not** performed: full `:app:testDebugUnitTest`, Android lint/build, physical-device fixture execution, live Steam transfer, game-running detection qualification.

**Do not enable** `R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED` or `.DEVICE_ACCEPTED` based on this report. Both must remain false until actual acceptance evidence and remaining safeguards exist.
