# R45 + R46 Consolidated UI — qualification boundary

Source pin: `a331d08a5e997c65f8d2d31fc35b0108f7260641` (merged R44B).

One atomic Devtool artifact supersedes standalone R45-A; do NOT apply the older R45-A ZIP first.

## Implemented

- Home-first navigation: Home / Game / Saves / Mods / More; preserves legacy route IDs and internal deep-link navigation.
- Original monochrome Home icon, five stable tab semantics and state-restoring navigation.
- Home hub with six explicit read-only navigation shortcuts and no backend read/write.
- Unified page intro component integrated into Game, Saves, Mods and More.
- Friendly and text-first errors for Saves and Mods, with explicit retry actions; raw backend errors not rendered in these screens.
- Sync errors still show localized, actionable messages and existing safe callbacks, but raw technical messages are visible only in unlocked debug diagnostics after an explicit action.
- Existing R44B responsive status cards, loading and empty states, Classic and Earthy themes, and accessibility remain in use.
- Four synthetic previews, Kotlin policy tests, host contract tests, Devtool `r45-ui-check`, GitHub source workflow.

## Explicitly excluded

- No changes to `GameDownloadManager`, `GameDownloadService`, Steam auth, game files, installed mods, saves or SMAPI.
- No automatic retries; no new destructive actions or permissions.
- No release-signing, release enablement, automatic device installs or live updates.
- Physical-device acceptance, screenshots, and real in-app screen navigation remain R47 work.

## CI checks

The new source workflow verifies previous R42/R43/R44 policy contracts. Existing hosted `Build Debug APK` compiles Kotlin, executes JVM tests and lint. CI success is not proof of real-device UX quality.
