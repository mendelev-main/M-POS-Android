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
