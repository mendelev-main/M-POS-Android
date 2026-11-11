# Этап 06 — Клиенты и лояльность

Статус: in progress — возобновлено владельцем2026-11-09 вместе с parity онлайн-заказов. Backend не меняем; переносим рабочий Web-протокол. Детали: [ONLINE-LOYALTY-PARITY.md](../../docs/ONLINE-LOYALTY-PARITY.md).

## Объём

Клиенты, consent/privacy, loyalty allocations. M17/зал/столы/бронирования исключены владельцем2026-10-28.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/OPERATIONS-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Модульная детализация

[Контракты инкремента](../../docs/CUSTOMERS-HALL-SPEC.md); [карта модулей](../../docs/MODULES.md), [порядок/документационные gates](../../docs/DOCUMENTATION-PLAN.md).

## Приёмка

C01: валидация контакта; C02: gift не поглощает доставку/другие строки; C03: outbox повтор; C04: роли/согласия; C05: excluded (бронирования исключены владельцем).

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Production клиентские данные в fixtures.

## Контекст и результат

Значимые решения в `harness/context/06-customers.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.


10.11 — локальная LO03: admin customer fullscreen/inline editor; исправлен consentVersion, отдельная диагностика admin403 и transient Web credential. Без изменений backend. Production save и визуальная приёмка владельца pending; evidence — build-log.md.


10.11 — LO02 picker/create follow-up implemented locally: fixed-prefix pill fields/card results/optional enrollment, stale search и safe create/select flow, attach-before-consent. Адресные проверки — build-log.md; реальные клиенты/consent не менялись, device verification pending.
