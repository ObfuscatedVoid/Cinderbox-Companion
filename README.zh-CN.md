# Cinderbox Companion（简体中文）

[English](README.md)

Cinderbox Companion 是 Android 版《星露谷物语》的辅助应用，可在手机上同步 Steam 云存档、下载游戏文件，以及管理 SMAPI 模组。它支持官方 Android 版和 [Cinderbox](https://www.nexusmods.com/stardewvalley/mods/43278) 模组客户端。

应用会根据系统语言使用简体中文、中国香港繁体中文或中国台湾繁体中文界面。

> 这是独立的玩家项目，与 ConcernedApe、Chucklefish 或 Cinderbox 开发者没有隶属、赞助或合作关系。《星露谷物语》是 ConcernedApe 的商标。

## 使用前请先备份存档

**使用本应用有风险。操作前请手动备份存档，并把备份保存在安全位置。** 应用会在同步前自动创建备份，但不能保证同步、游戏下载或模组管理不会影响存档。发生存档丢失或损坏时，独立保存的手动备份是最可靠的恢复方式。

## 主要功能

- **Steam 云存档同步**：将云端存档下载到设备，或将本地存档上传到云端；覆盖前可查看版本冲突。
- **游戏下载**：使用拥有《星露谷物语》的 Steam 账号下载游戏文件，支持选择分支和下载后校验。
- **Cinderbox 设置向导**：引导授权、下载及安装 Cinderbox APK，并在下载游戏文件前检查目录。
- **SMAPI 设置**：将 SMAPI 内部文件解压到 Cinderbox 所需的位置。
- **模组管理**：浏览 Nexus Mods，下载、安装、更新和管理 SMAPI 模组及内容包；需要免费的 Nexus Mods API 密钥。
- **多种文件访问方式**：支持 Root、Shizuku、所有文件访问权限、SAF 和手动中转目录。
- **自动同步**：可在游戏关闭时自动上传存档，需要 Root 权限。

## 使用条件

- Android 8.0（API 26）或更高版本。
- 拥有《星露谷物语》的 Steam 账号；下载 PC 版游戏文件需要拥有对应的 PC 版游戏。
- Steam 登录和云存档同步需要网络连接。

## Android 版本与存档访问

Android 13 及更早版本通常更容易访问游戏存档。Android 14 及更高版本可能限制应用直接读取 `/Android/data/`。可根据设备情况选择：

| 方式 | 适用情况 |
| --- | --- |
| Root | 已获取 Root 权限的设备，可以直接访问文件。 |
| Shizuku | 通过 ADB 或无线调试启动 Shizuku，无需 Root。 |
| 手动中转 | 用能访问游戏目录的文件管理器，在游戏目录与应用选定的中转目录之间复制存档。 |
| Cinderbox 模式 | 存档位于 `/storage/emulated/0/StardewValley/desktop/Saves/`；授予所有文件访问权限后，通常不受 `/Android/data/` 限制。 |

首次使用时，应用会检测可用的文件访问方式。你也可以在“设置 → 文件访问”中更改。

## 开始使用

1. 安装 APK，使用 Steam 账号登录。Steam 令牌支持验证码和手机应用扫码。
2. 在“存档”页查看云端与本地存档。下载会把云端版本写入设备；上传会把本地版本写入 Steam 云端。覆盖前请确认所选版本。
3. 如需给 Cinderbox 或 Winlator 准备游戏文件，打开“下载”页并按向导操作。

## Cinderbox 文件目录

```text
/storage/emulated/0/StardewValley/
├── desktop/
│   ├── GameFiles/     # PC 游戏文件
│   ├── Mods/          # 模组
│   └── Saves/         # 存档
└── smapi-internal/    # SMAPI 内部文件
```

Cinderbox 0.8 起使用 `desktop/` 目录。若旧版文件仍直接放在 `StardewValley/` 下，应用会提示迁移；目标目录中已有同名文件夹时，不会自动合并内容。迁移前请先备份存档。

## 从源码构建

需要 JDK 17 和 Android SDK 35：

```bash
git clone https://github.com/cang-yang/Cinderbox-Companion.git
cd Cinderbox-Companion
./gradlew assembleDebug
```

调试版 APK 位于 `app/build/outputs/apk/debug/`。上游项目见 [ObfuscatedVoid/Cinderbox-Companion](https://github.com/ObfuscatedVoid/Cinderbox-Companion)。

## 相关链接

- [Cinderbox 下载页](https://www.nexusmods.com/stardewvalley/mods/43278)
- [Cinderbox 源码](https://github.com/Ekyso/Cinderbox)
- [Cinderbox Discord](https://discord.gg/AjstnPVYwS)
- [Nexus Mods API 密钥](https://www.nexusmods.com/users/myaccount?tab=api+access)

本项目按仓库中的 [LICENSE](LICENSE) 提供，不附带任何担保。
