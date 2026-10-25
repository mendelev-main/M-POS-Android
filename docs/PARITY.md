# Карта функционального переноса

Подробные требования: [функциональная спецификация](FUNCTIONAL-SPEC.md), [сценарии 01/02](CATALOG-WORKSPACE-SPEC.md), [операции 03–08](OPERATIONS-SPEC.md), [настройки/сайт](SETTINGS-SITE-SPEC.md).

Сквозные системные case IDs SYS/RES/ACC уточняют существующие acceptance: [SYSTEM-ACCEPTANCE.md](SYSTEM-ACCEPTANCE.md). Их описание не повышает статус этапов.

Инкремент01a реализован и проверяется отдельно; полный этап01 и остальные области ещё **planned**. Bootstrap не закрывает продуктовый acceptance. Для каждого ID менять статус только с evidence. Детали P/W — в `docs/PRODUCTS-WORKSPACE.md`; остальные acceptance IDs — в соответствующих планах.

| Acceptance IDs | Web features / adapters | Этап | Статус |
|---|---|---|---|
| P01–P10 | product-catalog/configuration/persistence/categories, product-photo | 01 | Core01a/01b/01c, P03 order/appearance/schema4 ci-verified API36 (run40101394875); device/full01 pending; evidence ниже |
| W01–W07 | pos.html, cart-composition/presentation, receipts, android-pos-stock | 02 | 02a ci-verified API36/run40106275939; 02b ci-verified API36/run40140796798; 02c revalidation ci-verified API36/run40172190371; 02d UI ci-verified API36/run40172821096; 02e slots/drag ci-verified API36/run40177332732; full W06/W07 не закрыты, device отложен владельцем |
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


## Текущая Android16 evidence и P03 order

