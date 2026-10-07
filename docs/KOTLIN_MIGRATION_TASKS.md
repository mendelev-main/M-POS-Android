# Реестр задач миграции на Kotlin

> Детализация 109 (07.10.2026): **5/20 — 25,00%**, осталось 15. Общий детальный план: **112/129 — 86,82%**, осталось 17. Крупные этапы: **107/110 — 97,27%**. [Подэтапы, критерии и правила подсчёта](../specs/109-native-runtime/tasks.md). Проценты относятся к количеству задач; физическая приёмка 110 ещё не выполнена.


Базовый план v1, 6 октября 2026 года. **107/110 задач выполнено (97.27%)**.
Осталось 3. Таблицы ниже содержат завершённые инженерные этапы
со спецификациями и оставшиеся границы из P1–P12/существующих feature-модулей.
030 заменена 033 и исключена из знаменателя.

Это доля завершённых инженерных задач, включая foundation/проверки/платформенные
границы, а не доля полностью нативных функций, кода или трудозатрат. Задачи
разного размера. Shadow/диагностические/частичные этапы закрыты только в своей
спецификации. Совместимый DOM/runtime в WebView сохраняется до 109. Физическая приёмка 110
ожидается, автоматическая готовность не означает её прохождения.

Критерий done: scope реализован, business/source/v13 parity и rollback описаны,
подходящие JS/JVM/lint прошли, результат опубликован в main. После этапа обновить
spec, status/roadmap/parity/tablet cases и реестр. IDs не переиспользуются;
знаменатель меняется только с объяснением изменения scope. Не меняем business
policy молча и не добавляем новые features ради процента.

Источник: [kotlin-migration-tasks.json](kotlin-migration-tasks.json).
Счётчик: `python scripts/migration-progress.py`; после проверки этапа:
`python scripts/complete-migration-stage.py ID specs/ID-name/spec.md "summary"`.
Порядок определяется зависимостями/приоритетом, номера не являются сроками.

## Реализованные этапы и отменённый этап

