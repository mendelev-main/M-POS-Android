# Карта функционального переноса

Подробные требования: [функциональная спецификация](FUNCTIONAL-SPEC.md), [сценарии 01/02](CATALOG-WORKSPACE-SPEC.md), [операции 03–08](OPERATIONS-SPEC.md), [настройки/сайт](SETTINGS-SITE-SPEC.md).

Сквозные системные case IDs SYS/RES/ACC уточняют существующие acceptance: [SYSTEM-ACCEPTANCE.md](SYSTEM-ACCEPTANCE.md). Их описание не повышает статус этапов.

Инкремент01a реализован и проверяется отдельно; полный этап01 и остальные области ещё **planned**. Bootstrap не закрывает продуктовый acceptance. Для каждого ID менять статус только с evidence. Детали P/W — в `docs/PRODUCTS-WORKSPACE.md`; остальные acceptance IDs — в соответствующих планах.

| Acceptance IDs | Web features / adapters | Этап | Статус |
|---|---|---|---|
| P01–P10 | product-catalog/configuration/persistence/categories, product-photo | 01 | 01a частично ci-verified: simple/category/stock/draft; 01b/01c planned; evidence ниже |
| W01–W07 | pos.html, cart-composition/presentation, receipts, android-pos-stock | 02 | planned |
| S01–S05 | payment, receipts, android-safety, stockConsumption | 03 | planned |
| I01–I04 | inventory, receiving-ui/drafts, purchase-orders, suppliers, warehouse-reporting, android-inventory | 04 | planned |
| H01–H04 | shifts, employees, analytics, administration guards | 05 | planned |
| C01–C05 | loyalty, privacy, hall-bookings, customer adapters | 06 | planned |
| B01–B05 | storage, backup/photo native boundaries | 07 | planned |
| D01–D05 | network-printer, cash drawer, native report/Telegram | 08 | planned |
| N01–N06 | web-orders, availability, delivery-tariffs, HTTPS/SSE native boundaries | 09 | planned |

В строке после реализации указывать commit/Actions URL и остающиеся device cases. Для частично завершённой области перечислять точные IDs; не присваивать процент готовности всему приложению по числу файлов или экранов.

## Инфраструктура обновлений (00.1)

U01–U06: [план](../harness/build/00.1-updates.md), [механизм](UPDATES.md). Native-only возможность, продуктовые P/W acceptance не изменяет. Статус ci-verified: [run 40071871635](https://github.com/mendelev-main/M-POS-Android/actions/runs/40071871635) — debug self-update API 28/35 и signed stable release; [run 40075276435](https://github.com/mendelev-main/M-POS-Android/actions/runs/40075276435) — production 1801→1901 через публичный HTTPS на API 28/35, permission UI, Cancel/retry и сохранённые настройки. Device acceptance pending; evidence в build-log.


## Инкремент01a

[CATALOG-01A.md](CATALOG-01A.md) отделяет реализованные частиP01/P02/P03/P09/P10 от полного каталога. Простые товары/category/stock save, constraints/rename/delete guard, actual rollback/repeat/concurrent revision/reopen и Compose create/category/recreate/stock подтверждены [run40083703323](https://github.com/mendelev-main/M-POS-Android/actions/runs/40083703323), commit19e9345, API28/35; оба сохранили Room product/stock8 через APK replacement. Итоговый [run40085597352](https://github.com/mendelev-main/M-POS-Android/actions/runs/40085597352), commitd74444c, прошёл все gates и выпустил0.1.16.1; visual corrections просмотрены.

P04/P05/P06/P07/P08, ручной порядок категорий, recipe/modifier usages/delete archive и дальнейшие schema migrations не считаются закрытыми. Общие UI базовые компоненты реализованы, полный DS/physical parity pending.


## Начало01b

P04/P05/P07: реализована часть чистого домена в RecipeCalculator; единицы/yield/вложенность/flags/дефицит/cost. [Контракт и ограничения](CATALOG-01B.md). Room migration/UI ещё pending; полные IDs не закрыты.


## 01b — Room/редактор

P04/P05/P07: units/base/display, minimum/packaging, nested recipes/yield/cost, simple stock flags подключены к Room schema2/редактору; migration1→2 и новые Room/Compose tests implemented. P09 дополнен FK/delete guard ингредиента; P10 — expanded draft/reopen/migration fixture. Полные acceptance ещё требуют CI и device evidence; [контракт01b](CATALOG-01B.md), точные результаты в build-log. P06/P08, ручной порядок категорий и physical parity не закрыты.


01b CI evidence: [run40094842882](https://github.com/mendelev-main/M-POS-Android/actions/runs/40094842882), commit6acfe83 — checks/API28/API35 success.19 JVM,4 Python,10 instrumentation на каждом API и updater E2E с реальным package replacement. Реализованные части P04/P05/P07 (domain/configuration/editor), P09(recipe delete guard), P10(migration/expanded draft/rollback) — ci-verified. Физическая приёмка, P06/P08 и остальные незакрытые критерии01 остаются pending. После run менялась подпись единицы в поле цены, не бизнес-логика; точное ограничение evidence в CATALOG-01B/build-log.


## 01c — ci-verified subsetP06/P08/P09/P10

P06 — groups/options/domain checks/selection preview; P08 — private staged photo/references/editor; P09 — modifier/draft delete guards; P10 — schema2→3/1→3/reopen/fault tests. [Контракт](CATALOG-01C.md). Наличие кода не означает CI/device acceptance; точный run в build-log. Ручной порядок категорий/physical cases ещё pending.


Итог01c: [run40098187895](https://github.com/mendelev-main/M-POS-Android/actions/runs/40098187895), commit eb40b22 — checks/API28/API35 success;23 JVM,4Python,16 instrumentation/API и2 explicit UI setup tests перед внешним updater. Media/DB faults, migration1→3/2→3, stable IDs/draft/session/photo checks passed; оба API: CATALOG UPDATE PASSED и UPDATE E2E PASSED. Schema3 export сверён, screenshots28/35 просмотрены. Новые runtime/test изменения после проверенного commit не вносились. Физический picker/provider/HEIC/font scale и ручной порядок категорий остаются pending; весь01 не закрыт.
