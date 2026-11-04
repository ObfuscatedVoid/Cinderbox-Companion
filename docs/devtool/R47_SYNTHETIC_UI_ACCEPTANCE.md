# R47 — Synthetic UI acceptance, not physical-device qualification

R47 moves beyond source-only checks by running eleven Android Compose instrumentation
cases on a **disposable, GitHub-hosted Android 35 emulator**. It explicitly does not
install, inspect, or alter an end user's physical device, Steam credentials, game
installation, save files, mods, account settings or release-signing keys.

## Scope

- Check all five primary tab labels, selected/unselected semantics, and user selection changes.
- Verify **all six** Home shortcuts invoke only their corresponding supplied callback; no navigation controller is simulated.
- Check R46 retry is never automatic and a missing callback never renders a retry.
- Capture seven **synthetic-only** UI previews, from a 320dp layout with 160% font:
  Home Earthy daytime/nighttime, Home Classic daytime, and Game/Saves/Mods/More
  Earthy contextual panels. Large-font scenarios keep a separate bottom-tab row
  visible and scrollable guide/error content; tests assert selected tab semantics
  and error visibility before capture. These are not real full app screens.
- Keep PNGs in Android app-private debug-test storage; the CI script retrieves exactly
  seven allowlisted filenames using `run-as com.sdvsync` on `emulator-5554` only.
- Require `ro.kernel.qemu=1` and exact test count before success.
- Collect all seven screenshots in memory, validate complete PNG chunks, CRCs,
  decoded pixel rows and unique SHA-256 hashes, then publish them atomically.
  Clear partial PNGs on failure and refuse 24-byte fake PNG headers.
- Produce `R47_SYNTHETIC_EVIDENCE.json` with source SHA, workflow run id,
  dimensions, hashes, and explicit non-qualification fields. A separate host-only
  verifier rechecks exact filenames, PNG decoding, source/run identity and claims.

## Test execution

The workflow `.github/workflows/r47-ui-acceptance.yml` runs on PRs and master
changes to the R47 files, using pinned GitHub Actions. It builds the app/test
APKs on GitHub and uses a disposable emulator. It also runs for future changes
to app UI source, MainActivity, or the Android build configuration. Host-only `r47-check` intentionally
requires no Gradle, Android SDK, ADB, account, emulator, or external files.

## Not proven by this phase

- Actual physical-device TalkBack speech/focus order, contrast interpretation and
  touch usability (must be checked on an authorized user device).
- Actual complete Game/Saves/Mods/More activity navigation under real storage and
  Steam states; the tests use synthetic Compose components.
- Release qualification, signed production APK, publication, or activation of
  existing-game live updates. The security policy and update coordinator remain
  unchanged and disabled.

If instrumentation or screenshot collection fails, the emulator receipt remains
`REFUSED`, never a release approval. If emulator provisioning fails before test
execution, the workflow uploads a `NOT_RUN` stub when the emulator job reaches
its artifact step. CI runner provisioning failures may prevent any artifact.
Host source checks do not replace a successful hosted build/instrumentation run.
