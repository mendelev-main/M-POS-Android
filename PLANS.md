# План этапов

Порядок задан пользователем: товары → рабочая зона → остальные возможности → настройки сайта/конфигурация. Работа начинается по запросу пользователя на соответствующий этап; закрытие этапа не означает автоматический старт следующего.

| Этап | Содержание | Зависимость | Статус |
|---|---|---|---|
| 00 | Harness, документация, Kotlin bootstrap, Actions, identity/signing | — | ci-verified: checks/emulator/signed APK; device pending |
| 00.1 | Проверка/скачивание APK, системная установка, GitHub Release канал | 00 | ci-verified API 28/35, production 1801→1901; device pending |
| 01 | Товары, категории, фото, единицы, рецептуры, модификаторы | 00 | 01a ci-verified API28/35, signed0.1.16.1; 01b ci-verified API28/35 (run40094842882); 01c ci-verified API28/35 (история); актуальный core/P03 order+color/symbol/schema4 ci-verified Android16/API36, run40101394875; device pending |
| 02 | Рабочая зона, раскладка, папки, корзина, текущие/отложенные чеки | 01 | 02a ci-verified API36/run40106275939; 02b ci-verified API36/run40140796798, 02c ci-verified API36/run40172190371; 02d UI ci-verified API36/run40172821096; 02e slots/drag ci-verified API36/run40177332732; 02f perf/adaptive ci-verified API36/run40184227465; device позже по решению владельца |
| 03 | Минимальная смена03a → оплата, скидки, возврат, история и сохранность | 02 | 03a ci-verified API36/run40206673038; D02 дополнен,03b.1 money ci-verified/run40223239353; 03b.2a checkout inputs ci-verified/run40224718420; 03b.2b atomic sale/history repository ci-verified/run40226577890; 03b.2c manual checkout/history UI ci-verified/run40231561635; 03b.3a split progress repository ci-verified (40234272200 emulator +40273381968 checks); 03b.3b.2 persisted editing ci-verified/run40285964291; 03b.3c sequential UI ci-verified/run40291228647; 03b manual block released0.1.61.1/main40348323683/public upgrade40354353799; refund03c planned; docs/SALE-03B.md |
| 04 | Склад, приёмка, поставщики, закупки, инвентаризация | 03 | planned |
| 05 | Полное управление сменой/кассой, сотрудники/роли, отчёты/аналитика | 03, 04 | planned |
| 06 | Клиенты, согласия, лояльность, зал/бронирования | 05 | planned |
| 07 | Backup/import, фото, проверяемые миграции и восстановление | 01–06 | planned |
| 08 | Принтер/ящик, PDF/XLSX, Telegram, настройки устройства | 05, 07 | planned |
| 09 | Backend/сайт, HTTPS/SSE, online orders/menu/availability, конфигурация | 03–08 | planned |

План21 логического модуля: [MODULES.md](docs/MODULES.md). Порядок/критический путь: [DELIVERY-ROADMAP.md](docs/DELIVERY-ROADMAP.md). Остаток документации: [DOCUMENTATION-PLAN.md](docs/DOCUMENTATION-PLAN.md). Старт —01a; currency каталога BYN подтверждена пользователем.

Документация сценариев: [спецификация](docs/FUNCTIONAL-SPEC.md). Инкременты 01a–01c/02a–02f и контракты описаны отдельно; статус этапа остаётся planned до реализации.

Каждый этап: `harness/build/NN-*.md` содержит границы и критерии; при начале создаётся его context; после проверок обновляются parity и журнал. Внутри этапов допускаются небольшие PR/итерации. Этап 07 — пользовательский перенос Web backup, а не отсрочка обычных Room migrations: migration tests нужны с первого изменения схемы в 01.


03b.3b.1: S04 pure unpaid allocation/stable ID lifecycle implemented; см. [SALE-03B](docs/SALE-03B.md). Persistence editor/совместимость progress1 и подтверждение по ID — следующий03b.3b.2, затем shared UI. Текущий progress API по-прежнему запрещает изменение всей подготовленной матрицы. Проверки новой подчасти фиксируются в build-log; весь03b.3 не закрыт.


03b.3b.1 S04 pure allocation/ID lifecycle ci-verified: [checks40284878361](https://github.com/mendelev-main/M-POS-Android/actions/runs/40284878361)/cb9b4d3, job121446079712 —65JVM/0failures/0errors/0skipped (9 новых SplitEditor),6Python,lintDebug/assembleDebug passed. Эмулятор не перезапускался: редактор пока не подключён к Room/UI; это не evidence сохранённого редактирования частичной оплаты. Следующий03b.3b.2 —transactional integration/legacy replay, затем UI.


03b.3b.2 S01/S02/S04/S05 persisted split editor scope ci-verified: [full CI40285964291](https://github.com/mendelev-main/M-POS-Android/actions/runs/40285964291)/cdf1f11. Checks121449577327:65JVM/0failures,6Python/preflight/lint/assembleDebug. Emulator121449577457/API36:70instrumentation/0failures/0skipped, SplitPaymentRepositoryTest8cases (5 новых); затем прежние2scale/6UIseed/financialseed+verify и actualdebugAPK1→2 passed. Legacy→2 aliases/reopen/replay-after-edit/final, paid snapshot freeze, cash tender/null refusal, retiredIDs, rollback/SQLite abort/races/zero/corruptformat проверены. Сохранённый редактор ещё не подключён к UI; activepartial APK replacement и physical pending. Actualupdater проверял committedfinancialfixture, не активную раздельную оплату. Следующий03b.3c —shared UI отдельных подтверждений/initial incomplete draft/pendingCash/recovery. Не полный03b и не переход к03c refund; main/stable0.1.46.1 прежние.


03b.3c S01/S02/S04/S05 sequential UI scope ci-verified: [full CI40291228647](https://github.com/mendelev-main/M-POS-Android/actions/runs/40291228647)/1bca4dd (app runtime31df7fe, последующие изменения только updater-driver/docs/Python regressions). Checks121466514672:65JVM/0failures/0errors/0skipped,8Python/preflight/lint/assembleDebug. Emulator121466514357/API36:73instrumentation/0failures/0skipped; SaleViewModelTest2/SplitSaleUiTest1/SplitUpgradeTest1 passed. Затем2scale/7UIseed/2financial seed+verify, actualdebugAPK1→2 SUCCESS: SPLIT/SALE/CATALOG/WORKSPACE/PARKED/HOME/SHIFT UPDATE PASSED. Partial fixture2 сохраняет confirmed IDs/remaining/null given/tender/replay, main in-app install при partial по-прежнему запрещён. Final split-confirm/progress1600×1125 reviewed: shared Close/Primary, change1, paid5/remaining5/pendingCash5/settled0, paid actions disabled, no clipping в показанных состояниях. Whole03/refund/physical/pairedWeb/font/dark/cold-force-kill timing pending. Следующий checkpoint —сверка03b и выпуск готового блока перед03c refund.
