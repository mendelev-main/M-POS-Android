# Native tablet acceptance evidence

The user defers comprehensive physical testing until the end (2026-10-06). Specs 040–048 already enable products/layout/posNavigation/employees/shifts/orders/parked/currentOrderSession/criticalStorageJournal Room authority after automated checks; settings/SSE remain mirrors by implementation. Record the APK commit, tablet model, Android version, scenario, expected/actual result and pass/fail. The complete [Russian status report and user checklist](MIGRATION_STATUS_RU.md) includes current scope and metrics. Use synthetic data on a test tablet. Do not commit backup documents, tokens, device keys, photos or production records as evidence.

## P2 catalog and recovery

1. Start with a synthetic catalog containing Unicode names, several categories, products without a category, recipes/modifiers and photo references. Record counts and expected ordering.
2. Add, edit and delete products/categories through the existing UI. Wait for initial mirroring and compare after each completed operation. In the debug WebView console, `await MPosCore.CatalogCutover.health()` exposes mode, source and aggregate comparison. Expect `comparison.ok:true`, `comparison.matches:true`, equal counts, `activeSource:'room'` and `roomCutoverAllowed:true`.
3. Force-stop and reopen the app. Expect unchanged UI data and ordering; repeat comparison after mirroring. A merely green comparison is not proof of snapshot/order/business parity: inspect product data and category ordering against the test fixture too.
4. Export a synthetic v13 backup, restore on a clean test installation, reopen and verify fields, recipe/modifier data, photos and catalog comparison. Check iPad-to-Android compatibility separately when an iPad is available.
5. Exercise rapid edits, a large synthetic history, process exit during native work, and then restart/re-mirror. A known failed/pending native write must not report healthy parity; legacy data must stay usable. Record how failure was induced rather than claiming an unperformed failure test passed.

Record these results for final acceptance; catalog/workspace authority is already implemented by specs 040–048, including migration and rollback. Projection comparison now checks the Room document against its indexes; separately compare full fields against the fixture/backup.

## P1 printer/notification settings

1. Change printer and notification settings through the UI, verify their native snapshot, reopen and compare again. Keep all existing printer routing/notification expectations.
2. Repeat during offline operation. Local saves must not depend on backend availability.
3. Exercise storage failure and Activity destruction during native writes where practical. Native errors must not undo a successful legacy save; no native callback should target a destroyed Activity. Record tests not performed as pending.

## P3 diagnostic SSE

1. Observe the same test stream with legacy EventSource and native shadow enabled explicitly. Compare known message payload fingerprints and counts, including Unicode, multiline/empty data and permitted whitespace.
2. Verify named events are excluded from both message observers. Disconnect/reconnect and background/foreground; shared business events must not be delivered twice.
3. Send a test frame over the native diagnostic line/frame bound. The shadow should reconnect without affecting authoritative browser dispatch. Do not infer native reconnect/Last-Event-ID business equivalence from parser tests alone.

## Backup image staging

Verify export/import/confirm/cancel, missing/corrupt photographs, shared image references, low storage and restart. Confirm cancellation removes staged files without pruning active photos; final pruning happens only after the existing finishImport protocol. Keep the 500 MB document and 2 MB decoded-image limits unchanged.

## Report template

- APK commit / variant:
- Tablet / Android version:
- Test fixture description (no attached private data):
- Scenarios actually run and results:
- Failures and reproduction steps:
- Scenarios not run:

A report must distinguish observed physical behavior from automated assertions. Physical evidence remains pending until final acceptance; products already use native authority, while other domains retain their documented source.

## Workspace ownership (041)

Verify category order/colors/symbols/WEB flags and tiles/folders after edits, force-stop and restore. These documents now use Kotlin/Room; their UI rules remain shared. Confirm that a stale legacy cache cannot replace native layout on restart. Include these cases in the final comprehensive acceptance rather than blocking the next migration stage.

## Employee and shift ownership (042–043)

Employees and shifts (including cash movements) now use authoritative Kotlin/Room persistence, alongside products/layout/posNavigation/employees/shifts/orders/parked/currentOrderSession/criticalStorageJournal. Full compatible documents remain the source; typed indexes are supporting structures. Atomic migrations and failed-write rollback, stale shadow protection, restart, actual v13 import and journal recovery are covered automatically. Employee authorization and shift financial engines remain reviewed JS. Other business documents remain on legacy storage; final physical checks remain pending.

## Paid receipt ownership (044)

Orders now use Kotlin/Room document authority with transactional receipt/line/payment indexes. Original sale, split payments, historical consumption and full-return/loyalty fields remain compatible. Actual JS payment-journal recovery, v13 import and full return are tested. Financial engines remain reviewed JS; full-array writes/index refresh remain a performance limitation. See [receipt storage model](RECEIPT_STORAGE_RU.md) for the proposed per-receipt repository and return ledger, which are not yet implemented. Final physical acceptance remains pending.

## Parked-order ownership (045)

Parked orders now use authoritative Kotlin/Room persistence. Complete delivery/customer/modifier/WEB/printing JSON is retained; document and header/line indexes commit atomically. Actual park/resume flows, delayed acknowledgement and failed-resume journal replay are automated. CurrentOrderSession and recovery journal remain legacy at this boundary; no single cross-document SQL transaction is claimed. Business commands and physical printing semantics are unchanged. Full-array refresh remains a limit.

Final tablet cases: park a delivery/WEB order with modifiers/customer and kitchen-printed items, force-stop, resume and compare fields; resume must disappear from parked history without losing the current cart after restart; verify delete and v13 round-trip. Final physical evidence remains pending.

## Session and critical recovery ownership (046)

CurrentOrderSession and criticalStorageJournal now use authoritative Kotlin/Room persistence and atomic singleton indexes. Legacy data is imported only once; secondary cache cannot override native recovery. Journal read failure propagates so existing JS recovery blocks new critical operations. Failed journal clear retains pending snapshots. Existing payment/return/park/resume replay and v13 tests now run through this boundary, including absent or failed caches. Replay orchestration and business commands remain JS; cross-document transactions are still journal-coordinated.

Final tablet cases: restart with unfinished cart, delivery/modifiers/WEB and printing state; restart after interrupted payment/park/resume, verify receipt/stock/shift/cart consistency and no duplicate operation. Native storage failure must prevent critical saves; backup v13 must preserve session. Physical results remain pending.

## Incremental receipt rows and history (047)

