# M POS Android development rules

1. The production iPad POS repository is the source of truth for parity behavior until Android parity is formally accepted.
2. Architecture is offline-first. Sales, receipts, shifts, products, recipes, inventory, receiving and printing must work without internet.
3. Persist local data before any network action. A backend failure must never block local POS work.
4. Keep existing `prilavok_` keys, JSON shapes and backup schema v13 compatible. Change them only with an explicit migration and compatibility tests.
5. Preserve the primary data direction: POS → Backend → Web / Mini App.
6. Never add automatic catalogue synchronization. Existing manual synchronization remains the only catalogue sync trigger unless the user explicitly changes this rule.
7. Availability publication may run only after a successfully persisted payment, matching the accepted POS behavior. After a failed or interrupted availability send, retry only after the next successfully persisted payment; startup, reconnect and foreground must not retry. Existing stock-change/manual-sync triggers must not bypass this retry gate. Spec 033 supersedes spec 030's automatic recovery triggers on Android.
8. Until a module has an approved native-migration specification, its reviewed HTML/JavaScript implementation remains the parity reference. Approved modules may move to Kotlin/Compose incrementally while preserving behavior and data compatibility.
9. Prefer native Kotlin APIs for Android platform boundaries: storage infrastructure, LAN ESC/POS, networking/SSE infrastructure, photos, file import/export, sharing, reports, diagnostics, updates and lifecycle. Do not add a general cross-platform runtime.
10. Do not commit signing keys, Telegram tokens, device keys, backup files or production data.
11. Keep the Android package ID and release signing key stable after the first production installation.
12. Continue migrating cohesive native domains in sequence; the user authorizes a broad migration program without per-stage manual gates. Preserve business semantics and add automated verification for each boundary; GitHub Actions runs it. Each migration needs a rollback/compatibility path until accepted.
13. The user authorizes immediate native authority cutovers with automated compatibility checks and defers comprehensive physical Android tablet testing until the end of the migration. Keep physical cases documented and pending; do not block each stage on tablet acceptance. Do not add dependencies on a particular manufacturer, model, screen resolution or chipset.
14. Native migration order is documented in `docs/NATIVE_MIGRATION_ROADMAP.md`; completed parity work remains documented in `docs/PARITY_MATRIX.md`.
15. All new or rewritten code uses M POS naming: Kotlin/Compose classes use `MPos...`, new JS/runtime namespaces use `MPosCore`, and new docs/UI must not introduce `Prilavok`. Existing `PrilavokCore` / `prilavok_` are temporary compatibility surfaces only and must disappear as their legacy modules are migrated.
16. The user authorizes commits and pushes to `main`. After completing and validating requested changes, publish them to `main` so GitHub stays up to date. Preserve unrelated user changes and never force-push.

17. The user requests no local APK assembly: GitHub automation builds APKs after commits. Write and maintain relevant tests, but do not routinely run JS/JVM tests, lint or APK builds locally. GitHub Actions runs verification in a separate job; the user reviews results and reports failures. Run local tests only when explicitly requested. Never report checks as passed before their actual execution.

18. For UI/design work, read `.agents/skills/mpos-native-design/SKILL.md` and its referenced `.agents/skills/frontend-design/SKILL.md`. Match `docs/NATIVE_DESIGN_SYSTEM.md` and the existing POS visual tokens using shared native theme primitives; preserve business behavior.

19. After each migration stage, update its spec and `docs/kotlin-migration-tasks.json`, `docs/KOTLIN_MIGRATION_TASKS.md`, migration status/roadmap/parity and pending physical cases. Run `python scripts/migration-progress.py` and report completed/total tasks to the user. Stable IDs and scope govern counting; exclude superseded stages, explain any scope-driven denominator change. The engineering task ratio is not native feature coverage or physical acceptance.
