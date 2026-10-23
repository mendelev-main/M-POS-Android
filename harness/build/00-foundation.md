# Этап 00 — Фундамент

Статус: ci-verified — checks, emulator и signed release; device acceptance pending. Evidence: harness/build-log.md, Actions run 40023385742.

## Объём

Harness, docs, Compose bootstrap, CI, постоянный ключ и проверка identity.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Приёмка

Инфраструктурные проверки, сборка, emulator smoke и signed APK. Физическая установка отдельно.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Бизнес-функции и миграция рабочих данных.

## Контекст и результат

Значимые решения в `harness/context/00-foundation.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