Canonical receipt archives now use complete per-receipt JSON in existing header rows; positions/payments are related rows. Snapshot reconciliation touches changed/deleted receipts only. Native full-array compatibility read/export is retained; noncanonical archives retain full-document mode. Secondary full archive cache is invalidated. Lazy migration/rollback, revision-checked individual upsert and SQL pagination are automated. Android history uses pages of 50 beyond the previous 300-receipt limit, with original detail/print/return renderer and stale-response protection.

Limits: shared business runtime still loads/submits full arrays, native reconciliation scans them and critical journal still stores full snapshots. No claim of constant-cost complete payment or reduced startup memory. Final tablet checks include 325+ receipts, tied dates, page controls, returns/print, new sales on history tab, restart and v13 export/import. Noncanonical fallback retains prior history presentation.

## Atomic local payment command (048)

Actual payment finalization now uses Kotlin validation/stock deduction/delivery cash checks and one Room transaction for catalog, shifts, one receipt, cleared session and idempotency marker. No full order archive is sent or copied to a payment journal. Exact retry is locally idempotent; stale/conflicting commands fail. Pricing, discounts/rewards and recipe expansion remain reviewed JS; external effects run after native acknowledgement. Nonpayment journal recovery remains unchanged. This partially implements P7, not the whole financial engine. Physical cash/card/split/delivery/reward/force-stop and v13 cases remain pending.

Business issue for separate refactor: existing cross-shift refund drawer calculation (100 opening minus 20 refunded from prior shift reports 100 instead of physical 80) is reproduced and retained, including native delivery cash parity. Return attribution and historical reports need a coordinated business fix.

## 049 — Refund attribution / cash drawer

- In shift A sell a cash receipt for 20, close A; open B with 100. Return that receipt in B: expected cash 80, refund 20, net revenue -20; A retains the sale and its original expected cash.
- Repeat for card and split (cash 8/card 12): card return leaves cash unchanged; split reduces B cash by 8 and card revenue by 12.
- Same-shift return restores stock and deducts the refund exactly once. A second return must be blocked.
- With only 1 cash left after a cross-shift refund, delivery requiring cash withdrawal 2 must be blocked without stock or receipt changes.
- Close B and compare the screen, PNG receipt sent to Telegram, print and PDF expected cash/difference/net totals. Negative revenue must remain visible.
- Restart and export/import v13; verify attributed returns remain. Old records without return shift use original-shift fallback and must not be guessed into another shift.

Status: pending physical tablet execution; automated coverage is not physical acceptance.

## 050 — Atomic return persistence

- Cash/card/split full returns: stock equals historical stockConsumption even after changing the recipe; card money is returned separately through the bank terminal.
- Return with changed noStockTracking: historical tracked quantities still restore; deleted historical stock product rejects the entire operation.
- Attempt a duplicate return; confirm no second stock restoration/movement. Restart after a return and compare drawer, history and v13 export/import.
- Interrupt acknowledgement/storage during return; reload before further critical actions if result is uncertain. Confirm either the full return persisted or none of it did; never mixed receipt/stock/cash state.
- Import a pre-return synthetic backup, return again; old command markers must not suppress the new return.
- Legacy receipt without stockConsumption uses the old restoration path. After recipe edits the original ingredient mix cannot be reconstructed; flag this business limitation for later review.
- Verify loyalty reversal after persisted return and no availability send/retry from the return itself. Failed availability retry remains gated by the next persisted payment.

Physical status: pending, deferred by user instruction.

## 051 — Manual deposit / withdrawal

- Open with 100; deposit 20 with a comment: expected cash 120; withdraw 20: expected 100. Confirm note, movement order and history after restart.
- Withdraw exactly the available amount; reject more than available, zero/negative/invalid input. With cross-shift return leaving 80, withdrawing 81 must fail without a new movement.
- Repeat a save tap while acknowledgement is pending; confirm one movement. Interrupt storage/ack, reload on unresolved status, confirm one complete operation or none.
- Import a pre-operation synthetic v13 backup and repeat a new operation; verify old markers do not suppress it. Export/import full history and extension fields.
- Preserved current cases: amount 0.001 is not rounded; negative drawer blocks deposits too. Record these for a later business precision/recovery decision.
- Close shift and compare expected cash/difference on screen, image/Telegram and printed report.

Status: pending physical testing at the end of migration.

## 052 — Open / close lifecycle

- First shift opens at zero. After closing with expected 100 and counted 95, next opening carries 95 from that closed shift, preserving employee name/phone.
- Validate ordinary staff selection and existing administrator password rejection/acceptance. Missing/stale employee or already-open shift must not add another shift.
- Close with zero, less/more than expected and fractional comma input; reject empty/negative/invalid counted cash. Difference can have either sign.
- Cross-shift refund: opening 80 less old cash refund 20 gives expected 60; closing counted 65 reports +5 consistently on screen/PNG Telegram/print/PDF.
- Interrupt storage/ack for open/close; unresolved status requires reload. Confirm exactly one local state change and no partial shift/projection record. Network failure must not undo locally saved closure.
- Restart and restore synthetic backup from before opening/closing; stale replay must not falsely acknowledge the restored state; new lifecycle can proceed.
- An unpaid cart and parked orders survive closing and opening the next shift; record this existing behavior for business review.
- Opening Telegram/monthly report and closing PNG/print start after local save. No availability retry is introduced.

Status: physical tablet / Telegram / printer evidence pending until final migration testing.

## 053 — Native report values / output consistency

- Close a synthetic shift with same/cross cash/card/split returns. Compare report dialog, Telegram PNG, LAN receipt and PDF: cash/card, expected/count/difference and movements must agree.
- After cross-shift return, old shift retains the sale; return shift carries refund/negative net revenue. Test same-shift returned receipt: image keeps all sale receipt entries as on iPad, while "Заказов" retains the current net count.
- Verify receipt sorting, item name/productName aliases, notes, currencies/establishment header, configured printer routing and copies.
- Interrupt native report read after closure: closure stays saved, no stale image/print is emitted. Reopen a manual report after recovery, restart and synthetic v13 import; values must refresh.
- Rapidly switch report windows or close loading modal; late responses must not restore dismissed/replaced views.
- Disabled Telegram notification does not send; report/model does not trigger catalogue sync or availability retry. Native read failure must not add a network retry loop.
- Exercise a representative large archive and record report latency/memory. No benchmark is accepted from cloud synthetic fixtures alone.

