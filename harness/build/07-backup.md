# Этап 07 — Backup и перенос

Статус: planned

## Объём

Версионированный экспорт/import Web v13 с preview, фото staging, migration/restore.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/OPERATIONS-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Приёмка

B01: malformed отказ без потери данных; B02: roundtrip; B03: dangling фото; B04: disk-full/interrupt/restart; B05: unknown versions.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Чтение sandbox Web и молчаливое объединение конфликтов.

## Контекст и результат

Значимые решения в `harness/context/07-backup.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
