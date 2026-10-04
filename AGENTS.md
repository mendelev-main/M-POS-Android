# M POS Android development rules

1. The production iPad POS repository is the source of truth until Android parity is formally accepted.
2. Architecture is offline-first. Sales, receipts, shifts, products, recipes, inventory, receiving and printing must work without internet.
3. Persist local data before any network action. A backend failure must never block local POS work.
4. Keep existing `prilavok_` localStorage keys and JSON shapes compatible. Change them only with an explicit migration and compatibility tests.
5. Preserve the primary data direction: POS → Backend → Web / Mini App.
6. Never add automatic catalogue synchronization. Existing manual synchronization remains the only catalogue sync trigger.
7. Availability publication may run only after a successfully persisted payment, matching the iPad implementation.
8. Reuse the reviewed HTML/JavaScript interface. Platform differences belong in small native bridges.
9. Use native Kotlin APIs for LAN ESC/POS printing, photos, file import/export, sharing and reports. Do not add a general cross-platform runtime.
10. Do not commit signing keys, Telegram tokens, device keys, backup files or production data.
11. Keep the Android package ID and release signing key stable after the first production installation.
12. Migrate and verify one native boundary at a time. Avoid broad rewrites of business logic.
13. Test critical behavior on physical Android tablets after every substantial stage. Do not add dependencies on a particular manufacturer, model, screen resolution or chipset.
14. Do not commit or push unless authorized by the user. The current session authorizes commits and pushes to `main`.