Status: pending comprehensive physical testing.

## Native shift screen (054)

- Open/close shift, deposit/withdraw cash and view historical reports. Compare all fields with legacy session rollback; include discounts, split and same/cross-shift refunds, delivery, movement comments and 20+ closed shifts.
- Navigate rapidly between tabs and open/dismiss forms/reports; native content must never cover navigation or a modal, nor reappear from late replies.
- Rotate/change window size and font scaling. Check scrolling, buttons, long employee/comment text, keyboard and Android Back without model-specific assumptions.
- Simulate read failure: error contains retry/explicit legacy screen; no invented zero/stale totals. Restart returns native screen. Import synthetic v13 and verify fresh finance/history.
- Record latency/memory with a representative large archive. Read does not publish availability or sync catalogue.

Status: pending comprehensive physical testing.

## First-run fix (055)

- Update/restart the APK. On a fresh installation open the first shift with a cashier and then with an administrator (existing password rule), starting cash 0; each operation must save once.
- Restart and verify employees and shift persist. Deposit, take a cash payment, close with counted cash, restart and reopen with the previous counted carryover.
- A reported save failure must remain visible with Android guidance; successful first opening must not raise Safari/"all data will reset" warning.
- Repeat for a synthetic v13 backup with absent/null shifts; check explicit empty [] and existing populated history too.

Status: pending physical verification of updated APK.

## User-reported physical evidence after 055 — 2026-10-06

User confirms on the Android tablet that an employee can now open a shift, and that export/import of their backup into the new Android POS restored products, employees and other data. This confirms the reported first-opening defect and that import flow at this scope works. No backup/production data retained in Git. Detailed field-by-field comparisons, payment/return/print and interrupted writes remain pending; this is not blanket acceptance of every physical case.

## Native cash forms (056)

- Deposit/withdraw with decimal comma, 0.001, optional/long comments; inspect exactly one preserved movement after restart.
- Insufficient withdrawal, negative drawer, same/cross-shift cash refunds, force-stop during save and repeated taps retain existing command behavior.
- Confirm/cancel/Back/outside, keyboard, font scaling, rotation and rapid modal replacement: one visible form, no stale acknowledgement or duplicate movement.
- Simulate known rejection and uncertain acknowledgement: editing allowed only for known rejection, reload required for uncertainty. No new availability publication or catalogue sync.

Status: pending physical verification of 056.

## Native closing form (057)

- Close with expected cash unchanged, zero counted, shortage/surplus and comma/fraction; inspect saved counted cash/difference and next opening carryover after restart/import.
- Compare expected cash after same/cross-shift cash/card/split refunds with drawer/report. New sale while form is open keeps original recalculation behavior.
- Check Telegram PNG and configured receipt printer run after successful close only; disabled gates/copies and unpaid cart/parked orders remain.
- Loading read failure: retry or explicit legacy fallback, no automatic stale financial model. Cancel/replaced form/rapid repeated taps/Back/keyboard/rotation/font scale must not duplicate or reopen stale forms.
- Interrupt acknowledgement/restart: recorded close exists once; unknown status requires reload before another operation.

Status: pending comprehensive physical verification of 057.

## Native opening form (058)

- Fresh zero-cash installation and imported populated history: select cashier, inspect initial cash from the latest closed counted sum, open/restart and verify one saved shift with correct employee/phone/source.
- Select administrator: password field appears. Missing/wrong password must not write; current correct password follows existing policy. Switch to cashier/cancel/reopen: no prior password remains.
- Empty staff, long/full Russian names, selection ordering, keyboard/IME, font scaling, rotation and Back behave without hardware assumptions.
- Read error supports retry or explicit legacy form; cancelled/replaced loading reply cannot reopen. Double taps/uncertain ack prevent another write until recovery.
- Opening Telegram/monthly gates run after local save only. No availability retry/catalogue sync. Backup import preserves employees, roles and carryover.

Status: pending comprehensive physical verification of 058.


059: исправлено оформление нативного экрана смены: палитра POS, карточки,
акценты и переключение light/dark. Бизнес-логика не изменена.
Спецификация: `specs/059-native-shift-theme/spec.md`. На планшете проверить
обе темы, читаемость и кнопки смены; физическая проверка пока ожидается.


060: Kotlin пересчитывает цены позиций, товарные скидки и итог перед
сохранением оплаты; расхождение откатывает транзакцию без внешних действий.
Отображение корзины, формирование цены модификаторов и распределение подарков
пока JS. Совместимость v13 сохранена; rollback: MPosNativePricingEnabled=false.
Спецификация и наблюдения по бизнес-правилам: `specs/060-native-settlement-pricing/spec.md`.
На планшете ожидаются скидки/доставка/подарки/смешанная оплата/импорт.


061: распределение подарков и снимок лояльности пересчитываются Kotlin
в транзакции оплаты перед проверкой итоговой цены. Порядок программ,
самый дешёвый товар, целые единицы и правила округления сохранены.
Сетевой guard/публикация/возвраты и UI пока прежние; v13 без изменений.
Откат: MPosNativeLoyaltyRewardsEnabled=false. Спецификация:
`specs/061-native-loyalty-reward-allocation/spec.md`.
Физические проверки пересекающихся программ, подарка со скидкой/доставкой,
отсутствия сети, возврата и импорта ожидаются.


062: цена новой позиции (база, доплаты модификаторов, ручной ввод/округление)
формируется Kotlin до добавления в корзину. Оплата проверяет базовый снимок
и доплаты; старые позиции без basePrice не переоцениваются. При ожидании
ответа добавления последовательны, оплата ждёт; отменённые/устаревшие ответы
не меняют корзину. Формы выбора/preview, merge orchestration и рецептуры ещё JS.
v13 без изменений. Откат: MPosNativeConfiguredPricesEnabled=false.
Спецификация: `specs/062-native-configured-unit-prices/spec.md`; физические
кейсы ручной цены, модификаторов, повторных нажатий/отмены/остатков/импорта ожидаются.


063: Kotlin разворачивает рецептуры/модификаторы по каталогу Room внутри
транзакции оплаты, проверяет исторический снимок и использует свой расчёт
при списании. Общие ингредиенты/дроби/порядок/допуск/циклы сохранены.
Предварительные проверки корзины, доступность и себестоимость пока JS.
v13 без изменений; rollback: MPosNativeRecipeConsumptionEnabled=false.
Спецификация: `specs/063-native-recipe-consumption/spec.md`.
Проверки на планшете ожидаются. Локальные APK не собираются по указанию
пользователя; сборку после коммита выполняет GitHub.


