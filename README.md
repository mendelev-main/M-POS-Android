# M POS Android

Native Android tablet port of the production M POS.

The application uses a small Kotlin shell around the reviewed offline POS interface. HTML, CSS and JavaScript are bundled into the APK and loaded locally. Kotlin owns device-specific work: LAN ESC/POS printing, photos, document sharing, reports and complete `.mposbackup` files.

## Architecture

- One hardware-accelerated `WebView`; no remote UI and no cross-platform runtime.
- `WebViewAssetLoader` serves bundled files through a trusted HTTPS origin.
- An origin-restricted message bridge preserves the existing iPad `window.webkit.messageHandlers` contract.
- Existing `prilavok_` keys and JSON records remain compatible with iPad backup schema v13.
- Network operations never replace or gate local POS persistence.

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

Release APKs must always use the same private signing key. The key is stored outside Git.

The repository is currently an engineering baseline, not an accepted production replacement. Track
the remaining native and physical checks in [the parity matrix](docs/PARITY_MATRIX.md).
