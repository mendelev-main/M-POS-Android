# 01a — реализация каталога,2026-10-24

Авторизована пользователем после выбораBYN/direct stock/no reason и дизайнаWeb. Scope: simple/category01a, Room first schema, commandId/fingerprint/revision, stock opening/adjustment ledger, owner mode, draft recovery, shared UI components/theme/Manrope и пользовательская иконка. Recipe/units expansion/modifiers/photo01b/01c и checkout02/03 не входят.

Room2.8.4/KSP2.2.21-2.0.4 выбраны по официальным release notes для закреплённой Kotlin2.2.21; зависимости проверяет CI. SDK локально отсутствует; Android tests/lint/compile выполняет Actions. Legacy PNG1254x1254 сохранён без изменения; adaptive foreground вручную перенесён в vector с чёрным фоном, Android задаёт mask. Key/package неизменны.

Новые проверки: parser/precision/fingerprint JVM; actual Room rollback, duplicate/conflict/stock/category constraints/concurrent updates/reopen/draft; Compose создать/stockedit/recreate/shared-close. Сначала результатCI, затем parity/release.


Итог01a: run40085597352/commitd74444c — checks/API28/API35/sign/publish success, stable0.1.16.1/code2601.11 JVM+4Python,5 instrumentation накаждомAPI плюс explicit UI setup/external update. Room product/stock8 пережили заменуAPK. FontVariation.weight задан явно после visual QA, onPrimaryContainer исправлен; screenshot review прошёл. Иконка отрисованаAndroid чётко. Постоянный key/package сохранены. Полный01b/01c/device/visual parity остаются следующими критериями; manual category order ещё planned.


## 01b — первый шаг в develop

RecipeCalculator — чистый domain без Android и записи stock. Компоненты в физической единице, Web batch qty не переносить напрямую. Precision: рациональные intermediates, агрегирование перед scale12 HALF_EVEN; zero underflow reject. Детали/оставшиеся зависимости в docs/CATALOG-01B.md. Следующий шаг — schema2 migration1→2 и commands, затем UI.


## 01b — persistence и UI

Schema2/MIGRATION_1_2 без destructive fallback; old products piece/default flags. Composite без balance; рецепт хранится FK rows, qty физические. Read editor snapshot атомарен; save validates all graph roots. Тип/base существующего товара фиксированы. Display conversion нормирует stock/minimum/cost/pack, price base unchanged. purchaseLabel — unit code (pack/box/bottle/compatible unit), packSize base; отдельный content display не сохраняется (UI uses current display). Composite cost не читать из products.cost, calculator разворачивает simple costs. Legacy default fingerprint сохранён для replay результатов01a.

Added CatalogConfigurationTest + CatalogConfigurationUiTest; schema2 взята из KSP artifact первого CI. Run40094594582 checks passed;35 found2 fixture defects (display piece with kg; missing optional indices), corrected6acfe83. Repeat40094842882 pending; не повышать verification до результата. Физическая UI/font scale pending;01c/remaining01 ещё впереди.


Итог01b: run40094842882/6acfe83 checks/API28/API35 success.19 JVM,4 Python,10 instrumentation/API + updater CATALOG UPDATE PASSED/UPDATE E2E PASSED. Migration1→2 valid against Room2; schema2 exported committed. Signing/publish skipped, main/stable не менялись. После проверенного commit уточнён текст поля цены (BYN за base unit), полного повторного CI ради подписи не было. Следующий предусмотренный этап внутри01 —01c modifiers/photo, затем category order/remaining acceptance;02 ещё не начинать автоматически.


## 01c — modifiers/photo

ScopeP06/P08/P09. Pure ModifierRules validate raw limits, signed delta/cardinality/final nonnegative price; configureModifiers feeds base+selected into RecipeCalculator (no sale). Schema3 + migration2→3 (chain1→2→3); FK group/option refs; fingerprints defaults backward compatible. Photo immutable staged/normalizedJPEG private file before draft/Product reference;16MiB/40MP→1200px/600000bytes, codecsJPEG/PNG/WebP/HEIF. Session-bound GetContent callback, old photo remains until Save, cleanup referenced product+draft protected/age24h. No photo upload. Details docs/CATALOG-01C.md.

Implemented tests: ModifierRulesTest4, ProductExtrasTest4, CatalogExtrasUiTest1; old schema1 migration test traverses both migrations. First CI40096819346 on682ec0c pending. Subsequent source parity refinement (JPEG geometry retries) still to verify. Physical picker/providers and finalUI pending; don't start02.


01c validation follow-up:40097122743 checks pass,media/Room/migrations pass28/35; UI fixtures required MIME/category on picker monitor and IME synchronization, suites now isolate unfinished editors.5b13d8d also persists generated legacy sessionId before editor is enabled; CatalogPhotoSessionTest covers restored callback queue/new ViewModel lifetime. Current run40097725989 pending. No main/release changes.


Run40097725989/5b13d8d:16 instrumentation passed eachAPI,API28 full updater E2E passed. API35 explicit UI reseed (after UTP removes target) exposed missing wait after async editor open. eb40b22 adds waitEditor for every Add/Edit, no assertions removed. Current40098187895 pending. Captured extras editor28 reviewed; physical parity pending.


Итог01c: eb40b22/[run40098187895](https://github.com/mendelev-main/M-POS-Android/actions/runs/40098187895) — checks/API28/API35 success,23JVM/4Python/16instrumentation/API. Затем2explicit UI tests (Extras+Launch) и updater package replacement на каждомAPI: CATALOG UPDATE PASSED/UPDATE E2E PASSED. KSPschema3 совпадает с Git; screenshots28/35 reviewed. После этого только документация. Native bounded image adapter/GetContent сейчас; Coil/PickVisualMedia target позднее (ADR-006); не считать их установленными. Main/stable/key не менялись. Остаются category order и финальная01/device acceptance;02 не начинать автоматически.


## Android16+ и порядок категорий

Новое требование владельца: minSdk36, только emulator36 вместо28/35. Метаданныеrelease и debug fixture36, config/harness/release verifier синхронизированы. Существующая история CI28/35 остаётся историей. P03 reorder: полная CAS list IDs/revisions, atomic dense sort/repeat/audit, append MAX+1 после holes. Tests CategoryOrderTest3+CategoryOrderUiTest1. До02 после финального audit остаются category color/symbol и device acceptance; не заявлять полный Web parity. Контракты docs/ANDROID-SUPPORT.md и docs/CATALOG-ACCEPTANCE.md. НовыйCI пока pending.


Итог Android16/P03:7cf27d9/run40099706934 checks+emulator36 success,24JVM/4Python/20instrumentation (3orderRoom+1orderCompose).2explicitUI+UpdateE2E passed; signer/archive modern PackageInfoFlags/min36 real APK verified through updater. Новый minSdk36 и матрица36 закреплены, no28/35 jobs. Docs-only после run, main/stable не менялись. Остаток audit:category color/symbol, device/provider/font-scale/low-space before full01.
