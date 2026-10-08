# Реестр задач миграции на Kotlin

> Детализация 109 (08.10.2026): **7/20 — 35,00%**, осталось 13. Общий детальный план: **114/129 — 88,37%**, осталось 15. Крупные этапы: **107/110 — 97,27%**. [Подэтапы, критерии и правила подсчёта](../specs/109-native-runtime/tasks.md). Проценты относятся к количеству задач; физическая приёмка 110 ещё не выполнена.


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


109.06 завершена: Kotlin выбирает текущую смену/сотрудника и задаёт последовательность recover → hydrate → activate → ready. Повторный запуск/импорт сериализованы; старые поколения и ответы отбрасываются. Изменение root-документов во время загрузки блокирует активацию, следующий запуск читает свежую сессию. После root-операций выбранные записи обновляются до acknowledgement, без повторной передачи истории. Foreground читает свежий root через FIFO; команды по-прежнему проверяют права в своих транзакциях. 6/20 внутри 109; 113/129 детально; 107/110 крупных этапов. Следующая задача — 109.07. JS-адаптеры исполнения recovery/hydration/effects остаются в 109.09–109.18; WebView удаляется в 109.19.

## Исправление обратной связи интеграционных тестов — 08.10.2026

[Telegram / WEB / LAN-print](CONNECTION_TEST_FIX_RU.md): corrected callback, correlated terminal result, bounded wait, duplicate/stale guards. 572/572 JS, 457/457 JVM; lint 0 ошибок / 22 предупреждения. Физическая проверка pending. Счётчики после независимого завершения 109.06: 107/110, 109 6/20, детальный план 113/129 — 87,60%; исправление подключений не закрывает новые подэтапы.

109.07 — нативная авторизация сотрудников подключена: JS authorization marker не разрешает смену роли/удаление; пароль вводится в MPosEmployeeAuthorizationDialog и проверяется в транзакции MPosEmployeeCommand. Ordinary create/edit, last-admin demotion, self/admin deletion guards и supplier permissions сохранены. Проверки: 575 JS / 464 JVM; lint 0 ошибок / 15 прежних предупреждений. Полные критерии 109.07 ещё не выполнены: editor/product/category delete и settings gates остаются. Прогресс без увеличения: 6/20 внутри 109, 113/129 детально; физические кейсы pending.


### 109.07: реквизиты организации — 08.10.2026

`MPosCompanyCommand` сохраняет реквизиты в одной Room-транзакции с проверкой
актуальной роли администратора, открытой смены и ожидаемого документа.
Понижение сотрудника, закрытие/смена смены и устаревшая форма отклоняют запись.
JS обновляет состояние только после подтверждения commit; неопределённый
результат по таймауту блокирует повтор до перезапуска, автоматического повтора нет.
Ключ `company` принадлежит Room через существующий workspace storage; старый
shadow не перезаписывает его. Импорт/экспорт v13, JSON null/отсутствие и неизвестные
поля сохраняются. Ручное редактирование по прежней логике оставляет четыре поля.
Флаг `MPosNativeCompanyCommandsEnabled=false` возвращает прежние обработчики,
сохраняя нативное хранилище. Внешний вид формы не менялся.

Добавлены JS/JVM проверки подтверждения записи, прав, конфликта данных,
таймаута и отката Room. **Локально не запускались** согласно AGENTS.md §17;
результат проверяется в GitHub Actions (см. `docs/CI_AND_APK_SIGNING_RU.md`).
Физическая приёмка ожидается. 109.07 остаётся в работе: следующие границы —
защищённые операции редактора/категорий и остальные настройки.
Прогресс: **107/110 крупных этапов; 6/20 внутри 109; 113/129 детальных задач**.


### 109.07: удаление каталога и разрешения редактора — 08.10.2026

Подключены `MPosCatalogDeleteCommand` и `MPosProductEditorCommand`.
Удаление товара/пустой категории, плиток и ссылок навигации атомарно: ошибка
любого сохранения откатывает всю операцию. Защита чеков для возврата,
использования в составе и непустых категорий читает актуальную Room.
Администратор открытой смены удаляет товар без пароля; остальные — с паролем;
категория всегда требует пароль, включая администратора. Смена не требуется
при удалении с правильным паролем — существующее разделение сохранено.

