# R47 R6 — KVM setup repair (PR #53)

## Observed failure

Run `41771993336`, commit `b19df0062f4df9815b9c40a84eb9fbcd35c995a4`,
failed immediately after writing the KVM udev rule on the hosted Linux runner.
The workflow did **not** reveal whether the failed command was `udevadm`
or the subsequent device tests. It did not boot an emulator or execute UI tests.
The separate debug APK build succeeded.

## Correction

The GitHub-hosted Ubuntu job now checks `/dev/kvm` is an existing character
special file. If the current user already has read and write access, it needs
no privileged operation. Otherwise it attempts `sudo chmod a+rw /dev/kvm`
**only on that pre-existing KVM character device**, checks access again,
and refuses with one of three stable, nonsensitive error markers when absent
or inaccessible. No persistent udev configuration or daemon reload is needed.

The testing configuration otherwise stays aligned with MMRL-LSPosed S09:
Ubuntu 24.04, Linux x86_64, Google APIs API 36, Pixel 6, two cores, 2048 MB,
SwiftShader, pinned emulator-runner. This does **not** silently fall back to
software emulation, another architecture, or a physical device. A runner
without usable KVM remains a failed job, not a false-positive qualification.

R47 still requires all 11 synthetic Compose tests and independent validation
of seven private screenshot artifacts. Existing account, mod, save, Steam,
release, and live-update safety boundaries are unchanged.

Host regressions exercise bash syntax, missing-KVM refusal and a safe character
device stand-in without accessing the actual KVM device, Gradle or Android.
Emulator availability and screenshot acceptance remain pending until hosted CI.
