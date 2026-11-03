# R44-B — Responsive and accessible UI components

Source base: `47886857021dc395810989fb486c02937a9432a0`, merged R44-A / PR #50.

## Scope

- Keep the Classic pixel border, palette and scrolling dialog unchanged in spirit.
- Present Earthy dialogs with Material 3 rounded surfaces and accessibility headings.
- Add width-class guidance (600 / 840dp thresholds) and a capped readable single-column measure.
- Add text-first state panels for loading, empty, needs attention, completed and informational status. Color is secondary to text.
- Integrate the responsive column and state panels into **Operation Center** and **Installation Health**.
- Preserve the existing callback ownership: UI state never independently invokes download, repair, rollback or file mutation.
- Add an optional 48dp minimum-action empty state without changing existing EmptyState calls.
- Provide synthetic light, dark, tablet and Classic Compose previews. These are visual targets, **not captured screenshots**.

## Safety and qualification

The existing device release policy flags, all R43 live-update gates and the old R44-A / R43-D source contracts must remain unchanged. This overlay changes UI files only; no downloader, synchronizer, persistence, permission, signing, or updater logic is included.

The `r44b-check` Devtool workflow runs source checks on the host. A separate GitHub Actions workflow runs these checks on pull requests, while the pre-existing `Build Debug APK` workflow owns Gradle compilation, JVM tests, Android lint and debug APK creation. Local Termux Gradle is not required.

**Not qualified by this overlay:** physical accessibility scans, TalkBack focus traversal, dynamic font screenshots, landscape/tablet screenshots, real game migration or release signing. Verify those separately before claiming device acceptance.

## Manual UI acceptance checklist

1. Earthy light and dark: dialog shape, title announcement and scrollable content with large font settings.
2. Classic light and dark: old pixel border remains recognizable.
3. Loading / review / empty / complete: meaningful status words without relying on color.
4. 360dp phone, 600dp medium, 900dp tablet, landscape and font scaling: no clipped buttons or horizontal overflow.
5. TalkBack: headings, state notifications and operation navigation action are read in a sensible order.
6. No automatic retry, install, save deletion or update operation triggered by rendering any state panel.

## Next milestones

After CI and actual UI acceptance: R45 Home/Game/Saves/Mods/More navigation; R46 unified error presentation; R47 hosted/device evidence and release qualification. Original M25–M29 functionality remains separately planned.
