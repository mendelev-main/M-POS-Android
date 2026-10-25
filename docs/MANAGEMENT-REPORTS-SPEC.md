# Смены, сотрудники и отчёты

Паспорта M13/M14/M15; planned. Минимальное M13 вводится 03a как предпосылка продажи; полный UI/roles/reporting —05. Источники Web: `shifts.js` (currentShift/shiftTotals/cashDrawerBalance, submit handlers), `employees.js`, `analytics.js` (analyticsRange/analyticsData), administration-session docs. Literal credentials/legacy hardcoded secrets не переносятся в native.

## M13: минимальная смена 03a

Локальная касса имеет registerId и не более одной открытой смены. OpenShift принимает commandId, actor/owner, openingCash BYN и время/zone context. Сохраняет shiftId/status/open snapshot. Повтор команды не создаёт ещё одну открытую смену, конкурирующий open получает конфликт. В single-owner03a кассир обозначен как локальный владелец; это не полноценный employee auth05.

CommitSale проверяет active shiftId внутри той же transaction boundary. CloseShift конкурирует с sale/cash movement: либо sale включена до close, либо отклонена как закрытая смена. Нет записи «после» закрытия с тем же shiftId. Если draft открыт при закрытии, он остаётся draft и требует новой актуальной смены перед оплатой.

Минимальные экраны 03a: статус смены, открыть с opening cash, закрыть с preview/подтверждением и итог. Сложная история/roles/deposits/withdrawals screens вводится 05, но contract totals/refund требует решения D02 до продаж. «Минимальная» не разрешает скрыть несогласованные деньги фиктивной сменой.

## M13: движение наличных и формулы

CashMovement имеет id, shiftId, type/deposit/withdrawal/refund reference, amount/currency, reason, actor, commandId. Refund наличными связывается с refund allocation, а не вычитается второй раз через общую ручную withdrawal. Нельзя одновременно учитывать одну физическую выдачу как два события.

Native candidate формула для согласования D02: expectedCash = openingCash + cashTenderNetSales − cashRefunds + manualDeposits − manualWithdrawals. cashTenderNetSales — поступившая для продаж наличная часть за вычетом выданной сдачи, не общая полученная купюра. Возврат может относиться к продаже другой смены: cashRefund относится к смене фактической выдачи, originalReceipt сохраняет прежний shiftId. Политика cut-off/карточных возвратов утверждается до реализации.

Пример candidate: opening100, наличный net sale40, card sale60, cashRefund10, deposit20, withdrawal5 →expectedCash145BYN. Если принято 50 при sale40/сдача 10, увеличение кассы 40. Это проектный example, не утверждение совпадения Web cashDrawerBalance; Web фильтрация returned receipts анализируется отдельно, ошибки двойного учёта не копируются.

Close snapshot хранит expected/actual/difference и набор связей/временной cutoff. Actual вводится явно, пустое поле не 0. Разница не «исправляет» receipts. Повтор close возвращает прежний snapshot. После закрытия запрещено редактировать состав отчёта как текущую смену.

## M14: сотрудники и административная сессия

EmployeeDirectory: стабильный employeeId, отображаемое имя, role/capabilities binding, active/revision. Выбор сотрудника в смену не является admin login. Сотрудник с историей отключается безопасно по принятой политике, история не теряет автора.

Команды Create/UpdateEmployee, DeactivateEmployee, AssignCapabilities и LoginAdmin/LogoutAdmin различны. Назначение прав требует соответствующего actor и проверки revision; нельзя повысить свои права произвольным полем UI. Credential verifier/восстановление доступа/attempt throttling определяются D04. Встроенный общий пароль Web не переносится.

Runtime admin session имеет явный indicator и exit, подтверждения после logout снова проверяют доступ. Ошибка входа не изменяет кассира, shiftId, receipts и права. Реализованный выбор ролей не считается готовым без use-case tests [ACCESS-AND-AUDIT.md](ACCESS-AND-AUDIT.md).

## M15: отчёт — согласованный snapshot

ReportRequest: period[startInclusive,endExclusive), business zone, register/shift/employee filters, reportType/policyVersion и scope права. Выбранные локальные даты переводятся в instants по явно указанной зоне;24 часа не предполагаются для любого календарного дня. ReportSnapshot содержит request, generatedAt, dataCutoff/revisions, rows/totals/currency и предупреждения недоступных источников. Export печатает этот snapshot, не делает второй несогласованный запрос.

Семейства: продажи/возвраты/способы оплаты/доставка/скидки; смена и касса; товары/категории/сотрудники; stock/receiving/inventory; loyalty remote показатели с отдельным статусом. Модуль аналитики не изменяет ledger/receipts и не подменяет локальные подтверждённые цифры неуспешным HTTP.

Метрики-кандидаты до D04: gross sales, refunds, net sales, cash/card allocations, receipt count, average ticket, COGS/margin по историческому cost snapshot. У каждой метрики фиксируются включение доставки/скидки, gift, partial refund, временная ось sale/refund, пустой период/деление на 0. «Выручка» без этих определений недопустима. Нельзя брать текущую product cost/category для пересчёта исторической прибыли/категории без явно названного альтернативного отчёта.

Перемещение товара между категориями не переписывает receipt category snapshot. Валюты не складываются без утверждённого conversion contract; в текущем старте каталог BYN. UI summary/rows/export должны сходиться по одному набору allocations с формальной допустимой rounding difference.

## Перед D02/D04 и приёмка

D02: утвердить пример 145BYN/сдачу, refund cross-shift, payment/discount/rounding и минимальный close. D04: роли, credential storage/provider, reporting time axis, period defaults, COGS source, export columns. Backend loyalty metrics требуют D05 contracts.

MG01:open повтор/два конкурентных open →одна смена; MG02:sale+close →однозначная граница; MG03:сдача/возврат не удвоены; MG04:logout во время формы →write denied; RP01:пустой период →нулевые суммы/определённый average; RP02:категория/cost изменены →исторический отчёт неизменен; RP03:export equals snapshot totals; RP04:refund другой смены показан по утверждённой оси. Mapping H01–H04/S04/S05/D05, tests planned.

Текущий контракт подготовки03a: [SHIFT-03A](SHIFT-03A.md), перенос openingCash из последнего counted согласно pinned Web; single-owner граница явная. Подтверждённая часть D02 и оставшиеся allocations — [MONEY-03](MONEY-03.md). Старые candidate/open questions выше читать с этими уточнениями, не как реализованный runtime.

03a реализует минимальную открытую/закрытую смену, перенос counted, расхождение, atomic result/audit и admission API внутри Room transaction; текущий scope/evidence — [SHIFT-03A](SHIFT-03A.md). MG01 проверяется непосредственно, MG02 sale+close и MG03 money/refund остаются до продажи. Не считать прошедший close/open тест доказательством ещё отсутствующей продажи или полного05.