064: добавление обычной/модифицированной/ручной позиции и слияние количества
ожидают проверки Kotlin по Room. Нехватка сообщает ингредиент; устаревшие
ответы при изменении корзины/остатков/состава/отмене не добавляют позицию.
Проверка read-only, списание только при оплате. Степпер количества и прочие
canFulfillCart caller/preview пока JS. v13 сохранён; rollback:
MPosNativeStockPreflightEnabled=false. Спецификация:
`specs/064-native-cart-stock-preflight/spec.md`. Физические кейсы ожидаются.
Локальные APK не собираются, тесты и lint обязательны.


065: qty + delta, удаление по <=0 и проверка всей корзины выполняются
Kotlin/Room. Изменения количества и добавление идут в одной FIFO; оплата ждёт.
Просроченные/отклонённые ответы не меняют строки. Контекст заказа после
удаления последней строки степпером сохранён, как в исходнике; спорные
правила уменьшения/legacy ключей описаны в спецификации. v13 без изменений.
Rollback: MPosNativeCartQuantityEnabled=false.
`specs/065-native-cart-quantity/spec.md`; физические кейсы ожидаются.
Локальная APK не собирается; обязательны тесты и lint.


066 — ожидает комплексной проверки на планшете:
- Открыть оплату обычного товара, рецепта и модификаторов при достаточном/недостаточном остатке.
- Оплатить наличными, картой и смешанно; проверить подарок, включая отсутствие связи.
- Закрыть оплату или изменить внесённую сумму во время чтения: старый ответ не подтверждает платёж.
- Быстро менять количество и нажать оплату: дождаться изменения корзины.
- После успеха убедиться в единственном чеке и единственном списании; после отказа — в отсутствии списания.


067 — ожидает комплексной проверки на планшете:
- Сравнить итог/строки оплаты и сохранённый чек: процентная/фиксированная скидка, ручная цена, модификаторы, дробное количество, доставка.
- Проверить подарок на дешёвую позицию, перекрывающиеся программы и подарок вместе со скидкой/доставкой.
- Проверить наличные/сдачу, карту, смешанные части, обновление подарка перед оплатой.
- Во время ожидания расчёта закрыть оплату/изменить заказ/сумму: устаревший ответ не подтверждает платёж.
- При недоступном bridge расчёт не даёт оплатить нулевой чек; повторить после восстановления.


068 — ожидает комплексной проверки на планшете:
- Полная оплата картой: нативная инструкция/сумма, подтверждение → один сохранённый чек, списание/печать после сохранения.
- Смешанная оплата: карточная часть сохраняется один раз; следующая часть и финальное завершение работают.
- Отмена кнопкой, системной Back и вне окна: чек/часть не сохраняются.
- Светлая/тёмная темы, длинное название валюты, большая сумма и крупный системный шрифт.
- Смена/закрытие окна или изменение заказа: старое подтверждение не оплачивает новый заказ.
- Закрыть приложение до подтверждения: платёж не появляется автоматически после запуска; состояние внешнего терминала проверить отдельно.


069 — ожидает комплексной проверки на планшете:
- Наличная часть: точная сумма/номинал/сдача; меньше суммы не подтверждается.
- Ввод точкой и запятой, дроби на границе округления, сохранённый tender из импорта.
- Наличная+карта и несколько наличных частей: одна запись progress на confirm, финальное сохранение/списание единожды.
- Отмена/Back/касание вне окна, замена окна/изменение заказа: часть не становится оплаченной.
- Ошибка записи progress: paid=false, tender остаётся для повторного открытия, как в исходнике.
- Светлая/тёмная темы, крупный шрифт, клавиатура и прокрутка номиналов.


070 — ожидает комплексной проверки на планшете:
- На основной оплате нажать внесённую сумму: native editor, точка/запятая, максимум две цифры после разделителя, quick sums/preview.
- Done обновляет сумму, без создания чека; Cancel сохраняет прежнюю сумму, Back/замена окна не применяют старый ответ.
- Оплата наличными: точная сумма, недостаточно средств, сдача, нулевая стоимость и дробный total после скидки.
- Подарок online/offline: «Продолжить без подарка» обновляет total/tender до сохранения; закрытая оплата не оплачивается после позднего gift ответа.
- Доставка без выбранного тарифа/нехватка ингредиента: сохранение не запускается.
- Переключение cash/card/split, закрытие и повторное открытие оплаты, обе темы/клавиатура/крупный шрифт.
- Успешная продажа создаёт один чек и списание; внешние эффекты только после локального сохранения.


071 — визуальная приёмка на планшете ожидается:
- Светлая/тёмная темы: смена, employee picker/dropdown, admin password, empty/loading/error/busy/blocked состояния.
- Manrope и контраст кнопок/сумм совпадают с основным POS; disabled/focus различимы.
- Открытие обычным сотрудником/admin (проверить своим паролем без публикации), повторное открытие не сохраняет пароль.
- Крупный системный шрифт, portrait/landscape, длинное имя/валюта/большая сумма: не обрезаются, scroll доступен, кнопки видны при клавиатуре.
- История: карточки/действие отчёта открывают ту же смену; значения и порядок совпадают с чековой историей.
- Внесение/изъятие/закрытие, card/cash/split: новый внешний вид, прежние данные/подтверждение и единственное сохранение.

072 — смешанная оплата, первоначальный план (пока не проверено на планшете):
- Заказ 10,01: открыть две части; ожидается 5,01 и 5,00, сумма ровно 10,01.
- Изменить количество на 3/10, редактировать суммы и методы; проверить прежние правила.
- Оплатить одну часть, перезапустить, завершить: оплаченная сумма сохраняется,
  итоговый чек и списание остатков выполняются один раз.

073 — количество смешанных платежей (физическая проверка ожидается):
- Заказ 10,01, paid карта 5,01: 3 части → 5,01 paid + 2,50 + 2,50;
  2 части → 5,01 paid + 5,00. Paid/tender/метод неизменны.
- Быстрые +/-; во время ответа оплата ждёт. Выход/изменение заказа отменяет ответ.
- После первой paid перезапуск; завершение создаёт один чек/списание.

074 — редактирование mixed суммы (физическая проверка ожидается):
- 10,00: paid 2,00 + unpaid 4,00 + 4,00; средняя 3,00 → 2,00/3,00/5,00.
- Последняя 1,00 → 2,00/7,00/1,00; paid/методы неизменны, tender очищен
  только у edited строки. Это сохранённое исходное правило.
