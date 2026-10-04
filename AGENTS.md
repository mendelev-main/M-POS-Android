# M POS Android development rules

1. The production iPad POS repository is the source of truth for parity behavior until Android parity is formally accepted.
2. Architecture is offline-first. Sales, receipts, shifts, products, recipes, inventory, receiving and printing must work without internet.
3. Persist local data before any network action. A backend failure must never block local POS work.
4. Keep existing `prilavok_` keys, JSON shapes and backup schema v13 compatible. Change them only with an explicit migration and compatibility tests.
5. Preserve the primary data direction: POS → Backend → Web / Mini App.
6. Never add automatic catalogue synchronization. Existing manual synchronization remains the only catalogue sync trigger unless the user explicitly changes this rule.
7. Availability publication may run only after a successfully persisted payment, matching the accepted POS behavior.
8. Until a module has an approved native-migration specification, its reviewed HTML/JavaScript implementation remains the parity reference. Approved modules may move to Kotlin/Compose incrementally while preserving behavior and data compatibility.
9. Prefer native Kotlin APIs for Android platform boundaries: storage infrastructure, LAN ESC/POS, networking/SSE infrastructure, photos, file import/export, sharing, reports, diagnostics, updates and lifecycle. Do not add a general cross-platform runtime.
10. Do not commit signing keys, Telegram tokens, device keys, backup files or production data.
11. Keep the Android package ID and release signing key stable after the first production installation.
12. Migrate and verify one native boundary at a time. Avoid broad rewrites of business logic. Each migration needs a rollback/compatibility path until accepted.
13. Test critical behavior on physical Android tablets after every substantial stage. Do not add dependencies on a particular manufacturer, model, screen resolution or chipset.
14. Native migration order is documented in `docs/NATIVE_MIGRATION_ROADMAP.md`; completed parity work remains documented in `docs/PARITY_MATRIX.md`.
15. All new or rewritten code uses M POS naming: Kotlin/Compose classes use `MPos...`, new JS/runtime namespaces use `MPosCore`, and new docs/UI must not introduce `Prilavok`. Existing `PrilavokCore` / `prilavok_` are temporary compatibility surfaces only and must disappear as their legacy modules are migrated.
16. Do not commit or push unless authorized by the user. The current session authorizes commits and pushes to `main`.
