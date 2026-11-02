# Devtool R39 hosted Android DAG — opt-in preparation R1

> **Source candidate only.** A05/A07 R7 GitHub Actions and source contract remain unchanged except an additional R39 source-only test. No R39 producer workflow has been exported or hosted-qualified, no APK has been produced by R39, and no device or signing action is authorized.

## Baseline

- GitHub PR #35 source head: `2f9fb057cb47d180956c35786f0ddab99ec4e295` (R7 source gates and pinned A05/A07 hosted checks PASS). Merge first; do not modify that PR.
- Observed Devtool `main` R39 revision: `e08564c21d8a8d63627a6356c10a88e674e3e432`. Review/pin explicitly; do not follow moving `main` during generation.
- Cinderbox Gradle wrapper JAR SHA-256: `423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`; wrapper distribution Gradle 8.11.1 official ZIP with full distribution SHA-256 pin.
- App's Gradle script renames `debug` output to `app/build/outputs/apk/debug/CinderboxCompanion-v0.1.1.apk` because its `versionName` is `0.1.1`. Verify the real output on the GitHub runner.

## New opt-in R39 candidate graph

- `r39-hosted-lint`: semantic Android `:app:lintDebug`; toolchain Java 17, Android 35/build tools 35.0.0, exact wrapper SHA-256, no outputs.
- `r39-hosted-apk`: semantic Android `:app:assembleDebug`; owns only `app/build/outputs/apk/debug/CinderboxCompanion-v0.1.1.apk` with exact expected-output pin; debug-only, no device, signing or release permissions.
- `r39-hosted-debug`: two explicitly ordered target-local nodes, lint → apk, *not* an alternative replacement for the existing A05/A07 workflow.

## Operator migration after merging #35

```zsh
cd ~/Code/Cinderbox-Companion
git fetch origin
git switch master
git pull --ff-only origin master
git switch -c feature/r39-hosted-dag

devtool apply-overlay ~/storage/downloads/CINDERBOX_R39_PREP_R1.overlay.zip
python3 -B -m unittest discover -s scripts/devtool -p 'test_r39_hosted_source.py' -v
./devtoolw host-check

# R39 source only: plan first; do not run Gradle locally.
devtool --repo "$PWD" actions provider-export \
  --target cinderbox --workflow r39-hosted-debug \
  --devtool-sha e08564c21d8a8d63627a6356c10a88e674e3e432 \
  --mode multi-job --plan --json
```

Only if the exporter confirms full admission of **both** nodes and the exact output, generate the *new* GitHub Actions workflow on the clean/reviewed migration branch (`--yes` is a separate explicit step; it writes files but does not dispatch or build). Inspect/generated YAML, run `--verify`, commit & push; run a source-only PR check and hosted debug build on GitHub. Inspect DAG `needs`, source/run/attempt handoff, resulting APK SHA and debug signer. Keep the passing A07 native debug and signed-candidate workflows as fallback until both hosted and artifact-import parity pass. A green source test alone is not hosted/device/release qualification.

If the provider exporter refuses a job or output: **stop and inspect the error**. Do not bypass source, output, or signer safety checks, and do not substitute a hand-written workflow for canonical exporter output.

## R39-R2 export compatibility correction (2026-11-02)

R39 `actions_android_provider.py` explicitly accepts the empty string for a
Gradle project located at the repository root. It **refuses** `"."` because
its bounded subdirectory grammar does not admit a dot path segment. R2 sets
`ci_android_project_dir = ""` on the two opt-in R39 jobs, leaving all existing
A05/A07 and Gradle outputs untouched. Both root and nested-directory cases
retain path/symlink guards.

**Important second blocker:** the pinned R39 provider's APK basename regex
`[A-Za-z0-9_-]+\.apk` rejects Cinderbox's actual versioned output
`CinderboxCompanion-v0.1.1.apk`. Do not rename the production/legacy APK
or turn off exact-output ownership. Apply the separate, test-backed Devtool
provider patch, commit/push Devtool to obtain a new immutable 40-hex SHA, and
use that new SHA for read-only `actions provider-export --plan` and later
generated workflows. Until then, provider-export cannot be called qualified.

R39-R2 applies on `feature/r39-hosted-dag` at original commit
`03aef79e53d84623586ec89bf537283ebd860412`, not on `master` or PR #35.

## R39-R3 — Explicit inputs for generated-output ownership (2026-11-02)

The opt-in R39 graph has two native jobs (lint -> debug APK) and an exact
`app/build/outputs/apk/debug/CinderboxCompanion-v0.1.1.apk` producer.
Devtool RM-06 deliberately refuses wildcard inputs such as `app/src/**` or
`gradle/**` when the graph contains any output producer: it cannot infer
which generated inputs are owned by which node from a glob.

R39-R3 changes **only** those two opt-in jobs' input lists to exact tracked
build anchor paths (`.devtool.toml`, wrapper files, Gradle build/settings
files, `app/build.gradle.kts`, Android manifest). It does **not** remove
the existing target-level change-detection globs or widen RM-06 admission.
The exact Git commit/tree and full checkout remain the actual source identity;
the job input lists are *not* an exhaustive source manifest. Outputs, native
`lint -> apk` dependency, signing and connected-device gates are unchanged.

Run the read-only exporter plan again using the **pushed**, patched Devtool
commit (`fix/r39-versioned-apk`), with no local Gradle execution. Accept the
next gate only if both nodes and exact APK output are present; source-only
regression tests do not prove hosted runtime or exporter acceptance.
