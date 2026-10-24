# Архитектура

Полный выбор технологий, статус подключения и привязка к этапам: [STACK.md](STACK.md) (ADR-002). Эта страница описывает архитектурные границы.

Подробности будущей системы: [модель](DOMAIN-MODEL.md), [состояния](STATE-MACHINES.md), [сохранность](RESILIENCE.md), [доступ/аудит](ACCESS-AND-AUDIT.md), [приёмка](SYSTEM-ACCEPTANCE.md). Эти документы описывают требования, не установленные бизнес-компоненты.

Решение ADR-001, 2026-10-23: Kotlin, Jetpack Compose Material 3, single Activity; offline-first. Минимум Android 9/API 28 как в Web. UI ориентирован на планшет, адаптивен к доступной ширине; landscape — основной сценарий приёмки, ориентацию жёстко не фиксируем.

Границы21 логической области и отличие от Gradle modules: [MODULES.md](MODULES.md).

Сейчас один `:app` и bootstrap без бизнес-данных. В этапе 01 добавляем разделение `feature/products`, `domain`, `data/local`; отдельные Gradle modules выделяем при реальной необходимости. Не создаём заранее пустые модули всех функций.

Целевая структура: Compose → ViewModel (immutable UiState/действия, StateFlow) → use cases → repository → Room. Доменные расчёты на чистом Kotlin с JVM tests, без Android Context. Room — единственный владелец локальных продуктов, продаж, движений, сессий и durable outbox; DataStore для неоперационных предпочтений. Фото — app-private files с ID/metadata, системные pickers. DB migrations с тестами, destructive fallback запрещён для рабочих данных.

Финансовая команда в одной Room transaction проверяет актуальность, рассчитывает итог, сохраняет receipt snapshot, stock movements, сессию и outbox с idempotency key. После commit публикуется успех. Внешняя сеть/печать асинхронна: её сбой не отменяет продажу и не создаёт вторую. Конкуренция сериализуется в use case/transaction; disabled кнопка не заменяет защиту.

Рецептуры разворачиваются с защитой циклов; доступность учитывает текущие и отложенные резервы без двойного списания. Закрытая продажа неизменна; возврат — новая операция со ссылкой на исходную. Авторизация проверяется в use cases; admin session runtime-only, secrets не попадают в backups/logs.

В 09 адаптеры HTTPS и SSE реализуют описанные контракты, persistent retries и observable sync status. Endpoint и реальные payload fixtures сначала исследуются в Web/backend; неизвестные контракты не выдумывать. Никаких production вызовов при старте bootstrap.

Сборочная база: AGP 8.13.2 / Gradle 8.13 / Kotlin 2.2.21 / JDK 17 / compile+target SDK 36; версии закреплены. Это выбранная совместимая база, не утверждение о самых новых версиях. Compose BOM 2025.10.00. Обновления отдельной задачей с CI.
