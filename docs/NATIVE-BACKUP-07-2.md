# 07.2 — native-экспорт и проверка целостности

Статус: NX01–NX06 implemented / validation pending. Это следующий блок после read-only Web inspection07.1. Восстановление рабочей базы, автоматическая защитная копия перед заменой и Web→native converters ещё не реализованы. B02 roundtrip полного приложения/B04 activation recovery не закрыты.

## Формат и границы

`.mposnative` — ZIP, `format=mpos-native`, version1, schema9; это не Web JSON/.mposbackup13. Первый entry `manifest.json`: version/schema/createdAt и точные name/size/SHA-256 каждого файла. `database.sqlite` — независимый SQLite snapshot; `photos/<photoId>.jpg` — только referenced immutable фото товаров и сохранённого editor draft. Все17 business tables и Room identity сохраняются, включая workspace documents/current/parked/layout/employees/suppliers/receivings/inventory/cash/checkouts/split-progress, shifts/register, receipts/refunds, stock/unit/revisions, operation_results/fingerprints/audit/config. Payloads и SQL types копируются без повторного расчёта/Double. Упаковывать только текущие товары без tombstones/results запрещено.

В копию не входят private preferences/update channel/installer markers, текущая административная сессия и embedded verifier. Админ-доступ не восстанавливается. Сейчас сайт/loyalty/integrations не имеют native-моделей; их Web-конфиденциальные настройки не добавляются в эту копию. Формат не зашифрован, SHA-256 обнаруживает повреждение, но не доказывает автора. Будущий restore обязан отдельно проверить schema/domain/refs/paid state; успешная проверка целостности не является activation token.

## NX01–NX06

- NX01: один source Room transaction копирует schema/таблицы в отдельную SQLite базу с точными NULL/INTEGER/TEXT/BLOB; FLOAT отказывается. Это чтение committed WAL через SQL, не копирование живых db/wal/shm. Только локальный trusted schema SQL используется при создании snapshot; входящий архив не исполняется. Source schema9 и allowlist обязательны. InstallationGate координирует snapshot с installer; admin lock на admission/commit, поздний publish проверяет исходный epoch.
- NX02: фото из products и editor draft скопированы в том же source transaction, который сериализует cleanup. Непривязанные orphan/prepared файлы не экспортируются. Missing/невалидный ID/неполное фото — отказ, не удаление ссылки. JPEG≤600000bytes и≤16Mpx; paths из ZIP не используются для извлечения.
- NX03: bounded archive/strict UTF-8 JSON/no duplicate keys/unknown format/schema отказ. ≤256MiB database,≤512MiB total expanded,≤5000photos,≤1MiB manifest,≤2Msource rows. Input archive≤528MiB. Нет download внешних фото или выполнения вложенных URI. Missing/extra/duplicate entries, wrong sizes/hash/CRC/path traversal отказываются. Только новые private staging directories; finally cleanup, старые interrupted staging старше24h — disposable. Рабочие данные не удаляются.
- NX04: перед export success — повторное extraction, hash, SQLite integrity_check/foreign_key_check/table allowlist/Room identity и проверка всех photo refs. Это structural integrity, не полный domain validator и не замена restore preflight.
- NX05: SAF CreateDocument записывает файл только после private verification; provider файл читается заново и сверяется по length+SHA-256. Ошибка/cancel/revoke/неподдержанный readback не даёт успех. SAF не гарантирует атомарный файл: после отказа выбранный документ может быть частичным; его не удаляем автоматически, поскольку URI может указывать на прежний пользовательский файл. Для durable protective copy07.3 нужен отдельный app-private протокол, одного SAF success недостаточно. Stock/cash/live data не меняются даже при отказе экспорта.
- NX06: общий экран настроек с shared controls, native export/native inspection отдельно от Web inspection; chooser operation kind+original admin epoch. Нельзя передать callback другого chooser или старый callback новой сессии. Busy запрещает параллельный запуск. Raw file URI/path/JSON/PII не сохраняются в state/log/errors/SavedState. Logout сбрасывает counts/result; process death не возобновляет экспорт сам.

## Проверяемый пример

Native заказ100BYN, cash40 (given50/change10), card60 unpaid, открытая смена. Snapshot сохраняет frozen checkout+split-progress/paid indexes/stablepartIDs/revision и command results. Открытие копии новой Room-базой в тесте должно дать те же документы/баланс10.0004/смену/paid4000; replay первой части возвращает прежний результат и не меняет документы. Product photo и отдельное draft-only photo сохраняются byte-for-byte. Проверяется reopen независимой копии; переключение рабочих consumers и полноценный live restore не заявляются.

Faults: captured/archived/verified/written, cancellation, изменившийся источник, provider readback corruption, missing photo, malformed archive, old authorization и late result. Source должен остаться прежним. Реальный disk-full, разные SAF/cloud providers, power-loss и физический планшет — отдельная будущая приёмка.07.3 activation требует RESILIENCE.md и fault/restart на каждом checkpoint.

## Следующий блок

07.3: app-private verified protective copy текущего target (включая active/paid), durable prepared/activate/recovery protocol, повторный preview при target revision/generation change, дополнительное подтверждение активного target.07.4: legacy Web converters, frozen payments, lost acknowledgement и historical unknown coverage.07.5: явное подтверждение unsupported skip и применение. Замена разрешена владельцем; target paid30 и source paid40 — отдельные dataset, не сумма70.
