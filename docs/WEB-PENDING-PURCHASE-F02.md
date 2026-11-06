# F02.PO — операционный перенос ожидаемых закупок

Статус: PO01 model/codec4 implemented, локальные300JVM/lint/debug/testAPK/preflight прошли. Imported purchase restore пока явно отказывается до полного source/bootstrap reconciliation; API36 для нового формата не заявляется. PO02–PO05 planned; runtime-блокировка pending закупок сохраняется. F01 базовый Android-прогон прошёл292/0fail; новая F02 production-модель им не проверена.

Источник: pinned Web1682c88334e6fb13586ce4b530157c59ca062e43, purchase-orders.js makePurchaseLine/createPurchaseOrder/savePurchaseOrderEdit; Android backup переносит исходный JSON. Web увеличивает requestVersion, но не сохраняет полную цепочку предыдущих вариантов заявки. Native PurchaseOrderRules требует все предыдущие версии, поэтому перенос числа5 вместе с пустой history запрещён.

## PO01 — происхождение и начало native-истории

Ввести versioned imported-source metadata: SHA оригинального файла, исходный order ID, nullable исходная requestVersion/updatedAt. Native order начинает собственную историю с revision/requestVersion1 как новый перенесённый документ; это не утверждение о первой версии в Web. Исходная версия5 отображается отдельно, без четырёх вымышленных history entries. Следующее native редактирование создаёт реальную native revision2 и сохраняет bootstrap snapshot.

Не создавать operation_results/audit для якобы выполненного Web Place/Edit, не запускать размещение заявки/транспорт. Original source и legacy archive сохраняются.

## PO02 — строки и упаковки

Сохранять source product ID/name, requestedQty/requestedUnit, stockUnit, packSize/contentUnit и expectedQty. Восстановленная позиция ссылается на фактические ревизии нового каталога. Не выдавать их за исторические Web revisions.

Примеры: 3 коробки ×2кг → ожидание6кг; 3 коробки с неизвестным размером → ожидание неизвестно, не0. Legacy qty без requestedQty допускается только как явно распознанное старое физическое количество при сохранённой единице. Несовместимые/неизвестные единицы и несовпадение source expectedQty с точным пересчётом блокируют импорт, а не исправляются молча. Исторические названия не заменяются текущими.

## PO03 — работа после переноса

Поставщик и товары должны существовать; pending документ доступен существующему интерфейсу закупок и приёмки. Проверять актуальные native ссылки при новой операции. Заказ10/приёмка3 закрывает заказ, склад+3; повтор команды не добавляет ещё3. Приёмка12 требует явного подтверждения превышения. Никакой приёмки при самом импорте.

## PO04 — restore validation

Imported metadata требует полного исходного файла и совпадения source ID/version. Bootstrap snapshot должен совпасть с исходной заявкой; после редактирования проверяется первая реальная native history entry. Новые native-заказы без imported metadata продолжают прежний путь. Старые PurchaseOrder codec1/2/3 остаются читаемыми; новый формат не угадывает происхождение старых документов.

## PO05 — проверки перед снятием блокировки

JVM: version5 без fabricated history, absent version, packaging known/unknown, decimal0.0004, mismatched expected, duplicate/missing refs, source unchanged, codec compatibility/tamper. API36: candidate→reopen→receiving3→repeat/fault rollback; source→native export→restore; supplier/product guards. Только после выполнения operational path и независимой validation снять запрет pending purchases в WebCandidateDatabase. Receiving drafts и inventory остаются отдельными частями F02.
