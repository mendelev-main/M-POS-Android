# Implementation plan

1. Extend the existing bounded breadcrumb preferences with safe snapshots and best-effort recording. Use a pure Kotlin report policy shared by record/export to sanitize old records.
2. Create an IO-only exporter with ContentResolver, BuildConfig and Android API metadata. Use the Activity Result document contract already established for backup, without reading backup/POS data.
3. Add a diagnostics channel to the origin-restricted native bridge and a settings action in the Android-only JS adapter. Keep shared HTML/source-manifest parity unchanged.
4. Test actual Kotlin filtering, retention and malformed input using JVM JUnit/real org.json. Execute the Android adapter with a simulated DOM/bridge to test manual triggering, duplicate prevention and preserved channel contracts.
5. Run all Node tests, JVM unit tests, lint, debug build and minified unsigned release build. Record physical acceptance separately.