- Быстрый ввод/Backspace/пустое/запятая, +/- между вводами, оплата во время
  ответа; выход отменяет ответ. Сверить отзывчивость, restart и один чек/списание.

075 — нормализация/восстановление mixed draft (физическая проверка ожидается):
- 10,01: paid карта 5,01, unpaid наличные 5,00 → restart/open сохраняет значения;
  завершить: один чек/списание, paid повторно не оплачивается.
- All-paid до restart: восстановление не выполняет auto finalize.
- Повторно открыть unpaid части, проверить методы/tender и ровное распределение.
- Сохранённые подарок/доставка/скидка; изменённая скидка/повреждённый draft —
  прежняя политика несовпадения суммы. Проверить время запуска и Back при ожидании.

076 — cart preview (pending): qty/price/discount/gift/delivery, быстрый add/delete,
scroll/swipe/open comment/keypad при ответе; суммы строк/корзины/оплаты/чека.
Dark/light/large font и длинный чек; замер отзывчивости. Source preview до ответа.

077 — доставка (pending): без тарифа checkout запрещён; явно выбрать 0/2/5,
сверить чек/итог; type туда/обратно, удаление тарифа, быстрые select/type,
Cancel/modal replacement/pending checkout, restart/backup v13.

### 078 — pending: настройки заказа

- Сохранить название, имя, телефон и адрес с крайними пробелами/кириллицей.
- Редактировать телефон привязанного клиента: id и бонусы должны сохраняться.
- Дважды нажать сохранение, изменить поле/закрыть форму во время запроса.
- Проверить restart/backup v13: поля, comment WEB и оплаченные split части.

### 079 — pending: отложенные заказы

- Hold/resume с клиентом, WEB comment, доставкой, модификаторами; restart/v13.
- Повторный hold после кухни: печатать только добавленное количество.
- Delete отложенного заказа не должен менять текущую корзину.
- Отказ записи должен сохранить текущую корзину без печати.
- Быстрые изменения контекста/корзины/split не должны создавать двойной заказ.
- Проверить отдельно исходное ограничение: paid split parts не входят в parked.

## Этап 080

080 pending: категории add/rename/color/symbol/menu/order; duplicate/used delete; type edit с units/dependencies/unreturned/returned receipt; фото offline; rapid modal/form changes; restart/v13; без availability от редактирования.

## Этап 081

081 pending: nested/shared/cycle/missing recipes, units/yield, modifier name/min/max/self/duplicate/negative qty; 0→1 и default omission source rules; new IDs/reopen/v13; delta price/stock/return; rapid edit/close/double save; фото offline, без новых network triggers.

## Этап 082

082 pending: folder create/rename/delete/move/root/reorder; root duplicate tiles/20-limit/occupied-cell drag/cancel; rapid gestures/close/field edit/storage fail; restart/v13 order/coords; Cyrillic/emoji 80 UTF-16 boundary; no backend catalogue/availability effect.

## 083 employee commands — pending

Create/edit ordinary employees offline; change roles with correct/incorrect password; preserve extension fields after v13 import/edit/export. Check demotion of the last administrator retains current policy. Delete ordinary employee with an open shift and correct/incorrect password; reject self/admin deletion and deletion without a shift. Restart and verify saved records. Confirm no availability/catalogue network publication. Record APK commit and actual results at final tablet acceptance; automated checks are not physical acceptance.

## 084 customer association — pending

Select/remove a customer, refresh profile and verify rewards reset, retained address/extensions and metadata. Repeat offline; central search/create still requires backend. Exercise rapid selection, modal close, old profile response, storage failure, force-stop/restart and v13 export/import with a WEB order and paid split draft. Verify no automatic catalogue/availability requests.

## 085 gift eligibility — pending

Available/unavailable gifts online; offline/timeout cancel and explicit continue without gift. Change customer/redemption/order or close dialog during read; no obsolete payment continuation. Check profile refresh and reconnect without automatic gift work, completed payment/return/restart. Correctly selected gift must still use existing price/receipt allocation.

## 086 loyalty sale/reversal journal — pending

Pay with customer online/offline; restart during sending; repeated reconnect with live requests; return while sale is pending/sending/synced; ensure sale completion precedes pending reversal. Simulate network success/local finish failure; reopen and verify allowed idempotent recovery. Export/import v13 and reopen. Several pending receipts plus a new sale/return must preserve all financial/stock totals, receipt history and return fields. Availability must still wait for next saved payment.

## 087 WEB journals/ACK — pending

Accept online/offline/timeouts with valid estimate; prepared/local/ACK/confirmed restart points; legacy missing-estimate prompt; kitchen printing enabled/disabled; no duplicate parked order. Mark ready, move to another session and restart/reconnect; pending remains ACK eligible, prepared never sends. Failed local confirmed save must retain native queue/event until durable confirmation. Concurrent unrelated records, read failure, v13 export/import/reopen, stock and paid split metadata. Check availability remains payment-gated.

## 088 — WEB SSE transport (pending)

- Open POS, create WEB order: one notification, correct persisted order after restart.
- Switch Wi-Fi off/on and background/foreground: stream reconnects, no duplicate active stream; no catalogue/availability publication.
- Request owner live report while foreground stream is connected; response retains current shift values.
- Close/reconfigure backend during delivery: previous connection cannot update the new session.
- Large/multiline payload and slow local save: events retain order, memory does not accumulate a JS message queue.
- Server 204/wrong MIME closes; connection loss retries; replay depends on backend IDs/replay support.

## 089 — каталог и фото (pending)

- Без нажатия «Синхронизировать» запуск/сеть/foreground не отправляют каталог.
- Администратор вручную отправляет категории/цены/видимость/фото: статус и WEB-каталог совпадают.
- Офлайн или отказ сервера: ошибка без автоматического повтора, повтор только по кнопке.
- Фото сохраняется локально до отправки; успех обновляет imageUrl, 413/таймаут оставляет imageUploadPending.
- При смене изображения во время запроса старый ответ не заменяет новое изображение.
- Отмена/закрытие приложения закрывают запрос; восстановленный бэкап v13 и лимит 500 МБ работают как раньше.

## 090 — availability после оплаты (pending)

