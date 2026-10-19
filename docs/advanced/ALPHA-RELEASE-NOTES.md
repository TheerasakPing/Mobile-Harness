# Mobile Harness — Advanced Alpha Preview

Source: fork of techjarves/Mobile-Harness v1.0.7, MIT. Maintains upstream attribution.

## What this build contains
1. Session task journal, process-kill detection, task-scoped cancellation, history UI.
2. Local Skill Markdown import and local MCP JSON manifest import, SHA-256 verification, per-project approvals.
3. Sequential, explicitly approved Claude/DeepSeek/Antigravity plans; fail-fast and no auto replay.
4. SSH connectivity **probe** using strict host key checking and known_hosts in workspace.
5. GitHub Actions unit tests and Android ARM64 online **debug APK** artifact.

## Preview installation
- Open the latest successful Advanced Runtime Checks workflow for this branch.
- Download artifact `mobile-harness-advanced-online-debug`, unzip, install included APK on Android 9+ ARM64.
- The debug variant uses package `com.jarves.mh.advanced` to avoid replacing upstream app and data.
- Debug APK uses CI debug signing; NOT a production-signed or Play release.
- Android may require permission to install APKs from your browser/files app.
- Online runtime bundles still come from the upstream pinned runtime release.
- Do NOT import secrets into third-party skill/MCP configurations.
- No automatic MCP server execution or remote installs.

## Status and known limits
- Unit CI and APK build must both pass for a usable preview.
- Not verified on physical Android devices; test native PRoot, JNI process spawn, web preview, battery/background lifecycle.
- Remote file editing, SSH sync and GitHub remote job workers NOT implemented.
- Parallel multi-agent worktrees and MCP transport/launcher NOT implemented.
- Foreground service currently shows one primary active task notification; concurrent background jobs require additional QA.
- Crash recovery preserves state but does NOT resume or replay effectful tasks automatically.

## Release gate
Do not claim production-ready until device tests on Android 9/13/14/16 ARM64, security review, signing and upgrade/migration QA pass.
