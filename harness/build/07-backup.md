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


2026-11-02:07.3b AC01–AC06 ci-verified/API36: [FullCI41477976788 attempt2](https://github.com/mendelev-main/M-POS-Android/actions/runs/41477976788/attempts/2)/6fff1e0 SUCCESS.184JVM/279Android16/0failures/0skipped,16Python/preflight/lint/debugAPK/testAPK. Downloaded reports inspected: NativeActivationTest11, NativeActivationUiTest1, BackupViewModelTest8; actual debugAPK1→2 UPDATE E2E PASSED and actual native restore process E2E PASSED. OLD process sealed/pinned, fresh ordinary MainActivity selects NEW DB+photos together/admin locked; paid40 retained, remaining60 completed once, change10/cash40, target30 protective copy retained. Missing authoritative pointer after completed restore blocks frozen legacy fallback. Light shared confirmation and fresh-process result screenshots inspected: readable/no overlap. Attempt1 had279tests/1failure: PurchaseOrderUiTest Compose AndroidPrefetchScheduler/Choreographer Looper error, stack resumed on CoroutineScheduler worker via test frame interceptor; no proven production cause. One unchanged failed-job rerun passed all gates; assertions/timeouts/runtime unchanged. Earlier41449112827 attempt2 also passed184JVM/278API36 and both E2E. Physical tablet/dark/150%/real power-loss/disk-full/large data remain pending; callback faults and force-stop do not prove these. Native format1/schema9 only; Web conversion07.4 and mapped activation07.5 remain planned.06/09 TODO,M17 excluded. Main/signing/applicationId/schema unchanged; no stable release.


2026-11-02: начат07.4a PM01–PM04, docs/WEB-CONVERSION-07-4.md. Pure WebPaymentMapping converts current/parked version1 payment evidence to exact Long minor amounts, retains nullable tender/paid status/source position; no native command/ledger writes and no activation. Source payment.js normalizer permits missing paid cash tender; explicit paidCashTenderUnknown blocks later activation rather than fabrication. All-paid draft requires receipt reconciliation; selected loyalty entitlement preserved as blocker, not reset. Frozen quote/shift/actor/cash/provenance reconciliation and remaining mappings07.4b–e planned. Web UI still structural preview only. Main/signing/schema unchanged.


2026-11-02:07.4a PM01–PM04 locally verified (not ci/device-verified): `/bin/bash scripts/local-android.sh build -Pkotlin.incremental=false` SUCCESS,191JVM/0fail/0error/0skip including7WebPaymentMappingTest; lintDebug/assembleDebug passed. Harness preflight and git diff --check passed. Tests cover40/50/10/60, repeat/source unchanged, missing tender unknown, independent parked card/cash, all-paid reconciliation, selected loyalty blocker, exact money/malformed values, absent/future draft. Pure DTO only; no Room/UI/cash/activation change, no emulator rerun or release for this increment. Next07.4b isolated catalog/identity/employee/shift mapping. FullWeb migration still planned.


2026-11-03: начат07.4b.1 CM01–CM05/SM01–SM03, docs/WEB-CONVERSION-07-4.md. Added pure scoped WebIdentity, exact scalar reader, catalog projection (base/display/pack/recipe batches/modifiers/unknown unit/metadata/photo blockers), employee and historical shift/cash projections. No Room/product-save/receiving/cash writes, admin session, HTTP, schema change or activation. Current cost/name/time never fill missing history. Tests/build in progress. Durable isolated materialization/layout/photo/metadata07.4b.2 remains next, before legacy07.4c and financial reconciliation07.4d.


2026-11-03:07.4b.1 CM01–CM05/SM01–SM03 implemented with local evidence, not ci/device-verified. `/bin/bash scripts/local-android.sh build -Pkotlin.incremental=false` SUCCESS (final1m5s):204JVM/0fail/0error/0skip, lintDebug/assembleDebug passed. New WebCatalogMappingTest8/WebStaffMappingTest5: exact9.0004/minimum0.0004, base kg/display g/500g pack→0.5kg, nested batches→physical child yield/exact0.0008 consumption, stable namespace identities/numeric-string duplicate IDs, legacy null units/negative permission, absent cost/stock unknown, required price/type, dangling/cyclic refs/dimension/precision refusal, distinct categories/metadata/URL blockers, signed modifier deltas/composite option quantity, current directory vs historical name, admin role no cashier substitution, closed counted1025/expected unknown, exact refund cash movement, invalid closures/duplicate open/role/money, absent actor/time not replaced. Source unchanged/repeat mapping deterministic. Harness preflight/git diff --check passed. Build prints existing read-only ~/.android analytics warning; successful exit, no permission expansion. No UI/Room/cash mutation, emulator/CI/release not run for pure projection increment. Native format1/schema9/main/signing unchanged.07.4b.2 photo/layout/metadata/durable staged materialization still required;07.4c legacy history→07.4d frozen financial reconciliation→07.4e validation→07.5 activation pending.06/09 TODO,M17 excluded.