- Сохранённая оплата отправляет текущие остатки простых товаров/составов и WEB settlements; неуспешная локальная оплата не отправляет.
- Офлайн оплата → сбой отправки → reconnect/restart/foreground/возврат/приход/редактирование/ручной sync: availability не отправляется.
- Следующая сохранённая оплата отправляет актуальный snapshot с возрастающей revision.
- Даже после успешной отправки ручной sync/редактирование/возврат не отправляют availability без новой оплаты (актуальное правило 090).
- Выход в фон во время HTTP закрывает запрос; повтор разрешён только новой оплатой.
- Несколько оплат во время успеха: только последний ожидающий snapshot; при сбое очередь сбрасывается до оплаты после сбоя.
- Бэкап v13 импортируется как раньше, внутренние разрешения/тикеты не появляются в бизнес-данных; stale WEB stock до следующей оплаты ожидаем.


## 091 — поставщики (pending)

- Кассир создаёт/редактирует поставщика и привязки товаров; удаление недоступно.
- Администратор в открытой смене удаляет поставщика; после закрытия смены удаление не проходит.
- Старые заказы/приёмки сохраняют имя поставщика после его переименования/удаления.
- Импорт v13, повторный запуск, одинаковые имена и пустые привязки работают; ошибка сохранения не меняет список в памяти.


## 092 — заказ поставщику (pending)

- 3 бутылки × 500 мл при stockUnit=l → 1,5 л; неизвестный размер упаковки уточняется при приёмке.
- Формирование сохраняет purchaseUnit/packSize/contentUnit и pending заказ, но не меняет остаток; повторный запуск/импорт v13 сохраняют данные.
- Ошибка сохранения оставляет корзину и исходные товары, не создаёт заказ.
- Администратор удаляет ожидаемый заказ: он остаётся в истории, история приёмок содержит нулевую adminDeleted запись; черновики удалены.
- Кассир/закрытая смена/received заказ не допускают удаления.
- Старые/неполные записи приёмки импортируются без потери исходных JSON; PDF/поделиться/текст заказа совпадают с прежним поведением.


## 093 — подтверждение приёмки (pending)

- Открыть предпросмотр, продать товар, затем подтвердить: приход/себестоимость используют актуальный остаток.
- 3 бутылки по 500 мл переводятся в 1,5 л; повторные строки товара последовательно пересчитывают стоимость.
- Частичная поставка закрывает исходный заказ и сохраняет shortage, новый заказ не создаётся.
- У товара без учёта остатков приход сохраняет остаток и меняет себестоимость; повторные строки используют цену последней положительной строки.
- Самостоятельная приёмка очищает отдельный черновик, приёмка заказа удаляет его receivingDraft/receivingDraftV2.
- Ошибка записи оставляет каталог, заказ, историю и черновик исходными; двойное нажатие не создаёт второй приход.
- Офлайн, повторный запуск, экспорт/импорт v13 сохраняют приход и дополнительные поля; приёмка не отправляет availability.


## 094 — черновики приёмки (pending)

- Начать приёмку заказа, выйти и перезапустить: кнопка «Продолжить приёмку», строки/единицы/упаковки восстановлены.
- Старый черновик из бэкапа v13 восстанавливает количество и сумму, в том числе нули; неизвестный размер упаковки оставляет количество незаполненным.
- Сохранить частично заполненный заказ: пустые поля разрешены; остатки/себестоимость не меняются.
- Самостоятельный черновик сохраняет накладную и строки после перезапуска/импорта v13; открытие исправляет orderId на null.
- Полученный/удалённый заказ открыть или перезаписать черновиком нельзя.
- Ошибка записи оставляет введённые поля и прежний заказ, кнопка сохранения снова доступна; двойное нажатие не создаёт повторную запись.


## 095 — инвентаризация (pending)

- Остаток 10 л, фактический 8 л: «Фиксировать» сохраняет 8 л и расхождение −2; себестоимость не меняется.
- Продать 1 л после фиксации: завершение сохраняет остаток 7 л, история содержит зафиксированное расхождение −2.
- Положительные/нулевые/дробные количества округляются как раньше; null/некорректный ввод/товар без учёта не фиксируются.
- Итоговые потери учитывают только отрицательные расхождения и стоимость товара на момент завершения.
- Плановая инвентаризация обновляет lastCompletedAt, внеплановая не меняет его; все строки нужно зафиксировать.
- Ошибка записи не оставляет отдельно изменённый остаток или историю, черновик сохраняется для повторной попытки.
- Продолжить после перезапуска/импорта v13; исходные поля и история сохраняются, старые shadow-записи не откатывают документы.
- Проверить существующий сценарий отмены: после фиксации 10 → 8 л отмена убирает черновик, остаток остаётся 8 л. Неверный текст окна обсуждается отдельно; политика не изменена.
- Офлайн фиксация/завершение не отправляют availability; публикация возможна только после сохранённой оплаты.


## 096 — складские отчёты (pending)

- Сверить выбранные дни: движения в начале включены, движение в следующую полночь исключено; проверить часовой пояс планшета.
- Приход 500 мл в товар с единицей л = 0,5 л; суммы приёмки и поставщики соответствуют накладным.
- Продажа до начала периода и возврат внутри периода учитывают только возврат; старые чеки без stockConsumption показывают предупреждение.
- Для удалённого товара/товара без учёта остаток неизвестен, количество закупки видно; adminDeleted приёмки не входят.
- Начало/конец — оценки по известным движениям; формирование отчёта не меняет реальный склад.
- Быстро менять даты и закрывать страницу во время загрузки: старый результат не заменяет новый и не открывает закрытую страницу.
- PDF/XLSX содержат выбранные до запуска разделы/период/формат, повторное нажатие во время загрузки не создаёт повторный экспорт.
- Ежемесячный Telegram отчёт при открытии смены администратором сохраняет текущие настройки/успешный маркер периода; новых отправок при запуске/сети нет.
- Бэкап v13, офлайн чтение, русская сортировка Е/Ё и латиницы сохраняют действующее поведение.


## 097 — аналитика продаж (pending)

