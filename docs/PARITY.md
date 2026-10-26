# Карта функционального переноса

Подробные требования: [функциональная спецификация](FUNCTIONAL-SPEC.md), [сценарии 01/02](CATALOG-WORKSPACE-SPEC.md), [операции 03–08](OPERATIONS-SPEC.md), [настройки/сайт](SETTINGS-SITE-SPEC.md).

Сквозные системные case IDs SYS/RES/ACC уточняют существующие acceptance: [SYSTEM-ACCEPTANCE.md](SYSTEM-ACCEPTANCE.md). Их описание не повышает статус этапов.

Инкременты01a–01c/02a–02f имеют отдельные CI evidence ниже; полный physical acceptance01/02 не закрыт, последующие runtime этапы planned. Bootstrap не закрывает продуктовый acceptance. Для каждого ID менять статус только с evidence. Детали P/W — в `docs/PRODUCTS-WORKSPACE.md`; остальные acceptance IDs — в соответствующих планах.

| Acceptance IDs | Web features / adapters | Этап | Статус |
|---|---|---|---|
| P01–P10 | product-catalog/configuration/persistence/categories, product-photo | 01 | Core01a/01b/01c, P03 order/appearance/schema4 ci-verified API36 (run40101394875); device/full01 pending; evidence ниже |
| W01–W07 | pos.html, cart-composition/presentation, receipts, android-pos-stock | 02 | 02a ci-verified API36/run40106275939; 02b ci-verified API36/run40140796798; 02c revalidation ci-verified API36/run40172190371; 02d UI ci-verified API36/run40172821096; 02e slots/drag ci-verified API36/run40177332732; 02f perf/adaptive ci-verified API36/run40184227465; full W06/W07 не закрыты, device отложен владельцем |
| S01–S05 | payment, receipts, android-safety, stockConsumption | 03 | S01/S02/S04/S05 initial manual checkout/history scope ci-verified/run40231561635; sequential split ci-verified/run40291228647; full03/S03 planned |
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

Перед03a выполнена [сверка01/02](PHASE-02-REVIEW.md); device критерии остаются pending. Подготовлены SHIFT-03A/MONEY-03, подтверждённая часть D02 не означает реализацию S01–S05.

