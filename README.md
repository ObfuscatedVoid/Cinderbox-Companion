# Cinderbox Companion

A companion app for Stardew Valley on Android. Syncs save files with Steam Cloud, downloads game files, and manages SMAPI mods — all from your phone.

Works with both the official Android port and [Cinderbox](https://discord.gg/AjstnPVYwS) (the modded Android client).

> **This is an independent fan project.** It is not affiliated with, endorsed by, or connected to ConcernedApe, Chucklefish, or the Cinderbox developers. Stardew Valley is a trademark of ConcernedApe.

---

## Important Warning

**Use this app at your own risk.** I have limited experience with Stardew Valley modding and cannot guarantee how syncing, downloading, or mod management will affect your save files.

**Always back up your saves before using this app.** The app creates automatic backups during sync, but you should also keep your own copies in a safe location.

I am not responsible for any loss of save data, corrupted files, or damage to your device that may result from using this app. If something goes wrong with your saves, having a manual backup is the only reliable way to recover them.

---

## Features

- **Steam Cloud Sync** — Pull saves from Steam Cloud to your device, or push local saves back. Conflict detection tells you which version is newer before you overwrite anything.
- **Game File Download** — Download Stardew Valley game files directly from Steam using your own account. Supports branch selection and file verification.
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

The app works best on **Android 13 and below**, where file access to the game's save directory is straightforward.

**Android 14+** introduced stricter file access restrictions that prevent apps from reading `/Android/data/` directly. Workarounds:

- **Rooted device** — Full access with no restrictions.
- **Shizuku** — Grants elevated file access without root. Install Shizuku from the Play Store and start it via ADB or wireless debugging.
- **Manual file transfer** — Use a file manager that still has access to `/Android/data/` (such as ZArchiver, MT Manager, or MT2) to manually move saves between the game directory and a staging folder. The app's SAF staging mode supports this workflow.
- **Cinderbox mode** — Cinderbox stores saves in `/storage/emulated/0/StardewValley/Saves/` instead of inside `/Android/data/`. This path remains accessible with the standard All Files permission even on Android 14+, avoiding the stricter restrictions that apply to app-private directories.

## Getting Started

1. **Download** the latest APK from the [Releases](https://github.com/ObfuscatedVoid/Cinderbox-Companion/releases) page and install it.
2. **Log in** with your Steam account. Steam Guard (2FA) is supported — you can sign in with a code or scan a QR code from the Steam mobile app.
3. **Sync saves** — The Saves tab shows all your Steam Cloud saves. Pull them to your device or push local saves back to the cloud.
4. **Download game files** (optional) — If you need game files for Cinderbox (or something like Winlator), go to the Download tab. The setup wizard will guide you through the full process.

## File Access Setup

On first use, the app auto-detects the best available method to access save files. You can change this in Settings under "File Access". The available methods depend on your device:

| Method    | Requires                           | Android Version             |
| --------- | ---------------------------------- | --------------------------- |
| Root      | Rooted device                      | All                         |
| Shizuku   | Shizuku app running                | All                         |
| All Files | MANAGE_EXTERNAL_STORAGE permission | 11–13 (may not work on 14+) |
| SAF       | User-selected directory            | All (staging mode on 14+)   |
| Manual    | File manager                       | All                         |

## Cinderbox Directory Structure

If you're using Cinderbox, the app expects files in the following structure:

```
/storage/emulated/0/StardewValley/
├── GameFiles/
│   ├── Stardew Valley.dll
│   ├── StardewValley.GameData.dll
│   ├── BmFont.dll
│   ├── xTile.dll
│   ├── Lidgren.Network.dll
│   └── Content/
│       └── (PC game assets)
├── smapi-internal/
│   └── (SMAPI files)
├── Mods/
│   └── (your mods)
└── Saves/
    └── (your save folders)
```

The setup wizard and SMAPI extraction handle most of this automatically. Game files require a Steam account that owns the PC version.

Please join the [Cinderbox Discord](https://discord.gg/AjstnPVYwS) for help with Cinderbox-specific issues and an up-to-date guide and list of compatible mods.

## Building from Source

```bash
git clone https://github.com/ObfuscatedVoid/Cinderbox-Companion.git
cd ./Cinderbox-Companion
./gradlew assembleDebug
```

The debug APK will be in `app/build/outputs/apk/debug/`.

Requires JDK 17 and Android SDK 35.

## Links

- [Cinderbox Discord](https://discord.gg/AjstnPVYwS) — Community for the Cinderbox modded Android client
- [Nexus Mods API Key](https://www.nexusmods.com/users/myaccount?tab=api+access) — Required for mod browsing and downloads

## License

This project is provided as-is with no warranty. See [LICENSE](LICENSE) for details.
