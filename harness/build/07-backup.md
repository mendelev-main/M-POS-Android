# Этап 07 — Backup и перенос

Статус: in progress,07.1 WP01–WP06 +07.2 NX01–NX06 +07.3a RA01–RA06 ci-verified (inspection/export/protection/preparation only); после05c/05d, без ожидания отложенных06/09. Scope — реально реализованные модели01–05 и фото.

## Объём

Версионированный экспорт/import Web v13 с preview, фото staging, migration/restore.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/OPERATIONS-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Системные контракты

[Restore activation/миграции](../../docs/RESILIENCE.md), отдельные версии APK/schema/format и сверка pending effects; SYS-09/SYS-12.

## Приёмка

B01: malformed отказ без потери данных; B02: roundtrip; B03: dangling фото; B04: disk-full/interrupt/restart; B05: unknown versions.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Чтение sandbox Web и молчаливое объединение конфликтов.

## Контекст и результат

Значимые решения в `harness/context/07-backup.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.


Текущий контракт [BACKUP-07.md](../../docs/BACKUP-07.md): source формат JSON (также Android .mposbackup), не ZIP. Только structural/photo/payment preview сейчас; native export/restore/Web activation не реализованы. Source active shifts/partials required пользователем.


2026-11-02: 07.1 WP01–WP06 ci-verified в рамках read-only inspection: [FullCI41407482232](https://github.com/mendelev-main/M-POS-Android/actions/runs/41407482232)/abe9e0c SUCCESS.179JVM/251API36/0failures/0skipped,16Python/preflight/lint/debugAPK/testAPK. Actual debugAPK1→2 UPDATE E2E PASSED, app-private data preserved,25 seed UI tests. Strict JSON/source v13 fixture/open shift/paid40-unpaid60/photo bounds/admin epoch/stale picker/late result и settings route/recreation прошли. Первая попытка41405536715 сохранена как failed: неверный Back selector в тесте исправлен на shared navigation без изменения runtime/gates. Это только structural preview, не restore/export/полный Web import; B02–B04 и converters/activation planned. Schema9/main/signing неизменны;06/09 TODO,M17 исключён. Physical tablet/dark/150%/large backup и реальные обезличенные Web fixtures pending.


2026-11-02: начат07.2 NX01–NX06, [NATIVE-BACKUP-07-2.md](../../docs/NATIVE-BACKUP-07-2.md): exact SQLite tables/types snapshot+referenced photos, manifest1/schema9/SHA-256, SAF export+readback/native inspection/admin chooser epoch. Implemented/validation pending. Snapshot из source transaction, без live db/wal copy/пересчёта. Это не live restore/protective activation/Web converter, B02–B04 не закрыты.


2026-11-02:07.2 NX01–NX06 ci-verified в рамках native export/integrity: [FullCI41415093355](https://github.com/mendelev-main/M-POS-Android/actions/runs/41415093355)/b789ae6 SUCCESS first attempt.184JVM/257API36/0failures/0skipped;16Python/preflight/lint/debugAPK/testAPK. Downloaded HTML NativeBackupTest3/BackupViewModelTest4/BackupUiTest2/SettingsVisualUiTest1 passed. Source transaction→independent snapshot→archive→extraction→Room createFromFile preserves all table rows/types, completed receipt, purchase/supplier documents, active shift, frozen partial paid4000/given5000/change1000, exact stock9.0004, command replay and two photos (product/draft-only). Faults captured/archived/verified/written, cancellation, provider readback corruption, missing photo, old chooser authority and late result leave source unchanged/no false success. Actual debugAPK1→2 UPDATE E2E PASSED/app-private data preserved;26UIseed strict gate retained. Light native-backup component screenshot (synthetic report) and main-shell settings-backup inspected: shared controls, readable/no overlap. Physical tablet/dark/150%/large backups/cloud SAF/real disk-full/power-loss remain pending. Native export format1/schema9 implemented; live restore/app-private durable protective activation/Web converters still planned, B02/B04 full acceptance not closed. Main/signing/schema unchanged;06/09 TODO,M17 excluded.


2026-11-02: начат07.3a RA01–RA06, durable protection+prepared source/inventory без live activation. Контракт [NATIVE-RECOVERY-07-3.md](../../docs/NATIVE-RECOVERY-07-3.md), implemented/validation pending. Приёмка source40/target30 отдельно, publication faults/reopen/auth/fingerprint; B02/B04 full restore и07.3b writers/generation/activation planned. Main/schema/signing неизменны.


2026-11-02:07.3a RA01–RA06 ci-verified (protection/preparation only): [FullCI41421510948](https://github.com/mendelev-main/M-POS-Android/actions/runs/41421510948)/85b473c SUCCESS first attempt.184JVM/265API36/0failures/0skipped,16Python/preflight/lint/debugAPK/testAPK. Downloaded HTML NativeRecoveryPreparationTest5/NativeRecoveryUiTest1/BackupViewModelTest6/SettingsVisualUiTest1 passed. Actual source40/target30 separate persisted snapshots, independent Room protection reopen/old part-command replay, all9 publication checkpoints/finally cleanup/prior published copies retained, malformed source/corrupt linked protection/cancellation/old authority, VACUUM/time-independent fingerprint and exact0.0004→0.0005 detection/unknown payment=null passed. Fresh repository inventory reads published files, incomplete .stage does not activate; working documents/shift/stock/paid30 and original source bytes unchanged. Actual debugAPK1→2 UPDATE E2E PASSED/app-private data preserved,27UIseed strict count retained. Light native-protection component screenshot(synthetic adapter) + main-shell settings-backup inspected: shared controls/clear40vs30/readable/no overlap; lower Web card edge is scrolling position. Physical tablet/dark/150%/large retained inventory/real disk-full/power-loss/process-kill timing remain pending. No live replacement/activation implemented, B02/B04 full restore and07.3b maintenance/domain/schema/generation/recovery remain planned. Schema9/main/signing unchanged;06/09 TODO,M17 excluded.


2026-11-02: начат07.3b AC01–AC06, [NATIVE-ACTIVATION-07-3B.md](../../docs/NATIVE-ACTIVATION-07-3B.md): native schema/domain validation, separate generations/pinned paths/durable pointer+pending, all-table SQL seal, explicit active confirm/stale target/lost-ack guard, startup-before-repositories. Implemented/validation pending, не Web migration. Локальный RestrictedApi lint на TableInfo заменён public PRAGMA verifier без suppression.
