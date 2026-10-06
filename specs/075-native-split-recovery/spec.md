# 075 — нормализация и восстановление смешанной оплаты

## Область и польза

MPosSplitRecoveryEngine выполняет два чистых read-only решения через
splitRecoveryRead: normalize частей и restore paymentDraft. Сохраняет
оплаченные суммы, версию/JSON формы, EPS cashGiven 0.0001/change 0.001 и
округление исходника. Не записывает/резервирует ничего, не запускает
сеть/печать/повтор оплаты. Канонический currentOrderSession по-прежнему Room
(046); повторов финансовой транзакции этот этап не добавляет.

## Нормализация

Paid-сумма сначала складывается, потом округляется в копейки. Неоплаченные
строки в существующем порядке получают max(0, roundedTotal − paidCents),
остаточные копейки первым. Paid строки, object identity, методы, tender,
дополнительные поля/порядок всех строк сохранены. _splitCount становится длиной.
Открытие существующих unpaid частей ждёт решения; при ошибке/изменении
контекста не открывает/не меняет части. Общая FIFO count/amount/normalize;
normalize разделяет группы ввода. Открытие paid draft сохраняет reviewed guard:
показывает существующие части без перераспределения. Первое создание частей
остаётся этапом 072; полный UI/payment shell не переносится здесь.

## Восстановление

Перед возвратом Room currentOrderSession загрузчику готовится native validation
cache. Один дополнительный local read-only расчёт только при наличии draft.
Quote использует сохранённые cart prices/qty/modifiers price, текущие discount
definitions, сохранённые programs/redemptions и delivery: каталог не переоценивает
чек. Source loadAll применяет подтверждённый draft только для точного draft stamp
и совпадающей суммы; остальные startup правила/markStorageBroken сохранены.

Draft version строго numeric 1; totalCents integer >=0; 2–10 частей, минимум одна
paid===true. Methods/cashGiven/change/amount нормализуются как source. Сумма
частей точно равна draft total и корзине. updatedAt использует Number(...) || now,
новое now фиксируется при запросе, данные extra удаляются как исходником.
JSON scalar/one-item array legacy coercions сохранены. Черновик не записывается
назад: raw storage/backup v13 остаются прежними. All-paid восстановление не
завершает чек автоматически; остаётся прежний экран с действием завершения.

## Совместимость и ошибки

Rollback MPosNativeSplitRecoveryEnabled=false возвращает source normalize/open/
validate. Native response проходит protocol checks. При невалидном draft native
возвращает valid=false, source применяет свою прежнюю политику invalid draft.

Bridge/quote/protocol failure, отсутствующий cache, несовпадающий draft/total и
непредставимые timestamps используют **reviewed synchronous validator**. Это
сознательный read-only compatibility путь: нельзя терять paid сведения из-за
ошибки подготовки/таймаута. Ошибка hook не превращает успешное чтение Room в
пустую сессию. Никакого fallback write/изменения критического журнала нет.
Snapshot creation/progress commit и общий startup renderer остаются JS;
их native atomic storage/settlement boundaries не меняются. Полное устранение
startup JS ожидается с нативным экраном/состоянием (103/109).

## Автоматическая проверка

145 source golden cases: 46 normalize, 59 restore/invalid/legacy coercion,
40 сочетаний gift/discount/delivery pricing. Проверяется неизменность input,
точное равенство normalized draft и сохранённых paid полей; отдельный тест
unencodable timestamp оставляет compatibility путь. JS проверяет await открытия,
identity/tender, stale/error/rollback, cache stamp/total, protocol,
подготовку storage read и сохранение payload при ошибке hook. File-backed Room
reopen + correlated native validation сохраняют raw paid draft без записи/repay.

## Физическая приёмка — ожидается

Paid карта 5,01 + unpaid наличные 5,00, заказ 10,01: перезапуск → paid 5,01,
unpaid 5,00; открыть, завершить, один чек/списание. All-paid перед restart →
никакой автоматической повторной оплаты. Unpaid части повторно открыть → ровное
распределение, методы/tender сохранены. Gift/discount/delivery restart; damaged
synthetic draft, unavailable native read → source parity. Проверить время запуска
и реакции открытия/Back на планшете. APK локально не собирается.

## Сохранённые вопросы для будущего бизнес-рефакторинга

Изменённая текущая скидка может поменять итог корзины и заблокировать restore
уже частично оплаченного draft; исходник не хранит версии discount definitions.
All-paid после перезапуска требует явного завершения, не automatic finalize.
Unpaid tender сохраняется при нормализации. Правила оставлены без изменений;
пересмотр требует отдельного бизнес-решения.

## Прогресс

После публикации 075: 74/110 инженерных задач выполнено (67,27%), осталось 36.
Следующая 076 — итоги/preview корзины при всех изменениях. Физическая приёмка
остаётся отдельной незавершённой задачей 110.
