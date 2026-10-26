# 03c — полный возврат чека

Предпосылка выполнена:03b выпущен0.1.61.1/code7101, public production upgrade5601→7101 прошёл40354353799. Рабочая ветка develop, Android16+/API36, BYN. S03 — первый full refund; частичный возврат/loyalty/acquiring не включаются автоматически. Применяются mpos-phase/mpos-domain и pinned Web1682c883.

## Источник и денежные границы

`receipts.js`17–48/68–114: stockConsumption snapshot, предварительная проверка всех складских целей, original cash payment amounts, активная смена/проверка наличности, snapshot products/orders/shifts →commitCriticalStorage →UI/внешние эффекты. Android journal сохраняет операцию; текущий live stock adapter учитывает резервы и не заменяет исторический возврат текущей рецептурой. Source regressions: return uses sold recipe/changed tracking, cash-card-split returns, invalid snapshot/missing target, return journal, precise negative consumption.

Подтверждено владельцем:

- Полный возврат первым, исходная продажа/закрытая смена сохраняются в истории. Вчерашняя продажа10 BYN, возврат сегодня →из сегодняшней кассы10 один раз, прошлую смену не переписывать.
- 2026-10-26: чек100 BYN, cash40/card60 →cash refund40 из текущей кассы, card refund60 вручную через терминал. Нельзя выдавать cashGiven50 вместо исходных cash40 или повторно выдавать сдачу10.

**2026-10-26 подтверждено владельцем:** полный возврат доставки отменяет также оплату курьеру. Товары20+delivery5/card25 →покупателю25 через терминал, cash credit5 в текущую смену. Cash25 →покупателю25 наличными, отмена расхода курьеру+5, net cash−20. Это согласованное отличие от Web, где courier expense остаётся. Возврат означает также возврат выданной курьеру наличности в кассу; приложение не переводит деньги через банк/терминал автоматически.

Обнаруженное отличие Web: `shifts.js` cashDrawerBalance добавляет refundCashMovements обратно к netMovements, компенсируя withdrawal. При cross-shift order из yesterday, opening today20/refund withdrawal10 даёт20, а не утверждённые10. Это воспроизведено вызовом реальной pinned функции в Node VM. Native должен хранить original sale без фильтра returned и вычитать refund.cash в refundShift один раз: same-shift opening+saleCash−refundCash; cross-shift today opening−refundCash. Не копировать компенсацию Web и не вычитать возврат одновременно через фильтрацию sale и отдельную cash operation.

## 03c.1a — исторический складской план

`HistoricalRestock.plan` — чистый Kotlin без Room/UI и без записи возврата. Принимает сохранённые consumption quantities и граф продажи, текущие товары и balances/revisions. Для каждого сохранённого tracked simple ingredient готовит положительный delta, before/after и expected/next revision; выход отсортирован по productId. Изменённый рецепт, стоимость, noStockTracking/allowNegativeStock и display unit не пересчитывают расход. Базовые единица и тип товара в native каталоге неизменяемы; несоответствие исторической/текущей base unit, отсутствие simple target/balance, неверный расход/дубликат graph ID/переполнение revision дают отказ всего плана.

Количество — exact BigDecimal, scale до12 из stock consumption policy, без округления до0.001 и без money scale2. Положительный historical delta ограничен18 integer digits как RecipeCalculator; разбор quantity ограничен128символами, exponent/NaN/запятая/пробелы в storage отклоняются. Текущий отрицательный остаток можно увеличить, не обнуляя его и не применяя сегодняшние запреты отрицательной продажи. Пустой consumption означает отсутствие восстановления stock; из current recipe новый расход не изобретается. Native receipts всегда имеют snapshot; Web legacy fallback будет отдельным предметом импорта07.

Примеры: old consumption0.0004kg, balance9.9996 →10kg, даже если новый рецепт требует10kg и текущий tracking выключен. Historical all-untracked →empty plan, даже если tracking позже включён. Balance−2 +historical0.0004 →−1.9996.1000отдельных корректировок0.000000000001 →0.000000001 без потери точности.6JVM tests покрывают эти сценарии и refusals; CI evidence в build-log. Это кандидат восстановления, а не выполненный refund: replay/SQLite/история/касса/UI остаются следующими шагами.

## Следующая транзакционная часть03c.2

После чистого денежного плана: immutable refund record с UNIQUE receiptId и commandId, связями с original receipt и активной refundShift, original snapshot/hash, cash/card amounts, deliveryCashCredit, historical restored quantities и timestamp. Room migration и fixtures всех поддержанных старых schema обязательны. Оригинальный receipt, его shiftId/number/payment и closed snapshots не менять.

