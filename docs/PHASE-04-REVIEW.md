# Сверка этапа04 перед05 — 2026-10-29

Текущий подтверждённый scope04a.1–04c.2: ci-verified Android16/API36. Полная физическая приёмка остаётся pending по решению владельца. Источник поведения — pinned Web1682c883; M17 исключён, дополнительные расходы/сторно receiving отложены владельцем.

| Критерий | Реализация и наблюдаемая проверка |
|---|---|
| I01 единицы/упаковки | Receiving draft/posting и standalone price mode: exact base quantities, неизвестная упаковка отказывается, HALF_UP по стоимости строки; RI/RP/SA tests |
| I02 stale baseline | Inventory Count/Fix: sale→refund с тем же stock, receiving, tracking off/on, process generation требуют нового факта; IF01–IF05 |
| I03 отмена сохраняет фиксации | InventoryClosingRepositoryTest: cancel после applied и последующей правки сохраняет current balance и immutable fixed/raw history; IT01–IT05 |
| I04 повтор/restart/отказ | Each-write failure/SQL abort/replay/concurrency в receiving/inventory; actual APK1→2 фикстуры закупок, приёмки и инвентаризации |
| Складские queries | StockList/LowStock/MovementHistory SD/SR, PurchaseStatus/PurchaseOrder history, ReceivingHistory, InventoryHistory; period/document links без записей |

Evidence: [FullCI40829976103](https://github.com/mendelev-main/M-POS-Android/actions/runs/40829976103)/55b042f:133JVM/203API36/13Python,0failures/0skipped,2scale/17UIseed/14upgrade fixtures, actual debugAPK1→2. Ранние SUP/PO/RI/RP/SA/IC/IF/IT/SR cases входят в эту регрессию; подробные прошлые runs сохранены в harness/build-log.md. Снимки shared stock period/document UI просмотрены. Это не device-verified статус.

Оставшийся объём не скрыт: физический планшет/dark/150%/cold-process UI; дополнительные расходы и сторно receiving отложены; сводные стоимостные складские отчёты и beginning/end balances относятся к M15/этап05, экспорт к08. В ledger историческая единица не добавлена; не восстанавливать её из текущей карточки. Полноценные роли05 ещё отсутствуют, все текущие изменения single-owner.

Программные I01–I04 в согласованном scope ci-verified; переход к05 допустим при сохранении перечисленных открытых пунктов. Первый05a — внесение/изъятие наличных и их журнал в локальной смене. Сотрудники/auth/roles и аналитика — следующие отдельные блоки D04; не считать их готовыми по наличию базовой смены.
