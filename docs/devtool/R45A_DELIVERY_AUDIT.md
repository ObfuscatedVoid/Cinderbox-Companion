# R45-A overlay delivery audit

- Exact merged source base, Git tree and each modified Git blob are checked **before** modifications.
- All textual anchors are checked before any file is written; temporary-file substitution is atomic per file, and Devtool's atomic transaction handles rollback on validation failures.
- Eleven new source/CI/docs files plus four pinned target-file modifications through a restricted before-validation finalizer.
- GitHub-hosted Android compilation is required for accepting Kotlin UI integration; synthetic local source checks are not Android builds.
- No game, saves, mods, SMAPI, Steam credential, journal or release policy source is modified.
- No automatic push, release, APK device installation or publication. The user creates/pushes the feature branch and PR after the automatic local commit.
