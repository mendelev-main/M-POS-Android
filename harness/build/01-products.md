# Этап 01 — Товары

Статус: инкремент01a ci-verified с CI evidence в [CATALOG-01A.md](../../docs/CATALOG-01A.md); 01b domain/Room schema2/editor ci-verified API28/35, run40094842882; 01c ci-verified API28/35, run40098187895; device pending

## Объём

Реализовать P01–P10 из docs/PRODUCTS-WORKSPACE.md; Room и migrations, доменные модели каталога.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Детализация перед кодом

[Сценарии и инкременты](../../docs/CATALOG-WORKSPACE-SPEC.md), [контракты и fixtures](../../docs/IMPLEMENTATION-CONTRACTS.md). Взять первый незакрытый инкремент; полный диапазон acceptance не считать выполненным после одного шага.

## Системные gates

До 01a: [модель/типы](../../docs/DOMAIN-MODEL.md), явная денежная единица цены, decimal limits и первая Room schema. До 01b: precision при неконечном делении recipeYield. Миграции/фото fault cases — [RESILIENCE.md](../../docs/RESILIENCE.md); приёмка SYS-01/SYS-02.

## Модульная детализация

[Контракты инкремента](../../docs/FOUNDATION-CONTRACTS.md); [карта модулей](../../docs/MODULES.md), [порядок/документационные gates](../../docs/DOCUMENTATION-PLAN.md).

## Требования пользователя2026-10-24

Редактирование остатка в карточке без ввода причины; внутри atomic delta/revision, F11–F13. Дизайн/общие компоненты по [DESIGN-SYSTEM.md](../../docs/DESIGN-SYSTEM.md), DS01–DS07. Полный типовой scope Web не сокращается выбранной последовательностью01a/01b.

## Приёмка

Все P IDs с JVM/Room/UI evidence; фото и restart на устройстве.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Продажи, реальный backend, импорт всех Web данных.

## Контекст и результат

Значимые решения в `harness/context/01-products.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.


Начало01b: [precision и доменный контракт](../../docs/CATALOG-01B.md). P04/P05/P07: domain/Room migration/editor implemented; точный CI результат — build-log. Полный этап01 не закрыт.


01c: [контракт modifier/photo](../../docs/CATALOG-01C.md), migrations2→3/1→3, command guards/staging fault tests/UI. РезультатыCI — build-log; физическая приёмка отдельно.


Текущая поддержка — [Android16+](../../docs/ANDROID-SUPPORT.md); предыдущие API28/35 evidence исторические. Порядок категорий и [финальный audit](../../docs/CATALOG-ACCEPTANCE.md) добавлены; coreCI36 ci-verified20instrumentation/run40099706934, full01/device ещё не закрыты.