`MPosEditorAuthorization` принимает пароль только из нативного поля.
Разрешения stock/no-stock временные, привязаны к товару и исходному документу,
не входят в Room/backup и теряются после перезапуска. Поддельный JS-флаг не
разрешает запись. Сохранение карточки повторно проверяет роль, документы,
защищённые остатки/конфигурацию/WEB и политику смены типа внутри транзакции.
Разрешение погашается после commit; при известном откате сохраняется для
исправления формы. Таймаут запрещает повтор до перезапуска.

Сбор полей, рецептов/фото и последующие эффекты остаются адаптерами, относящимися
к 109.09/109.16–109.18. Новый точечный hook сохранения в product-persistence.js
обратим; sync проверяет границу до замены файлов, parity-тест восстанавливает
только точный hook/timeout guard перед сверкой исходного SHA. Импорт и backup
v13 используют существующее хранилище без editor grants. Явные флаги rollback:
MPosNativeCatalogDeleteEnabled, MPosNativeEditorAuthorizationEnabled,
MPosNativeProductEditorCommitEnabled (false возвращает прежнюю границу).

Написаны проверки native transaction rollback, смены роли, stale documents,
поддельных/устаревших grants, таймаутов и UI acknowledgement. **Не запускались
локально:** GitHub Actions выполняет JS/JVM/lint по AGENTS.md §17.
Планшетная приёмка pending. Остались settings gates, WEB toggle товара и
защищённые операции лояльности; 109.07 остаётся in_progress.
Счётчик не завышен: **107/110; 109 — 6/20; детально 113/129 (87,60%)**.


### 109.07: реализация завершена — 08.10.2026; CI pending

Оставшиеся настройки backend/Telegram и WEB toggle товара проверяют актуальную
роль нативно перед Room commit. Панель администратора, реквизиты и защищённые
проверки Telegram/WEB читают свежие права перед действием. Backend-test и ручной
catalogue sync ждут acknowledgement настроек; автоматической синхронизации нет.
Network/telegram сохраняют исходные JSON/v13 и неизвестные поля при импорте;
первый save network сохраняет уже созданный startup device key. Редактирование
Telegram по прежней логике заменяет поля и сохраняет lastMonthlyWarehouseSent.

Корректировка лояльности использует нативный ввод пароля и HTTPS, прежний endpoint,
поля и серверную проверку. Идентификатор/имя администратора и подключение берутся
из свежей Room, а не из JS. Пароль не входит в bridge, журналы и backup. HTTP
запрос выполняется после закрытия DB-транзакции вне storage FIFO; timeout 5 секунд,
автоматического повтора нет. Создание/редактирование поставщиков и незащищённые
действия сохраняют прежние права; права лояльности на сервере не ужесточены.

**Согласованное пользователем правило:** после неподтверждённой корректировки
повтор возможен только после проверки актуального баланса. Нативная отметка
mpos_loyalty_adjustment_verification_v1 создаётся до запроса, сохраняется при
обрыве/timeout/5xx и после перезапуска. Она отдельно от кассового recovery и не
блокирует продажи. Успешный ответ/явное отклонение снимают её; при неопределённом
результате только успешный нативный GET баланса конкретной программы позволяет
вернуться к ручному вводу. Форма показывает актуальный прогресс/подарки и сбрасывает
дельты; проверка баланса не отправляет корректировку. Старый token не снимает
отметку новой операции. Отметка не содержит пароль/токен Telegram/device key;
внутренний служебный ключ не меняет формат бизнес-данных backup v13.

Rollback flags: MPosNativeAdminAccessEnabled, MPosNativeAdminSettingsEnabled,
MPosNativeProductWebEnabled, MPosNativeLoyaltyAuthorizationEnabled. Прежние
обработчики доступны явно до приёмки. Подготовленная, но уже закрытая нативная
форма не запускает поздний финансовый HTTP запрос.

