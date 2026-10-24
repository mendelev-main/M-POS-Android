# Этап 01 — Товары

Статус: planned

## Объём

Реализовать P01–P10 из docs/PRODUCTS-WORKSPACE.md; Room и migrations, доменные модели каталога.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Детализация перед кодом

[Сценарии и инкременты](../../docs/CATALOG-WORKSPACE-SPEC.md), [контракты и fixtures](../../docs/IMPLEMENTATION-CONTRACTS.md). Взять первый незакрытый инкремент; полный диапазон acceptance не считать выполненным после одного шага.

## Приёмка

Все P IDs с JVM/Room/UI evidence; фото и restart на устройстве.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Продажи, реальный backend, импорт всех Web данных.

## Контекст и результат

Значимые решения в `harness/context/01-products.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
