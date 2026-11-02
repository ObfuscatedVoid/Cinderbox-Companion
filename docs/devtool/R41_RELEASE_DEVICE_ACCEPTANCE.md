# R41 — Protected release-device acceptance, explicit human installation consent

**Status:** source implementation and GitHub-hosted kit preparation; **device acceptance not yet run**. This is independent of R40 protected signing, which already passed on run `41502904211`. It preserves prior R41 debug-device synthetic evidence: all 29 tests passed on six selected lanes using CI debug APKs, **not the release-signed APK**.

## Immutable candidate / identity

- Source of **APK**, not R41 workflow code: `ea79d4044764f4f3c5827a05dd32a821d1aec20c`.
- Signed-only R40 workflow run: `41502904211`; release package `com.sdvsync`, version `0.1.1` (code 14).
- APK SHA-256: `11fe95fa1755c4d1e7d396f41df92386941dc478eefee9a32eba273ee96e6c80`.
- Pinned release signing certificate SHA-256: `1a97bfe73fccba607b7816e50db0fa1c16850b3036c4bb156efe047e70046ca0`.
- R41 workflow code has **a different commit SHA** after PR merge; it must be equal to the reviewed *current* `master` commit at dispatch, and is recorded separately from R40 APK source. This separation is deliberate.
- No APK is built or signed locally. No private signing key is downloaded to the phone.

## Boundary A — GitHub prepares the candidate, never touches a device

After R41 PR merge, dispatch the new workflow on the exact current master SHA:

```zsh
REPO=Mikeyphw/Cinderbox-Companion
R41_SOURCE="$(gh api "repos/$REPO/branches/master" --jq '.commit.sha')"
gh workflow run cinderbox-r41-release-device.yml \
  -R "$REPO" --ref master \
  -f reviewed_source_sha="$R41_SOURCE" \
  -f r40_signed_run_id=41502904211 \
  -f confirm=R41_PREPARE_ONLY
```

The GitHub job validates source and runs Python source regressions; downloads `r40-signed-candidate` and `r40-verified-signed-build` artifacts from **the same R40 run**; checks R40 workflow identity, master branch, source hash, full success, signer/receipt pins, SHA256SUMS; independently verifies GitHub OIDC/Sigstore subject digest, source SHA, branch and signer workflow. It uploads `r41-verified-release-device-kit` containing one release APK, `SHA256SUMS`, R40 M21 signed-candidate receipt, R40 independent acceptance receipt and `R41_HOST_KIT.json`.

This GitHub-hosted step **never installs**, never contacts ADB, never has release keystore secrets, never publishes and never claims device qualification. If any R40 evidence fails, the job fails and produces no accepted kit.

## Boundary B — A read-only, source-bound Termux plan

After the R41 preparation workflow succeeds, download its exact run's kit:

```zsh
cd "$HOME/Code/Cinderbox-Companion"
git switch master && git pull --ff-only
R41_RUN_ID=REPLACE_WITH_SUCCESSFUL_R41_PREPARE_RUN
KIT="$HOME/cinderbox-r41-release-kit-$R41_RUN_ID"
mkdir -p "$KIT"
gh run download "$R41_RUN_ID" -R Mikeyphw/Cinderbox-Companion \
  --name r41-verified-release-device-kit --dir "$KIT"

python3 scripts/devtool/r41_release_device.py plan \
  --kit "$KIT" --source ea79d4044764f4f3c5827a05dd32a821d1aec20c \
  --r40-run-id 41502904211 --r41-run-id "$R41_RUN_ID" \
  --serial localhost:5555 \
  --output "$HOME/r41-release-plan.json"
```

The plan independently re-authenticates R40 attestation and both workflow runs, checks local R41 runner source, checks ADB device state and reads only installed-package lists. **It does not install, uninstall, replace, launch or clear anything.** If `com.sdvsync` **or** `com.sdvsync.test` remains installed, the plan says `BLOCKED_EXISTING_INSTALLATION` and produces **no** approval phrase. Your 29 passing debug tests and their local receipts remain intact.

Debug and release app signatures differ; Android cannot install one over the other. If you want to proceed, you must independently decide whether to back up important app-private data and manually remove the installed debug packages. An uninstall can delete app-private data. **The R41 script never uninstalls for you.** After those packages are absent, run the *plan command again* with a new output path (e.g. `$HOME/r41-release-plan-clean.json`) to obtain a fresh token. No install can take place until then.

## Boundary C — Explicit, one-time installation decision

A clean plan prints `READY_FOR_MANUAL_APPROVAL` and a **unique** phrase `R41_INSTALL_<24 hex characters>`, bound to the APK digest, signed source SHA, both GitHub run IDs, local R41 runner source, selected ADB transport, hashed device fingerprint and Android ID, plan nonce and plan creation time. It expires after 20 minutes. The command atomically consumes the plan before attempting installation; any retry requires a fresh plan and operator approval. It cannot be reused with a different device, APK or source. The phrase is an explicit operator confirmation, **not** a cryptographic second factor.

Only after inspecting the plan and intentionally choosing to install, use:

```zsh
python3 scripts/devtool/r41_release_device.py install \
  --kit "$KIT" --source ea79d4044764f4f3c5827a05dd32a821d1aec20c \
  --r40-run-id 41502904211 --r41-run-id "$R41_RUN_ID" \
  --serial localhost:5555 \
  --plan "$HOME/r41-release-plan-clean.json" \
  --approve-install 'PASTE_EXACT_R41_INSTALL_TOKEN' \
  --approve-launch \
  --output "$HOME/r41-release-device-result.json"
```

The install command first **revalidates** both GitHub runs, R40 Sigstore attestation, kit SHA-256, certificate, local R41 source, time, approval and device identity, and rechecks **both** packages are absent. Then (and only then) it uses `adb -s localhost:5555 install <release.apk>` **without `-r`**, and checks the installed base APK SHA-256 equals GitHub's original signed APK. With `--approve-launch`, it separately requests normal Activity start (`am start -W -n com.sdvsync/.MainActivity`) and records the command result. If `--approve-launch` is omitted, no Activity launch is requested and the receipt says `NOT_RUN`.

Receipts are **local**, do not include raw device logs, do not touch Steam content or Cinderbox game files, and retain `device_qualified=false`, `release_qualified=false`, `publication_allowed=false` even after a successful install. An accepted release-device APK install proves exact-byte installed provenance and optionally Activity start, **not** end-to-end UI, data migration, live Steam, application stability, or readiness to publish. Manual release approval is a separate milestone.

### Fail-closed checks

- Wrong R40 GitHub run/commit/workflow, R41 workflow source, failed CI, wrong ref, wrong cert/hash, inconsistent receipts or rejected Sigstore verification → **blocked**.
- Debug app or test APK already installed, device changed, ADB offline, package query inconsistent, token missing/mismatched, plan older than 20 minutes or reused output file → **blocked**.
- Release APK installation failure, unreadable/multi-split installed APK, incorrect installed hash, Activity start failure → failure JSON; no automatic uninstall/data clearing/retry.
- This workflow is **personal-device acceptance**, never a GitHub-hosted disposable emulator. Never report it as an emulator test pass.
