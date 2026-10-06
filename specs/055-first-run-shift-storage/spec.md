# 055 — First-run shift storage and Android error presentation

Reported physical case: empty HONOR Android tablet, opening fails for both cashier and administrator with generic local shift lifecycle error. Employees survive restart. Failure marks the shared storageBroken flag, so every page displays the reviewed iPad browser/Safari warning.

## Reproduction and correction

Room authority initialization correctly preserves an absent document or JSON null. Reviewed Storage.get('shifts', []) maps either to an empty list. Native shift commands previously constructed JSONArray directly from read().payload: the first opening failed with JSONException (null cannot be converted to JSONArray). Tests previously initialized explicit [] or a populated history, missing this first-install state.

MPosShiftStorage.readRecords now treats absent/null documents as an empty list while rejecting malformed documents. It does not persist a synthetic empty array. Lifecycle, shift report/screen, payment, return and cash movement reads use the same interpretation; all existing state/employee/cash/recovery/replay validations remain. Opening writes a shift only in the existing atomic commit. Original JSON extensions, backup v13, schema, primary keys, authentication and network triggers are unchanged.

Fixed whitelisted lifecycle failure messages provide safe Russian guidance for stale state, pending recovery and invalid cash. Unknown parser/SQL errors produce a fixed message without returning raw source records or credentials. Transaction failure still returns ok=false and prevents JS state updates/post-commit outputs; uncertain acknowledgements retain the existing exact-retry/reload behavior.

An Android-only adapter replaces renderStorageWarning before loadAll. It reports failure to confirm an operation and asks the user to check its result before repeating/restarting. It does not claim that all prior data disappeared or recommend Safari. storageBroken remains visible after failure; the warning is not simply suppressed. The reviewed iPad source/hash remains unchanged and sync tooling retains the adapter.

## Evidence / acceptance

Before correction, new native regression fails with JSONException: null cannot be converted to JSONArray. Automated cases exercise absent/null initialization, both employee roles, zero carryover, opening replay/closure, untouched empty-screen documents and safe failure messages. JS tests verify Android warning visibility/text, unchanged failure state, initialization order and non-Android behavior. Full JS/JVM suites, lint and debug/minified unsigned release builds required.

Physical acceptance still pending: update the installed APK, restart, open first shift with cashier/admin (existing auth), deposit/cash payment/close/reopen and confirm persistence after restart. Check empty/null v13 restore and native screen navigation. Do not claim cloud tests validate the physical tablet.
