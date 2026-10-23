# Этап 03 — Продажи

Статус: planned

## Объём

Оплата наличными/картой/смешанная, скидки/доставка, receipt snapshots, stock transactions, повтор/возврат.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Приёмка

S01: commit атомарен; S02: повтор idempotent; S03: historical refund; S04: decimal rounding/split; S05: история после restart.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Эквайринг/фискальный SDK без отдельного контракта.

## Контекст и результат

Значимые решения в `harness/context/03-sales.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
