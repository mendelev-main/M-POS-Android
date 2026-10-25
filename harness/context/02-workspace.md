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


## 02b итог — 2026-10-25

[run40140796798](https://github.com/mendelev-main/M-POS-Android/actions/runs/40140796798), runtime commit091b2f3: checks121011943215 и emulator36 job121011943276 success. 35 JVM tests/0 failures,4Python,lint/assembleDebug;38 instrumentation tests на Android16/API36 и4 explicit UI seed tests перед внешним updater. Реальная установка APK1→2 сохранила stock8, current quantity5/total15 BYN и parked label/type delivery/total5 BYN. Полные metadata отдельно проверены Room reopen и Compose restore, не приписываются внешнему APK assertion. SQLschema5 byte-equal KSP export, migration cart payload1→2 проверена в Room. Signing/publish/production-upgrade skipped на develop.

Просмотрены screenshots order-details/parked-swap/parked-orders: общие Close/buttons/fields/dialogs, читаемые действия без перекрытий в показанных состояниях; форма параметров прокручивается. Полная визуальная/device приёмка не заявляется. После проверенного runtime — только документация. Следующая часть02c: явный пересчёт устаревшего заказа с показом изменений и подтверждением. Fixed slots/drag, count-based folder width, modifier total preview/performance и физическая приёмка остаются открытыми. Main/stable/signing identity не менялись.

## 02c начало — 2026-10-25

Продолжение авторизовано владельцем. Использованы mpos-phase/mpos-domain. Контракт WORKSPACE-02C: explicit current revalidation (parked сначала resume), pure preview и transactional confirm с owner/revision/token/result/audit. Полный before/after и aggregate reserve проверяются заново; неизвестные варианты и fixed→manual требуют исправления строки, не теряются молча. SQLschema5/payload2 неизменны. Native snapshot safety отличается от Web live recipe behavior по принятому контракту. Добавлены5 JVM/2Room/1UI; CI pending. Local cached compiler supplemental; фактический Android blocked до wrapper ~/.gradle lock, SDK отсутствует. Device deferred.


## 02c итог и следующий контекст

Финальный runtime2ce31c2, [run40144720147](https://github.com/mendelev-main/M-POS-Android/actions/runs/40144720147): checks121023650500 success —40JVM/0failures,5Python,lint/assembleDebug. Emulator job121023650607:41/41instrumentation passed (включая2RevalidationRepository+1RevalidationUI);4explicit UI seed tests passed. External updater остановился до Settings: screen.png и window.xml показывают android:id/alertTitle «System UI isn't responding». Quickstep-only recovery корректно не закрыла этот другой диалог. Общий job/run failure, установка APK1→2 в этом run НЕ пройдена. Предыдущие02b updater evidence остаются историей, не доказательством финального02c.

W03/W06 revalidation domain/Room/UI automated tests passed на финальном commit; статус инкремента implemented, full CI gate pending из-за external emulator infrastructure. Не ставить full02/ci-verified/device-verified. Новые повторы после трёх завершённых emulator executions (первая попытка+retry+финальный run; промежуточный40144591508 отменён) остановлены, чтобы не расходовать ресурсы при повторяющемся SystemUI/Quickstep ANR. Android application runtime exception не наблюдалась, но это не доказывает причину системного зависания. Состав noStockTracking виден в review и исключён из warehouse reserve; physical tests deferred пользователем. Нативный пересчёт не меняет SQLschema5/payload2, stock/ledger, main/stable/signing. Дальше: устранить нестабильность disposable emulator/external updater и закрыть его gate; затем оставшиеся UI02 (fixed slots/drag/folder widths/modifier total preview).

## Продолжение — стабилизация CI

Владелец авторизовал продолжение по плану. mpos-phase. Проверен action.yml pinned emulator-runner a421e438: pre-emulator-launch-script/ram-size/avd-name поддерживаются. Гипотеза нагрузки software renderer: PixelC2560×1800/320dpi заменён на1600×1125/200dpi с прежним viewport1280×900dp (61% меньше пикселей), named disposable AVD mpos-ci-api36, RAM3072M/cores2. Это гипотеза, причина ANR пока не установлена. Gate/утверждения/Android36/геометрия в dp не снижаются; SystemUI ANR не закрывается автоматически. На failure собираем lastanr/cpu/mem/events/display.6Python/preflight/diff passed; CI далее. Для workflow push используется уже авторизованный SSH key (проверен GitHub identity), дополнительных прав gh token не требуется.

## 02d UI начало

Независимо от инфраструктурного gate реализуются два документированных UI gaps02: count-based folder width/columns и live modifier/manual total. Источник pos-navigation.js renderPosFolderModal, pos.html611, cart-composition.js updateModifierSelectionTotal. Контракт WORKSPACE-02D; mpos-domain для partial-price preview, commit validation остаётся strict. Fixed slots/drag не объявляются выполненными. Первый local test ошибочно сравнил BigDecimal scale3.50 vs3.5, fixture приведена к canonical, расчёт верен. Итог локальных/CI далее.


## CI gate02c восстановлен

[run40172190371](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172190371), commit3a9a4d0 (runtime приложения прежний2ce31c2): checks121105200829 и emulator36 job121105200646 success.40JVM/0failures,6Python/lint/assemble;41instrumentation+4explicit UI seed. Logs: CATALOG/WORKSPACE/PARKED UPDATE PASSED и UPDATE E2E PASSED, actual package1→2, stock8/current qty5 total15/parked delivery label total5 сохранены. AVD1600×1125/200dpi; screenshot updated-workspace.png имеет1600×1125, просмотрен без системного overlay/перекрытий. Quickstep recovery в логах не вызывался. Это закрывает ранее заблокированный CI gate02c; причина прошлых ANR не доказана, но новая конфигурация успешно прошла сценарий без подавления системных диалогов. Signing/publish skipped на develop, physical deferred.

После этого реализован отдельный02d UI. Финальный runtime da8db71, [run40172821096](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172821096) pending. Не приписывать его новым UI assertions успех run3a9a4d0. Local Android попытка этого продолжения остановилась до wrapper на sandbox ~/.gradle lock (SDK отсутствует); actual Android идёт CI. Fixed-slot/drag/performance/physical остаются открытыми.


## 02d итог, следующий шаг

[run40172821096](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172821096), final runtime da8db71: checks121107096327 и emulator36 job121107097017 success.41JVM/0failures,6Python,lint/assembleDebug;41instrumentation и4explicit UI seed tests passed. Обновлённый WorkspaceUiTest подтверждает live total2.5→3 BYN, strict required-group refusal, folder return/recreate; прежние Room/revalidation/parked regression suites проходят. External updater: actual package1→2, CATALOG stock8, current quantity5/total15 BYN, parked delivery label/total5 BYN сохранены. Signing/publish/production-upgrade skipped на develop.

Screenshots workspace-folder/workspace-configuration просмотрены: компактная папка с1товаром, единые Close/buttons, сумма3 BYN при manual2.50+milk0.50; перекрытий в показанных состояниях нет. Снимки на1600×1125/200dpi, viewport1280×900dp. Это второй последовательный успешный полный run с новой AVD конфигурацией (после40172190371); Quickstep recovery в обоих логах не вызывалась. Причина прошлых ANR окончательно не доказана, системные ошибки не подавлялись. Физическая font-scale/orientation/планшетная приёмка отложена пользователем;4колонки/прочие размеры не объявляются device-verified по одному screenshot.

02c gate закрыт,02d UI в указанном scope ci-verified. SQLschema5/payload2, main/stable/key не менялись. Следующая часть W01: persistent fixed slots и drag с atomic CAS/replay/rollback, пустые места и доступная альтернатива перемещению. Большой каталог/performance/Coil и physical acceptance остаются открытыми. Полный02 не закрыт,03 не начинать автоматически.
