# M POS Android

Android tablet port of the production M POS with a native Kotlin shell and a bundled offline POS web runtime.

The current application deliberately keeps the reviewed HTML/CSS/JavaScript business runtime inside the APK while Kotlin owns Android-specific work. The project is being migrated incrementally toward a more native architecture where doing so improves reliability, maintainability or device integration. The migration plan lives in [docs/NATIVE_MIGRATION_ROADMAP.md](docs/NATIVE_MIGRATION_ROADMAP.md).

Текущий [статус миграции, проценты и проверки на планшете](docs/MIGRATION_STATUS_RU.md) — полный отчёт на русском.

## Architecture

- One hardware-accelerated `WebView`; no remote UI and no cross-platform runtime.
- `WebViewAssetLoader` serves bundled files through a trusted HTTPS origin.
- An origin-restricted message bridge preserves the existing iPad `window.webkit.messageHandlers` contract and adds Android-only native boundaries.
- M POS naming is mandatory for all new and rewritten code. `PrilavokCore` and `prilavok_` exist only as temporary legacy compatibility surfaces for the bundled parity runtime and backup schema v13 until those boundaries are migrated.
- Network operations never replace or gate local POS persistence.
- Native migrations are performed one module/boundary at a time with parity and rollback evidence.
- Kotlin processes Room shadow operations in a bounded FIFO; raw JSON and domain projections commit atomically. Failed or pending writes block green native catalog diagnostics. Local POS storage remains authoritative; see [spec 035](specs/035-transactional-native-shadow-storage/spec.md).

## Platform support

- Android tablets from any manufacturer; no model-specific APIs or dimensions
- Responsive landscape interface for different tablet sizes, resolutions and pixel densities
- Android 9 and newer (minimum API 28), targeting Android 15 / API 35
- Package: `com.mendelev.mpos`

## Build

Install Android Studio, then install Android SDK Platform 35 and Build-Tools 35 from SDK Manager.
The first installation requires accepting Google's Android SDK license in Android Studio.
The project has been compiled with Platform 35 and Build-Tools 35.0.0; both debug and minified
release variants pass compilation. GitHub Actions repeats source-parity tests, lint and the debug
build for every change to `main`.

```bash
./gradlew assembleDebug
```

Install over USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Production APKs must always use the same private release signing key. The key is stored outside Git.

The repository is currently an engineering baseline, not an accepted production replacement. Track
the remaining physical parity checks in [docs/PARITY_MATRIX.md](docs/PARITY_MATRIX.md).

## Android diagnostics and availability

In Settings, “Сохранить диагностику” saves a local JSON report through Android's document picker.
It includes app/API version and up to 200 technical events, excluding receipts, customers and secrets.
Cancellation does not change POS data. Physical export acceptance remains pending.

After a failed/interrupted availability send, Android waits for the next successfully saved payment
before attempting again; restart, reconnect and foreground do not retry. See
[spec 033](specs/033-availability-payment-retry-policy/spec.md) and the
[native migration review](docs/NATIVE_MIGRATION_REVIEW.md) for the active policy and sector priorities.

Run Kotlin report/storage tests with `./gradlew testDebugUnitTest` in addition to the Node checks.

When shift-close notifications are enabled, Telegram receives a PNG receipt with shift totals and cash
movements, matching the iPad report contract. Shift closure is committed locally before sending;
Telegram failure does not undo closure or stop LAN receipt printing. See
[spec 034](specs/034-telegram-shift-receipt-image/spec.md) for image parity and remaining physical checks.
