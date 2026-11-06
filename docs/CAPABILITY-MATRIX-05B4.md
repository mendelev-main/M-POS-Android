# 05b.4 — матрица прав существующего приложения

Статус: audit и35 direct denied variants ci-verified/API36: [CI41100246927](https://github.com/mendelev-main/M-POS-Android/actions/runs/41100246927)/f668fef.151JVM/236instrumentation/0failures/0skipped,16Python/lint/build, actual debugAPK1→2 passed. Не вводит новые группы прав. Источник/решения: [STAFF-ACCESS-05B](STAFF-ACCESS-05B.md). Будущие возможности не считаются готовыми.

## Текущие действия

| Действие | Без административного входа | Проверка внутри записи | Evidence |
|---|---|---|---|
| Чтение каталога, остатков/движений и документов | Разрешено, без изменения фактов | read repositories; initialization — только idempotent служебная конфигурация | StockRead/StockDocument tests, locked draft test |
| Корзина, параметры, скидка/ручная цена, текущий/отложенный заказ | Разрешено по действующим revisions/locks | WorkspaceRepository; это ещё не оплата | Workspace/Split tests |
| Новая смена | Только выбранный действующий employee; admin из справочника исключён | ShiftRepository → EmployeeRepository.cashier в транзакции | EmployeeRepositoryTest |
| Продажа/части оплаты/возврат, внесение/изъятие, закрытие | Только допустимая активная смена/кассир; admin login не нужен | Sale/Refund/Split/Shift/ShiftCash repository: actor/shift/revision/gate | Existing money/repository/upgrade fixtures, AdminAccessTest |
| Товар/остаток/категория/порядок/удаление и product draft save/discard | Запрещено | CatalogRepository → adminTransaction | 35 direct variants + A/AR tests |
| Home tiles, folder/layout save/delete/move/reset/reorder/step | Запрещено | WorkspaceRepository.home/layout → adminTransaction | 35 direct variants, native/read-only HOME UPDATE |
| Сотрудник save/delete и смена роли | Запрещено; при входе активный кассир всё равно неизменяем | EmployeeRepository → adminTransaction + live active actor check | 35 direct variants + ED tests |
| Поставщик save/delete | Запрещено | SupplierRepository → adminTransaction | 35 direct variants + supplier atomic tests |
| Закупка draft save/clear/place; заказ edit/cancel | Запрещено | PurchaseDraft/PurchaseOrder → adminTransaction | 35 direct variants + purchase tests |
| Приёмка draft save/clear и post | Запрещено | Receiving/ReceivingPosting → adminTransaction | 35 direct variants + receiving tests |
| Инвентаризация configure/start/count/fix/finish/cancel | Запрещено | InventoryRepository → adminTransaction | 35 direct variants + inventory tests |
| Проверка/скачивание/системная установка APK | Разрешено, по прежнему контракту; Android подтверждает установку | Update coordinator + installation/data gate | Existing actual APK1→2 driver |
| Административная панель/редакторы/confirmations | Только актуальная runtime session; logout отзывает старый UI | UI epoch + repository permit | AR tests; UI не заменяет repository guard |

35 direct variants = один параметризованный по действиям Room сценарий:35 прямых вызовов текущих семейств после logout с настоящим ownerId; каждый обязан вернуть CatalogFailure(access). После каждого сравниваются все persisted таблицы (включая business docs, stock, result и audit), а не только отсутствие UI успеха. Синтетические базовые документы созданы до logout. Некоторые action variants требуют отдельного бизнес-контекста при активном входе; их happy path/конфликты проверяются существующими предметными тестами, matrix проверяет раннюю границу авторизации.

Служебные initialize/config и cleanupPhotos не являются пользовательскими административными командами. initialize не выдаёт admin session/employee. Photo cleanup удаляет только файлы без product/draft references; это существующее безопасное обслуживание, не редактирование каталога от кассира. Фотография в карточке/черновике меняется через guarded draft/product команды.

## Дополнение05c.1

SalesReportRepository.snapshot — полный денежный read model: только active admin, чтение внутри adminTransaction; без initialize/result/audit writes. SalesReportRepositoryTest проверяет locked/stale permit и неизменность таблиц. Денежный UI05c.2 подключён только для admin (CI41186247439); cashier quantities/percentages относятся к будущим группировкам05d; прежняя матрица35 изменяющих вариантов от этого не меняется. RPT05 ci-verified/API36: [CI41184335908](https://github.com/mendelev-main/M-POS-Android/actions/runs/41184335908).

## Оставшиеся границы

Нет нового permission editor и индивидуального входа администраторов. Нет обещания защиты от root/подмены APK. Physical tablet и новые интеграционные capabilities остаются отдельными gates. В полном этапе05 ещё нет аналитики/выдачи отчётов. Поддерживаемые версии — только Android16+/API36.