Критерии реализации 109.07 закрыты; **новые проверки не запускались локально**,
результат GitHub Actions и физическая приёмка ещё не подтверждены. Это завершение
инженерной задачи, а не заявление об успешных тестах или полном уходе от WebView.
Прогресс: **107/110 крупных; 7/20 внутри 109; 114/129 детально (88,37%)**.
Далее 109.08 — нативное состояние навигации, выбора разделов, поиска и Back.

### 109.08 начата: владелец выбранного раздела

`MPosWorkspaceNavigationOwner` владеет tab и revision в StateFlow на время Activity.
Первое initialize принимает стартовый раздел; повторная инициализация не заменяет
нативный выбор. `setTab` отправляет прямую команду без DOM target, и только ответ
меняет совместимую JS-проекцию и вызывает render. При быстрых кликах старый ответ
не возвращает интерфейс к предыдущему разделу. Повторный выбор не создаёт историю
Back и не увеличивает revision; поиск/корзина/смена не меняются. Таблица разделов
не ужесточена: неизвестные строки сохраняются как в reviewed handler. Нестроковый
legacy input и явный MPosNativeWorkspaceNavigationEnabled=false используют rollback.

Проверки StateFlow, повторной инициализации, быстрых кликов и изоляции заказа
написаны, локально не запускались — выполняет Actions. 109.08 в работе:
поиск, выбор/Back, нативные controls/read models и остальные DOM route targets
ещё остаются. **114/129; 109 — 7/20; 107/110 крупных**, физическая приёмка pending.


### Исправление проверок и продолжение 109.08 — 08.10.2026

По запросу пользователя воспроизведены проверки Actions локально без сборки APK:
GitHub API вернул Forbidden, поэтому результат исходных удалённых запусков
не установлен. Найдены семь JS-сбоев тестовых окружений: отсутствовали
currentShiftEmployeeIsAdmin и criticalStorageRecoveryPending; тест ручного
menu sync не ожидал асинхронного сохранения сетевых настроек. Исправлены fixture
и ожидание создания HTTP-запроса, исходные assertions сохранены. После
исправления 626/626 JS и 2/2 Python проверок версий прошли.

В 109.08 добавлен нативный запрос поиска в MPosWorkspaceNavigationOwner:
первичная инициализация сохраняет query, повторная не заменяет его; изменение
только query увеличивает revision, повторное значение — нет. Таблицы и поиск
не изменяют корзину/смену/stock и не сохраняются в v13. JS сохраняет прежнее
String(value||'') и передаёт подтверждённый запрос reviewed-фильтру плиток.
Поздние ответы после нового ввода, смены раздела/категории/папки либо очистки
поиска не меняют экран. Explicit rollback сохраняет исходный onSearch.

Полный JS-набор после продолжения: **630/630**, JVM **509/509**, без ошибок
и пропусков; lint **0 ошибок / 15 предупреждений**, Gradle BUILD SUCCESSFUL.
Это локальное воспроизведение CI по прямому запросу пользователя; результат
нового удалённого запуска Actions ещё не подтверждён. APK не собиралась. DOM-фильтрация и переходы category/folder/Back ещё
сохраняются: это частичный шаг 109.08, а не завершение этапа.
Прогресс без увеличения: **114/129**, внутри 109 **7/20**, крупных **107/110**.
Физическая приёмка остаётся pending.


### 109.08 — владелец маршрутов workspace, 08.10.2026

Kotlin владеет category/folder/edit состоянием. Подготовка перехода не меняет
выбор; одноразовое подтверждение проверяет актуальность owner, а для папки —
согласованные текущие products/posNavigation из Room. Сохранены FIFO и порядок
Back (окно папки → inline folder → root), очищение поиска и edit effects.
Обычные переходы не перечитывают каталог; бизнес-документы и v13 не меняются.
Явные rollback-флаги сохранены. Новые проверки написаны для Actions, локально
не запускались. Предыдущий 98f5f73 подтверждён успешным Actions 37744808114.
Этап 109.08 ещё в работе: native controls/read models, Android Back, DOM
route targets и согласование нового runtime остаются. Поздний accept
отменяет только свой выбор, сохраняя более новые tab/query. **114/129**, 109 **7/20**.


