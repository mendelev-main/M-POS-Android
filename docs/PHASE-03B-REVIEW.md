# Сверка блока оплат03b перед выпуском

Дата:2026-10-26. Объём — native manual accounting в BYN, Android16+/API36, single local owner. Это checkpoint блока оплат перед refund03c, а не закрытие всего этапа03. Планшетная приёмка отложена владельцем; не считать её выполненной.

Основное evidence: [develop CI40291228647](https://github.com/mendelev-main/M-POS-Android/actions/runs/40291228647), head1bca4dd/app31df7fe.65JVM/8Python/lint/build,73instrumentation/0failures/0skipped,2scale/7UIseed/2financial seed+verify и actual debug APK1→2 passed. История двух отказов updater-driver и исправление с Python regressions — build-log. Подпись/production upgrade в этом develop run не запускались.

| Приёмка | Реализованный и проверенный объём | Evidence |
|---|---|---|
| S04 | net line HALF_UP2, sum rounded lines, одна line скидка percent/fixed-per-unit, отдельная доставка, cash/card/mixed/change, remainder-first2–10/zero, overflow | SaleMoneyTest5, SplitEditorTest9; утверждённые примеры MONEY-03 |
| S01 | atomic receipt/stock ledger/register/current/checkout/progress/result/audit, exact consumption и резервы; никаких внешних эффектов до commit | SaleRepositoryTest6, SplitPaymentRepositoryTest8, migrations доRoom7; fault каждой записи и реальные SQLite refusals |
| S02 | parent sale command/unique session, replay после результата/редактирования/final, stable IDs и legacy aliases, concurrent sale/close/edit/confirm | те же Room tests; один победитель и одно списание |
| S05 | historical snapshots, receipt reopen после rename, prepared/result recovery из пустого workspace, partial/complete progress новым VM, actual APK replacement | SaleUiTest1, SaleViewModelTest2, SplitSaleUiTest1, SaleUpgradeTest1, SplitUpgradeTest1 |
| UI | shared Close/buttons/dialogs/fields, подтверждение cash/manual card, редактирование только unpaid, pendingCash отдельно от settled drawer, запрет выхода/close/install при partial | Compose и Room guards; screenshots split-confirm/progress/прежних modal states просмотрены |

Schema7/checkout1/receipt1/progress2; applicationId `com.mendelev.mpos`, minSdk36 и закреплённый сертификат не меняются. Нативная программа остаётся независимой от Web package. Current/parked/home/catalog/shift fixtures прошли updater; partial finance fixture отдельная БД, не обход main installer guard. При подписанном выпуске main pipeline обязан повторить gates на merge commit и проверить package/version/certificate.

## Явные границы и отличия

- Карта — ручное подтверждение операции на внешнем терминале; acquiring/фискализация/печать/online sync не подключены. Внешние outbox jobs не выдумываются.
- Доставка — explicit manual tariff snapshot; fee выдаётся наличными при final sale, в том числе для card. Настройки тарифов/сайта — последующие модули.
- Наличные draft могут быть не введены; ни equal split, ни admission calculator не сохраняют выдуманный received cash. Подтверждение требует настоящего given и показывает change.
- После last paid требуется «Завершить продажу» с final preview вместо Web auto-finalize. До него complete progress сохранён, receipt/stock/drawer ещё не проведены.
- Предварительные ID до first paid могут перенумероваться при подготовке checkout. После first paid ID/nextId durable, удалённые ID не используются повторно; paid не меняются. Сырые поля ещё не сохранённого окна не обещаются durable.
- Нули подтверждаемы (устранён Web dead end), единственная unpaid часть всегда exact remainder. Примеры и причины — SALE-03B.
- Физическая приёмка/OEM/peripherals, paired Web visuals, checkout font-scale/dark matrix, произвольные process-kill timing windows и двойной отказ outcome read не объявляются verified. Room fault/reopen/VM recreate/API36/debug replacement — фактически выполненный объём.
- S03/refund ещё не реализован. Первый scope уже согласован: полный чек, historical расход, cross-shift наличная выдача сегодня один раз. Loyalty/order stacking/partial refund — отдельные контракты.

## Release checkpoint

Текущая сверка подтверждает готовность03b в указанном manual scope к PRdevelop→main. Порядок: PR checks →merge →main checks/emulator →permanent signing/identity verification →stable publish →production public-channel upgrade →запись версии/evidence и синхронизация develop. Новые refund writes до этого checkpoint не начинаются. Предыдущий stable0.1.46.1 не переименовывать и не перезаписывать его APK.
