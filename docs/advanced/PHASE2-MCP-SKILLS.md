# Phase 2A — MCP & Skills Inventory Foundation

- Kotlin/JVM `ExtensionCatalog`, pure standard library, no dependency changes.
- SHA-256 verified payload; metadata persists inside app-private extension directory.
- Project-scoped, explicit capability allowlist; default-deny and fail-closed if payload changes.
- Updated bundle digest revokes old approvals.
- No remote execution, installation hooks, credentials, MCP server launches or Compose UI.
- Existing Agent CLI processes are **not** yet routed through this gate, so do not describe this as enforced global permissions.

## Validation
Standalone Kotlin CLI test harness: 12/12 checks (2026-10-19).
JUnit source exists but Android Gradle/device testing is pending.

## Next integration tasks
1. Connect catalog from a process-scoped Android owner; expose verified skills/MCP inventory.
2. Build Compose inventory, per-project grants, revoke and audit events.
3. Add guarded MCP launcher behind capability approvals.
4. Start no MCP servers or third-party skill scripts until user explicitly opts in.
