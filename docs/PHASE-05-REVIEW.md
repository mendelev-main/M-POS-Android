# Программный checkpoint05 перед backup07

Дата2026-11-01. Это сверка реализованных контрактов, не полная готовность продукта и не физическая приёмка.

| Блок | Подтверждённый scope |
|---|---|
| Смены/касса | H01/H02 и CM01–CM05: exact cash, opening carry counted, deposits/withdrawals, close snapshot, replay/rollback/concurrency; evidence SHIFT-CASH-05A.md и PARITY.md |
| Доступ/сотрудники | A/ED/AR, H03/H04 и capability matrix: две группы, отдельная runtime admin session, активный кассир не изменяется, после process death admin locked; CAPABILITY-MATRIX-05B4.md |
| Календарная аналитика | RPT01–RPT05/UI01–UI05: event-date sale/refund, average только новых продаж, delivery included/separate, period/epoch/read-only; REPORTS-05C.md/ANALYTICS-UI-05C2.md |
| Исторические группы/прибыль | RG01–RG06: original seller, исторические имена/категории/recipe cost, отдельные единицы, net profit с отменой delivery expense; REPORT-GROUPS-05D.md |
| Смены/financial stock links | RS01–RS05: целая пересекающаяся смена, сравнение сохранённого cash, exact source/document/command/time/delta без auto repair; SHIFT-REPORTS-05D2.md |
| Складские количества/оценка | WH01–WH06 ci-verified/API36: current anchor, полный ledger, historical unit proof, no false zero/partial total, current cost valuation HALF_UP rows; WAREHOUSE-REPORTS-05D3.md. [FullCI41305115280 attempt2](https://github.com/mendelev-main/M-POS-Android/actions/runs/41305115280/attempts/2)/883bbaf SUCCESS,173JVM/247API36/0failures/0skipped |

Новая schema9 добавляет только nullable movement.unit; legacy JSON/command fingerprints и исторические суммы не переписываются. Migration8→9 и прежние chains обязательно проверяются. Обновление debugAPK не заменяет реальную проверку production upgrade с8→9 при будущем выпуске. Пока main/signing/стабильный канал не меняются.

Остаются отдельные gates: физический Android16 планшет, тёмная тема/150% новых складских строк, cold-process/keyboard/picker, большие данные и точное визуальное сравнение с Web. Export/PDF/XLSX/Telegram/оборудование —08, backup/recovery/import —07.06 и09 TODO владельца; M17 исключён. Дополнительные расходы/storno приёмки отложены.

Следующий программный блок после проверки WH —07a native backup всех реально реализованных моделей/фото/schema9. До restore закрепить политику активной смены и частичной оплаты, целостный replace/staging/checkpoint, неизвлечение admin session/секретов в backup; не начинать merge с Web данными без preview и поддерживаемых fixtures07b.


2026-11-01: 05d.3 WH01–WH06 ci-verified/API36: [FullCI41305115280 attempt2](https://github.com/mendelev-main/M-POS-Android/actions/runs/41305115280/attempts/2)/883bbaf SUCCESS.173JVM/247instrumentation/0failures/0skipped,16Python/preflight/lint/debugAPK/testAPK. Downloaded HTML: WarehouseReportTest2/ReportUiTest2/RefundRepositoryTest8 passed; real8→9 preserves null legacy unit/old command replay/read-only reopen, SQL abort rolls back creation, all schema1..8→9 preserve stock/receipt. Exact0.0004/manual boundaries/unknown unit/incomplete ledger/noTracking, current-cost HALF_UP rows7.06, no partial money total and future-date rejection passed. Actual debugAPK1→2 UPDATE E2E PASSED with existing financial/warehouse fixtures and locked admin new process. Light component-host analytics-warehouse/locked screenshots inspected: readable/shared controls/no overlap, lower card edge is scroll position. Main navigation separately in existing suite; physical tablet/dark/new warehouse rows150%/large data/exact Web comparison pending. Schema9 only nullable movement.unit, JSON/fingerprints/main/signing unchanged. Attempt1 retained: Quickstep system ANR caused13 UI failures; unchanged emulator-only retry passed, no gates weakened. Earlier413039 failed old target8 assertion, corrected to9 and extended1..8;413037 intentionally cancelled after owner rounding reply. Programmatic checkpoint05 ready for07a,06/09 TODO, M17 excluded. Production package8→9 upgrade remains part of a future explicit release. Meaningful evidence/logs retained in harness.
