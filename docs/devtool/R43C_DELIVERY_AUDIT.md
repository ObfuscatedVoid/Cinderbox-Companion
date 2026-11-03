# R43-C consolidated overlay audit

This note is for the exact-source overlay, **not** evidence of acceptance on the user's phone.

| Requirement | Source delivery | Verification boundary |
| --- | --- | --- |
| No live updates | Both R43B4 flags unchanged; explicit production deny witness; no start without measured size | Contract/static proof, not a working launch fence |
| Manager/service bypass | Coordinator guards + independent update-mode service check | Source guard; hosted Android compilation pending |
| Two atomic renames | Swap checks unknown/quiescent before each; existing backups and no-overwrite semantics retained | Java filesystem rename/fault injection on real shared volume pending |
| Capacity/ENOSPC | Checked arithmetic, unknown/zero/borderline/overflow, stage measured without links, journal/free-space allowance | Quota, FUSE/SAF and actual pressure pending |
| Crash/rollback | Read-only orphan/missing-witness/manual-review assessment; prior reconcile still requires explicit action | Full crash-point and reboot device matrix pending |
| Saves/Mods/SMAPI | No migration/delete of production content; app-private tests assert external siblings untouched in previous R43B4 | Full M25/M26/M27 functionality not claimed |
| UX | Existing user-facing protected status now uses read-only assessment; no debug path introduced | R43-D and R46 experience audit pending |
| Devtool | Schema-v2 overlay; exact GitHub HEAD/tree, SHA-256 direct files, SHA-1 blob-pinned patched files; r43c-check | Must pass real target `devtool apply-overlay`, CI then physical tests |
| Historic R43B4 | Original coordinator/swap contracts and five instrumented fixtures retained | R43B4 device 5/5 historical, never R43C current proof |
| Signing/release | No release/publishing settings or secrets edited | M28/M29 explicit authorization pending |

**Known gap**: the production interlock is intentionally an always-deny placeholder until an independently qualified game-launch coordination mechanism exists. An `UNKNOWN` state is a safety outcome, not a completed running-game feature. Do not flip the two booleans to test it on real GameFiles.
