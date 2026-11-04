# R45/R46 consolidated overlay delivery audit

- Based on exact R44B merge HEAD/tree; all changed preexisting source files are pinned by Git blob SHA-1.
- R45A `r45a_patch.py` applies the navigation patch first; `r45ui_patch.py` applies additional screens and `.devtool.toml` second.
- Both patchers fail on missing/duplicate anchors. The second patcher verifies the intermediate Devtool config exactly against the known R45A transformation.
- `rollback_on_failure = true`; no manual partial source edits required.
- Python host contract tests and Kotlin error policy tests included; no Android Gradle/local ADB run.
- CI must confirm Kotlin compilation; R47 must physically qualify navigation, TalkBack and large-font screenshots.
- No download, sync, mod or file update code is modified.
