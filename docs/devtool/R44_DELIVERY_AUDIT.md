# R44-A consolidated overlay delivery audit

| Constraint | Result |
|---|---|
| Source | GitHub master `8079c692dc997a28187457ffaed37faa4c1e5da6` |
| Base tree | `272e163a9f14fbba2d5ea8e77c9044cc28e65ec3` |
| Overlay schema | Devtool schema 2, exact-tree, fail conflict/dirty worktree |
| Git changes | Manifest single automatic commit, no auto push, no signing or release |
| Existing file changes | Theme, shared card, buttons, Game Download screen, Devtool TOML only |
| Payload changes | R44 tokens, safe status mapping+component+tests, host gate/test/patch, workflow, docs |
| No local Gradle | Yes. GitHub Actions handles Android debug tests/lint/build |
| Classical presentation | Existing pixel borders and pixel bevel preserved for Classic |
| Security | No update/download/storage/permission APIs touched; production flags stay false |
| Host checks | Patch synthetic-anchor fixture, Kotlin pure status-mapping host smoke |
| Pending | Full Android CI and physical screenshot/accessibility acceptance |

Do not overstate screenshot or device results. The new source-bound action on PR must succeed before merge.

## R2 validation correction

The initial R44-A payload applied its finalize patch but validation then refused
because an overly broad `fontScale` check matched a harmless Kotlin documentation
comment in `R44ProductDesignTokens.kt`. R2 changes **only** the host-only R44
source gate and its corresponding regression test to scan Kotlin source after
excluding comments. Actual code-level `fontScale` and `TextUnit.Unspecified`
overrides remain prohibited. The exact-tree source pin, UI code, release flags,
Devtool commit message, and rollback-on-failure contract are unchanged.
