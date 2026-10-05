# Spec 032 — Native diagnostic export

## Goal and benefit

Complete the P5 diagnostic boundary: a tablet operator can save a small local technical report when printing, storage or connectivity fails. Kotlin owns filtering, snapshot generation and Android document writing; JS only requests export. This works independently of backend connectivity and avoids copying business data into support logs.

## Contract

- Add “Сохранить диагностику” to the existing Android settings screen. Use the existing Android-only adapter, leaving the reviewed POS HTML and shared business modules unchanged.
- The operator explicitly selects a destination through Android CreateDocument (`application/json`). Cancel makes no POS changes. No automatic upload or broad storage permission.
- Versioned JSON contains schema version, generation time, app version/versionCode, Android API level, and at most the latest 200 technical events in recording order.
- Event fields are timestamp, allowlisted category/event and optional Boolean outcome. Rebuild stored records from an allowlist at export, including records written by older versions; never copy arbitrary extra fields or event text.
- No order/item/customer data, URLs, keys, tokens, settings, business backup or database contents.
- Record native lifecycle, printer, network and storage outcomes. A failed Room projection is a diagnostic failure even if its raw shadow write succeeded; the original callback remains unchanged.
- Diagnostic recording failure cannot interrupt delivery of the existing POS callback. Export uses IO dispatch and closes the selected document stream. Show a generic localized result without exception details.
- The pending document-picker flag survives Activity recreation. A second tap while waiting for the picker cannot open another picker. Report is generated when a destination is returned, not before opening the picker.

## Compatibility and rollback

No change to storage keys/JSON, backup v13, Room schema, payments, stock, shifts, loyalty, WEB triggers, catalog authority or synchronization. Existing breadcrumb preferences remain readable. Removing the diagnostics bridge/UI/export classes rolls back export; the existing POS channels retain their envelopes. No destructive migration.

## Physical acceptance (pending)

1. Offline tablet: open settings, save locally, inspect JSON and app/API metadata.
2. Produce printer failure, Room shadow/projection failure and network reconnect; inspect only allowlisted event/outcome fields.
3. Cancel the picker, tap repeatedly, rotate/background/return while it is open; retry saving. Simulate Activity/process recreation while the picker is open.
4. Select a provider that refuses writing: see a generic failure and continue local sale/receipt operations.
5. Verify local payment, split-payment recovery, backup and manual catalog sync retain existing behavior.
6. Generate more than 200 events, restart, export; confirm limit, order and absence of private data.

Automated build/unit tests do not establish these device outcomes or production acceptance.
