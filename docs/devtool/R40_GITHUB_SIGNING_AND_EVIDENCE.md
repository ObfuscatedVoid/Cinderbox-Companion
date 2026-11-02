# R40: Protected GitHub release signing, emulator qualification and independently verifiable evidence

This is an **opt-in, additive** workflow. R39's accepted `lintDebug -> assembleDebug` DAG and A07 fallback are unchanged. `R40` must **not** become a default build or a published release without separate policy approval.

## Four independent concerns

1. **Parsed JVM unit tests:** GitHub-hosted Gradle `:app:testDebugUnitTest` generates real JUnit XML. `r40_qualification.py junit` parses actual test cases, refuses zero tests, failures, errors, duplicates, skipped tests and inflated suite totals. XML and sanitized receipt are retained.
2. **Android tests:** The pinned, disposable `reactivecircus/android-emulator-runner` executes the scoped `PlatformStorageAliasDeviceTest` (app-cache-only synthetic fixture). This is real Android instrumentation on a **GitHub emulator**, not a physical phone or full device campaign.
3. **Release APK signing:** Only after both prior jobs succeed, the `github-sign` job enters the restricted GitHub environment `cinderbox-release`. It reuses `m21_actions_release.py provision/verify` and **the existing persistent `cinderbox` keystore alias**. It verifies the independently pinned release signing certificate, distinct from the pinned debug certificate. Builds only `:app:assembleRelease`, verifies non-debuggable package `com.sdvsync`, removes the temporary key, then creates a **candidate** artifact. There is no GitHub Release creation, Play publication or automatic production install.
4. **Independent provenance:** `actions/attest@v4` signs an SLSA provenance record for **the actual APK bytes** with short-lived GitHub OIDC/Sigstore identity. Separate `signed-emulator` and `remote-verify` jobs download the artifact from GitHub, run `gh attestation verify` with source-ref, source-digest and signer-workflow constraints, correlate the four evidence reports and check installed signed APK bytes on a **fresh disposable emulator**. The artifact attestation signs provenance; it **does not replace Android APK signing**.

All operations involving Gradle or emulators run on GitHub Actions; nothing builds on Termux.

## GitHub Environment setup before running

GitHub → repository Settings → Environments → **`cinderbox-release`**. Restrict the environment to `master`; enable human review/approval if your setup supports it. Protect the branch. The workflow uses this environment only for the `github-sign` job. Configure the **existing** signing identity, without rotating or regenerating keys:

Environment **secrets** (never paste values into chat, commits, Actions logs or terminal history):

- `CINDERBOX_RELEASE_KEYSTORE_B64`
- `CINDERBOX_RELEASE_STORE_PASSWORD`
- `CINDERBOX_RELEASE_KEY_PASSWORD`

Environment **variables** (public certificate fingerprint SHA-256, 64 lowercase hex digits):

- `CINDERBOX_RELEASE_CERT_SHA256` — cert from existing alias `cinderbox`
- `CINDERBOX_DEBUG_CERT_SHA256` — existing M16 debug signer cert, must be **different**

Existing documentation: `docs/devtool/M21_GITHUB_ACTIONS_RELEASE_CANDIDATE.md` and `scripts/devtool/m3b_key_setup.py inspect`. If this key has already been used for APK distribution, **preserve it**. Signing a new APK with a different certificate would block in-place upgrades. GitHub does not supply a permanent Android APK signing key; it safely runs the signing task using your protected persistent key. Do not use GitHub's code-signature / artifact attestation certificate as Android signing identity.

If the environment secrets or pins are absent, the signing step MUST fail closed; do not bypass it. If the user owns only a debug-signed installed app, a release-signed candidate may be **incompatible for in-place upgrade**. The workflow installs the release APK only onto an empty emulator.

## Apply and register

Branch from the exact reviewed `master` base, apply the R40 overlay, run source-only tests, commit and push for review:

