# M POS Android

Android tablet port of the production M POS with a native Kotlin shell and a bundled offline POS web runtime.

The current application deliberately keeps the reviewed HTML/CSS/JavaScript business runtime inside the APK while Kotlin owns Android-specific work. The project is being migrated incrementally toward a more native architecture where doing so improves reliability, maintainability or device integration. The migration plan lives in [docs/NATIVE_MIGRATION_ROADMAP.md](docs/NATIVE_MIGRATION_ROADMAP.md).

## Architecture

- One hardware-accelerated `WebView`; no remote UI and no cross-platform runtime.
- `WebViewAssetLoader` serves bundled files through a trusted HTTPS origin.
- An origin-restricted message bridge preserves the existing iPad `window.webkit.messageHandlers` contract and adds Android-only native boundaries.
- Existing `prilavok_` keys and JSON records remain compatible with iPad backup schema v13 until an explicitly specified migration is accepted.
- Network operations never replace or gate local POS persistence.
- Native migrations are performed one module/boundary at a time with parity and rollback evidence.

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
