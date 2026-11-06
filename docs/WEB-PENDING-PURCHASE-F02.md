# F02.PO — операционный перенос ожидаемых закупок

Статус: PO01–PO04 implemented для ожидаемых закупок без активного Web-черновика приёмки; PO05 API36 проверка ожидается. Локальные303JVM/lint/debug/testAPK/preflight прошли. Candidate сохраняет реальные native заявки, независимая restore validation сверяет bootstrap с оригиналом (после редактирования — с первой native history entry). Блокировки активной инвентаризации и черновиков приёмки сохраняются. Полный F02 не завершён.

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


2026-11-06: implemented WebPendingPurchases, candidate attachment и WebPendingPurchaseValidation. Native строки сохраняют source historical name/request qty/unit/pack/expected, реальные начальные refs; source requestVersion остаётся importedOrigin, native epoch1 не создаёт прежние команды/history. Guard проверяет полный набор исходных pending IDs, происхождение и начальный snapshot независимо от текущего справочника. Старые native копии без original source принимаются только без imported provenance. Native receipt terminal transition не требует source order оставаться pending в рабочей БД: pending проверяется в оригинале, native received сохраняет bootstrap.

Pure tests3: known pack3×2=6/source version5/historical names/no input mutation; unknown pack expectation=null/legacy0.0004; mismatched expected/missing unit/zero/duplicate product refusal. Added API36 WebPendingPurchaseTest: candidate/reopen/native archive restore; receiving3/10BYN→stock3/cost3.33/order received; faults8 boundaries/no extra movements; replay once; received native roundtrip. Эти API assertions пока только скомпилированы.

Дополнение безопасности: положительная приёмка проверяет SplitPaymentRepository.requireProductUnlocked внутри своей транзакции до записи себестоимости/ревизии. Нельзя инвалидировать текущую частично оплаченную корзину приходом того же товара. Добавлен API test receivingCannotInvalidateAnImportedPartiallyPaidCart с проверкой неизменности документов/stock/product/paid40 и отсутствия результата/движения при отказе. Локальная сборка backup-f02-purchase-lock.log SUCCESS1m18s; Android execution этого дополнения pending.

Совместимые изменения базы: если в заявке stockUnit=kg, а при экспорте у товара база g, native ожидание конвертируется точно (6кг→6000г). Requested unit/quantity/content unit остаются исходными. Legacy qty трактуется в записанной старой единице, не в нынешней; неизвестная/несовместимая размерность отказывается. Добавлен JVM regression modern+legacy kg→g. Итоговые локальные304JVM/0fail/skip, lint/debug/testAPK прошли.

Конфликт завершённой истории: pending заявка с receivedItems либо с уже связанной записью receivings отказывается до ручной сверки. adminDeleted не отменяет историческое проведение, поэтому скрытая запись также запрещает повторный импорт как ожидаемой заявки. Pure regression проверяет оба варианта и saved receivedItems; ни движение, ни команда приёмки не создаются.
