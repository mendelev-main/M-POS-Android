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
