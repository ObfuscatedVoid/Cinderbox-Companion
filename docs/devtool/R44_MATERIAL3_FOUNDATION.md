# R44-A — Material 3 visual foundation (source candidate)

Base: `Mikeyphw/Cinderbox-Companion` **master** `8079c692dc997a28187457ffaed37faa4c1e5da6`; tree `272e163a9f14fbba2d5ea8e77c9044cc28e65ec3`.

## Delivery

This consolidates the **first integrated R44 foundation**, not the complete navigation redesign or physical UI acceptance:

1. **Earthy-specific Material 3 typography/shapes.** `R44ProductDesignTokens.kt` uses existing Nunito assets, scalable `sp` type and 8–28 dp rounded shapes. `SdvSyncTheme` selects it *only* for `AppearanceStyle.EARTHY` and retains `Typography`, `StardewShapes`, and both original palette branches for `CLASSIC`.
2. **Cards.** `StardewCard` retains the exact Classic pixel-border and inset, and uses a round, tonally elevated Material 3 surface in Earthy. Existing click actions are unchanged, but accessible clickable role is explicit.
3. **Primary and outlined buttons.** Earthy gets Material 3-shaped controls, a 48 dp minimum target and a themed outlined border, without the Classic pixel bevel; original Classic drawing remains intact. Existing enabled/onClick and button variants remain unchanged.
4. **Semantic status with visible action guidance.** A read-only `R44StatusSummary` maps R43-D destination statuses to distinct readable badges/headings/body/next steps, including blocked, protected and review cases. It is only shown in Earthy mode on the existing Game Download card; Classic retains its original text. Colors are paired with `on*Container` foregrounds and **not the sole indicator**. No file access, Steam call or permission gate changed.
5. **Qualification hooks.** New pure Kotlin mapping unit tests and Python source gates; `r44-check` Devtool DAG is read-only, source-only and depends on existing security/R43-D jobs. Separate GitHub Action runs R44 plus previous R42/R43 guards. Existing hosted Debug APK CI owns Android compilation, unit tests, lint and debug signing.

## Preserved security constraints

- `R43BLiveUpdatePolicy.LIVE_UPDATES_ENABLED = false`, `DEVICE_ACCEPTED = false`.
- `docs/security/M3_RELEASE_POLICY.json` release, publication, device acceptance and auto-install flags remain `false`.
- Existing Steam downloader, Manager/Service, update swap, storage, game files, saves, mods, SMAPI and protected journals are not changed.
- Developer-only alternative folder controls remain behind both debug-build and explicit developer unlock.
- R43-D source checks and literal `Existing installations are protected` remain intact.
- A visual tone never means write authorization.

## Acceptance scope

**Completed by this source overlay:** source code, Kotlin pure mapping test, host source gates, Devtool metadata, GitHub workflow. Automatic Git commit is enabled after validation on an exact source/tree base.

**Not established by the overlay alone:** hosted Android build/lint/test success for this new commit, real screenshot comparisons, contrast verified for all new nested Material 3 containers, TalkBack/focus/font-scale and narrow-phone/device acceptance, real production update safety and release approval. Do not merge until PR CI is green; evaluate Earthy/Classic on a physical phone separately.

## Roadmap continuation

- **R44-B:** remaining shared banners/dialogs, empty/loading/list/chip patterns, typography consistency and screenshot/a11y acceptance. Preserve Android file safety gates.
- **R45:** Home / Game / Saves / Mods / More navigation and screen hierarchy.
- **R46:** human error copy while keeping technical diagnostics restricted.
- **R47:** evidence/device/release integration. Older M25–M29 roadmap remains active.
