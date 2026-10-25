# Каталог01: финальная проверка и оставшиеся критерии

Текущий инкремент завершает ручной порядок категорий P03 и переводит Android gate на16/API36. К02 автоматически не переходим: CI и device evidence фиксируются отдельно.

## Порядок категорий

Native требование P03 из CATALOG-WORKSPACE-SPEC/PRODUCTS-WORKSPACE. В pinned Web1682c883 product-categories.js выводит categoryOrder, но отдельный move handler в этом редакторе не найден. Кнопки «Выше/Ниже» — явное native дополнение утверждённого плана, а не заявление о буквальном копировании Web-контрола.

Команда принимает полный упорядоченный список ID+expectedRevision. В транзакции проверяет совпадение множества категорий, отсутствие дублей и все версии; затем присваивает плотный sortOrder и увеличивает revision изменившихся строк. Rename/create/delete/reorder между снимком и командой дают conflict. При повторе commandId возвращается сохранённый результат; новый порядок повторно не применяется. Отказ записи откатывает все позиции/версии/result/audit. Product.categoryId не меняется.

Создание категории добавляет её после максимального sortOrder, а не после COUNT: удаление середины не приводит к совпадению порядка новой категории с существующей. Миграция схемы не требуется (sortOrder/revision были вschema1). UI использует MposButton/Secondary в общем MposDialog, границы списка и busy блокируют невозможный шаг. Изменения сохраняются сразу, не зависят от сохранения карточки товара.

Тесты CategoryOrderTest: durable reopen/replay/сохранность categoryId, append после удаления, duplicate/stale/create conflicts, actual rollback после первой записи, concurrent reorder single winner. CategoryOrderUiTest: создать2 категории, переместить, recreate, проверить порядок.

## Проверка границ полного каталога

| Критерий | Реализовано | Остаётся |
|---|---|---|
| P01/P02 | Simple/Composite create/edit, BYN, поиск/category filter, validation/draft | Физический UX/клавиатура/масштаб текста |
| P03 | Категории create/rename/delete guard/order/color/symbol | Schema4/color/symbol ci-verified API36; [контракт](CATEGORY-APPEARANCE.md). Физическая приёмка отдельно |
| P04/P05/P07 | base/display, minimum/pack defaults, nested recipes/yield/cost, per-simple flags | Отдельный purchaseContentUnit/полный закупочный UX04, physical cases |
| P06 | modifier groups/options/guards/configuration preview | Выбор в заказе/резервы подключаются02; это не продажа |
| P08 | local photos/picker callback/staging/activation/cleanup | Реальный provider, HEIC/orientation/low-space и tablet visual acceptance |
| P09/P10 | recipe/modifier/draft/history delete guards; revisions/replay/rollback/reopen/migrations1→4 | Связи с ещё не существующими cart/layout/sales вводятся вместе с ними; device/process-kill scenarios |

Различать текущие контрактные P IDs и расширенный полный Web scope: category color/symbol реализованы отдельным инкрементом schema4; статус проверок фиксируется отдельно от core. Будущие cart/layout/sales поля подключаются в соответствующих этапах. Полный этап01 не помечается завершённым одним проходом CI; остаток явно указан здесь и PARITY.

Точная новая Android16 CI evidence — harness/build-log.md. Ранее01a/01b/01c прошли Android9/15 при старой поддерживаемой платформе; новые требования по ANDROID-SUPPORT.md заменяют ту runtime-матрицу, не переписывают исторические результаты.


CI-verified на Android16: [run40099706934](https://github.com/mendelev-main/M-POS-Android/actions/runs/40099706934), commit7cf27d9.24 JVM/4Python/lint/assembleDebug и20 instrumentation, включая3CategoryOrderTest+CategoryOrderUiTest. Затем2 explicit UI setup tests и updater E2E с package replacement/data preservation. После проверенного commit — только документы. На момент этого run colour/symbol оставались открытыми; текущий результат ниже. Полное закрытие01/device не заявляется.


## Итог category appearance

[run40101394875](https://github.com/mendelev-main/M-POS-Android/actions/runs/40101394875), a19cb3e, attempt2: checks и emulator36 success;26JVM/4Python/lint/assembleDebug,24instrumentation +2explicit UI seed; CATALOG UPDATE PASSED/UPDATE E2E PASSED (1→2, stock8/data preserved). Schema4 из KSP сохранена без ручного редактирования и byte-equal final artifact. Shared category editor screenshot reviewed. Остаток01: физическая tablet/provider/font-scale/low-space/process-kill приёмка; будущие cart/layout/sales/purchase/site подключения остаются в своих этапах.
