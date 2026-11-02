# 07 — native backup и совместимый Web-перенос

Статус: начат. Первый инкремент07.1 — WP01–WP06: bounded read/структурная проверка/фото/preview; реализован, validation pending. Это **не восстановление**, не доказательство B02 roundtrip и не готовый импорт в Room. Рабочие данные из preview не меняются; кнопки применения ещё нет.

## Решения владельца2026-11-01

1. Web import — полная замена, после проверки, защитной native-копии и явного подтверждения; без merge кассовых историй.
2. Открытые смены и частично оплаченные чеки требуется переносить. Чек100BYN, paid cash40/given50/change10 + unpaid card60 → сохранить paid40 и remaining60; нельзя снимать paid или повторно принимать деньги.
3. Модули06/09, M17 и неподключённые интеграции не активируются; preview явно показывает неподдерживаемое, пропуск только после подтверждения, исходный файл неизменён. Это не разрешение потерять сведения о локально уже проведённой продаже под видом «сетевого outbox».

## Форматы и происхождение

Pinned Web1682c88334e6fb13586ce4b530157c59ca062e43: backup.js completeBackupData/validateBackupData/applyBackupData и Kotlin backup/BackupManager.kt. `.mposbackup` Android — **JSON**, не ZIP: `productImages` = ID→Base64, `imageCount`; browser JSON может иметь inline `image` data URL или внешнюю ссылку. Android bridge source перед публикацией staging переписывает localImageId, а при отсутствии фото удаляет ссылку — это молчаливое поведение native не повторяет.

Версии1–13 распознаются по source profiles: core products/employees/shifts/orders; v12 printerSettings; v13 полный набор, nullable receivingDraft/inventoryDraft, receiptLabelTemplates optional. Legacy отсутствие optional секций не означает «взять текущие настройки native», особенно в replace-mode. Структурно прочитанная версия не означает поддержку всех бизнес-преобразований. Старый файл без currentOrderSession не доказывает отсутствие оплаченной части.

Тесты source-product-stock.test.cjs: backup export/import stockConsumption/legacy receipt, malformed/incomplete core/optional, rollback/restart, v13 full sections/printer/TTN/navigation, image bridge, exclusive safety lease2037 и inventory baseline invalidation2181. Android-safety.js запрещает финансовые изменения во время backup-import; privacy sanitization/source native bridge также изучены. Новая реализация не исполняет JS.

`node scripts/fixtures/generate-web-backup.cjs` создаёт synthetic export через **реальный completeBackupData** с проверкой SHA pinned source. Без production данных, ключей или клиентов. Fixture в src/test/resources/backup общий для JVM/androidTest, не target APK. Stub session содержит открытый заказ100/paid40; это fixture формы экспорта, не ещё один JS business runtime. Проверка на реальном пользовательском файле позже выполняется локально, без отправки его содержимого в сеть/логи.

## WP01–WP06 текущего инкремента

- WP01: вход max32MiB UTF-8/BOM, строгий JSON, duplicate decoded keys/trailing data/comments/invalid numbers/UTF-8 отказываются; depth64,400000nodes,100000array items,8MiB string, bounded decimal exponent. Одинаковая Kotlin grammar Android/JVM, без Double и lenient Android JSONTokener.
- WP02: version/core IDs/full sections/v12 printerSettings shape; неизвестные секции считаются, не исполняются. Unknown version/error не заменяет текущие данные.
- WP03: localImageId должен разрешаться в productImages; base64 strict с ASCII whitespace, ≤2_000_000B/photo, ≤20MiB total, Android bounds≤16Mpx и JPEG/PNG/WebP. Inline фото учитываются отдельно; URLs никогда автоматически не скачиваются. Проверка не создаёт active/staged photo файлы и не удаляет прежние.
- WP04: preview считает source open shifts/current+parked paymentDraft v1, exact cents/paid flags/given/change. Total parts должен совпасть с сохранённым totalCents; отсутствие paid/невалидная сдача/скрытая третья копейка не исправляются округлением. Сверка totalCents с составом заказа — следующий converter gate, не выполнена здесь.
- WP05: только admin, существующий epoch guard; chooser ticket нельзя использовать после logout/login, поздний IO не публикует суммы, process death не восстанавливает raw файл/URI/token в SavedState. Preview — только sanitized counts и digest, не payload/имена/контакты/ключи. Ошибки не содержат источник/URI/секреты.
- WP06: native settings→backup→SAF OpenDocument→preview/read failure; UI явно говорит, что восстановление ещё не подключено. Структурное чтение не даёт activation token, не является «готов к восстановлению» и не выполняет оплат/stock/Room writes. Нативный export/restore/B02–B04 остаются следующими инкрементами.

