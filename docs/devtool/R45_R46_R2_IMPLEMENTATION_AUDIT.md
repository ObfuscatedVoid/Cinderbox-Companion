# R45/R46 R2 — audited consolidated UI

Source: merged R44-B `master` at `a331d08a5e997c65f8d2d31fc35b0108f7260641`, exact tree `0310f72c52d10615c84610e5d0171cfe761aeb88`.

**Replaces**, rather than stacks on, `Cinderbox_R45_R46_Full_UI_Consolidated_AutoCommit_a331d08.overlay.zip` and the old standalone R45-A ZIP. Apply exactly one ZIP to a clean feature branch based on that HEAD.

## Audit findings and R2 corrections

| Original promise | Original ZIP | R2 remediation |
| --- | --- | --- |
| Home / Game / Saves / Mods / More | Added five stable tabs, Home and six navigation shortcuts | Preserved, with explicit route allowlist |
| Game / Saves / Mods / More screen hierarchy | Mostly one new section introduction per screen | Added source-owned, three-step area guidance in all four screens without changing workflows |
| Loading and empty experiences | Inherited R44-B shared components, but Saves/Mods still used spinner-only loading | Earthy now presents an accessible loading status with text; original Classic spinner retained; existing empty-state UX retained |
| User-friendly errors across major UI surfaces | Only Saves and Mods received R46 panels; other main paths still displayed raw technical errors | Added contextual allowlisted Game, game-download, SMAPI, Cinderbox-download and Steam-login panels; seven total categories |
| Hidden raw diagnostics | Sync error raw text restricted to unlocked debug mode | Also restrict Steam login copy/share logs to unlocked debug mode; regular sign-in and offline controls stay available |
| Full Android/device validation | Not run | Still not claimed: hosted CI and physical-device qualification required |
| Automatic commit / fail-closed source | Commit enabled and base pinned | R2 finalizer verifies exact intermediate source from R45A+R45UI before R2 modifications; rollback retained |

## Preserved implementation and boundaries

- Old `game_download`, `saves`, `mods`, and `settings` route IDs; unchanged sub-screen navigation, import/export and explicit action callbacks.
- Classic pixel art, palette, shape and button semantics; Earthy Material 3 with R44B accessible panels.
- Existing Game/SMAPI/Cinderbox retry, dismiss and authentication controls. R46 display components do **not** fire retries; they only wire optional supplied callbacks. No automatic retry was added.
- Existing confirmation dialogs for imports, profiles, repair, downloads, restore or protected-update operations.
- No modifications to Steam auth implementation, GameDownloadManager/Service, game files, save repositories, mod repositories, or SMAPI installer.
- Release/publication/device acceptance and live existing-game update flags remain false; source and UI acceptance do not enable them.

## Scope and evidence limitations

R46 provides consistent privacy-safe error **presentation for the seven covered contexts** and maintains existing localized errors elsewhere. It is not a claim that every exception in the application has been exhaustively classified. Raw logs may contain sensitive details even in debug mode: user action is required before copying/sharing them.

Host-side checks validate source anchors, policy tests and the exact patch chain. The hosted GitHub Actions Android build owns full compilation/JVM tests/lint/APK creation, and R47 still owns real-screen navigation, TalkBack, large-font/landscape behavior, screenshots and physical-device acceptance. Do not claim device qualification from previews or host-only tests.
