# Advanced Edition — Execution Backlog

As of 2026-10-19. Issues are currently disabled in the fork (GitHub returned HTTP 410), so keep work items here until enabled.

## P0 — Runtime session integration
- Owner: Android Runtime.
- Replace `RuntimeTaskController.stopAction` with a task-ID keyed cancellation manager.
- Add session journal init at cold process start and mark old RUNNING jobs INTERRUPTED.
- Add host task IDs to foreground Stop notifications and each agent runtime bridge.
- Finish/release tasks in finally; never auto-retry effectful tool actions.
- Verification: Stop A != B; kill/restart; corrupted journal; duplicate project lease; Android 9/13/14/16.

## P1 — MCP & Skills Manager UI
- Owner: Android Compose + Security.
- Read ExtensionCatalog manifests and project-scoped grants in an inventory screen.
- Show source/version/digest/capabilities. Explicit grant/revoke UX and auditable changes.
- MCP launcher must use permission gate; no automatic remote install scripts.
- Verification: digest tamper, project isolation, default deny, undeclared capabilities and revocation.

## P1 — CI and APK gates
- Owner: Build & Quality.
- Gradle unit tests, lint, online debug build and integration tests with JDK17 / Android SDK36 / NDK28.2.
- ARM64 device matrix Android 9/13/14/16 incl. RAM/disk/background/force-kill.
- Only produce signed online/offline APK after CI and review; preserve MIT upstream notice.

## P2 — Multi-Agent Scheduler
- Sequential task routing first. Concurrency only with isolated worktrees, scoped permissions,
  bounded budgets, rollback and human review.

## PR dependency order
1. PR #1: Phase 1A session journal and targeted cancellation registry (draft).
2. PR #2: Phase 2A extension inventory and policy (draft, stacked on #1).
3. Next: P0 runtime integration must land before MCP launcher or automated orchestration.

No merge, release, or production feature claim until verification gates pass.
