# Этап 08 — Оборудование и настройки

Статус: planned

## Объём

LAN ESC/POS, ящик, PDF/XLSX, Telegram; preferences и диагностика.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Приёмка

D01: mock transports; D02: печать не удваивает продажу; D03: ящик по cash/role; D04: fake Telegram; D05: физические образцы печати/экспорта.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Bluetooth/USB и production рассылка без задачи.

## Контекст и результат

Значимые решения в `harness/context/08-device.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
