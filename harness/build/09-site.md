# Этап 09 — Сайт и конфигурация

Статус: planned

## Объём

HTTPS endpoint/device auth, SSE заказы, ручной sync меню/фото, availability, delivery tariffs.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/SETTINGS-SITE-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Приёмка

N01: offline/HTTP/abort; N02: SSE reconnect/id/retry; N03: дедупликация заказа; N04: manual sync; N05: локальная касса без сети; N06: backend contract fixtures и staging приёмка.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Развёртывание/изменение backend без задачи.

## Контекст и результат

Значимые решения в `harness/context/09-site.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
