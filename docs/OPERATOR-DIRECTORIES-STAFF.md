# Права на справочники, сотрудники и метки чеков — 10.11.2026

Статус: implemented локально. Новое прямое правило владельца имеет приоритет над прежним Web role/admin поведением. main без commit/push; UI проверяет владелец в Android Studio. Применены mpos-phase/mpos-domain.

## Действующая матрица

| Действие | Без открытой административной сессии |
|---|---|
| Создать/изменить стоимость доставки | Разрешено |
| Создать/изменить поставщика, включая привязку товаров | Разрешено |
| Создать/изменить шаблон названия чека | Разрешено |
| Удалить поставщика/доставку/шаблон | Требуется admin session |
| Создать/изменить/удалить пресет скидки | Требуется admin session |
| Создать/изменить/удалить сотрудника | Требуется admin session |
| Изменить кассира открытой смены | По-прежнему запрещено до закрытия смены |
| Раскладка рабочей зоны | Разрешено по прежнему решению |

Удаление не входило в новое разрешение, поэтому сохранена административная граница. Остальные защищённые модули не менялись.

SettingsHome показывает + и кликабельные строки операционных справочников без admin. SettingsCatalogViewModel требует AdminForms только для скидок и удаления. SupplierViewModel сохраняет/редактирует без AdminForms permit; удаление использует прежний command permit, в том числе при неизвестном исходе. При logout операторский ввод поставщика сохраняется, подтверждение удаления снимается; обычный ввод доставки/шаблона также не сбрасывается из-за смены admin generation.

Repository проверяет политику внутри команды: dictionaryTransaction выбирает plain Room transaction для save/edit разрешённых видов, прежний adminTransaction для destructive/discount. Вложенных adminTransaction нет. InstallationGate, revisions/CAS, command fingerprints/outcomes, audit, rollback сохраняются. Supplier actorId остаётся проверкой установки (installation owner ID), не требованием административного входа.

Тип существующей SettingsEntry менять нельзя: SettingsRules.save проверяет old.kind. Поэтому передача ID скидки как DELIVERY/RECEIPT_LABEL не позволяет перезаписать скидку из операционной формы. Прежние idempotency fingerprints сохранены.

## Сотрудники

Переключатель «Администратор» и его пояснение удалены. Административный доступ существует только в runtime AdminSession и не выдаётся полем сотрудника. Legacy role=employee/admin сохраняется в codec/снимках/fingerprint для совместимости команд и backup; список выбора кассира учитывает только deleted. Старый role=admin теперь также допустим для смены и не имеет отдельного запрета удаления. Без открытого admin session изменять/удалять такого сотрудника по-прежнему нельзя. Любое изменение действующего кассира смены блокируется, независимо от legacy role.

Нет массовой миграции или переписывания старых смен/чеков/имён/ID. Новая форма создаёт обычного employee и не предоставляет переключения роли. WebStaffMapping больше не ставит activeCashierUnavailable только из-за legacy role=admin; проверки имени, actor references и остальных blockers не менялись.

Окно560dp, поля ФИО/телефон, равные кнопки48dp/radius16 с одинаковой шириной в одной строке: Удалить/Отмена/Сохранить; для нового сотрудника две равные кнопки. Подтверждение удаления/отказа от изменений также использует48dp. Основные поля и форма прокручиваются. Административная сессия и active-cashier guards остаются в ViewModel/Repository.

## Метки чеков R01

В левой карточке списка чеков метки получения и оплаты имеют одинаковый фиксированный размер128×23dp, radius8, icon12, font10/line13, центрирование. Поддерживаются На месте/С собой/Доставка и Наличные/Карта/Наличные + карта/неизвестно. Цвета и иконки не менялись. Полноразмерные метки отложенных остаются110×28; compact-вариант используется чековым списком.

## Проверки

JVM: EmployeeTest/EmployeeMutationsTest, ShiftRulesTest, WebStaffMappingTest обновлены под legacy role как metadata; SettingsRulesTest добавляет отказ подмены типа защищённой записи. Подтверждены сохранение historical names/fingerprints/tombstones и запрет любого редактирования active cashier.

OperatorDictionaryAccessTest (Room, compiled only): без admin создать/изменить/повторить доставку, шаблон, поставщика; отказ удалению и скидкам; rollback после fault. AdminAccessTest обновлён: supplier save разрешён, delete по-прежнему denied. EmployeeRepositoryTest проверяет открытие смены legacy admin и прежнюю гонку open/delete. ReceiptStickerSizeTest проверяет128×23 для всех типов/способов; EmployeeUiTest проверяет отсутствие employeeAdmin и48dp кнопки Save. Android tests только компилируются, пользовательский эмулятор не запускался и данные не изменялись. Результат команд — harness/build-log.md.

Финальный результат:20 JVM/0fail; compileDebugKotlin/compileDebugAndroidTestKotlin successful. Harness preflight и git diff --check passed. Runtime/device UI остаётся pending.