Владелец изменил minimum на16/API36; oldAPI28/35 runs выше исторические. [run40099706934](https://github.com/mendelev-main/M-POS-Android/actions/runs/40099706934), commit7cf27d9 — checks/emulator36 success,24JVM/4Python/20instrumentation. Core01a–01c regressed на новойплатформе; P03 ручной порядок/reopen/replay/conflicts/actual rollback/concurrent winner и Compose move/recreate ci-verified. Update E2E: actual package replacement1→2/private data preserved/stock8. Signing/publish skipped. [Platform](ANDROID-SUPPORT.md), [оставшиеся acceptance](CATALOG-ACCEPTANCE.md): category color/symbol и физические сценарии не закрыты.


## P03 — цвет и обозначение категории

[run40101394875](https://github.com/mendelev-main/M-POS-Android/actions/runs/40101394875), a19cb3e, attempt2: checks и emulator36 success;26JVM/4Python/lint/assembleDebug,24instrumentation +2explicit UI seed; CATALOG UPDATE PASSED/UPDATE E2E PASSED (1→2, stock8/data preserved). Palette10 из pinned Web, Unicode3 graphemes, preview/selected semantics/Saveable form, rename/order/stable product refs, validation/conflict/replay/actual rollback и migrations1→4/2→4/3→4. Native >3 символов даёт ошибку вместо Web silent truncation; [контракт](CATEGORY-APPEARANCE.md). Visual screenshot reviewed; физическая приёмка/full01 остаются pending.


## 02a — рабочая зона и текущая корзина

[run40106275939](https://github.com/mendelev-main/M-POS-Android/actions/runs/40106275939), commit0e6dd0b: checks/emulator36 success;31JVM/4Python/lint/assembleDebug,31instrumentation (6workspace Room+1workspace UI), затем3explicit UI seed tests. CATALOG UPDATE PASSED/WORKSPACE UPDATE PASSED/UPDATE E2E PASSED: package1→2, stock8 и current cart quantity5/total15 сохранены. W01: category/product tiles/search/folders/order CAS; W02: configured merge/manual/qty/remove/clear; W03: current aggregate reserve/shortage/per-simple flags, без parked; W06: durable snapshots/revision stale marker и safe deletion. Ни один полный W диапазон не объявляется закрытым. [Контракт/остаток](WORKSPACE-02A.md): parked02b, параметры заказа, полный revalidation02c, fixed slots/drag и физическая приёмка. Native папка пока использует общий wide dialog вместо Web count-based widths; modifier total preview и точная плотность/адаптивность — дальнейшая UI часть. Screenshots reviewed без перекрытий, pixel-perfect parity не заявляется. Ledger не изменяется, оплаты нет.


## 02b — отложенные заказы и общий резерв

W03: current+parked aggregate reserve и исключение прежнего собственного резерва; W04: atomic park/resume/swap/delete, replay/rollback/concurrency/ABA и защита от задержанных команд другого заказа; W05: подпись, here/takeaway/delivery, комментарий/контакты; W06: durable snapshot/owner, legacy payload migration и parked delete guards — ci-verified в указанном объёме. Полные W диапазоны не закрыты: explicit revalidation02c и остальные UI/device критерии впереди. [Контракт](WORKSPACE-02B.md).

[run40140796798](https://github.com/mendelev-main/M-POS-Android/actions/runs/40140796798), runtime commit091b2f3: checks121011943215 и emulator36 job121011943276 success. 35 JVM tests/0 failures,4Python,lint/assembleDebug;38 instrumentation tests на Android16/API36 и4 explicit UI seed tests перед внешним updater. Реальная установка APK1→2 сохранила stock8, current quantity5/total15 BYN и parked label/type delivery/total5 BYN. Полные metadata отдельно проверены Room reopen и Compose restore, не приписываются внешнему APK assertion. SQLschema5 byte-equal KSP export, migration cart payload1→2 проверена в Room. Signing/publish/production-upgrade skipped на develop.

Просмотрены screenshots order-details/parked-swap/parked-orders: общие Close/buttons/fields/dialogs, читаемые действия без перекрытий в показанных состояниях; форма параметров прокручивается. Полная визуальная/device приёмка не заявляется. После проверенного runtime — только документация. Следующая часть02c: явный пересчёт устаревшего заказа с показом изменений и подтверждением. Fixed slots/drag, count-based folder width, modifier total preview/performance и физическая приёмка остаются открытыми. Main/stable/signing identity не менялись.

## 02c — пересчёт текущего заказа

W03/W06: pure preview нового snapshot/суммы/состава и explicit transactional confirm с owner/revision/token, без изменения склада — implemented; domain/Room/UI checks passed, общий CI gate pending из-за системного ANR эмулятора во внешнем updater. [Контракт и ограничения](WORKSPACE-02C.md). Полный02/device не закрыты.

Финальный2ce31c2: [run40144720147](https://github.com/mendelev-main/M-POS-Android/actions/runs/40144720147),40JVM/5Python/lint/build success,41instrumentation+4explicit UI seed passed; external updater blocked системным android «System UI isn’t responding», общий run failure. Финальный APK replacement не объявляется пройденным. Детали в WORKSPACE-02C/build-log; полный02/device не закрыты.

## 02d — папки и сумма конфигурации

W01/W02 UI: ширины папок по числу колонок, адаптация к фактическому месту, пустая папка и предварительная сумма ручной цены+модификаторов — ci-verified API36/run40172821096. Строгая commit validation сохраняется. [Контракт](WORKSPACE-02D.md). Fixed-slot/drag, performance/physical/full02 остаются открытыми.

02c gate закрыт: [run40172190371](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172190371)/3a9a4d0,40JVM/6Python/41instrumentation+4UIseed, external updater success с current/parked/catalog data preserved. Android36, AVD viewport1280×900dp сохранён при снижении physical resolution. Полный02/device и отдельный02d UI этим run не закрыты.

02d итог: [run40172821096](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172821096)/da8db71,41JVM/6Python/lint/build,41instrumentation+4UIseed и actual updater success; папка/config screenshots reviewed без перекрытий. Точный scope WORKSPACE-02D; fixed slots/drag и full02/device остаются открытыми.

## 02e — slots/drag

W01/W06: настраиваемый home(max20)/пустые места/nearest free, atomic CAS/replay/rollback, category/folder drag Reorder и доступное управление — ci-verified API36/run40177332732. [Контракт/границы](WORKSPACE-02E.md). SQLschema5 без изменений; полный02/performance/device не закрыты.

02e evidence: [run40177332732](https://github.com/mendelev-main/M-POS-Android/actions/runs/40177332732)/d53328e,45JVM/6Python/lint/build,44instrumentation+4explicit UIseed и actual updater success с custom product slot8/current/parked/catalog preserved. Screenshot updated-home reviewed; точные ограничения/remaining scope WORKSPACE-02E. Полный02/performance/physical не закрыты.

## 02f — производительность и адаптивность

W01/W03/W07/DS04: background/lazy availability/search/selection, shared bounded Coil thumbnail cache и row/bottom/toggle под fontScale/viewport — ci-verified API36/run40184227465. [Контракт/метрики/ограничения](WORKSPACE-02F.md). Денежные и stock use cases не меняются, physical/full02 пока не закрыты.

02f evidence: [run40184227465](https://github.com/mendelev-main/M-POS-Android/actions/runs/40184227465)/f1639b8,47JVM/47instrumentation+2scale+4UIseed,lint/build/6Python и actual updater success.2000product fixture:306/1209/595ms load/search/add;150%portrait/landscape reviewed без перекрытий. W01/W03/W07/DS04 только scope WORKSPACE-02F; physical/OEM/full02 отдельно.
