# 02 — рабочая зона

2026-10-24 владелец авторизовал следующую часть; physical tablet acceptance01/02 позже, не blocker. Используем mpos-phase/mpos-domain, develop без stable release. Источники pinned1682c883: pos-navigation,cart-composition,android-pos-stock/android-safety и соответствующие source-product-stock regressions. Первый02a scope/решения/schema5/границы по docs/WORKSPACE-02A.md. Оплат/parked не включаем; W01/W02/W06 будут частично, без заявления full02.

02a implementation: pure CartEngine/LayoutEngine; current snapshot graph stores only dependency closure, one aggregate RecipeCalculator reserve before rounding. Delta Add/Step read latest in transaction; absolute qty/Clear/layout CAS. Schema5 workspace_documents current/layout + operation_results/audit same DB. Unknown payload does not reset. Shared AppBar/Photo/Tile and wide Dialog folder variant. Saved placement reset lets owner remove a layout reference before catalog delete. Current stock/recipe conflict leaves cart; no financial action.

Local checks:22 pure domain tests cached Kotlin2.1.20/JDK17 (supplemental),4Python/preflight/diff passed.7 pinned source regressions passed. `harness.py android` blocked before wrapper (sandbox ~/.gradle lock; no localSDK). Project Kotlin2.2.21/lint/APK/Room/UI to CI36. Added6Room tests +1Compose scenario, update E2E explicit seed3 tests + cart qty5/total15 survives package replacement. CI pending. Device user-deferred.


CI follow-up0e6dd0b/run40106275939 pending. First4e7d301 checks31JVM/lint/APK pass,6Room/new migrations pass; UI duplicate folder label corrected with Popup-scoped matcher. Schema5 captured from KSP. Tiny reserve underflow regression reproduced before fix and local22 passed after; keep precision validation on decreases as well as increases, not stock-shortage guard on decreases. Main/stable unchanged.


## Итог проверки,2026-10-25

[run40106275939](https://github.com/mendelev-main/M-POS-Android/actions/runs/40106275939), commit0e6dd0b: checks/emulator36 success;31JVM/4Python/lint/assembleDebug,31instrumentation (6workspace Room+1workspace UI), затем3explicit UI seed tests. CATALOG UPDATE PASSED/WORKSPACE UPDATE PASSED/UPDATE E2E PASSED: package1→2, stock8 и current cart quantity5/total15 сохранены. Actual logs и screenshots просмотрены. После tested commit — только docs. Schema5 byte-equal final KSP, включена в0e6dd0b. Не менять main/stable/signing identity. Полная02 ещё не закрыта: parked/ownership/order parameters02b, explicit revalidation02c, fixed-slot drag, count-based folder sizing/config preview/performance и user-deferred physical walkthrough. Следующую часть продолжать по этому контексту, не переоткрывать01/device blocker.

## 02b начало,2026-10-25

Владелец авторизовал продолжение: parked/metadata/reservations. Scope docs/WORKSPACE-02B.md, pinned parked-orders + android-pos-stock/android-safety и storage fault regressions. Room schema5 прежняя; current payload1→2 migrated on initialize transaction (sessionId/createdAt/details), orders registry monotonic; parked:<sessionId> wrapper1/cart2. Atomic park/resume/swap/delete; explicit confirmation for replacing empty prepared details. Current scoped commands carry expectedSessionId (including a previously opened configuration dialog); absolute commands CAS as before. Registry-backed parked revision prevents ABA; snapshots derived reserve per owner, other parked exclude current. Delete guard includes parked dependencies.

Local26domain tests supplemental cached Kotlin2.1.20/JDK17 passed and storage codec compiled with cached JSON. Added6Room tests (reopen/legacy/replay/ownership/concurrent/ABA/aggregate/metadata/swap and injected failure at every transfer write) +1Compose workflow. Native limits/label suffix/order metadata in02B contract. UI uses shared Close/Buttons/Field textarea variant; no payments/print/network. CI36 pending; user-deferred physical acceptance unchanged.
