# 05c.1 — ядро отчёта продаж и возвратов

Статус: implemented, локальные проверки/CI выполняются. RPT01–RPT05 не объявлены verified до evidence. Экран аналитики05c.2, прибыль/COGS/товарные группировки05d и экспорт08 сюда не входят. Клиенты/лояльность06 и сайт09 отложены: [TODO](PRODUCT-TODO.md).

## Источник и утверждённые примеры

Pinned Web1682c883: Web/js/features/analytics.js analyticsRange/analyticsOrders/analyticsData; regression source-product-stock.test.cjs «analytics keeps local payment, cost and grouping rules within the selected period» и «loyalty analytics failure stays isolated from local receipt analytics». Android admin adapter меняет presentation/admin panel, отдельного override analyticsData в android adapters не найдено. Числа Native берутся из immutable Room receipt/refund snapshots, не из JS runtime.

Владелец2026-10-30/31 подтвердил:

- Продажа100 BYN30октября остаётся +10030октября; полный возврат31октября — −10031октября; общий период0. В Web returnedAt исключает чек из исходного периода — намеренно не переносим.
- Два новых чека по100 и возврат старого на100: продажи200, возвраты100, net100; новых чеков2, средний чек продаж100. Возвраты считаются отдельными документами и не уменьшают saleCount.
- Товары20 + доставка5 = продажи25; доставка отдельным показателем5. Полный возврат25; возврат доставки5 отдельно. Courier cash expense/credit не прибавляется второй раз к продажам/возвратам.

## Read model и расчёт

ReportPeriod принимает включительные даты from/through и явный ZoneId; в instants используется [начало первого дня, начало дня после последнего). Не прибавлять24часа к каждой дате: DST23/25часов учтён. Период с обратным порядком/невозможными границами отклоняется. UI default period/zone появятся в05c.2; ядро не берёт скрыто locale/timezone устройства.

SalesReports.build — pure Kotlin. Событие продажи относится к receipt.createdAt; возврат — refund.createdAt. Исходный чек для возврата читается даже за пределами периода. Никакого фильтра «исключить возвращённую продажу» нет. Event list упорядочен по timestamp/kind/id. Full refund единственный на receiptId; amount/cash/card/delivery и timestamp проверяются против исходной продажи. Partial refund не реализован и не угадывается.

Входные суммы — неотрицательные Long в копейках. Сверяется cash+card=total без Long overflow. Все суммы периода и net — BigInteger копеек; отрицательный net допустим, переполнение не превращается в0. AverageSale = сумма новых продаж / saleCount, HALF_UP до копейки; при0 новых продаж —0. Aggregate totals не сериализуются в старые receipts и не меняют их money policy.

sales/refunds показывают положительные amounts, net=sales−refunds. В каждом наборе total/cash/card/delivery. Net cash — поступления/возвраты оплаты, а не остаток денежного ящика: opening/deposit/withdrawal/courier cash учитываются отдельным кассовым контрактом. Полученная купюра и сдача не становятся выручкой; берутся сохранённые net allocations. Доставка уже входит в total и не добавляется повторно.

GeneratedAt — metadata времени формирования, policyVersion1/currencyBYN. Условия периода определяют included events; generatedAt не является фильтром, скрывающим документы. Согласованный cutoff — одна read transaction Room, не второе чтение при расчёте/будущем экспорте.

## Чтение и права

SalesReportRepository.snapshot читает receipts/refunds внутри adminTransaction: авторизация и Room snapshot согласованы; старый coroutine permit после logout/relogin отказывается. Это полный денежный snapshot только для активной admin session. Cashier-facing агрегаты/проценты ещё не подключены, политика UI05c.2 будет отдельной. Не выдавать полный объект кассиру с сокрытием цифр только в Compose.

Чтение не вызывает initialize и ничего не сохраняет: config/results/audit/stock/business documents прежние. Сопоставляются entity суммы с ReceiptCodec snapshot, refund.receiptHash с исходным receipt payload; неизвестные форматы/повреждённые связи/суммы отклоняются без сброса данных/ложных нулей. Проверяется весь прочитанный набор, включая ссылки вне выбранного периода; повреждение не маскируется фильтром. Текущие product price/cost/category/name и сеть не участвуют. SQLschema8 прежняя, новые таблицы/миграции не нужны.

Текущее чтение загружает всю локальную историю ради проверки связей; производительность большой истории не измерена. Оптимизация range/index и source receipts lookup возможна после измерений с сохранением consistency/validation. Никакого обещания количества чеков/миллисекунд нет.

## Приёмка

- RPT01: half-open диапазон, явная зона, переходы DST23/25, обратный диапазон отклонён.
- RPT02: вчерашняя продажа/сегодняшний возврат/общий0; negative net; live catalog changes/reopen не меняют исторические суммы.
- RPT03: cash/card без сдачи; доставка внутри total и отдельно; refunds отдельно; average по новым продажам, HALF_UP/empty0.
- RPT04: суммы больше Long сохраняются точно; duplicate/missing/mismatched/ранний refund и corrupt amounts не дают отчёт из ложных0.
- RPT05: одна authorized Room transaction, locked/stale permit отказ; read даже пустой БД не создаёт config; полное сравнение таблиц после success/error/reopen.

8 JVM SalesReportsTest и3 API36 SalesReportRepositoryTest. Новое ядро ещё не связано с main navigation, поэтому новый визуальный результат/production report export не заявляется. Следующий инкремент05c.2 — ViewModel/shared UI старой аналитики с утверждёнными метриками; остальные будущие показатели не показывать как готовые.
