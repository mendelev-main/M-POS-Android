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
| P03 | Категории create/rename/delete guard/order | Цвет/символ категории из расширенного Web-контракта ещё не введены; нужны перед category tiles02 |
| P04/P05/P07 | base/display, minimum/pack defaults, nested recipes/yield/cost, per-simple flags | Отдельный purchaseContentUnit/полный закупочный UX04, physical cases |
| P06 | modifier groups/options/guards/configuration preview | Выбор в заказе/резервы подключаются02; это не продажа |
| P08 | local photos/picker callback/staging/activation/cleanup | Реальный provider, HEIC/orientation/low-space и tablet visual acceptance |
| P09/P10 | recipe/modifier/draft/history delete guards; revisions/replay/rollback/reopen/migrations1→3 | Связи с ещё не существующими cart/layout/sales вводятся вместе с ними; device/process-kill scenarios |

Различать текущие контрактные P IDs и расширенный полный Web scope: category color/symbol и другие неперенесённые поля не считать готовыми по факту прохода core tests. Изменение этих полей/будущая схема оформляются отдельным инкрементом до соответствующего UI. Полный этап01 не помечается завершённым одним проходом CI; остаток явно указан здесь и PARITY.

Точная новая Android16 CI evidence — harness/build-log.md. Ранее01a/01b/01c прошли Android9/15 при старой поддерживаемой платформе; новые требования по ANDROID-SUPPORT.md заменяют ту runtime-матрицу, не переписывают исторические результаты.
