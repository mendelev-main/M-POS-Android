# 07.4 — преобразование Web-копии

Начат после native restore07.3b. Runtime преобразования — Kotlin, source pinned1682c88334e6fb13586ce4b530157c59ca062e43. Web format versions1..13 не равны Room schema9/native archive1. Полное применение Web остаётся07.5; текущий код не заменяет рабочие данные.

## Порядок

1. 07.4a — точные факты частичной оплаты (PM01–PM04), без cash ledger/команд.
2. 07.4b — identity mapping, каталог/единицы/рецепты/модификаторы/фото, сотрудники и смены; преобразование только в изолированный набор. Stock из source не проводить повторно приёмкой.
3. 07.4c — legacy чеки/возвраты/складская история с явным coverage отсутствующих historical cost/unit/consumption, без выдуманной рецептуры. Требуется проверить представление и изменения schema до реализации.
4. 07.4d — current/parked frozen quote и привязка фактов оплаты к смене/кассе/idempotency, завершение остатка один раз. Quote сверяется с totalCents; нельзя активировать только потому, что суммы частей совпали.
5. 07.4e — складские документы/настройки поддержанного scope, preview mapped totals и полная domain validation изолированного набора. Затем07.5 protection/confirm/unsupported skip/activation/restart.

## 07.4a acceptance

- PM01: BYN→Long minor точно через BigDecimal, без округления импортируемых оплаченных фактов/Double; сумма частей равна totalCents,2..10частей, хотя бы одна paid.4000paid/6000unpaid, given5000/change1000 сохраняются отдельно. Cash contribution=4000, не5000.
- PM02: current и каждый parked остаются отдельными location-scoped фактами; исходный документ не меняется, повторное преобразование детерминированно. Position — индекс source, не native command ID. Нет присвоения paid через SplitEditor и новых результатов/проводок.
- PM03: отсутствующая given/change остаётся null с blocker. Source Web normalizePaymentParts допускает null даже для paid cash; native SplitEditor требует given. Не делать given=amount/change=0. Все части paid→отдельная сверка завершённого чека, не новый приём денег. Selected loyalty reward→blocker, не сброс скидки отложенного модуля06.
- PM04: неправильные деньги/версия/структура/сдача/способ/overflow отказываются. Преобразователь не обращается к Room/Context/сети/каталогу. Не логировать исходный документ, контакты/токены.

WebPaymentMapping выдаёт только evidence DTO. Пустой blockers НЕ означает готовность к активации: ещё не сверены frozen строки/скидки/доставка/сумма, наличие текущей смены, actor, кассовый вклад paid progress, происхождение команд и связь с завершённой историей. Ограничения источника не исправляются silently. UI пока использует structural Web preview07.1; mapper будет подключён в pipeline после остальных преобразований.

## Источник и проверки

`features/backup.js`: completeBackupData/validateBackupData/applyBackupData; `features/payment.js`: normalizePaymentParts/validateSplitPaymentDraft/splitPaymentDraftSnapshot; `features/privacy.js`: paid loyalty guard. Android `backup/BackupManager.kt` оборачивает JSON и изображения; native mapper принимает уже проверенный WebBackupDocument, не меняет source JSON/photo bytes. Регрессионный источник: синтетический web-v13-source-export.json, не реальная пользовательская копия.

PM01–PM04 implemented/locally verified:191JVM, включая7новых тестов,0ошибок/пропусков; lint/debug сборка и preflight прошли. CI для этого инкремента не запускался. Device/Room/activation evidence не заявляется для чистого преобразователя.

## 07.4b.1 — каталог и кадровые/кассовые факты

CM01–CM05/SM01–SM03 implemented с локальными проверками:204JVM/0ошибок/пропусков, включая13новых тестов; lint/debug/preflight прошли. CI/emulator для этого чистого преобразования не запускались. Это чистые Kotlin-проекции в отдельной памяти, пока не durable staged Room и не готовый импорт. Следующая07.4b.2 завершит layout/фото/metadata и staged materialization; после неё07.4c history. Наличие DTO не означает прохождение native restore validation.

