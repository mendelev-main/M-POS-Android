# 04a.1 — поставщики

Первый вертикальный инкремент M11 этапа04 после релиза0.1.74.1. Источник pinned Web1682c88334e6fb13586ce4b530157c59ca062e43: `Web/js/features/suppliers.js` и settings-section «Поставщики и товары» в `pos.html`. Реализованы список, создание/редактирование названия, поиск по товару/категории и список закреплённых простых товаров, удаление с подтверждением. Контактных полей source modal нет — в этом инкременте их не добавляем. Закупки/приёмка/инвентаризация ещё не подключены; их верхние вкладки остаются будущими страницами.

## Поведение и данные

Пустой список товаров допустим. Composite/удалённые ссылки отвергаются в команде. Название1–200символов, нормализуем пробелы; одинаковые названия разных поставщиков допустимы, как Web. Данные формы сохраняются при Activity recreation через ViewModel. X/Back/Отмена при изменениях запрашивают подтверждение; несохранённая форма после process death не обещается durable.

Экран доступен из Настроек; в старом Web список находился секцией настроек, здесь отдельная native страница и общий navbar. Редактор использует MposDialog/Close/Field/Primary/Secondary/Danger, прокручивается при нехватке места; никаких локальных крестиков/радиусов. Source name/checklist/search сохранены. Парное pixel comparison/физическая/тёмная/150% приёмка нового экрана отдельно.

Supplier(id,name,revision,productIds,deleted) хранится в namespace `suppliers` существующей Room `workspace_documents`, payload1. Это тот же transactional document механизм, который используется checkout/home; второй storage/runtime не вводится. SQLschema8 остаётся прежней. Codec отвергает неизвестную версию, дубли ID/товаров, некорректные revision и повреждённый payload; чтение не заменяет ошибку пустым списком. Per-supplier revision защищает редактор, global document revision монотонна. Удаление оставляет tombstone и очищает текущие привязки, чтобы исключить повторное использование ID/ABA; из списка поставщик исчезает. Исторические документы будущих закупок должны хранить собственный supplier snapshot и проверять active usage перед удалением; таких документов в04a.1 ещё нет.

Save/Delete: InstallationGate →Room transaction →проверка локального владельца →fingerprinted result replay →revision/refs validation →supplier document + operation_result + audit. Ошибка любой записи откатывает всё. Никаких stock/cost/price/movement writes. Удаление товара в каталоге заблокировано, пока он закреплён у живого поставщика; этот guard и Supplier save выполняются в одной Room transaction со своими командами. Конкурирующие редакторы одного supplier имеют одного победителя; изменения разных supplier сохраняют друг друга.

Управление пока разрешено только установленному локальному ownerId — та же граница, что в сменах03. Полная идентификация сотрудников/admin sessions этапа05 здесь не заявляется; actor проверяется repository, а не свободным role полем UI. Контакты/секреты не добавлялись.

После потерянного ответа ViewModel проверяет original command outcome; при невозможности проверки сохраняет тот же commandId и блокирует закрытие/новое действие до «Проверить результат». Известный commit остаётся успешным, даже если последующее чтение списка сломалось. Структурно неверная форма отвергается до команды, не становится неизвестным результатом.

## Приёмка

SUP01: name/simple bindings/empty selection, invalid reference refusal. SUP02: save/reopen/recreate/edit/delete и неизменные stock/cost. SUP03: idempotency/conflict/revision/ABA/concurrent editors. SUP04: каждый3write fault/SQLite abort/owner/installer/unknown-result и known-save reload failure. SUP05: фактическая замена debugAPK1→2 сохраняет registry/links/tombstones/audit/replay. Это части M11/I04, не закрытие всего этапа04/I01–I04.

Тесты: SupplierRulesTest3, SupplierRepositoryTest4, SupplierViewModelTest3, SupplierUiTest1, SupplierUpgradeTest1. Updater сохраняет прежние3financial fixtures и добавляет1supplier fixture; UI seed8→9. Результаты локального/CI прогона — harness/build-log.md, до evidence статус implemented.

## Следующая часть

04a.2 — durable черновик закупки, выбор supplier/simple lines, единицы/упаковки/unknown expected quantity, без изменения stock. Затем receiving по подтверждённым D03: weighted cost, одно подтверждение закрывает заказ даже при недопоставке, over-receiving требует явного подтверждения. Дополнительные расходы/сторно не включать без отдельного контракта. Релиз каждого инкремента не производится.

## Итоговая приёмка

[FullCI40472850012](https://github.com/mendelev-main/M-POS-Android/actions/runs/40472850012)/6f387ee SUCCESS:83JVM/99instrumentation/0failures/0skipped,12Python/lint/build; затем2scale/9UIseed/3financial+1supplier fixture и actualAPK1→2. SUPPLIER UPDATE PASSED: registry/links/tombstones/audit/replay сохранены. Existing sale/split/refund/catalog/workspace/parked/home/shift assertions также прошли. Supplier-card/list1600×1125 просмотрены: единый Close/Primary/Secondary, имя/поиск/checkbox/actions без перекрытий и без клавиатуры/системного окна. Scope SUP01–SUP05 ci-verified; physical/dark/150% supplier matrix/forced cold-kill windows остаются отдельной приёмкой. Stable0.1.74.1 не содержит этот инкремент; код пока только develop.