- Выручка/количество/средний чек и прибыль совпадают с архивом за включённые дни; сумма строк может отличаться от чека со скидкой/доставкой.
- Смешанные оплаты считают наличные и карту из payments; пустой payments не возвращается к method, прочие способы не добавляются к этим двум показателям.
- Продажа 1 октября с возвратом 7 октября исключается и из аналитики 1 октября — текущая политика сохранена, вопрос об учёте по дате вынесен отдельно.
- Категория из текущего каталога, название из чека; разные товары с одним названием объединены, сотрудник определяется по snapshot смены.
- Стоимость склада не зависит от выбранного периода, исключает составные/∞ товары и отрицательные остатки/стоимость.
- Сверить сегодня/7/30 дней, ручной период, местную полночь и последнюю миллисекунду дня; быстрое изменение периода не применяет старый ответ.
- Кассир не видит административные финансовые KPI/денежные значения сотрудников; доступные графики остаются как раньше.
- Смена вкладки во время загрузки не меняет новый экран; фокус date input и прокрутка сохраняются при обновлении.
- Офлайн локальная аналитика работает, центральная лояльность показывает прежний статус связи. Ошибка чтения показывает повторный запрос без JS fallback/ложных нулей.
- Светлая/тёмная тема и увеличенный шрифт: загрузка/ошибка/кнопка «Повторить» соответствуют стилю приложения. Бэкап v13 сохраняет расчёты после восстановления.


## 098 — зал и бронирования (pending)

- Офлайн создать квадратный/прямоугольный стол, переименовать, повернуть, переместить и перезапустить приложение: карта сохраняется. Светлая/тёмная тема и стиль прежние.
- Создать бронь 19:00–21:00: новая в 20:59 отклоняется, в 21:00 разрешается. Отмена освобождает время; правка отменённой брони не восстанавливает её статус.
- Бронь 23:00–01:00 мешает новой в 00:00 следующего дня; проверить местное время на планшете.
- Пустое имя гостя не сохраняется и не меняет текущую бронь. Гости минимум один, UI stepper до 30, прежние права кассира/администратора.
- Удалить стол: удаляются его брони, остаются остальные столы/брони и архив чеков с прежним названием стола.
- Повторные нажатия во время сохранения не создают дубликаты. Ошибка сохранения не закрывает форму с сообщением об успехе; неуспешное перемещение возвращает исходные координаты.
- Экспорт/импорт v13 и перезапуск сохраняют столы, статусы, время, гостей, заметки и дополнительные поля. Никаких синхронизаций каталога/публикаций остатков от операций зала.


## 099 — нативные задания печати (pending)

- На принтерах 58/80 мм проверить пробную печать и сохранённые чеки, кириллицу, комментарии, суммы/скидки/доставку, обрезчик. Содержание/растровый формат этого этапа сохранён, полную физическую приёмку выполнить отдельно.
- Касса/кухня/двойной тип: ручная печать игнорирует auto-флаг, автоматическая учитывает его. Настройка categories=[] направляет все позиции, непустой список — только выбранные категории; пустой кухонный документ не печатается.
- Сверить 1/2/3 копии и порядок двух чеков на одном принтере. Разные принтеры работают параллельно; недоступный принтер не создаёт неограниченное число потоков.
- Сохранённая оплата печатает сумму из архива Room; уже напечатанная кухня не печатается повторно при оплате. Отложенный/WEB-заказ сохраняется до вызова кухни; backend ACK не меняет точку вызова печати.
- Закрытие смены печатает сохранённый отчёт на кассовых принтерах независимо от autoPrintReceipt; при отсутствии принтера показывается прежнее сообщение. Telegram-изображение работает как раньше.
- Разорвать связь во время отправки, затем вернуть сеть/перезапустить/вернуть приложение: автоматической допечатки нет. Проверить бумагу до ручного повтора, поскольку часть чека могла уже попасть в принтер.
- Сбой сохранения внутреннего задания/начала отправки не отправляет данные; неопределённый финальный статус не выдаёт подтверждение физической печати.
- Полное закрытие приложения останавливает транспорт. Бэкап v13 сохраняет настройки/заказы, импорт не восстанавливает и не запускает старые задания печати.
- Пока сохраняется прежняя политика: позиции могут иметь kitchenPrinted=true даже при отсутствии настроенного кухонного принтера/ошибке связи. Это статус бизнес-снимка, не подтверждение физической печати. Автоматический повтор после обрыва запрещён решением пользователя.

## 100 — настройки и сотрудники (pending)

- Проверить оба оформления, портрет/альбом, системный шрифт 150–200%, длинные ФИО и клавиатуру: подписи читаются, действия переносятся, длинные формы прокручиваются.
- Создать/переименовать сотрудника без смены; проверить запрос пароля при изменении роли, неверный пароль, отмену и сохранение. Переключение роли не теряет ФИО/телефон.
- Удаление другого обычного сотрудника требует открытой смены и исходной проверки; текущего сотрудника/администратора удалить нельзя. История чеков/смен сохраняется.
- Проверить административные настройки организации/доставки/скидок, доступ кассира, панель администратора и переходы к отдельным страницам поставщиков/лояльности/склада.
- Сохранить принтер, параметры 58/80 мм, касса/кухня, категории, копии, автоматическую печать и уведомления; полностью закрыть/открыть приложение и сверить значения. Пробная печать использует сохранённые настройки; ошибка сохранения не запускает печать.
- Сделать экспорт v13, изменить настройки, импортировать и сверить принтеры/звуки вместе с остальными данными. Если настройки не восстановились после уже записанных бизнес-данных, полного сообщения об успехе быть не должно; после восстановления повторить импорт.
- Проверить повторное нажатие во время сохранения, отмену/кнопку Android «Назад», ошибку/неизвестный результат записи. Неизвестный результат блокирует новую запись до перезапуска.
- Проверить backend/Telegram формы, маскировку токена, тест соединения и ручную синхронизацию. Сохранение настроек, импорт, сеть и возврат в приложение не запускают новые автоматические синхронизации/публикации остатков/повторы печати.
- Превью — синтетическая отрисовка Android Views, не скриншоты HONOR. WebView и исходные обработчики авторизации всё ещё являются границей совместимости; отказ от WebView относится к 109.

## 101 — обычное рабочее место (pending)