- CM01: ID преобразуются детерминированно `web-`+SHA256 с namespace вида записи и version1; source ID сохраняется. Числовой1 и строковый«1» эквивалентны как Web String(id); повтор отказ. Product/employee/shift/category/group/option разделены, local owner не используется. Категории берутся из trim product.category, «Без категории»→null; разные регистры названий не сливаются автоматически. Layout и порядок ещё отдельный шаг.
- CM02: simple stock/cost/minimum хранятся в source.stockUnit, stockDisplayUnit не пересчитывает их. Отрицательный остаток не включает allowNegativeStock автоматически. Не заданная stockUnit остаётся null, старое `unit` не подставляется молча; legacyUnitUnknown. Отсутствующие cost/stock остаются null с blocker; price/type обязательны. Деньги/количества exact decimals, никаких default0 для неизвестных фактов и округления импортируемой истории.
- CM03: component.qty у Web составного ингредиента — количество партий; native physical quantity=qty×child.recipeYield в child.recipeUnit. Для simple компонента qty уже base stock unit. Recipe DAG/refs/dimension/positive quantities/depth64 проверяются, underflow/excess precision отказывается. Price остаётся source price за продаваемую порцию; workspace current/parked/receipt sell quantities и frozen price требуют отдельного согласованного преобразования07.4d, не просто подстановки в native cart.
- CM04: modifier min/max/options/refs/negative delta проверяются native ModifierRules; composite option qty также переводится из партий в физическое количество. Упаковка500g при stockUnit kg становится0.5kg, без изменения складского остатка. Несовместимые измерения отказываются.
- CM05: embedded photoKey остаётся ссылкой на проверенный source document, до безопасного JPEG преобразования photoTranscodePending; image/imageUrl URL не скачиваются, externalPhotoNeedsExplicitSkip. Неподключённые поля карточки отмечены именами unmappedFields и productMetadataNotMapped; исходный документ остаётся источником полных данных. Это не подтверждение пропуска metadata. Stage07.4b.2 должен обработать их/показать явное решение перед активацией.
- SM01: employee IDs/роли employee/admin сохраняются через native record validation, импортная начальная revision1. Администраторская сессия не создаётся. Cashier текущей смены определяется source employeeId; отсутствующий/административный actor открытой смены отмечается blocker, не заменяется владельцем.
- SM02: shift.employeeName — отдельный исторический снимок. При отсутствии не берётся сегодняшнее имя; timestamps/opening/count/expected/difference nullable, не заменяются now/0. Разрешена только одна open shift. Несогласованные status/time/closure totals и неизвестные роли отказываются. Web closeShift сохраняет countedCash без expectedCash; expectedCashNeedsHistory требует сверки с платежами/кассовыми движениями в07.4c/d, не текущими товарами.
- SM03: cashMovements сохраняются как направление deposit/withdrawal+amount/subtype/sourceID/position/time/note; никаких новых проводок. Missing movement ID/time явно отмечается, duplicate IDs отказываются. openingSourceShiftId пока source reference; register/timezone/previous-shift lineage, прочие поля shift/employee и финансовое происхождение сверяются до staged activation.

Источник: pinned product-persistence.js/saveProductConfiguration, product-configuration.js/stockUnit/componentBaseQty/componentDisplayQty/normalizeModifierGroup; android-pos-stock.js/reserveKnown (composite expands qty×component.qty, modifiers also source batches); employees.js/saveEmployee; shifts.js/newShift/closeShift/cashMovements; backup.js и Android BackupManager — общий transport JSON. Никакие source permissions/password/network hooks не исполняются.


## 07.4b.2 — раскладка и изолированные фотографии

LP01–LP04/PP01–PP04 implemented, проверки выполняются. Это asset-only staging и проекция навигации; полный staged Room/native archive ещё не создаётся. Metadata карточек и сборка всего набора остаются следующей частью07.4b.3, затем07.4c/d. Не активировать неполный набор без финансовой истории.

- LP01: categoryOrder включает пустые категории, затем добавляет категории товаров; цвет/symbol проверяются общим CategoryAppearance. «Без категории» соответствует native пустому categoryId. Онлайн-флаги перечисляются deferredFields, не используются для изменения модуля09. Орфанные appearance keys отмечаются blocker.
- LP02: source five-column col/row сохраняются, native slot=row×5+col. Нет/коллизия/неподходящие координаты приводятся по source ensureLayoutPositions first-free правилу с normalizedPositions count. Валидная row за native100slots сохраняется с null nativeSlot и blocker; capacity>20 отказывается, не отрезает explicit tiles. Некорректные и повторные ссылки не исчезают.
- LP03: posNavigation1 category/folder/product/parentId преобразуются с scoped folder IDs; сохраняются последовательность и one-level membership. Не размещённые товары добавляются по source sortOrder, с source order при равенстве. Нет parent folder/product/category, другой category или nested folder→отказ вместо source silent cleanup. LayoutEngine.project не используется для очистки повреждённого импорта.
- LP04: чистые JVM tests: empty categories/appearance/slot19+11, folder/order, source first-free collision, out-of-range native position, dangling/duplicate navigation, uncategorized/online deferred, deterministic/no source mutation. Home and navigation metadata с неподключёнными полями отмечаются blocker.
- PP01: WebPhotoPreparation требует оригинальный AdminAuthorization и checks до/во время подготовки/публикации; logout/relogin не передаёт власть старой операции. Исходные photo keys — opaque map keys, никогда имена файлов. Два товара с одним source key используют один подготовленный JPEG.
- PP02: только referenced embedded bytes, JPEG/PNG/WebP≤2MB/16Mpx; source bytes не меняются. Общий ProductPhotoStore выполняет flatten/resize≤1200/JPEG≤600k. URL не скачиваются. Новые UUID.jpg пишутся исключительно noBackupFilesDir/web-conversion-photos/<uuid>.stage/media, не filesDir/selected dataset.
- PP03: stage manifest1 связывает id/source SHA256/source-key→native-photo UUID/size/SHA256. Перед публикацией recheck JPEG format/bounds/hash, ограниченный manifest и точный список фото. Fsync files/directories, один rename stage→published и fsync root/parent. Это не generation active pointer, не complete import authority.
- PP04: повторное open(id,sourceSha) новым сервисом перепроверяет manifest/hash/photos; неверный source hash/ID или corruption отказывается. Ошибка до publication удаляет только собственный stage, после publication полный опубликованный asset сохраняется для повторной проверки. Android tests проверяют conversion/shared opaque key/reopen/corruption,4 fault points, malformed image/old permit. Реальные disk-full/power-loss/large data и physical tablet остаются отдельными gates; injected faults не заменяют их.

Источник: pinned pos-navigation.js normalizePosNavigation/posCategoryItems/ensureLayoutPositions, pos.html/categoryLayoutSnapshot/syncCategoryOrder, product-categories.js; Android BackupManager localImageId→bytes. Transport metadata/unsupported settings пока не выполняются. Пользовательский UI не изменён; интеграция prepared assets в полный converter и07.5 preview/confirm остаётся отдельно.