Выпуск01b–02f: PR1/merge87f57ae, main run40198464669 checks/emulator/signing/publish success, stable0.1.41.1/code5101/minSdk36. U01–U06 public channel [run40199534929](https://github.com/mendelev-main/M-POS-Android/actions/runs/40199534929) ci-verified API36: production2601→5101,Cancel/retry/restored download/receipt/settings. Physical остаётся pending.

03a H01/H02 minimal:open/close/carry/revision/atomic result/audit/installation boundary,Room6/shared UI — ci-verified API36/run40206673038. [Scope](SHIFT-03A.md). S01–S05/employee roles/полные отчёты05 не реализованы.

03a evidence [run40206673038](https://github.com/mendelev-main/M-POS-Android/actions/runs/40206673038)/fc7cde5:50JVM/52instrumentation+2scale+5UIseed,6Python/lint/build, migrations1–5→6, actual updater сохраняет active shiftId/opening140BYN и прежний catalog/current/parked/home. Closed/carry screenshot reviewed. H01/H02 только минимальный scope;MG02(sale+close)/MG03(refund) и S01–S05 planned, physical later.

03b.1 S04 pure money: line HALF_UP/fixed-per-unit/cash-change/equal split/delivery cash-out/overflow ci-verified/run40223239353. [Контракт](SALE-03B.md). Нет checkout/sale/history runtime; S01/S02/S05 не закрыты.

03a release0.1.46.1/main9ab4d4f:40214423719 success; U public channel [40223234154](https://github.com/mendelev-main/M-POS-Android/actions/runs/40223234154) API36 production5101→5601/Cancel/retry/receipt/settings passed.03b.1 [40223239353](https://github.com/mendelev-main/M-POS-Android/actions/runs/40223239353)/91acd06:55JVM/6Python/lint/build, S04 pure только. S01/S02/S05/checkout planned; physical позже.

03b.2a checkout inputs only: [CI40224718420](https://github.com/mendelev-main/M-POS-Android/actions/runs/40224718420)/c753666 passed55JVM/53instrumentation/6Python/lint/build + actual updater. CheckoutRepository сохраняет подготовленные вводы/revisions/tombstone и выдерживает rollback/concurrent save/reopen/stale cart/closed shift/installer gate/explicit free delivery. Это не S01/S02/S05: receipt/stock commit/shift totals/history/UI ещё planned. Данные нового checkout проверены reopen отдельной БД; actual updater проверяет прежние catalog/current/parked/home/shift fixtures, а не новый checkout. Physical позже.

03b.2b S01/S02/S05 repository scope ci-verified [40226577890](https://github.com/mendelev-main/M-POS-Android/actions/runs/40226577890)/bb40ae2:56JVM/60instrumentation/6Python/lint/build; atomic receipt+stock+shift+current+checkout+result/audit,9fault boundaries/SQLite abort/replay/reopen/close race/installer gate/exact recipe flags/parked reserves/delivery. Actual debug1→2 dedicated financial DB сохраняет receipt/ledger/result/shiftcash/cleared current/tombstone и replay idempotency; прежниеUI fixtures такжеpassed. Schema7 из CI export. Это не UI оплаты/истории и не S03; next03b.2c, physical позже.

03b.2c S01/S02/S04/S05 initial manual checkout/history UI scope ci-verified [40231561635](https://github.com/mendelev-main/M-POS-Android/actions/runs/40231561635)/e23f3c5:56JVM/62instrumentation/6Python/lint/build +2scale/6UIseed/financial seed+verify actualdebugAPK1→2 passed. Cash UI refusal/prepare/recreate/same command/commit/restore/result reopen from empty workspace/ack/history rename, card delivery+SQLite retry/mixed discount mapping verified. Shared modal screenshots reviewed1600×1125. Не весь03b: sequential paid parts/full pairedWeb/physical/S03 pending. Actual finance upgrade —dedicated app-private DB; production signed upgrade вdevelop не запускался.

03b.3a S01/S02/S04/S05 paid-part repository scope ci-verified: [API36emulator40234272200](https://github.com/mendelev-main/M-POS-Android/actions/runs/40234272200/job/121292369386)65instrumentation/0failures и[checks40273381968](https://github.com/mendelev-main/M-POS-Android/actions/runs/40273381968)/747f83356JVM/6Python/lint/build passed. Wholefirst run failed lint indentation, whitespace исправлен без измененияruntime. Durable paid flags/result/audit/3write rollback+SQLite refusal, frozen command guards/reopen/replay/final10write rollback/zero parts/races verified. No partial UI, unpaidediting/interimcash/activepartial actualupgrade pending; прежнийupdater/6UIseeds passed. Не полный03b.3/physical.


03b.3b.1: S04 pure unpaid allocation/stable ID lifecycle implemented; см. [SALE-03B](SALE-03B.md). Persistence editor/совместимость progress1 и подтверждение по ID — следующий03b.3b.2, затем shared UI. Текущий progress API по-прежнему запрещает изменение всей подготовленной матрицы. Проверки новой подчасти фиксируются в build-log; весь03b.3 не закрыт.


03b.3b.1 S04 pure allocation/ID lifecycle ci-verified: [checks40284878361](https://github.com/mendelev-main/M-POS-Android/actions/runs/40284878361)/cb9b4d3, job121446079712 —65JVM/0failures/0errors/0skipped (9 новых SplitEditor),6Python,lintDebug/assembleDebug passed. Эмулятор не перезапускался: редактор пока не подключён к Room/UI; это не evidence сохранённого редактирования частичной оплаты. Следующий03b.3b.2 —transactional integration/legacy replay, затем UI.


03b.3b.2 S01/S02/S04/S05 persisted split editor scope ci-verified: [full CI40285964291](https://github.com/mendelev-main/M-POS-Android/actions/runs/40285964291)/cdf1f11. Checks121449577327:65JVM/0failures,6Python/preflight/lint/assembleDebug. Emulator121449577457/API36:70instrumentation/0failures/0skipped, SplitPaymentRepositoryTest8cases (5 новых); затем прежние2scale/6UIseed/financialseed+verify и actualdebugAPK1→2 passed. Legacy→2 aliases/reopen/replay-after-edit/final, paid snapshot freeze, cash tender/null refusal, retiredIDs, rollback/SQLite abort/races/zero/corruptformat проверены. Сохранённый редактор ещё не подключён к UI; activepartial APK replacement и physical pending. Actualupdater проверял committedfinancialfixture, не активную раздельную оплату. Следующий03b.3c —shared UI отдельных подтверждений/initial incomplete draft/pendingCash/recovery. Не полный03b и не переход к03c refund; main/stable0.1.46.1 прежние.


03b.3c S01/S02/S04/S05 sequential UI scope ci-verified: [full CI40291228647](https://github.com/mendelev-main/M-POS-Android/actions/runs/40291228647)/1bca4dd (app runtime31df7fe, последующие изменения только updater-driver/docs/Python regressions). Checks121466514672:65JVM/0failures/0errors/0skipped,8Python/preflight/lint/assembleDebug. Emulator121466514357/API36:73instrumentation/0failures/0skipped; SaleViewModelTest2/SplitSaleUiTest1/SplitUpgradeTest1 passed. Затем2scale/7UIseed/2financial seed+verify, actualdebugAPK1→2 SUCCESS: SPLIT/SALE/CATALOG/WORKSPACE/PARKED/HOME/SHIFT UPDATE PASSED. Partial fixture2 сохраняет confirmed IDs/remaining/null given/tender/replay, main in-app install при partial по-прежнему запрещён. Final split-confirm/progress1600×1125 reviewed: shared Close/Primary, change1, paid5/remaining5/pendingCash5/settled0, paid actions disabled, no clipping в показанных состояниях. Whole03/refund/physical/pairedWeb/font/dark/cold-force-kill timing pending. Следующий checkpoint —сверка03b и выпуск готового блока перед03c refund.


03b release checkpoint завершён: PR4/checks40347045936→merge eafff3d, [main40348323683](https://github.com/mendelev-main/M-POS-Android/actions/runs/40348323683) SUCCESS65JVM/73instrumentation/8Python/lint/build +2scale/7UIseed/2financialseed+verify/debug APK1→2, подписывание/publish. Stable [0.1.61.1/code7101](https://github.com/mendelev-main/M-POS-Android/releases/tag/v0.1.61.1), native package/cert/minSdk36 прежние. [Public signed upgrade40354353799](https://github.com/mendelev-main/M-POS-Android/actions/runs/40354353799)/job121682674847 SUCCESS API36 production5601→7101/HTTPS/check/download/restore/permission/Cancel/retry/system install/settings receipt. Develop fast-forward к main, runtime после проверок не менялся. Scope/ограничения PHASE-03B-REVIEW; S03/refund03c следующий, physical позже.
