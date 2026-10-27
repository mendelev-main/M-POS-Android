# 04a.3 — формирование заказа и история закупок

Статус: PO06–PO10 ci-verified/API36. Продолжает [черновики](PURCHASE-DRAFT-04A.md), часть M11/I01/I04; не завершает весь склад04.

## Источник и выбранные границы

Pinned1682c883: `Web/js/features/purchase-orders.js` (finalizePurchaseOrder, makePurchaseLine, purchaseHistoryMarkup, viewPurchaseOrder, purchaseOrderText) вместе с корневым `android-purchase-validation.js`. Диагностический adapter сохраняет ввод и объясняет ошибки, но не меняет алгоритм journal/conversion. Отдельных purchase regression tests в доступном reference checkout не найдено; native поведенческие fixtures перечислены ниже. Reference не обновлялся.

Web atomically сохраняет pending order и purchase defaults продуктов, затем очищает RAM cart. Native требует сначала явного Save draft; Submit доступен для сохранённой неизменённой формы и отдельного подтверждения. Это осознанное дополнительное условие durable draft, не фоновая отправка. Blank/zero draft rows не включаются; хотя Web new finalize отвергает0, Web edit filters empty/zero. Native выбирает единый positive-only контракт для draft→order; пустой результат отклоняется. Неверные числа/единицы не пропускаются под видом пустой строки.

В scope: Create pending, история по дате (при равенстве стабильный ID), карточка, локальное копирование текста. Edit/cancel pending, приёмка, расходы/стоимость/сторно и отправка поставщику ещё не подключены. Кнопки будущих действий используют общую «Скоро будет подключено». Продажа, касса и stock не меняются. Нельзя трактовать «Сформирован» как «Принят» или как успешную сетевую отправку.

## Вход и сохранённый снимок

Команда Place: commandId, actorId, expected draft document revision, стабильный orderId, полный сохранённый draft. Repository требует тот же sourceDraftId и точное равенство сохранённой формы; другой редактор/несохранённый ввод отклоняются. На один sourceDraftId допускается только один orderId. Save/replay старых04a.2 fingerprint и payload не изменены.

Order содержит id, createdAt (clock), requestVersion1, status=pending, source snapshot с supplier id/revision/name и положительными строками product id/revision/name/base unit/raw quantity/order unit/pack/content unit, а также явные expectedQuantities в base unit. Пример24бутылки×500мл→12л; неизвестная упаковка→null/«уточнить при приёмке», никогда0/1. Exact BigDecimal без округления, ограничения DATA12fraction/18integer также действуют на нормализованный размер defaults. Дата в копируемом тексте явно UTC (без зависимости от смены timezone); карточка показывает локальное время. Текст/карточка используют только snapshot, не новые имена и упаковки каталога.

Ввод количества остаётся decimal text (в том числе comma), expected хранится canonical decimal/null. По сравнению с Web native не хранит misleading qty=0 для unknown expectation. Нет вычисления цены/суммы заказа: они относятся к следующей приёмке. Receipt/request versions и будущие receiving revisions не смешивать.

## Атомарная запись

InstallationGate →Room transaction→current owner→same fingerprint replay→draft CAS/live supplier+simple bindings/product revisions→append order→product purchase defaults/revision→clear saved draft+advance revision→operation result→audit. Внутри product update применяется существующий guard locked split payment. Product defaults: purchaseLabel=requestedUnit, packSize нормализована в base; physical unit или неизвестный packSize очищают старый packSize. Отдельного contentUnit в каталоге пока нет: его смысл сохраняется нормализацией, историческая строка хранит исходную единицу.

Defaults обновляют product revision, но не stock balance/revision/cost/price, recipe/modifiers и movements. При ошибке любой записи всё откатывается, включая defaults и очистку черновика. После commit UI показывает pending order. Ошибка последующего чтения не превращает известный commit в неизвестный: form очищена, orderId известен, предлагается перечитать историю.

