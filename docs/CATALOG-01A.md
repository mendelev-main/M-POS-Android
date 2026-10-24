# Каталог01a — реализация и проверка

Статус: реализован код, итоговая CI-приёмка ожидается. Это первый инкремент01, не весь каталог/касса.

## Что доступно

Товары → добавить: название, категория, ценаBYN, себестоимость зашт, остатокшт. Можно создать/переименовать категорию, выбрать её для товара, искать по имени/категории и фильтровать. Редактор сохраняет draft отдельно от committed товара. Назад с изменениями предлагает продолжить/отбросить. После записи отображается сохранённый список.

Остаток меняется прямо в карточке без причины; ledger delta и stockRevision пишутся вместе с товаром. Результат команды повторяем, fingerprint защищает повтор ID с другим payload. Если stock изменён с момента открытия, изменяемый stock отклоняется как конфликт; обновление только названия не восстанавливает старый запас.

Категория с товарами не удаляется. Товар со складскими движениями не удаляется физически; нулевой товар без истории может быть удалён с подтверждением. Архивирование/зависимости рецептур ещё не реализованы. Порядок категорий пока автоматический; ручная перестановка остаётся этапу01.

## Общая UI база

Manrope, light/dark palette, тёмная верхняя панель, shared buttons/close/dialog/fields/notice. Debug «Компоненты» показывает галерею состояний; release не содержит этой кнопки. Updater использует общую theme/buttons, его security checks сохранены. Структура редактора повторяет Web header/content/summary при широкой доступной области; pixel parity всего приложения пока не заявляется.

## Данные и ограничения

Room schema1 экспортирована в `app/schemas`;8 entities: app_config/categories/products/stock_balances/stock_movements/operation_results/editor_drafts/audit_events. БДmpos.db, destructive fallback отсутствует, Android allowBackup=false сохранён. До появления первой БД updater preferences/files не перемещаются. В этой схеме unit всех продуктов фиксированаpiece; при добавлении единиц01b миграция обязана сохранить прежние movements какpiece.

Room2.8.4, KSP2.2.21-2.0.4 для Kotlin2.2.21. Основание: [Room release notes](https://developer.android.com/jetpack/androidx/releases/room#2.8.4), [KSP release](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4). Совместимость подтверждается реальной сборкойCI, не выбором номера в документе.

Режим локального владельца не является готовой системой ролей. Фото/recipe/modifiers/base-display units/negative stock flags —01b/01c. Cart/payments/receipts не реализованы. Цена0 подготовлена для будущей ручной цены02; это не кнопка бесплатной оплаты. Корзины/истории продаж в этой версии нет.

## Evidence

JVM: parser/canonical decimal/search key/malformed/overflow/fingerprint,8 предыдущих updater cases. Room instrumentation: actual rollback after product write, repeat create/stock adjustment, payload conflict, stale stock, metadata-only preservation, category duplicate/references/rename, concurrent revisions, disk reopen+draft/config. Compose: создать категорию/товар, выбрать категорию, recreate, stock10→8, common close/gallery.

External updater acceptance должен сохранить Room товар/stock8 через реальную package replacement1→2, а также прежние hash/corruption/Cancel/restart checks. Отчёты содержат screenshot каталога, editor, shared dialog, ApplicationIcon и updated catalog. Физический планшет/клавиатура/полный visual parity и будущие schema migrations остаются отдельной приёмкой.