## Матрица обязательных преобразований (ещё не реализованы)

| Данные Web | Правило native-переноса / риск |
|---|---|
| Products/categories/layout/folders | ID mapping, units/display/pack conversion, modifier/recipe DAG, фото mapping; не применять source.stock через приёмку повторно |
| Current stock и история | Source.stock уже включает совершённые операции. Нужен import baseline/provenance/coverage; не переигрывать старые receiving/sale/refund как новые движения и не выдавать отсутствующий ledger за доказанную историю |
| Employees/shifts | Web role employee→native cashier, admin отдельно. Сохранить historical actor/name, closed counted/expected/difference и source IDs. Active imported actor не заменяется текущим администратором |
| Orders/refunds | Web не содержит обязательный native receipt graph/command snapshot для каждого legacy чека. Нужен отдельный legacy representation; нельзя синтезировать историческую себестоимость/рецептуру по нынешнему товару. Сохранённый stockConsumption/return timestamps/allocations использовать как факты с coverage |
| Partial payments | Frozen total/paid/given/change, stable part IDs, remainder and original prices. Не clear/refresh/recharge paid parts. Открытая смена и current/parked session входят в проверку атомарного activation |
| Lost acknowledgement | Сверить current paid draft с уже завершёнными orders и операционными идентичностями: закрытая продажа не проводится второй раз. Unknown result требует разрешения, не автоматической повторной оплаты |
| Receivings/purchase/inventory | TTN/grouped+legacy single-line invoices, exact original amounts, supplier links, received order terminal state; applied inventory fixes сохраняются, непроведённый count теряет authority и требует нового baseline |
| Customer/order metadata | Данные получателя доставки и применённая скидка — часть существующего заказа/чека, их нельзя удалять из-за TODO06. Loyalty program/remote identifiers остаются неактивными, но финансовая скидка не исчезает |
| Hall/booking / online links | Не активировать исключённые/отложенные модули. Обычный orderLabel и завершённая локальная продажа сохраняются; не отправлять ACK/API автоматически |
| Integration secrets/outbox | Web export содержит deviceKey/botToken. Не логировать, не показывать, не включать в обычный native export автоматически. Разделять локальные факт/идемпотентность и задания внешнего транспорта; никаких auto print/upload/Telegram/loyalty retries |
| Native formats | Web version13 ≠ Room schema9 ≠ native format version. Native roundtrip должен сохранять exact command/results/revisions и photos, а не пересобирать их из Web JSON |

## Следующая последовательность

07.2 native snapshot format/export + validators/checksums/photos;07.3 native restore staging/activation/recovery/защитная копия;07.4 Web converters/legacy data/payment reconciliation;07.5 Web preview mapped totals/explicit unsupported confirmation и activation. Потребуются fixture каждой поддерживаемой ветки и fault/restart tests. Решение владельца о replace/source-active принято; политика **текущей целевой** открытой смены при замене ещё закрепляется перед activation. Source original file всегда остаётся неизменным.

Атомарность БД+фото не обещается несколькими rename. До активации выбрать durable протокол по RESILIENCE.md, доказать old-or-new dataset на каждом checkpoint. Existing InstallationGate/update gate и admin revoke не заменяют durable restore marker. До этого шага live replacement отсутствует.


2026-11-02: дополнительная source проверка privacy.js/privacySanitizeBackup: Web перед restore запрашивает backend правила удалённых контактов, а paid split с loyaltyRedemptions>0 вообще требует отдельного восстановления, поскольку сброс подарка меняет уже принятую сумму. Native07 не должен отправлять deviceKey из файла/вызывать source backend автоматически или сбрасывать оплаченный discount. Перед mapped activation нужны отдельные правила PII/withdrawn contact data и frozen monetary entitlement;06/09 TODO не дают права считать такие данные восстановленными без проверки. Текущий07.1 только локальный read-only preview и не исполняет privacy/backend callbacks.
