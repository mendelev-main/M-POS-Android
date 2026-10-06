# 079 — команды отложенных заказов в Room

MPosParkedCommand владеет переходами списка parked: append hold, filter по
строгому id для resume/delete, patch kitchen print status. Native заново строит
append/filter и сравнивает результат с compatibility candidate; полное expected
сверяется с актуальным Room JSON в транзакции. Порядок, неизвестные поля, items,
customer extensions, WEB/comment metadata не теряются. При duplicate legacy id
resume использует первый заказ, filter удаляет все совпадения как source.
Новые hold id не должны совпадать с существующими.

Hold атомарно сохраняет parked + пустой currentOrderSession. Resume атомарно
сохраняет восстановленную session + parked без выбранного заказа. Delete не
меняет текущую session. Native проверяет отсутствие recovery journal, authority,
пустую корзину resume по входному состоянию, items/customer defaults, label,
orderType, comment/source/WEB, delivery Number/strict selected, kitchen bool и
existing printedItems. Сохраняет raw совместимые документы и их проекции.
На ошибке второго документа Room откатывает и raw, и проекции первого.

Снимок hold (цены, employee, category), timestamp/uid, kitchen delta/snapshot и
legacy printedItems reconstruction пока создаются reviewed JS. Они остаются
совместимыми входами; весь builder/printing/loyalty lookup не объявляется native.
Типы/расчёты оплаты уже имеют отдельные native границы. UI списка/модали пока
WebView, тема не меняется. Никаких новых таблиц/JSON keys/изменений backup v13.

Adapter перехватывает только park-order/resume-parked/delete-parked/
park-order-print-state в commitCriticalStorage. Pending cart/split/context
запрещает запись; другие операции проходят через прежний dispatcher. Source
после ack меняет state/очищает cart, печатает, либо восстанавливает/рендерит.
Kitchen print выполняется после durable hold; отдельная запись print status
также native. Customer loyalty network начинается после durable resume.
Нет availability/catalogue publication и автоматических retry.

На timeout статус commit неопределён: criticalStorageRecoveryPending блокирует
повторные critical команды до перезапуска/восстановления Room. Эти команды не
имеют нового idempotency marker, поэтому автоматически не переотправляются.
На отказе native нет молчаливой записи через legacy path. Явный rollback
MPosNativeParkedCommandsEnabled=false возвращает исходный critical dispatcher.

Проверки: JVM hold/resume/delete metadata/source, stale expected, pending journal,
invalid print patch и forced second-document failure/atomic rollback; JS actual
reviewed handlers, ack-before-clear/print/loyalty, failed hold preserving cart,
freeze/correlation/authority, rollback/unrelated dispatch/pending/uncertain commit.
Полные JS/JVM/lint обязательны. Physical acceptance остаётся pending.

Физически: hold с доставкой/клиентом/модификаторами и кухонной печатью; restart,
resume WEB comment/customer/printed quantities; повторный hold печатает только
delta; delete не очищает current cart; backup v13 roundtrip; при отказе хранения
cart остаётся; быстрые context/cart/split actions не создают двойной заказ.

Бизнес-вопросы сохранены без изменения policy: hold не переносит paid split
parts; resume очищает loyalty cache и загружает его заново после local commit;
receiptDisplayNumber строится по текущему размеру parked и может повторяться;
отсутствующий printedItems при kitchenPrinted=true восстанавливается source.
Физический результат печати не подтверждается: флаг означает прежний вызов
printKitchenOrderNow, не подтверждение принтера. Это будущая граница 099.

После публикации: 78/110 (70,91%), остаётся 32. Следующий 080 — товары/категории.