```zsh
cd ~/Code/Cinderbox-Companion
git fetch origin
git switch -c feature/r40-github-signing origin/master
devtool apply-overlay ~/storage/downloads/CINDERBOX_R40_GITHUB_SIGNING_QUALIFICATION.overlay.zip
python3 -B -m unittest discover -s scripts/devtool -p 'test_r40_qualification.py' -v
git diff --check
git add .github/workflows/cinderbox-r40-qualification.yml scripts/devtool/r40_qualification.py scripts/devtool/r40_emulator_smoke.sh scripts/devtool/r40_signed_emulator.sh scripts/devtool/test_r40_qualification.py docs/devtool/R40_GITHUB_SIGNING_AND_EVIDENCE.md
git commit -m 'ci(r40): add protected GitHub signing and qualified evidence DAG'
git push -u origin feature/r40-github-signing
gh pr create -R Mikeyphw/Cinderbox-Companion --base master --head feature/r40-github-signing --title 'ci(r40): GitHub-signed candidate with unit, device, attestations' --body 'Opt-in, source-bound GitHub release signing with protected environment, parsed JUnit, disposable emulator tests and independent Sigstore verification. No release publication or production install.'
```

After **review**, **CI pass**, environment configuration and **PR merge**, run the new workflow on the exact merged `master` SHA:

```zsh
cd ~/Code/Cinderbox-Companion
git switch master
git pull --ff-only
SHA=$(git rev-parse HEAD)
gh workflow run cinderbox-r40-qualification.yml -R Mikeyphw/Cinderbox-Companion --ref master -f reviewed_source_sha="$SHA" -f confirm=R40_SIGNED_CANDIDATE
gh run list -R Mikeyphw/Cinderbox-Companion --workflow cinderbox-r40-qualification.yml --limit 5
```

The workflow exists only on `master` **after merging**; attempting manual dispatch before registration may fail with GitHub 404. If any stage fails, do not reinterpret a prior R39 run or unrelated M21 release candidate as R40 evidence.

## Independent verification from Termux (no Gradle)

```zsh
REPO=Mikeyphw/Cinderbox-Companion
RUN_ID=REPLACE_WITH_EXACT_R40_RUN
SHA=$(gh run view "$RUN_ID" -R "$REPO" --json headSha -q .headSha)
mkdir -p "$HOME/r40-proof/$RUN_ID"
gh run download "$RUN_ID" -R "$REPO" -n r40-signed-candidate -D "$HOME/r40-proof/$RUN_ID"
APK="$HOME/r40-proof/$RUN_ID/CinderboxCompanion-release-candidate.apk"
sha256sum "$APK"
gh attestation verify "$APK" -R "$REPO" --source-digest "$SHA" --source-ref refs/heads/master --signer-workflow "$REPO/.github/workflows/cinderbox-r40-qualification.yml"
```

Review `M21_RELEASE_CANDIDATE.json`, `SHA256SUMS` and `r40-verified-candidate-evidence` from that same run. These checks bind the remote artifact to a verified GitHub build identity rather than trusting a standalone JSON self-report. For Android signature identity, additionally use the pinned Android SDK `apksigner verify --print-certs` on an appropriate host and compare with the **independently inspected** public signing certificate. No keystore secrets are needed for verification.

## Explicit limitations

- This is a **signed release candidate**, not a published/approved release (`release_qualified=false`, `publication_allowed=false`).
- Emulator qualification is not physical-device or user-data upgrade acceptance. Do not auto-install on a real device or uninstall the existing app.
- One scoped instrumentation test plus parsed unit tests does not prove every M14–M24 scenario passed; extend through additional separately selected device campaigns, not by changing historic receipts.
- SLSA/Sigstore authentication verifies the signed build provenance and artifact digest; it does not guarantee bug-free source, reproducibility, keystore custody or device compatibility.
- Signing credentials are environment-scoped; no signing key is generated or stored in the repository.

## R40-R1: accelerated emulator boot (run 41455791370)

The first R40 run on `master` at `ff8ad473e93a43aa65cb2ab88e2ede7e1a71b605`
passed all 221 JVM unit tests (37 original JUnit XML reports, zero failures,
errors or skips), and compiled both Android emulator APKs. It did not reach
signing because the disposable emulator exceeded its 600-second boot timeout.
The action reported `/dev/kvm` access denied and launched `-accel off`.

R40-R1 adds the documented KVM permissions setup to **both** hosted emulator
jobs, then checks `/dev/kvm` is available and accessible before invoking the
emulator. It explicitly requires `disable-linux-hw-accel: false`; a runner
without KVM fails with `R40_KVM_DEVICE_UNAVAILABLE` or
`R40_KVM_ACCESS_UNAVAILABLE` rather than performing costly software emulation.
It also removes `-r` from the scoped `am instrument -w` command so the Android
instrumentation runner emits the human-readable `OK (N tests)` summary required
by the existing strict parser, while preserving the final
`INSTRUMENTATION_CODE: -1` requirement.

