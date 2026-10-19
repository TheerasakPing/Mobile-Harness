# Mobile Harness — Advanced Edition Roadmap

Base: upstream v1.0.7 (96b1da08), fork: TheerasakPing/Mobile-Harness.
Keep MIT attribution; all new work on feature branches with tests before merge.

## Phase 1A — session foundation (this commit)
- Durable, per-session journal: RUNNING, INTERRUPTED, COMPLETED, FAILED, CANCELLED.
- Process-local one-writer-per-project cancellation registry with targeted task IDs.
- JVM/JUnit tests. No Android process resume; no bridge or UI wiring yet.

## Phase 1B — runtime integration (next)
- Introduce process-scoped supervisor. No single global stopAction.
- Bind foreground service Stop intents to explicit task IDs.
- Bridge terminal outcomes to journal and release leases in finally.
- Show interrupted sessions with explicit user-controlled retry (never automatic replay).
- Android device acceptance: interruption, cancellation, concurrent projects, background conditions.

## Phase 2 — MCP and Skills Manager
- Declarative manifests and trusted-source inventory; default-deny capability policy.
- File-based and trusted catalogs first; do not install arbitrary remote install hooks.
- Kotlin Compose management UI and per-project enable/disable.
- Audit permission approvals, no secrets in manifests.

## Phase 3 — safe orchestration
- Per-project sequential agent jobs, bounded context and token budgets.
- Parallel work only on isolated worktrees; review and merge before applying changes.

## Phase 4 — remote development
- Opt-in SSH, explicit identity and host verification, recovery and resource caps.

## Phase 5 — release
- CI unit and instrumentation tests, 9/13/14/16 ARM64 matrix, signing and verified APK releases.

### Gate
Do not claim Phase 1A integrated or ship an APK based only on standalone JVM tests.
