# Cinderbox Companion

A companion app for Stardew Valley on Android. Syncs save files with Steam Cloud, downloads game files, and manages SMAPI mods — all from your phone.

Works with both the official Android port and [Cinderbox](https://www.nexusmods.com/stardewvalley/mods/43278) (the modded Android client).

> **This is an independent fan project.** It is not affiliated with, endorsed by, or connected to ConcernedApe, Chucklefish, or the Cinderbox developers. Stardew Valley is a trademark of ConcernedApe.

## Save safety

Keep an independent backup of your saves before syncing, restoring, or migrating them. Companion creates local backups before replacement, but an interrupted process or a failing storage provider can still require manual recovery. Review the source, destination, and conflict warning before confirming a sync.

## Features

- **Steam Cloud Sync** — Pull saves from Steam Cloud to your device, or push local saves back. Conflict detection tells you which version is newer before you overwrite anything.
- **Game File Download** — Download Stardew Valley game files directly from Steam using your own account. Supports branch selection and a post-download file-readability check. This check does not independently compare installed files against Steam manifest hashes.
- **Cinderbox Setup Wizard** — Guided setup for Cinderbox: handles permissions, downloads the APK, walks you through installation, and verifies everything is ready before downloading game files.
- **SMAPI Setup** — Extracts SMAPI internals to the correct location so mods can load.
- **Mod Manager** — Browse Nexus Mods, download, install, update, and manage SMAPI mods and content packs directly from the app. Requires a free Nexus Mods API key.
- **Multiple File Access Methods** — Supports Root, Shizuku, All Files permission, SAF, and manual staging to access save files across different Android versions and device configurations.
- **Auto-Sync** — Optionally push saves to Steam Cloud automatically when the game closes (requires root).

## Requirements

- Android 8.0 (API 26) or higher
- A Steam account that owns Stardew Valley
- An internet connection for Steam login and cloud sync

## Android Version Compatibility

Android 11 introduced restrictions on access to other apps’ directories under `Android/data`. All Files access does not remove those restrictions, and the system document picker also limits access there. Device software and provider behavior vary. See Android’s [storage changes](https://developer.android.com/about/versions/11/privacy/storage) and [All Files access documentation](https://developer.android.com/training/data-storage/manage-all-files).

- **Official Android game:** use an available Root or Shizuku strategy, or copy saves manually through a staging directory selected in Settings. Privileged access depends on the device and its configuration.
- **Cinderbox:** saves live in `/storage/emulated/0/StardewValley/desktop/Saves/`. Companion requests All Files access on Android 11+, or the storage runtime permission on Android 8–10.
- **SAF staging:** select a folder containing complete save directories. Manually transfer saves between that folder and the game directory when direct access is unavailable.

## Getting Started

1. **Download** the latest APK from the [Releases](https://github.com/ObfuscatedVoid/Cinderbox-Companion/releases) page and install it.
2. **Log in** with your Steam account. Steam Guard (2FA) is supported — you can sign in with a code or scan a QR code from the Steam mobile app.
3. **Sync saves** — The Saves tab shows all your Steam Cloud saves. Pull them to your device or push local saves back to the cloud.
4. **Download game files** (optional) — If you need game files for Cinderbox (or something like Winlator), go to the Download tab. The setup wizard will guide you through the full process.

## File Access Setup

On first use, the app auto-detects the best available method to access save files. You can change this in Settings under "File Access". The available methods depend on your device:

| Method | Requires | Scope |
| --- | --- | --- |
| Root | Root permission | Paths allowed by the root service |
| Shizuku | Running Shizuku service and permission | Paths allowed by the device’s shell access |
| All Files | All Files access on Android 11+; storage permission on Android 8–10 | Shared storage; other apps’ private directories can remain restricted |
| SAF | A directory selected in the system picker | Only that directory and its children |
| Manual | Manual transfer and readable shared storage | Saves copied into the configured location |

## Cinderbox Directory Structure

If you're using Cinderbox, the app expects files in the following structure:

```
/storage/emulated/0/StardewValley/
├── desktop/
│   ├── GameFiles/
│   │   ├── Stardew Valley.dll
│   │   ├── StardewValley.GameData.dll
│   │   ├── BmFont.dll
│   │   ├── xTile.dll
│   │   ├── Lidgren.Network.dll
│   │   └── Content/
│   │       └── (PC game assets)
│   ├── Mods/
│   │   └── (your mods)
│   └── Saves/
│       └── (your save folders)
└── smapi-internal/
    └── (SMAPI files)
```

The setup wizard and SMAPI extraction handle most of this automatically. Game files require a Steam account that owns the PC version.

If you used the **Cinderbox client** before version 0.8, those files lived directly under `StardewValley/` (`Saves/`, `Mods/`, `GameFiles/`).
Companion 0.1.1+ uses the `desktop/` layout required by Cinderbox 0.8+. It offers to move leftover files on launch; you can also move them from Settings. Existing destination save or mod folders are left intact, with their old copies reported as conflicts. An existing `GameFiles/` folder is never merged with another version. Back up your saves before migrating.

Please join the [Cinderbox Discord](https://discord.gg/AjstnPVYwS) for help with Cinderbox-specific issues and an up-to-date guide and list of compatible mods.

## Building from Source

```bash
git clone https://github.com/ObfuscatedVoid/Cinderbox-Companion.git
cd ./Cinderbox-Companion
./gradlew assembleDebug
```

The debug APK will be in `app/build/outputs/apk/debug/`.

Requires JDK 17 and Android SDK 35.

## Development checks

Run formatting separately before compilation:

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Run instrumentation tests on a disposable emulator or test device:

```bash
adb install -r app/build/outputs/apk/debug/CinderboxCompanion-v0.1.1.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.sdvsync.test/androidx.test.runner.AndroidJUnitRunner
```

Tests use isolated fixtures and do not upload Steam saves. Back up device data before testing. The test runner restarts the app, so test connection recovery separately in one running app process.

`./gradlew minifyReleaseWithR8` checks release compilation and shrinking without packaging a signed release. Release installation and signing need separate validation. Lint reports dependency and target-SDK update advisories; review them separately from source-code defects rather than suppressing them.

Before a release, also check large text, portrait/landscape navigation, pull-to-refresh, offline recovery, import cancellation, and the actual storage provider being supported. Passing fixture tests does not establish root, Shizuku, physical-device, full-game, or live Steam-upload compatibility.

## Links

- [Cinderbox on Nexus Mods](https://www.nexusmods.com/stardewvalley/mods/43278) — "Cinderbox - Play Desktop SDV on Android" — download the APK here
- [Cinderbox Source Code](https://github.com/Ekyso/Cinderbox) — Official Cinderbox repository
- [Cinderbox Discord](https://discord.gg/AjstnPVYwS) — Community for the Cinderbox modded Android client
- [Nexus Mods API Key](https://www.nexusmods.com/users/myaccount?tab=api+access) — Required for mod browsing and downloads

## License

This project is provided as-is with no warranty. See [LICENSE](LICENSE) for details.