No change to release keystores, environment secrets, signing certificates,
Android app code, release policy or production-device permissions. R40 remains
candidate-only and must pass a fresh hosted run for this fix to count.

## R40-R2: move both disposable emulators to Intel macOS (run 41462219417)

R40-R1, dispatched from master `c8234d96d7ed91cddd09d9d2b09c16f563863edc`,
again passed real JVM unit tests and compiled Android app/instrumentation APKs.
The Linux KVM preflight failed with `R40_KVM_ACCESS_UNAVAILABLE` even after
`udevadm trigger`. `github-sign`, `signed-emulator`, and `remote-verify` were
correctly skipped; release signing did **not** run. The error is the hosted
Linux runner's inaccessible `/dev/kvm`, not an Android test or app failure.

R40-R2 changes **only the two ephemeral emulator jobs** to GitHub's documented
`macos-15-intel` runner using the Android Emulator's native macOS hypervisor
path. They verify the runner OS and architecture before invoking the existing
SHA-pinned emulator action. Removes Linux-only KVM permissions/acceleration
configuration. The unit-test, protected GitHub signing, and remote-verification
jobs remain on Ubuntu; no code or signing keys are moved onto another host.

The Intel macOS runners may consume different GitHub Actions minutes or incur
charges depending on plan and repository visibility. Run once on reviewed
master and inspect fresh remote receipts before promoting any qualification.
This source-only fix is **not** evidence that emulator, signing or remote
provenance stages passed. Fail closed if the emulator cannot start; never fall
back to `-accel off` or to a personal Android device.

## R40-R3: signing-only mode after third emulator boot timeout (run 41466933054)

R40-R2 merged source `0a49c7c3cd407ca0d4be82aae788bacfc2fcd967` passed 221
Gradle JVM tests and both Android APK assemblies. Intel macOS then started an
Android emulator but `adb` remained `device offline` for 600 seconds; the
scoped Android instrumentation never executed. The protected signing job was
**skipped** because of its `needs: emulator-debug` dependency. Thus there is
no R40 release signing, device acceptance, or attestation from this run.

R40-R3 separates independent signed-build qualification from device
qualification. The new `qualification_scope` input defaults to `signed-only`;
`full` is an explicitly requested, fail-closed Android emulator campaign. In
`signed-only`, `unit-tests` must PASS with nonempty parsed JUnit XML, followed
by `github-sign` with existing protected `cinderbox-release` secrets and
certificate pins, then `remote-sign-verify` without signing secrets. The
independent job downloads exact GitHub artifacts, rehashes original XML and
APK against M21's source/run/certificate-bound evidence, and verifies GitHub
OIDC/Sigstore attestation with repository, workflow, ref and source digest
constraints. It uploads `r40-verified-signed-build`; this receipt explicitly
claims **no** emulator tests, device qualification, release approval or
publication permission. It will not exist if signing, JUnit parsing or remote
authentication fails.

With `qualification_scope=full`, the existing two disposable macOS emulator
jobs still run and `remote-verify` requires ALL their real outputs AND the new
independent signing receipt. A failed or skipped emulator never yields full
qualification. The full lane is still blocked by emulator startup until
a reliable host, image or test-device lane is qualified.

First acceptance command (after reviewed R40-R3 PR is merged):

```zsh
REPO=Mikeyphw/Cinderbox-Companion
SHA=$(gh api "repos/$REPO/branches/master" --jq '.commit.sha')
gh workflow run cinderbox-r40-qualification.yml -R "$REPO" --ref master \
  -f reviewed_source_sha="$SHA" -f confirm=R40_SIGNED_CANDIDATE \
  -f qualification_scope=signed-only
```

Only dispatch `qualification_scope=full` after an emulator host has been
proved to boot. This avoids repeated long-running, failed macOS jobs.

Warning: `gh attestation verify` trusts the GitHub OIDC identity for build
provenance; it is not an Android APK signer or code quality guarantee. The
Android signing key is the existing persistent protected release keystore.
