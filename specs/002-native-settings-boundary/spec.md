# Feature Specification: Native Android settings boundary

## Goal

Introduce the first incremental native-persistence boundary without changing POS business behavior. Android-specific printer and notification settings are mirrored into Kotlin-owned storage while the existing JSON/localStorage representation remains available for current UI, backup v13 and rollback compatibility.

## User scenarios

### P1 — Preserve settings outside the WebView boundary

When printer or POS notification settings are changed in the existing interface, Android receives and persists the same platform-settings snapshot natively.

### P1 — No POS behavior regression

Sales, payments, shifts, stock, receipts and WEB orders continue using their existing persistence and business logic unchanged.

### P2 — Future native ownership

The Kotlin boundary can return its stored snapshot through the secure origin-restricted bridge, enabling a later specification to make native settings authoritative without inventing another storage contract.

## Functional requirements

- Add an Android-only `settings` bridge channel.
- Persist one versioned JSON platform-settings snapshot using Android app-private storage.
- Mirror printer settings and POS notification settings after startup and after successful changes/restores.
- Never store signing material, Telegram tokens, device keys or production order data in this settings store.
- Keep localStorage values unchanged as the compatibility cache in this stage.
- Do not change backup schema v13.
- Do not modify shared iPad business modules solely for this migration; Android-specific glue stays in Android-only assets/Kotlin.

## Acceptance

- Existing JavaScript parity/source tests still verify shared runtime equality except for explicitly listed Android-only scripts.
- Android bridge test includes the `settings` channel.
- Native store accepts a versioned platform-settings snapshot and can return it.
- Debug build/lint pass.
- Physical check: change printer/notification settings, restart app, verify existing UI behavior unchanged.
