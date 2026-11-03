# R43-D consolidated overlay audit and proof boundary

| Item | Delivery | Proof and limitation |
| --- | --- | --- |
| Source pin | Exact Git parent HEAD/tree + exact SHA-1 Git blob pins for ViewModel, download screen and Devtool TOML | Source changes fail closed on drift; no automatic rebase |
| Public destination | Fixed Cinderbox check cached as typed ViewModel state, refreshed on lifecycle resume | Read-only; does not bypass writer checks or grant Steam entitlement |
| Status categories | Setup, inaccessible, available for checks, protected existing, manual review | Explicit action copy; no raw production path dump |
| Developer alternative | Wizard and alternate path remain `DEBUG && unlocked` | Manager/service still enforce their own check |
| Protected updates | Existing coordinator gates + release booleans unchanged | Still **disabled** and real-device unqualified |
| Tests | Seven Kotlin unit tests, three Python host tests and source contract, regression workflows | Source-unit/host acceptance only until GitHub PR build passes |
| Android build | Existing `build.yml` only; overlay has no Gradle commands | Do not run Gradle in Termux; inspect exact-commit PR logs |
| Prior milestones | Source regression scripts R42/R43A/B4/C included | No historical evidence rewritten |
| Roadmap | R43-D targeted cleanup | R44/R45/R46 UI/error work and M25–M29 remain |

**Safety:** No action on `/storage/emulated/0/StardewValley/desktop/GameFiles`, no removal of original game/Saves/Mods/SMAPI, no activation of live updates or release publication.