Повтор commandId с тем же fingerprint возвращает тот же orderId до live refs/CAS и не обновляет defaults второй раз. Другой payload/orderId с этим commandId отклоняется. Два одновременных Submit имеют одного победителя. Lost reply блокирует новое действие, «Проверить результат» использует исходную команду.

## Данные и ссылки

Room schema8 без SQL migration. Namespace `purchase-orders` в `workspace_documents`, payload1 `{version,items}` и global revision; каждый order содержит version1 source draft snapshot. Неизвестная версия/повреждённый/дублированный order/sourceDraftId/неверные expected quantities блокируют чтение без сброса. Существующий `purchase-draft` payload1 остаётся совместимым. Оба namespace — постоянные данные будущего backup, не cache.

Supplier/product delete guards учитывают всю историю закупок в той же Room transaction; отвязка товара от поставщика не разрешает удалить историческую карточку. Rename/unlink разрешены и не переписывают snapshot заказа. Деактивация/сессии05 — отдельный scope; пока команда доступна только локальному owner, проверка в repository.

## Интерфейс

Общие Mpos button/Close/Dialog/Field/Select, существующий верхний navbar. History переключает локальную панель, не стирает dirty form. Submit подтверждается с описанием defaults и очистки draft. Карточка показывает supplier snapshot, дату, request ID, строки и ожидаемые quantities, статус «Ожидается приёмка». Текст доступен для выделения и локального копирования; external send отсутствует. Detail scroll ограничен доступным окном, footer действий остаётся доступным. После recreation история и открытая карточка восстанавливаются; уже закрытая карточка самопроизвольно не открывается повторно.

## Acceptance

| ID | Проверка |
| --- | --- |
| PO06 | Saved-only submit, positive lines, unknown pack null, pending snapshot и defaults |
| PO07 | Replay/concurrent winner/owner/gate/CAS/stale refs, каждый write fault/SQLite abort/lost reply |
| PO08 | Restart и historical text после metadata rename, delete guards после unlink |
| PO09 | Confirm→detail→history/recreate; stock/cost неизменны, общие controls, Soon receiving |
| PO10 | Actual debug APK replacement сохраняет pending order/source/defaults/result и не дублирует replay |

Tests: PurchaseOrderRulesTest3 JVM, PurchaseOrderRepositoryTest4, PurchaseViewModelTest+2, PurchaseOrderUiTest1, PurchaseOrderUpgradeTest1 API36. Старые draft tests остаются. CI execution/evidence отдельно после результата. Physical tablet, dark/150%/forced process-death UI pending.

Следующая часть: bounded edit/cancel pending с revision и историей, затем receiving draft/commit по D03. Согласованные rules receiving: weighted cost;3из10 закрывает заказ с shortage7;12из10 требует явного подтверждения. Не выполнять receiving напрямую при Submit.

## Итоговая проверка

[FullCI40483522354](https://github.com/mendelev-main/M-POS-Android/actions/runs/40483522354)/dbd5fb2 SUCCESS:91JVM/116API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/11UIseed/6upgrade fixtures (3financial+supplier+purchase draft+purchase order), actual debugAPK1→2. PURCHASE ORDER UPDATE PASSED: pending snapshot/raw quantities/requestVersion1, cleared draft, current product metadata/defaults and replay without duplicate preserved. Existing sale/split/refund/catalog/workspace/parked/home/shift assertions также прошли.

HTML116/0/0 и91JVM/0 проверены. Purchase-order/history1600×1125 просмотрены: общий modal Close/кнопки, snapshot24×500ml→12l, pending status и строка истории доступны без перекрытий, клавиатуры или постороннего окна. UI test также проверяет первый клик New order из истории и recreation без повторного Submit. Physical/dark/150%/forced cold-process UI отдельно. Schema8, stable0.1.74.1 и подпись не изменены; код только develop.
