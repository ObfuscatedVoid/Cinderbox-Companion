# R45-A — Five-tab navigation and read-only Home

Source base: `a331d08a5e997c65f8d2d31fc35b0108f7260641` (tree `0310f72c52d10615c84610e5d0171cfe761aeb88`).

## What changes

- Add a **Home** primary tab, and make it the main-screen landing route.
- Reorder/rename the bottom navigation to **Home / Game / Saves / Mods / More**.
- Preserve stable existing internal enum keys (`DOWNLOAD`, `SETTINGS`) and routes (`game_download`, `saves`, `mods`, `settings`) used by deep links and parent-to-child tab switching.
- Add a synthetic, data-free Home screen with explicit links to Game, Saves, Mods, More, Installation Health and Operation Center. It never claims a game installation or sync is healthy and does not read Steam data, paths, files, saves or mod inventory.
- Preserve Classic/Earthy themes, selected-tab semantics and the 56dp bottom-bar minimum tap area, with previews for compact light/dark Earthy and Classic.
- Keep all R42–R44B UI and update restrictions, including disabled production live game updates and release/publication.

## Devtool / GitHub

`r45a-check`: `security-contract` → `r44-design-system-source` → `r44b-experience-source` → `r45a-navigation-source`.

`R45A Five-Tab Navigation and Home Source Contracts` runs host-only Python checks and existing R42/R43/R44 source gates. Android compilation, JVM tests, lint and debug signing remain in the existing hosted **Build Debug APK** workflow. Device navigation, TalkBack and screenshots are **not** qualified by this overlay.

## Acceptance checklist

1. Devtool applies the source-pin and all host gates, creates one automatic Git commit; working tree clean.
2. GitHub PR source regressions and hosted Android build all pass.
3. Manual device visual checks in both appearance styles and narrow/large-font layouts before labeling R45 UI device-qualified.
4. Home quick links navigate only to intended existing screens. Back from Game/More goes to Home. Deep links to legacy routes are preserved.
5. No activation of download/update/repair/sync and no changes to release policy.

Future R45-B: screen-specific Game/Saves/Mods/More information architecture, ergonomics and state consistency. R46: unified user-facing errors. R47: device qualification and evidence.
