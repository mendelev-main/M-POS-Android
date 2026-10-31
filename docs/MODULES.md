# Карта модулей приложения

Проектный план от 2026-10-24: **20 логических модулей:18 в текущем объёме,2 в TODO** (M17 исключён владельцем2026-10-28; нумерация сохранена). Это области ответственности внутри первоначального `:app`, а не 20 Gradle project и не 20 готовых функций. M01/M21 реализованы частично; в01a добавлены базовые M02/M03/M06/M07/M10, В develop01b/01c добавлены units/recipes/modifiers и ограниченный M04 photo adapter; весь M04 (включая backup/media integration) ещё не завершён. Это не готовность полных модулей; scope в [CATALOG-01A.md](CATALOG-01A.md). Статусы реализации — [PARITY.md](PARITY.md).

Зависимости ниже означают предметные/инфраструктурные предпосылки. Они не задают прямые Kotlin imports экранов. Domain использует интерфейсы портов; Room/platform adapters подключаются в composition root. Данные разных областей объединяет одна transaction, когда этого требует команда продажи/переноса.

06/M16 и09/M20 отложены владельцем2026-10-31: [TODO](PRODUCT-TODO.md). Логические области сохранены, реализация не начинается;07/08 используют только готовые модели.

## Реестр

| ID / область | Владеет | Нужные предпосылки | Ввод / документ |
|---|---|---|---|
| M01 Оболочка и настройки | навигация, запуск, группы config, composition root, общие состояния и design system | M02; подключает только готовые features | bootstrap→01; [базовые контракты](FOUNDATION-CONTRACTS.md) |
| M02 Предметные типы | IDs, quantity/unit, money/currency, revisions, ошибки, clock/command contracts | без Compose/БД/HTTP | 01; [модель](DOMAIN-MODEL.md) |
| M03 Локальное хранение | Room lifecycle, migrations, transaction runner, schema/constraints | M02 | 01; [foundation](FOUNDATION-CONTRACTS.md), [recovery](RESILIENCE.md) |
| M04 Файлы и изображения | picker/staging/private files, PhotoRef storage, SAF adapters | M02; metadata через M03 | 01c,07; [каталог](CATALOG-WORKSPACE-SPEC.md), [recovery](RESILIENCE.md) |
| M05 Задания и интеграционные эффекты | outbox, attempts/lease, retries/reconciliation, sync status | M02/M03, actor/binding context M06 | 03 durable envelope,06/08/09 transports; [recovery](RESILIENCE.md) |
| M06 Доступ и аудит | capability contracts, actor context, audit events, secret references | M02/M03; provider single-owner сначала, M14 позже | 01 minimum,05 roles; [доступ](ACCESS-AND-AUDIT.md) |
| M07 Каталог | товары/категории, рецепты/модификаторы, usages, правила удаления | M02/M03/M06, opening ledger M10; фото M04 | 01a–01c; [каталог](CATALOG-WORKSPACE-SPEC.md) |
| M08 Рабочая зона | layout/folders/tiles, cart/current/parked, параметры, reservations | M07/M10, M03/M06 | 02; [каталог/корзина](CATALOG-WORKSPACE-SPEC.md) |
| M09 Продажи | checkout, receipt snapshots/history, payment allocations, refunds | M08/M10/M13, M03/M05/M06 | 03; [операции](OPERATIONS-SPEC.md), [состояния](STATE-MACHINES.md) |
| M10 Складское ядро | ledger/balance/stockRevision, расход и доступность по recipes, reservation calculation | M02/M03; получает catalog snapshot через port | opening01a, availability02, sale03, UI04; [модель](DOMAIN-MODEL.md) |
| M11 Поставщики и закупки | supplier, purchase orders, receiving draft/documents; shortage закрывает заказ как Web | M07/M10/M03/M06 | 04; [закупки](WAREHOUSE-SPEC.md) |
| M12 Инвентаризация | физический count/baseline, apply correction, history/cancel remainder | M10/M03/M06 | 04; [склад](WAREHOUSE-SPEC.md) |
| M13 Смена и касса | open/close shift, cash movements, shift boundary, totals contract | M02/M03/M06; read ports sales, без вызова checkout | minimum03a, full05; [управление](MANAGEMENT-REPORTS-SPEC.md) |
| M14 Сотрудники и admin | employee directory, credentials provider, runtime admin session, role assignment | M06/M03; права не следуют из shift | 05; [управление](MANAGEMENT-REPORTS-SPEC.md) |
| M15 Отчёты и аналитика | report query/snapshot, показатели/периоды/группировки, сверка | M09/M10/M13, складские read models M11/M12 | 05, exports08; [отчёты](MANAGEMENT-REPORTS-SPEC.md) |
| M16 Клиенты и лояльность — TODO | customer reference/search/consent, gift allocation, loyalty effects | M09/M06/M05; server contract/staging для remote части | 06; [клиенты](CUSTOMERS-HALL-SPEC.md) |
| M17 | Исключён: зал, столы и бронирования | — | решение владельца2026-10-28 |
| M18 Backup и перенос | native export/restore, Web v13 adapter, preview/activation | реализованные модели01–05, M03/M04/M06, coordination M05 | 07; [recovery](RESILIENCE.md), [операции](OPERATIONS-SPEC.md) |
| M19 Оборудование и выдача | print/drawer adapters, PDF/XLSX/Telegram transport, result jobs | M09/M13/M15/M04/M05/M06 | 08; [операции](OPERATIONS-SPEC.md) |
| M20 Сайт и конфигурация обмена — TODO | device binding, remote orders/SSE, menu/photos/availability/tariffs | M07/M08/M09/M10/M16/M05/M06; restore binding policy M18 | 09; [сайт](SETTINGS-SITE-SPEC.md) |
| M21 Обновления APK | release channel/check/download/verify/system install/receipt | Android/HTTP; future guard M01/M03 | 00.1 ci-verified; [updater](UPDATES.md) |

