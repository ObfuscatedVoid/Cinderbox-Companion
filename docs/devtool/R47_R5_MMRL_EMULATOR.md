# R47 R5 — Adopt the MMRL-LSPosed S09 hosted emulator configuration

## Proven source

Mikeyphw/MMRL-LSPosed `.github/workflows/s01-hosted-devtool.yml`
(`emulator-instrumentation` job, main at `3c15233cce2435c30392f0bcd3c89bb4895671c2`).
An API 36 emulator job completed successfully in GitHub run `41759587780`.
The newer MMRL run `41767060096` failed in its hosted build job; its emulator
job never ran. Do not conflate that with the successful emulator run.

## What R5 reproduces

- GitHub-hosted Ubuntu 24.04 `x86_64`, KVM on a pre-existing
  `/dev/kvm` character device with fail-closed accessibility checks.
  R6 initializes permissions directly if needed, without a udev daemon.
- Pinned `ReactiveCircus/android-emulator-runner`
  `a421e43855164a8197daf9d8d40fe71c6996bb0d`.
- Android 16 API 36, `google_apis`, `x86_64`, `pixel_6` profile,
  2 cores, 2048M RAM, animations disabled.
- Headless SwiftShader emulator, no cameras or audio.

Cinderbox keeps **its own** JDK 17 and SDK 35 compile toolchain and additionally
installs platform 36 for the emulator. This is deliberate: changing production
compile settings is not necessary to match the emulator runtime.

## Safety and evidence

This is an infrastructure-only patch. The synthetic 11-case Compose test,
seven screen variants, private-file capture, SHA-256/PNG verification, and
sanitized R4 failure summary remain untouched. The existing emulator-only
`ro.kernel.qemu` and fixed ADB serial safeguards stay active.

Source-only Python validation runs in Termux. The Android emulator, build,
APK installation and instrumentation execute **only on GitHub Actions**.
Do not interpret a successful APK compilation as emulator acceptance.

The R47 source gate refuses changes that remove KVM, revert to macOS,
switch off API 36, change the pinned action or skip evidence verification.
`LIVE_UPDATES_ENABLED`, `DEVICE_ACCEPTED`, and public release gates remain
false. No physical device, Steam account, root grant, save, mod or live-game
update is accessed by this patch.

The existing workflow does not yet implement MMRL's independently signed
artifact-pair handoff: APKs continue to be built in the same hosted job.
This is a deliberate narrow scope: **the Android emulator runtime
configuration** is matched, not the unrelated MMRL build, signing or app model.