- В обеих темах и ориентациях сверить категории/папки/плитки с сохранённой раскладкой, включая свободные ячейки и плитки с несколькими колонками/строками. Проверить длинные названия и системный шрифт 150–200%; корзина/оплата доступны через прокрутку.
- Выбрать обычный, составной, безлимитный/отсутствующий товар, товар с ручной ценой и модификаторами. Сверить ограничения остатков и сохранение заказа при полном перезапуске.
- Сверить цену/скидку/доставку/лояльность/общий итог после обновления native quote; прокрутка списка не прыгает при изменении суммы.
- Две строки одного товара с разными модификаторами/комментариями: редактирование и удаление затрагивают выбранную строку. Проверить удаление кнопкой и свайпом влево, вертикальная прокрутка не удаляет строку.
- Проверить папку, выбор товара/возврат/кнопку Android «Назад», параметры заказа/клиента, отложить/заказ готов/оплатить и отсутствие оплаты без смены. Модальные формы должны перекрывать рабочее место без нативной панели поверх них.
- «Раскладка»: исходный редактор/перетаскивание/создание/перемещение/удаление папки остаются доступны; после «Готово» возвращается нативный обычный режим.
- Быстрые повторные нажатия во время изменения заказа не дублируют сохранение/печатные действия; неизвестный результат записи блокирует новую операцию.
- Импорт v13/смена вкладки/фон/возврат/сеть не добавляют новых автоматических публикаций остатков, синхронизаций каталога или повторов печати. Физическую скорость/память оценить отдельно: до 109 исходный DOM ещё сохраняется как путь совместимости.

## 102 — редактор товара/рецепта/модификаторов (pending)

- В обеих темах/ориентациях, со шрифтом 150–200% и клавиатурой: пройти все пять разделов, длинное название/заметку, активный раздел, сводку и кнопки сохранения/назад/удаления. Прокрутка и введённые поля не должны теряться после обновления формы.
- Создать простой товар, закрыть/открыть карточку/перезапустить приложение; сверить название/категорию/цену/артикул/заметку/закупочную упаковку. Себестоимость существующего товара readonly; остатки и онлайн-настройки сверить под администратором и кассиром.
- Остаток 7 л → мл должен показывать 7000 мл, сохранение не меняет базовую единицу; сверить кг/г, себестоимость/минимальный остаток, «Не вести учёт». Набрать 2,5 с паузой после запятой — поле не сбрасывается.
- Составной товар: поиск/категории ингредиентов, повторное добавление, количество/удаление, единицы, автоматический и ручной выход. Нулевое/невалидное количество, отсутствующий ингредиент и цикл дают прежние ошибки; товар с единицами/связями нельзя превратить в другой тип.
- Модификаторы: несколько групп, min/max, выбор/поиск товара, POS-название, количество для списания и доплата, удаление варианта/группы, повторное открытие; запрет self/duplicate/missing проверить при сохранении.
- Фото: новое/существующее локальное и старое URL-фото; выбор/удаление/отмена, offline, поздний ответ после выхода, чередование карточек. Недоступный предпросмотр не блокирует сохранение; обновление имени не должно повторно загружать то же фото.
- «Назад»: остаться/не сохранять/сохранить и продолжить, в том числе переход к связанному товару. Последние набранные символы должны участвовать в dirty-check. Во время сохранения через закрывшееся подтверждение нельзя редактировать/повторно сохранять/закрывать редактор. Ошибка записи остаётся в редакторе; неопределённый результат требует перезапуска.
- Удаление защищённого товара/товара, необходимого для возврата/составного товара, сохраняет прежние запреты. После v13 импорта проверить состав/модификаторы/фото/остатки; каталог синхронизируется только вручную, публикация остатков — после сохранённой оплаты, печать сама не повторяется.
- Это ожидаемые физические проверки, а не уже пройденная приёмка. WebView/private auth/configuration остаются до 109; скорость/память измерить комплексно в 110.

## 103 — нативная оплата (pending)

- В обеих темах, landscape/portrait, шрифт 150–200%: сверить позиции/скидки/лояльность/доставку/итог, длинный чек, прокрутку и доступность всех действий. Сверить cash/card controls и вложенные native cash/count/amount формы без второго окна поверх ввода.
- Наличные: точная/недостаточная/большая сумма, номиналы, ввод 2,50/сдача; rapid repeated pay даёт ровно один чек/списание. Проверить смену/остатки/тариф доставки до оплаты.
- Карта: открытие формы не создаёт чек; «Оплата прошла» — явное подтверждение после терминала. «Отмена» обычной карты сохраняет подтверждённое правило: появляется empty split, затем «Назад» возвращает обычную оплату.
- Раздельная оплата: 2–10 частей, суммы/копейки/методы, cash/change/card, paid readonly, split draft после полного restart. Нативная кнопка, аппаратный Back и жест Back (Android 13+) не закрывают partially-paid split; причина отказа видна. All-paid draft не финализируется сам после рестарта.
- Недоступная лояльность: отмена или продолжение без подарка; повторная проверка после смены клиента/заказа; подарок/доставка/скидки совпадают с v13 и нативным settlement.
- Отказ/неопределённость записи: чек/остаток/история/корзина не показывают неподтверждённый успех, no duplicate settlement; unknown result блокирует действия с указанием перезапуска. После recovery сверить ровно один результат.
- После успешного commit: оплаченный чек/«Готово»/ручная печать; автоматическая печать и публикация остатков идут только по существующим triggers, без replay при обрыве/фон/сеть/повторном входе. Отказ сети не отменяет локальную продажу.
- История/возвраты проверяются отдельно в 104. Это список ожидаемых физических проверок, не заявление о прохождении; WebView runtime остаётся до 109.

## 104 — история чеков и полный возврат (pending)

- Более 50 чеков: загрузка, страницы назад/далее, ошибка/повторить, пустая история, выбор чека после перехода страницы; номер/дата/сумма/возврат различимы.
- Список и детали прокручиваются независимо; выбор/открытие/закрытие модального чека сохраняет позицию списка. Проверить narrow/landscape, обе темы, увеличенный системный шрифт.
- Детали: товары, комментарии, модификаторы, скидки/подарки, клиент/доставка, cash/card/split, внесено/сдача; печать только по ручному действию, без автоматического повтора после обрыва.
- Полный возврат cash/card/split из текущей и старой смены: возврат записан в текущую смену; наличные только в размере cash части, карточная часть возвращается терминалом. Остатки восстанавливаются по сохранённому составу продажи; старый чек — прежний fallback.
- Закрытая смена, повторный возврат, недостаток наличных, отсутствующий складской товар, ошибка/неопределённый commit: ясное сообщение, без повторного изменения остатков/кассы; неизвестный результат требует восстановления после перезапуска.
- Двойное нажатие/Back во время сохранения, отмена подтверждения, успешный результат → открыть чек/Готово; после перезапуска/импорта v13 статус и учёт сохранены. Возврат не добавляет публикацию доступности в обход payment-only gate.