## Границы и взаимодействие

M01 собирает routes/providers, но не считает сумму/остатки. M02 не превращается в общую свалку всех domain entities: правила конкретной области живут с областью. M03 обеспечивает сохранение; бизнес-решение «можно ли вернуть» принимает M09. M10 разворачивает catalog snapshot через port — не вызывает экран M07; M07 пишет opening через тот же stock contract.

M13 не зависит от экрана/команды M09: ledger оплат и read port достаточны для закрытия/сверки. M09 проверяет active shift через порт M13. M15 только читает подтверждённые данные, не изменяет receipts ради красивого графика. M16 предлагает validated allocations через checkout contract; добавление loyalty не создаёт вторую реализацию оплаты. M17 исключён из продукта.

M05 знает delivery policy/статус эффекта, а transport конкретного сервиса — его adapter. Один общий outbox не означает одинаковый auto retry для принтера и HTTP. M18 не отправляет восстановленные external jobs автоматически. M20 не владеет локальной кассой: сеть отсутствует — допустимые локальные операции продолжаются.

Обязательная общая UI-база M01: [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md). core/ui/theme/components/preview создаются в01a; общий язык из Web, не default Material. M07 stock editor принимает поправку без причины через M10 ledger command.

## Физическая структура на старте

Один `:app`: `app` (composition/navigation), `core/model`, `core/contracts`, `domain/catalog`, `domain/stock`, `data/local`, `feature/products`, существующий `update`. Каталоги создаются только с реальным кодом. UI → ViewModel → use case → ports → adapters; data implementation не импортирует Compose feature. Aggregate database регистрирует нужные entities/DAO на этапе, не создаёт пустые будущие таблицы.

Выделять отдельные Gradle modules после измеримой нужды: enforcement границ, разные владельцы/тестовые зависимости, время сборки или platform-free JVM reuse. Тогда API/domain отделяется от implementation без циклических project dependencies. Текущая карта не требует сразу build-logic/convention plugins и десятков пустых modules.

## Где смотреть готовность документации

[DOCUMENTATION-PLAN.md](DOCUMENTATION-PLAN.md) считает 8 пакетов углубления, а не количество Markdown файлов. [DELIVERY-ROADMAP.md](DELIVERY-ROADMAP.md) задаёт порядок/критический путь. Полный паспорт модуля означает известные границы; готовность его реализации требует заполнить gate соответствующего пакета. Общая архитектура и приёмка не заменяют конкретный backend payload/образец принтера.
