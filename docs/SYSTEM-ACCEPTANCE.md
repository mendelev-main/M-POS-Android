# Сквозная приёмка и готовность этапов

Статус: план будущих проверок системы; уже выполненные Actions для 00/00.1 перечислены в [PARITY.md](PARITY.md) и build-log. Наличие таблицы ниже не означает реализованный тест.

## Как читать системные критерии

SYS/RES/ACC — дополнительные test case IDs, а не новые продуктовые этапы. Они уточняют P/W/S/I/H/C/B/D/N. Обнаруженная проблема записывается у соответствующего acceptance, не закрывается отдельной зелёной строкой инфраструктуры.

| Case | Последовательность | Доказательство / acceptance |
|---|---|---|
| SYS-01 | пустая установка → simple+category → restart → редактирование → отказ записи | Room содержит только committed revision, draft доступен; P01/P03/P10 |
| SYS-02 | unit→recipe→modifier→cart→park/unpark | точные потребности и один reserve, без stock movement до оплаты; P04–P07/W02–W04 |
| SYS-03 | каталог изменён при открытом current/parked → reopen → revalidate | старый draft сохранён, разница показана, sale stale запрещена; W06/S01 |
| SYS-04 | два конкурентных sale одной сессии / повтор commandId | один receipt, один stock effect, понятный второй result/conflict; S01/S02 |
| SYS-05 | sale→смена рецепта/флагов→refund→повтор refund | обращается исходный snapshot один раз; S03 |
| SYS-06 | sale/CloseShift одновременно → restart → отчёт | согласованный состав смены, сверяемые allocations; H01/H02/S05 |
| SYS-07 | физический пересчёт→sale+refund→apply старого baseline | явный повторный подсчёт, без перезаписи новых движений; I02/I03 |
| SYS-08 | logout при открытом catalog/restore/settings confirm | вызов use case отклонён, данные прежние; H03/H04 |
| SYS-09 | backup→staging restore→kill на каждой границе→reopen | активен целый прежний/подтверждённый новый dataset, без смешения фото; B01–B05 |
| SYS-10 | outbox запрос принят сервером→timeout→restart | reconcile/тот же ключ; без второй mutation; C03/N03 |
| SYS-11 | печать partial→повтор запроса operator | receipt не повторён; неопределённая бумага показана; D01/D02 |
| SYS-12 | APK upgrade при наличии Room draft/history/outbox | миграции сохранны, admin session не активна, pending effects не удвоены; P10/W06/S01/U04 |
| SYS-13 | offline длительно→local order/sale→reconnect/SSE replay | локальные данные сохранны, remote order один, backlog виден; N02/N03/N05 |

## Среды и ограничения

JVM проверяет domain без Android: graph, units, quantities, rounding после утверждения policy, state transitions. Room instrumentation проверяет actual constraints/transactions/migrations, concurrent writers и reopen. Compose tests проверяют наблюдаемые формы, восстановление/navigation, отсутствие ложного успеха. Внешние тесты обновления APK переживают замену процесса приложения; существующая CI driver схема остаётся основой.

Emulator API 28/35 — текущая база CI. Физический планшет нужен для доступности UI/ориентации/клавиатуры/picker и APK permissions/OEM, принтер/ящик — для D05. Успех JVM не закрывает device criterion. Тесты не используют production деньги, реальные контакты/заказы, печать и Telegram без отдельной задачи.

Перед 01 задаётся синтетический профиль данных: размеры каталога/рецепт-графа/фото/черновиков. Перед эксплуатацией — согласованные объёмы истории и backlog, модель планшета/память, измеряемые бюджеты старта/поиска/пересчёта. Конкретные миллисекунды и «поддерживает N товаров» нельзя объявлять без замера. Нагрузочный fixture фиксирует seed/объём/устройство/build mode; сравнение выполняется на одинаковой базе.

## Решения до начала кода

| Gate | Что закрыть | Где записать |
|---|---|---|
| 01a | limits полей, decimal converter/пределы, initial currency policy, Room schema, single-owner граница | context01 + schema/fixtures |
| 01b | precision при recipeYield=3, стоимость вложенности, graph limits | domain fixtures + [DATA.md](DATA.md) |
| 02 | layout hierarchy, stable merge key, revalidation/reservation ownership | context02 + JVM/Room examples |
| 03 | денежная policy, способ/части оплаты/возврата, минимальные правила активной смены до полноценного05 | ADR + S04 fixtures + изменение плана при необходимости |
| 04/05 | valuation закупки, baseline lifecycle; роли, бизнес-день/закрытие | context04/05 + I/H fixtures |
| 06 | consent и server loyalty/offline rules | product decision + fake contract |
| 07 | форматы/replace-merge/activation checkpoints, поведение pending external effects | B fixtures + restore protocol |
| 08/09 | профиль реального оборудования; backend schemas/replay/idempotency/ownership/staging | adapter contracts + device/staging evidence |

Неразрешённые gate не мешают уточнять документацию или делать независимый предыдущий инкремент. Они запрещают выдавать придуманное поведение за готовое согласованное правило.

## Definition of ready / done

Ready инкремента: выбран конкретный сценарий/IDs, прочитан pinned handler+adapter, нет неизвестности, меняющей его результат, заданы fixtures и схема/миграция при необходимости. Done: код и meaningful tests, CI evidence для того же commit, ошибки/повтор/restart проверены, docs/parity актуальны; device pending указан явно. Подзадача 01a не закрывает весь этап01.

В PR указывать проблему/новое поведение, schema/version changes, выполненные команды/Actions и реальные ограничения. Приоритет регрессий определяется изменением: docs-only не требуют новой APK; изменения схемы требуют migrations; sale требует precision/duplicate/failure evidence. Не запускать всё многократно без изменения/новой причины.

## Эксплуатационная передача

Перед использованием для настоящих операций: принятое решение currency/rounding/shift/roles, поддержанная схема/backup, проверенный upgrade на целевом планшете, достаточное хранилище, возможность открыть/экспортировать данные, известный порядок восстановления. Это этапные критерии рабочей версии, не заявление о готовности текущего bootstrap. Выпуск приложения сам по себе не означает поддержку эквайринга/фискального оборудования.
