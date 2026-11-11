# Этап 09 — Сайт и конфигурация

Статус: in progress — возобновлено владельцем09.11.2026, включая онлайн-заказы; лояльность остаётся TODO. Текущий контракт, части и обсуждаемые развилки: [BACKEND-09.md](../../docs/BACKEND-09.md).

## Объём

HTTPS endpoint/device auth, SSE заказы, ручной sync меню/фото, availability, delivery tariffs.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/SETTINGS-SITE-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Системные контракты

[Outbox/reconciliation](../../docs/RESILIENCE.md), независимые local/server states из [STATE-MACHINES.md](../../docs/STATE-MACHINES.md); SYS-10/SYS-13.

## Приёмка

N01: offline/HTTP/abort; N02: SSE reconnect/id/retry; N03: дедупликация заказа; N04: manual sync; N05: локальная касса без сети; N06: backend contract fixtures и staging приёмка.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Развёртывание/изменение backend без задачи.

## Контекст и результат

Значимые решения в `harness/context/09-site.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.


10.11 — ON02/ON06 implemented locally: окно события по Web + admin-only soft deletion через silent PATCH cancelled, encrypted inbox tombstones и запрет позднего local accept. Endpoint не менялся; physical delete/server CAS отсутствуют. Контракт ONLINE-LOYALTY-PARITY.md, проверки build-log.md, manual acceptance pending.
