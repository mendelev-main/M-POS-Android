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