| ID | Статус | Спецификация / ограниченный scope |
|---|---|---|
| 001 | выполнено | [Feature Specification: Android parity baseline](../specs/001-android-parity/spec.md) |
| 002 | выполнено | [Feature Specification: Native Android settings boundary](../specs/002-native-settings-boundary/spec.md) |
| 003 | выполнено | [Feature Specification: Core local persistence shadow foundation](../specs/003-room-shadow-persistence/spec.md) |
| 004 | выполнено | [Feature Specification: Native catalog projection](../specs/004-native-catalog-projection/spec.md) |
| 005 | выполнено | [Feature Specification: M POS catalog repository and parity diagnostics](../specs/005-mpos-catalog-repository/spec.md) |
| 006 | выполнено | [Feature Specification: Controlled native catalog read boundary](../specs/006-native-catalog-read-boundary/spec.md) |
| 007 | выполнено | [Feature Specification: Catalog cutover controller](../specs/007-catalog-cutover-controller/spec.md) |
| 008 | выполнено | [Feature Specification: Native employee projection](../specs/008-native-employee-projection/spec.md) |
| 009 | выполнено | [Feature Specification: Native shift and cash movement projection](../specs/009-native-shift-projection/spec.md) |
| 010 | выполнено | [Feature Specification: Native order, line item and payment projection](../specs/010-native-order-projection/spec.md) |
| 011 | выполнено | [Feature Specification: Native held-check projection](../specs/011-native-held-check-projection/spec.md) |
| 012 | выполнено | [Feature Specification: Native warehouse stock-event projection](../specs/012-native-stock-event-projection/spec.md) |
| 013 | выполнено | [Feature Specification: Native network transport boundary](../specs/013-native-network-transport/spec.md) |
| 014 | выполнено | [Feature Specification: Native shadow SSE](../specs/014-native-shadow-sse/spec.md) |
| 015 | выполнено | [Feature Specification: SSE parity diagnostics\n\n## Goal\nCompare the legacy browser SSE stream with the native shadow stream without processing an order twice.\n\n## Contract\n- Legacy EventSource remains authoritative.\n- Legacy raw event.data is SHA-256 fingerprinted before JSON parsing.\n- Native shadow fingerprints the raw assembled SSE data frame.\n- Diagnostics expose counts, last hashes, matches and mismatches only.\n- No native fingerprint event may invoke order, acceptance, payment, storage or printing business handlers.\n\n## Acceptance\nAutomated architecture/build verification plus physical same-stream comparison and reconnect testing are required before transport cutover.\n](../specs/015-sse-parity-diagnostics/spec.md) |
| 016 | выполнено | [Feature Specification: Shadow SSE lifecycle\n\n## Goal\nKeep diagnostic native SSE bounded to the foreground Activity while preserving legacy WEB-order behavior.\n\n## Rules\n- Backgrounding cancels only the native shadow call/job.\n- Explicit shadow intent/config is retained across background/foreground.\n- Foreground resumes shadow observation when previously requested.\n- Explicit stop and Activity destroy clear/cancel shadow state.\n- Legacy EventSource remains untouched and authoritative.\n\n## Acceptance\nAutomated build plus physical background/foreground and network interruption verification before any cutover.\n](../specs/016-shadow-sse-lifecycle/spec.md) |
| 017 | выполнено | [Feature Specification: Continuous SSE diagnostics\n\n## Goal\nPreserve shadow SSE diagnostic evidence across Activity background/foreground reconnects.\n\n## Rules\n- Explicit fresh start resets counters and last hash.\n- Lifecycle resume does not reset event count, reconnect count, or last hash.\n- SHA-256 input encoding is explicitly UTF-8.\n- No business payload is persisted or replayed.\n- Legacy EventSource remains authoritative.\n\n## Acceptance\nAutomated build plus physical background/reconnect comparison before cutover.\n](../specs/017-continuous-sse-diagnostics/spec.md) |
| 018 | выполнено | [Feature Specification: WEB acceptance recovery projection](../specs/018-web-acceptance-recovery-projection/spec.md) |
| 019 | выполнено | [WEB acceptance recovery parity](../specs/019-web-acceptance-recovery-parity/spec.md) |
| 020 | выполнено | [Current order session recovery projection](../specs/020-current-order-session-recovery/spec.md) |
| 021 | выполнено | [WEB ready durable recovery](../specs/021-web-ready-durable-recovery/spec.md) |
| 022 | выполнено | [Loyalty recovery shadow status](../specs/022-loyalty-recovery-shadow/spec.md) |
| 023 | выполнено | [WEB ready recovery Room projection](../specs/023-web-ready-room-projection/spec.md) |
| 024 | выполнено | [WEB ready recovery parity diagnostics](../specs/024-web-ready-recovery-parity/spec.md) |
| 025 | выполнено | [WEB ready durable local-state gate](../specs/025-web-ready-local-durability-gate/spec.md) |
| 026 | выполнено | [Critical storage journal Room shadow](../specs/026-critical-storage-journal-shadow/spec.md) |
| 027 | выполнено | [Critical storage journal parity diagnostics](../specs/027-critical-storage-journal-parity/spec.md) |
| 028 | выполнено | [Loyalty durable retry-state boundary](../specs/028-loyalty-durable-retry-state/spec.md) |
| 029 | выполнено | [Loyalty interrupted-send restart recovery](../specs/029-loyalty-interrupted-send-recovery/spec.md) |
| 030 | заменена 033 | [Availability restart and reconnect recovery](../specs/030-availability-restart-reconnect-recovery/spec.md) |
| 031 | выполнено | [Spec 031 — Native diagnostic breadcrumbs](../specs/031-native-diagnostic-breadcrumbs/spec.md) |
| 032 | выполнено | [Spec 032 — Native diagnostic export](../specs/032-native-diagnostic-export/spec.md) |
| 033 | выполнено | [Spec 033 — Availability retry after the next payment](../specs/033-availability-payment-retry-policy/spec.md) |
| 034 | выполнено | [Spec 034 — Telegram shift-close receipt image](../specs/034-telegram-shift-receipt-image/spec.md) |
| 035 | выполнено | [Spec 035 — Transactional ordered native shadow storage](../specs/035-transactional-native-shadow-storage/spec.md) |
| 036 | выполнено | [Spec 036 — Bounded Kotlin backup input](../specs/036-bounded-native-backup-input/spec.md) |
| 037 | выполнено | [Spec 037 — Kotlin backup image preparation boundary](../specs/037-native-backup-image-preparation/spec.md) |
| 038 | выполнено | [Spec 038 — Bounded Kotlin SSE message reader](../specs/038-bounded-kotlin-sse-reader/spec.md) |
| 039 | выполнено | [Spec 039 — Durable ordered Kotlin platform settings mirror](../specs/039-durable-kotlin-settings-mirror/spec.md) |
| 040 | выполнено | [Spec 040 — Authoritative Kotlin catalog persistence](../specs/040-authoritative-kotlin-catalog/spec.md) |
| 041 | выполнено | [Spec 041 — Authoritative Kotlin workspace persistence](../specs/041-authoritative-kotlin-workspace/spec.md) |
| 042 | выполнено | [Spec 042 — Authoritative Kotlin employees persistence](../specs/042-authoritative-kotlin-employees/spec.md) |
| 043 | выполнено | [Spec 043 — Authoritative Kotlin shifts persistence](../specs/043-authoritative-kotlin-shifts/spec.md) |
| 044 | выполнено | [Spec 044 — Authoritative Kotlin paid-receipt persistence](../specs/044-authoritative-kotlin-receipts/spec.md) |
| 045 | выполнено | [Spec 045 — Authoritative Kotlin parked-order persistence](../specs/045-authoritative-kotlin-parked-orders/spec.md) |
| 046 | выполнено | [Spec 046 — Authoritative Kotlin session and recovery persistence](../specs/046-authoritative-kotlin-session-recovery/spec.md) |
| 047 | выполнено | [Spec 047 — Incremental native receipts and paginated history](../specs/047-incremental-native-receipts/spec.md) |
| 048 | выполнено | [Spec 048 — Atomic native local payment completion](../specs/048-atomic-native-payment-command/spec.md) |
| 049 | выполнено | [049 — Shift accounting and cross-shift refunds](../specs/049-cross-shift-refund-accounting/spec.md) |
| 050 | выполнено | [050 — Atomic Kotlin full return](../specs/050-atomic-kotlin-full-return/spec.md) |
| 051 | выполнено | [051 — Atomic Kotlin manual cash movements](../specs/051-atomic-kotlin-cash-movements/spec.md) |
| 052 | выполнено | [052 — Atomic Kotlin shift opening / closing](../specs/052-atomic-kotlin-shift-lifecycle/spec.md) |
| 053 | выполнено | [053 — Native shift summary / report read model](../specs/053-native-shift-report-model/spec.md) |
| 054 | выполнено | [054 — Native Kotlin shift summary screen](../specs/054-native-shift-screen/spec.md) |
| 055 | выполнено | [055 — First-run shift storage and Android error presentation](../specs/055-first-run-shift-storage/spec.md) |
| 056 | выполнено | [056 — Native cash deposit / withdrawal input forms](../specs/056-native-cash-movement-forms/spec.md) |
| 057 | выполнено | [057 — Native shift closing form](../specs/057-native-shift-closing-form/spec.md) |
| 058 | выполнено | [058 — Native shift opening / employee input](../specs/058-native-shift-opening-form/spec.md) |
| 059 | выполнено | [059 — оформление нативного экрана кассовой смены](../specs/059-native-shift-theme/spec.md) |
| 060 | выполнено | [060 — Kotlin pricing at settlement](../specs/060-native-settlement-pricing/spec.md) |
| 061 | выполнено | [061 — Native loyalty gift allocation at settlement](../specs/061-native-loyalty-reward-allocation/spec.md) |
| 062 | выполнено | [062 — Kotlin formation of configured unit prices](../specs/062-native-configured-unit-prices/spec.md) |
| 063 | выполнено | [063 — Native recipe consumption at settlement](../specs/063-native-recipe-consumption/spec.md) |
| 064 | выполнено | [064 — Room stock preflight before cart addition](../specs/064-native-cart-stock-preflight/spec.md) |
| 065 | выполнено | [065 — Native quantity decision and stock preflight](../specs/065-native-cart-quantity/spec.md) |
| 066 | выполнено | [066 — Проверка остатков перед оплатой через Kotlin/Room](../specs/066-native-payment-preflight/spec.md) |
| 067 | выполнено | [067 — Единый расчёт Kotlin для открытия и подтверждения оплаты](../specs/067-native-payment-totals/spec.md) |
| 068 | выполнено | [068 — Нативное подтверждение оплаты картой](../specs/068-native-card-confirmation/spec.md) |
| 069 | выполнено | [069 — Нативный ввод наличных для смешанной части](../specs/069-native-split-cash/spec.md) |
| 070 | выполнено | [070 — Обычная наличная оплата: нативный ввод и tender](../specs/070-native-ordinary-cash/spec.md) |
| 071 | выполнено | [071 — Единый нативный визуальный стиль M POS](../specs/071-native-pos-design/spec.md) |
| 072 | выполнено | [072 — первоначальное распределение смешанной оплаты](../specs/072-native-initial-split-plans/spec.md) |
| 073 | выполнено | [073 — изменение количества частей смешанной оплаты](../specs/073-native-split-count/spec.md) |
| 074 | выполнено | [Редактирование суммы смешанной части и парсер ввода](../specs/074-native-split-amount/spec.md) |
| 075 | выполнено | [Нормализация/возобновление mixed draft и его проверки](../specs/075-native-split-recovery/spec.md) |
| 076 | выполнено | [Нативные итоговые preview корзины при всех изменениях](../specs/076-native-cart-preview/spec.md) |
| 077 | выполнено | [Выбор доставки, тариф и проверки заказа](../specs/077-native-delivery/spec.md) |
| 078 | выполнено | [Локальные настройки заказа и клиента; сохранение комментария](../specs/078-native-order-context/spec.md) |
| 079 | выполнено | [Жизненный цикл отложенного заказа: hold/resume/delete](../specs/079-native-parked-commands/spec.md) |
| 080 | выполнено | [Правила редактирования товаров и категорий](../specs/080-native-catalog-edit/spec.md) |
| 081 | выполнено | [Правила редактирования рецептов и модификаторов](../specs/081-native-recipe-edit/spec.md) |
| 082 | выполнено | [Папки, порядок, перенос плиток и навигация — бизнес-команды](../specs/082-native-navigation/spec.md) |
| 083 | выполнено | [Сотрудники, роли и авторизация без изменения политики](../specs/083-native-employee-commands/spec.md) |
| 084 | выполнено | [Клиенты: локальные команды и хранение](../specs/084-native-customer-context/spec.md) |
| 085 | выполнено | [Онлайн-проверка доступности подарка и offline policy](../specs/085-native-loyalty-eligibility/spec.md) |
| 086 | выполнено | [Начисление/отмена лояльности и разрешённые durable retry](../specs/086-native-loyalty-journal/spec.md) |
| 087 | выполнено | [WEB acceptance/ready: нативный основной журнал и ACK](../specs/087-native-web-journals/spec.md) |
| 088 | выполнено | [Переключение WEB SSE на нативную доставку/восстановление](../specs/088-native-web-sse/spec.md) |
| 089 | выполнено | [Ручная синхронизация каталога и media transport](../specs/089-native-catalog-transport/spec.md) |
| 090 | выполнено | [Публикация доступности в Kotlin: только после оплаты](../specs/090-native-payment-availability/spec.md) |
| 091 | выполнено | [Поставщики: бизнес-команды и локальное хранение](../specs/091-native-supplier-commands/spec.md) |
| 092 | выполнено | [Заказы поставщику: расчёты, статусы и сохранение](../specs/092-native-purchase-commands/spec.md) |
| 093 | выполнено | [Приёмка: расчёт, себестоимость и атомарное подтверждение](../specs/093-native-receiving-command/spec.md) |
| 094 | выполнено | [Черновики приёмки и восстановление](../specs/094-native-receiving-drafts/spec.md) |
| 095 | выполнено | [Инвентаризация: пересчёт, расхождения и атомарное применение](../specs/095-native-inventory-commands/spec.md) |
| 096 | выполнено | [Складские отчёты по периодам и источники данных](../specs/096-native-warehouse-reports/spec.md) |
| 097 | выполнено | [Аналитика продаж/возвратов: Room aggregates и периоды](../specs/097-native-sales-analytics/spec.md) |
| 098 | выполнено | [Зал, столы, бронирования: нативные бизнес-команды](../specs/098-native-hall-commands/spec.md) |
| 099 | выполнено | [Нативное управление заданиями печати и business triggers](../specs/099-native-print-jobs/spec.md) |
| 100 | выполнено | [Нативные настройки и управление сотрудниками](../specs/100-native-settings-employees/spec.md) |
| 101 | выполнено | [Нативное рабочее место: каталог, папки, корзина](../specs/101-native-workspace/spec.md) |
| 102 | выполнено | [Нативный редактор товара/рецепта/модификаторов](../specs/102-native-product-editor/spec.md) |
| 103 | выполнено | [Нативный основной экран оплаты](../specs/103-native-payment-screen/spec.md) |
| 104 | выполнено | [Нативная история чеков, детали и возвраты](../specs/104-native-receipt-history/spec.md) |
| 105 | выполнено | [Нативные отложенные заказы, клиенты и лояльность](../specs/105-native-parked-customer-loyalty/spec.md) |
| 106 | выполнено | [Нативные экраны склада/закупок/приёмки/инвентаризации](../specs/106-native-warehouse-screens/spec.md) |
| 107 | выполнено | [Нативные экраны аналитики](../specs/107-native-analytics-screens/spec.md) |
| 108 | выполнено | [Нативный зал/столы/бронирования](../specs/108-native-hall-bookings/spec.md) |

## Оставшиеся задачи

| ID | Статус | Область | Что переносим | Зависимости |
|---|---|---|---|---|
| 109 | в работе | UI/architecture | Удаление активного WebView runtime и legacy мостов | 100,101,102,103,104,105,106,107,108 |
| 110 | запланировано | acceptance | Комплексная планшетная приёмка: parity, offline/restart, backup, производительность | 109,111 |
| 111 | запланировано | P5 | Нативные метаданные обновления APK и проверки подписи/версии | 032 |

## Последний этап

108: Нативная карта зала/столы/брони и выбор времени; 525 JS / 412 JVM, lint 0 ошибок / 15 прежних предупреждений. Команды 098, права, пересечения и v13 сохранены.
Физическая приёмка ожидается.

109 в работе: нативная проекция сохранённого заказа подключена при запуске; удаление WebView и навигация ещё не завершены. Счётчик: 107/110.

109: переходы категорий/папок/Назад/режима редактирования перенесены в Kotlin; общий этап в работе, 107/110 завершено.

109: согласованная загрузка и нормализация смен/сотрудников при запуске подключены к Room/Kotlin; синхронные проверки роли и полный runtime ещё остаются. 107/110 завершено.

## Текущий срез 109: нативное открытие смены

Нативная форма по умолчанию использует Kotlin-проверку действующего пароля,
чтение сохранённых сотрудников/смен и транзакционную команду открытия. Пароль
не передаётся в JS; результат возвращается после Room commit. JS ещё отвечает
за modal/render и уведомления после сохранения. Явный rollback возвращает
прежний обработчик. Этап 109 остаётся in_progress: **107/110 — 97,27%**.
Оставшиеся границы перечислены в [разборе runtime](NATIVE_RUNTIME_REMAINING_RU.md).

109.06 в работе: свежий трёхдокументный root context в Kotlin; корневой UI/lifecycle пока не завершён. Детальный прогресс 112/129, внутри 109 — 5/20.

109.06: нативный lifecycle-владелец сессии наблюдает три Room-документа и authority markers, обновляет видимый экран смены и закрывается с Activity. Fresh bootstrap и транзакционные команды не используют view-cache как проверку прав. Полный bootstrap/JS helper cutover остаются; прогресс 5/20 внутри 109, 112/129 детально.