Команда: InstallationGate →Room transaction →operation_result replay →активная смена/owner/revision/preview, no active split →исторический snapshot/stock plan/cash availability →refund+positive stock ledger+balances revisions+register revision+result+audit. Полный план проверяется до первой записи. Нет отдельного duplicate withdrawal: один refund record определяет net cash delta (deliveryCashCredit−cash refund) в сегодняшней смене. Unknown response восстанавливается по commandId; два разных commands на один receipt имеют одного победителя. Возврат инвалидирует stale close/checkout preview и будущий inventory baseline даже если balance вернулся к прежнему числу.

Проверки до UI: same/cross-shift, changed recipe/flags, точные0.0004kg, all-untracked/negative, недостаток cash, полный rollback каждой записи/SQLite abort, concurrent retry/different command/close/sale/installer, reopen/history/receipt immutability, actual APK preservation. Затем shared confirmation/history/refund result UI с ручной карточной частью и явной выдачей наличных. Ни настоящего acquiring, ни печати/Telegram/online вызовов ради теста; configured adapters пока отсутствуют. Физическая приёмка по решению владельца позже.


03c.1a S03 historical stock planning scope ci-verified: [checks40377899857](https://github.com/mendelev-main/M-POS-Android/actions/runs/40377899857)/672951b, job121765882966 SUCCESS71JVM/0failures/0errors/0skipped (6HistoricalRestock tests),8Python/preflight/lint/assembleDebug. Local62supplemental cached-domain/29pinnedsource regressions passed SHA matched. Stock plan не подключён к Room/UI; refund money/atomic record/cash/ledger/replay/restart ещё planned. Delivery example остаётся pending answer; mixed cash40/card60 подтверждён. Stable/main0.1.61.1 без изменений, emulator/подпись/publish не запускались для unused pure planner.


## 03c.1b — денежный план

`RefundMoney.plan` использует только исторические SaleQuote/SalePayment и текущую наличность. Проверяет суммы строк, частей оплаты, сдачу, исходные cash/card и deliveryCashOut, затем возвращает gross customer cash/card refund, отдельный deliveryCashCredit, signed cashDelta и expectedCashAfter. Не возвращает tender/change повторно, не пересчитывает тариф. Admission проверяет итог атомарного изменения: currentCash+deliveryCashCredit−cashRefund≥0. Поэтому cash20 достаточно для отмены cash25 delivery5; требуется фактический возврат курьерских5. Signed delta вычисляется до сложения с текущим cash, переполнение Long отказывается. Нулевой чек допустим.

6JVM сценариев: card/cash/mixed delivery, mixed tender/change, другая смена/нулевой чек, повреждённый snapshot/недостаток/overflow. Это чистый кандидат, не запись возврата и не UI. Следующий03c.2: Room transaction, уникальность, ledger, shift totals, replay/rollback/concurrency/migration; затем UI.


03c.1b S03 money planning scope ci-verified: [checks40379182536](https://github.com/mendelev-main/M-POS-Android/actions/runs/40379182536)/8a2f72f, job121770455891 SUCCESS. Downloaded17JUnit XML:77tests/0failures/0errors/0skipped, включая6RefundMoney;8Python/preflight/lintDebug/assembleDebug passed. Supplemental68domain passed locally; actual local wrapper blocked as recorded. Pure planner unused in Room/UI; emulator/signing/publish не запускались, stable0.1.61.1 прежний. Следующий03c.2 —атомарный refund record/stock/cash/replay/migrations, затем shared UI.


03c.2a S03 repository implemented, CI pending: Room8 immutable refunds (UNIQUE receiptId/commandId, FK RESTRICT receipt/shift), migration7→8/all production/test builders. RefundRepository.preview validates historical receipt, owner/active shift/no partial split, money and exact stock revisions; token includes original payload/current cash/stock plan/register revision. Commit InstallationGate→Room transaction, global command replay before current-state checks, revalidated preview, clock bounds, refund+positive stock ledger+balance revisions+register+result+audit. Receipt/closed source shift unchanged. Shift current cash=opening+all sale cash−delivery expense+refund delivery credit−refund cash; BigInteger exact aggregate avoids ordering-dependent intermediate negatives/overflow, final Long/nonnegative validation retained.

8instrumentation cases cover same/cross-shift delivery/mixed, replay/reopen/closed shift, every6write faults/SQLite audit abort, concurrent same/different commands, stale/tampered stock/money, actor/time/installer refusals, close/sale races, partial split refusal, all-untracked and historical0.0004kg after live recipe/tracking changes, migration1–7→8 with exact stock/receipt preservation. No refund UI or actual persisted-refund APK fixture yet; these remain next03c.2b/03c.3. Local preflight/8Python/diff-check pass; Android wrapper blocked at ~/.gradle/wrapper/...zip.lck as before, actual build/instrumentation delegated to CI. Full S03 not closed.