### 109.08 — системный Back и restart/import, 08.10.2026

Прежний порядок системного Back теперь выбирает Kotlin: import/modal/warehouse/
receiving/background после native settings. Повторное нажатие и поздние ответы
после замены окна/pause/destroy не выполняют старые эффекты. Root startup
сбрасывает временного владельца навигации; только ready разрешает новый выбор,
старые tab/search/route ответы не возвращают предыдущий runtime.
DOM presence и экранные close handlers пока остаются переходными адаптерами.
Тестовый FIFO fixture исправлен; Actions 37747195877 для 3f87071 успешен
(tests/lint и APK). Новые Back/runtime проверки ожидают следующий Actions.
Этап 109.08 не закрыт; **114/129**, внутри 109 **7/20**.


Проверка кода 8ca9dd6: [Actions 37748093813](https://github.com/mendelev-main/M-POS-Android/actions/runs/37748093813) успешно завершил job Tests and lint (JS, правила версий, Kotlin и lint). Подписанная APK собирается отдельным job. Локальные тесты/сборка не запускались; физическая приёмка pending.


### 109.08 — нативная панель workspace, 08.10.2026

Добавлены MPosWorkspaceToolbarModel и toolbarView: заголовок, «Назад»,
«Раскладка» и «Закрыть папку» формируются из Kotlin owner; имя папки — из
актуального Room posNavigation. Эти кнопки отправляют типизированную команду
непосредственно в Kotlin через FIFO хранилища, без нажатия скрытого HTML узла.
Revision и expected state защищают от старой панели и двойного перехода;
неприменённый ответ отменяет только свой принятый маршрут. Документы товаров,
корзины, оплат и смен не записываются. Категория без папки не читает Room;
модель панели кешируется между изменениями заказа, сбрасывается при навигации
и смене runtime. Сетка теперь исключает hidden плитки текущего JS фильтра.

Используется прежний MPosNativeTheme/Manrope, светлая/тёмная палитры и 48dp
кнопки. При ошибке модели остаётся reviewed presentation; явный rollback —
MPosNativeWorkspaceToolbarEnabled=false. Написаны проверки модели/Room,
типизированного нажатия, устаревшего ответа, оплаты во время чтения панели,
hidden плиток, кеширования и rollback. Локальные тесты и APK не запускались;
выполнение этого изменения ожидается в Actions, физическая приёмка pending.

109.08 остаётся in_progress: общие вкладки/поле поиска и controls редактора
раскладки ещё используют DOM, global Back ещё получает presence из адаптера.
Каталог/корзина read models относятся к 109.09. Прогресс не увеличен:
**114/129 (88,37%)**, 109 **7/20**, крупных **107/110**.

Проверка toolbar-кода `7bd7ecc`: [Actions 37750512318](https://github.com/mendelev-main/M-POS-Android/actions/runs/37750512318) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK собирается отдельным job; физическая приёмка pending.


### 109.08 — исправление подключения native UI, 08.10.2026

Проверка по сообщению с планшета выявила дефект: приложение объявляет `let state`,
а workspace/settings UI читали `window.state`. В браузере эти значения различны;
моки прежних тестов помещали state в window и пропускали дефект. Адаптеры workspace,
settings UI, platform settings и employee confirmation теперь выбирают lexical
state, сохраняя property fallback для совместимости. Regression fixtures используют
`let state` без window.state: включение экрана/кнопок, граница подтверждения принтера,
тема и подтверждение сотрудника. Это исправление интеграции, не новый закрытый этап.

Экран смены больше не исчезает при открытии нативного cash Dialog: фон остаётся
нативным, кнопки блокируются до закрытия формы, повторное нажатие не вызывает действие.
Hidden compatibility overlay отличает native Dialog от видимого HTML modal;
для видимого HTML modal native screen скрывается, чтобы не перекрывать окно.
Ошибка открытия возвращает управление; смена раздела/rollback/скрытие приложения
по-прежнему скрывают экран. Добавлены JS и native view проверки фонового слоя,
блокировки, возврата и stale replies. Формулы и сохранение cash movement не изменены.

Tests authored, GitHub Actions pending; локальные тесты/сборка APK не запускались.
Физическая проверка исправлений pending. Прогресс **114/129 (88,37%)**, 109 **7/20**;
109.08 остаётся in_progress, следующие controls — вкладки/поиск и DOM route targets.


### 109.08 — типизированный выбор категории с native плитки

Native category tile теперь передаёт openCategory + стабильный ID прямо в Kotlin
через ту же FIFO/revision проверку. Mounted HTML click handler не вызывается;
название карточки не подменяет ID. Прежние trim, очистка поиска/folder и выход из
editMode сохранены; бизнес-документы не записываются. Старый view token и повторное
нажатие отклоняются. Product/folder tiles и явный rollback пока используют reviewed
handlers; их read models ещё впереди. Написаны JS, Room и native view проверки,
выполнение следующего коммита в Actions pending. 109.08 in_progress; **114/129**.

Исправление `8855327`: [Actions 37752933255](https://github.com/mendelev-main/M-POS-Android/actions/runs/37752933255) — Tests and lint успешно (JS, Kotlin, lint); APK отдельным job, проверка на планшете pending.

Native category tile `a645d39`: [Actions 37753269784](https://github.com/mendelev-main/M-POS-Android/actions/runs/37753269784) — Tests and lint успешно. APK исправления `8855327` уже опубликована как Actions artifact M-POS-Android-release-0.1.173-1; категория собирается отдельным job. Физическая проверка pending.

## Стабильное обновление рабочей зоны — 08.10.2026

[Подробности и проверка](NATIVE_WORKSPACE_SMOOTHNESS_RU.md): обновления остатков/количества/итогов и строк корзины сохраняют Views; действие/token обновляются без пересоздания сетки. Cash/card/secondary/outline передаются в shared theme; busy блокирует повторы без серого мигания рабочих кнопок. Новые тесты выполняет Actions, физическая плавность pending. Новых завершённых migration IDs нет: 114/129 (88,37%), 109 — 7/20.


## Рабочая зона — визуальный паритет iPad, 08.10.2026

См. [перенос исходной раскладки](NATIVE_WORKSPACE_IPAD_PARITY_RU.md). Изменения ограничены рабочей зоной; CI и физическое сравнение pending. Новых завершённых этапов нет: 114/129 (88,37%), осталось 15; 109 — 7/20.


### 109.08 — открытие папки с native плитки (08.10.2026)

Folder tile передаёт openFolder + ID непосредственно в Kotlin FIFO, без вызова
mounted HTML click. Repository проверяет expected revision и актуальные products/
posNavigation из Room при подготовке и принятии маршрута; удалённая/неизвестная
папка отклоняется. Обычная hydration уже инициализирует оба owned документа;
навигация не создаёт authority markers и не перезаписывает документы из UI.

Ответ renderFolder проверяется до изменения JS projection: category должна
совпадать с выбранной, ID — строка. После принятия остаётся прежняя category,
поиск очищается, открывается folder modal; stale/некорректный ответ отменяет только
свой маршрут. Back закрывает папку без выхода из категории. Shared appearance и
retained Views актуального main сохранены. Product tile/renderer/read models пока
сохраняют compatibility path (109.09). Добавлены JS/Room/native view проверки;
Actions следующего коммита pending, физическая приёмка pending.

109.08 in_progress: native tabs/search controls, редактор раскладки и global Back
presence остаются. Прогресс **114/129 (88,37%)**, 109 **7/20**.

Проверка folder-кода `c7e976a`: [Actions 37764445102](https://github.com/mendelev-main/M-POS-Android/actions/runs/37764445102) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK отдельным job; физическая приёмка pending.


### 109.08 — нативные основные вкладки верхней панели, 08.10.2026

MPosWorkspaceHeaderModel формирует reviewed destinations/labels и selected state
из Kotlin owner: M POS, Заказы, Приёмка, Чеки, Аналитика, Бронирования, Настройки.
MPosWorkspaceHeaderController отправляет selectHeaderTab прямо через native FIFO,
без нажатия HTML-кнопки. CAS по expected state/revision и token подтверждают
переход; discard отменяет только свой выбор, сохраняя новый search и последующие
явные selections (в том числе повторный выбор той же вкладки). Restart/import
сбрасывает outstanding selection. Native completion не разблокирует новую команду.
Header read не читает Room/catalog и не создаёт authority markers или документы.

Native controls используют navy/Manrope/selected pill и 48dp touch targets;
геометрия трёх групп ещё согласуется с source topbar. События, напоминание inventory
и shift pill остаются под их reviewed domain handlers. При оплате, редакторе,
модальном/складском overlay панель скрывается. Если source topbar требует внешнего
горизонтального скролла (группы за viewport), сохраняется исходный scroller;
при подходящей геометрии native controls возвращаются автоматически. Это временная
граница presentation, не утверждение полностью нативной верхней панели.

Rollback: MPosNativeWorkspaceHeaderEnabled=false возвращает исходные кнопки;
MPosNativeWorkspaceNavigationEnabled=false также выключает native header.
Добавлены Kotlin owner/model, Room boundary, Android View и production lexical
state JS проверки: busy/stale/runtime/late read, несовпавший DOM, modal/payment,
source rollback и narrow→wide. Локальные тесты/lint/APK не запускались; Actions
следующего коммита pending. Физическая оценка тем/портрета/font scale pending.

109.08 остаётся in_progress: поиск/filter controls, shift-tab control, controls
редактора раскладки и global Back presence ещё впереди. Domain/catalog read models
и удаление presentation geometry относятся к дальнейшим runtime этапам.
Прогресс **114/129 (88,37%)**, внутри 109 **7/20**, крупных **107/110**.

Fixed header `b0196c5`: [Actions 37767655391](https://github.com/mendelev-main/M-POS-Android/actions/runs/37767655391) — Tests and lint успешно (JS, правила версий, Kotlin, lint). Адаптер зарегистрирован в sync-pos-assets и parity fixtures; первый run 37767548247 до регистрации упал на JS проверках. APK отдельным job; физическая проверка pending.


### 109.08 — фильтрация текущих плиток в Kotlin, 08.10.2026

Live onSearch передаёт только типы/ID текущих sections-wrap плиток в
selectFilteredSearch. MPosWorkspaceSearchModel ищет по названиям авторитетного
каталога Room: trim по JavaScript whitespace, русский lower-case, substring,
строго строковые ID и первое совпадение getProduct. Пустой запрос показывает
все плитки, включая папки и отсутствующие товары; при непустом папки/категории
скрываются. Область поиска не расширена до всего каталога или folder modal.
Render-time фильтрация renderPosScreen остаётся в 109.09, нового search UI нет.

Одна native команда возвращает query/token/visibility. JS только проецирует
подтверждённую маску, без originalSearch и повторного render. Проверяются runtime,
последний запрос, прежний query/context, identity grid/плиток и type/ID. Stale или
некорректный ответ отменяет только свой query; новые query, routes, tab и import
не откатываются. Отмена последнего из нескольких неподтверждённых запросов
возвращает последний спроецированный query. Ошибка authority/модели не меняет query.

Repository сохраняется на время native FIFO; индекс имён перестраивается только
при изменении сырого Room catalog document. Каждая непустая фильтрация проверяет
текущий документ и authority; пустой запрос/отсутствующий grid не требуют SQL.
Данные каталога, корзина и финансовые документы не записываются. Rollback через
MPosNativeWorkspaceNavigationEnabled=false сохраняет reviewed onSearch.

Добавлены shared reference/Kotlin fixtures (русский текст, Unicode, whitespace,
строгие ID, дубликаты и coercion имён), Room freshness/no-writes/authority,
query token/runtime и JS stale/malformed projection проверки. Локальные тесты,
lint и APK не запускались (§17 AGENTS); GitHub Actions следующего коммита pending.
Физическая приёмка pending. 109.08 in_progress: native search presentation,
shift-tab control, редактор раскладки и global Back presence остаются.
Прогресс **114/129 (88,37%)**, внутри 109 **7/20**, крупных **107/110**.

Live search `1564939`: [Actions 37769761293](https://github.com/mendelev-main/M-POS-Android/actions/runs/37769761293) — Tests and lint успешно: JS/reference, правила версий, Kotlin и lint. APK собирается отдельным job; физическая приёмка pending.


### 109.08 — полный участок кнопки смены, 08.10.2026

Нативная shift-pill включена в MPosWorkspaceHeaderController: Manrope/navy,
white text, зелёный/красный индикатор, compact pill и 48dp. Label и status берутся
из первого открытого shift авторитетного Room document, не из текста/onclick DOM.
Формат employeeShortName сохранён, включая whitespace/первые два UTF-16 initials.
Source geometry и narrow topbar fallback остаются до native root presentation.

shiftHeaderView читает актуальный shifts document без записи/инициализации.
selectShiftHeader сравнивает его digest и expected navigation/revision в одной
Room transaction. При открытой смене подтверждается native tab=shift; при
закрытой — effect=openShift. stale changes не переключают tab/не запускают окно.
Устаревший принятый переход отменяется только своим headerToken.

NativeOpenForm.openNative запускает MPosShiftOpenDialog без openOriginal,
HTML form, select/password DOM fields. Это production путь новой кнопки; другие
legacy entry points пока относятся к следующему участку. Сумма переноса,
сотрудники и роли читаются existing native opening repository; credential
остаётся внутри Kotlin dialog и существующего native opening command.
Existing commit/ack, recovery gate и post-commit уведомления сохранены.
Cancel/fallback, pending busy и import/runtime invalidation обработаны;
SystemBack учитывает token нативного окна даже без HTML modal. Explicit fallback
возвращает reviewed форму. Global source geometry/Back presence других overlays
не удалены и этап 109.08 ещё не закрыт.

Добавлены JS boundary, Kotlin model/repository и View проверки: open/closed,
первый открытый shift, label/status, freshness/authority/no-writes, stale query,
дубли, busy/cancel/fallback/runtime/Back без DOM. Локальные тесты/lint/APK не
запускались (§17 AGENTS). Actions следующего коммита pending; tablet pending.
Список исполняемых участков: docs/NATIVE_MIGRATION_EXECUTION_RU.md.
Прогресс **114/129 (88,37%)**, 109 **7/20**. Участок не добавляет новую задачу
в общий счётчик: входит в стабильный ID 109.08.

Shift header `b38a2c4`: [Actions 37772473180](https://github.com/mendelev-main/M-POS-Android/actions/runs/37772473180) — Tests and lint успешно (JS, Kotlin, lint); участок вычеркнут в execution checklist. Tablet acceptance pending.


### 109.08 — все входы открытия смены без HTML формы, 08.10.2026

Production openShiftModal теперь сразу вызывает NativeOpenForm.openNative:
кнопка workspace, действие native shift screen и topbar используют один путь.
Hidden select/password/HTML modal не создаются; старый openOriginal вызывается
только при explicit compatibility flags или «Прежняя форма». Повторное нажатие
сохраняет тот же token/диалог и не отправляет вторую команду. Отказ bridge не
переключает молча на старую форму. Existing save/ack/recovery/effects не изменены.

NativeOpenForm уведомляет presentation через mpos-native-open-state на open и
abandon. Header скрывается и возвращается по состоянию окна; shift screen
остаётся native под диалогом, получает только block/unblock, без нового Room read.
Workspace сохраняет native фон и блокирует команды; закрытие окна разблокирует
его без HTML MutationObserver. Это блокировка окна, а не recovery failure:
сообщение о перезапуске при ней не появляется. Back и restart/import защищены
existing token/runtime guards предыдущего участка.

Написаны проверки всех default entries, повторного нажатия, отказа bridge,
событий без DOM, блокировки/разблокировки native shift/workspace/header, сохранения
и explicit compatibility path. Локально JS/JVM/lint/APK не запускались (§17);
Actions следующего коммита pending. Физическая приёмка pending.
109.08 остаётся in_progress; **114/129 (88,37%)**, внутри 109 **7/20**.
