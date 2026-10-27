# 04a.5 — черновик приёмки и предварительный расчёт

Статус: ci-verified (RI01–RI05, только draft/preview). Часть M11/I01/I04, продолжает [закупки](PURCHASE-ORDER-CHANGES-04A.md). Приход ещё не проводится.

## Источник и границы

Pinned1682c883: receiving-drafts.js (receivingDraftForOrder/open/save), receiving.js (invoiceReceivingItems/receivingStockUpdates/discrepancy), receiving-ui.js (input/fields/preview). Android inventory adapter относится к инвентаризации, override receiving в доступном pinned checkout не найден. Срез не обновлялся.

В scope: список pending заказов, переход из карточки закупки, автоматически сохраняемое начало приёмки, resume/save/clear, номер/дата накладной, actual quantity/unit/pack/content и сумма строки BYN, exact conversion и indicative stock/cost preview. Поддерживается ввод **общей суммы строки** — default total mode Web. Режим ввода цены за единицу, независимая приёмка, история проведённых приходов, дополнительные расходы, сторно и actual commit — следующие части. Их кнопки обозначены Soon, ложной приёмки/записи stock нет.

Сохранение черновика не меняет status/revision закупки, current purchase draft, product defaults/revisions/cost/price, balances/revisions/movements, смену и кассу. Preview — чистый read/calculation, не обещание проведённого прихода. После него всегда потребуется повторная проверка в будущей transaction commit.

## Черновик и факты

На заказ один активный ReceivingDraft с UUID; разные заказы независимы. Registry `receiving-drafts` payload1 в existing Room schema8/workspace_documents. Per-order ReceivingRecord.revision/CAS; global document revision монотонна, Clear сохраняет tombstone revision. Повтор старого Save после Clear не возвращает черновик. Existing order codecs payload1/2 читаются как раньше, receiving codec не меняет их формат.

Черновик хранит полный order snapshot (order/request revisions/history), invoiceNumber (до120символов), optional invoiceDate YYYY-MM-DD и ordered list actual lines. Каждая строка содержит productId/name/baseUnit/productRevision, stockRevision/stock/cost/noStockTracking baseline и raw quantity/invoiceUnit/packSize/contentUnit/total. Точность и деньги — DATA; FK refs проверяются в repository. Clear не удаляет заказ. Registry постоянный, входит в будущий backup, не cache.

Как Web: при known expected actual quantity предварительно заполнено requested quantity, но сумма пустая; unknown expected начинается **пустым** quantity, не0/1. Оператор подтверждает реальные факты. Не поставлено — quantity0, UI устанавливает сумму0 и запрещает её правку. Пустой actual не равен0; неполный draft можно сохранить. Положительный actual с пустой суммой не даёт preview. Нулевой total при положительном actual допустим как явно заданная бесплатная строка. Строки order-bound receiving нельзя просто удалять — для недопоставки0. Unknown pack можно сохранить, но positive preview требует её явного содержимого.

Неверные/отрицательные числа, несовместимые units, размер упаковки0, неверная дата и сумма с дробью меньше копейки отвергаются без потери ввода. Native не сохраняет произвольный malformed text как валидную накладную. BYN total input max2fraction +Long minor range; суммы нескольких строк складываются точно с overflow refusal. Quantity12fraction/18integer, точный пересчёт без округления до0.001. Physical unit switch сохраняет объём (1л→1000мл), total остаётся тем же; switch physical↔pack требует явной проверки упаковки, fallback1 не вводится.

## Изменения основания и конкуренция

Save требует точный актуальный pending order snapshot/revision. Product должен оставаться simple с той же baseUnit. Смена stock/product revision сама по себе не запрещает сохранить факты draft: они остаются со старым baseline. **Preview** требует полностью актуальный baseline, включая stock revision/quantity, product revision/name/cost/tracking mode. После продажи/ручной правки или изменения order нужен explicit rebase; результат прошлого preview очищается при редактировании/перечитывании.

«Обновить основание» сверяет receiving record CAS, подставляет текущий order/products/stock, сохраняет raw invoice fields и actual quantities/units/pack/totals. Старые invoice rows остаются даже при удалении позиции из последней заявки; новые order products добавляются с **пустыми** quantity/total (native осознанное отличие от Web prefill), чтобы не выдавать новое ожидание за уже введённый факт. Все строки обязаны относиться к current/historical order products. Дубли product/draft/order IDs отклоняются. Другая receiving revision не усваивается незаметно: нужен явный discard/reload.

Cancel закупки не уничтожает native draft: факты остаются read-only/resumable для просмотра и Clear. Save/preview/rebase по cancelled order запрещены. В Web удаление очищало draft; native сохраняет факты во избежание скрытой потери ввода. Исторические order product/supplier guards уже защищают все receiving refs, включая removed request lines. Standalone/add arbitrary product в этой части отсутствует.

## Предварительный расчёт

Фактическое base quantity считается точно из invoice quantity×packSize×conversion. Для expected known: shortage=max(expected−actual,0), overage=max(actual−expected,0). Для historical removed rows expected0. Unknown expected остаётся null, сравнение показывается как неизвестное. Положительный overage явно помечается: перед будущим проведением потребуется отдельное подтверждение. Весь actual0 блокирует preview, но не Save draft.

Tracked: previewStock=oldStock+actual; для cost старое quantity=max(0,oldStock), previewCost=(oldQty×oldCost+lineTotal)/(oldQty+actual). NoStockTracking: stock прежний, cost=lineTotal/actual. Zero actual сохраняет старую cost. Это Web поведение, включая negative baseline. Пример владельца10л×2BYN +10л за40BYN →20л и3BYN/л; receive3/10 показывает shortage7, но заказ ещё pending.

Preview cost — BigDecimal scale12 HALF_EVEN как существующая precision cost; это **предварительное отображение**, не утверждение реализации commit valuation. Неограниченные JS Double/toFixed/stock rounding не переносятся. Перед04a.6 закрепить окончательные правила cost precision/overflow, price-input rounding (если включать), подтверждения overage и повторного чтения актуальных balances. Подтверждённые D03: weighted cost, одно получение3/10 закрывает заказ с shortage7 без остатка ожидания,12/10 explicit confirmation. Ничего из этого пока не выполняется записью склада.

## Атомарность и интерфейс

InstallationGate→Room transaction→current local owner→fingerprinted outcome replay→per-order CAS/order refs→registry+result+audit. Любая из3write failures/SQLite abort откатывает все записи. Command lists фиксируются перед gate. Полные employee/admin sessions05 ещё не реализованы, owner проверяется в repository. Lost reply блокирует изменения/закрытие, retry использует ту же команду. Known commit/read-fail остаётся известным сохранением, повторное чтение не пишет факты заново.

Общие Mpos theme/header/Close/Dialog/Field/Select/buttons. Вкладка «Приёмка» подключена к pending/resume list; кнопка карточки закупки ведёт в нужный receiving. При dirty независимом purchase draft переход из карточки заблокирован до Save/Discard. Нативный receiving form имеет scroll, фиксированные actions, dirty close/reload confirmation. Activity recreation сохраняет текущий ввод через ViewModel; process kill гарантированно восстанавливает только saved draft. Preview popup показывает total/stock/cost/discrepancies и явно помечает отсутствие проведения. История проведённых приходов и standalone Soon.

## Acceptance

| ID | Проверки |
| --- | --- |
| RI01 | Auto-save open/resume/invoice facts/incomplete draft/unknown pack/zero and unit conversion |
| RI02 | Exact preview/weighted/nontracking/negative/shortage/overage, no product/stock/order posting |
| RI03 | Owner/gate/CAS/concurrency/fingerprint/each write failure/SQL abort/lost reply |
| RI04 | Explicit rebase preserves facts/removed rows/blank new rows; stock/order conflicts and cancelled clear |
| RI05 | UI save/dirty discard/recreate/preview, reopen and actual APK replacement retain data/results |

Tests: ReceivingRulesTest5 JVM; ReceivingRepositoryTest4, ReceivingViewModelTest3, ReceivingUiTest1, ReceivingUpgradeTest1 API36. Existing financial/supplier/purchase suites retained. No destructive test seed on user's local database; hosted ephemeral emulator only. CI evidence appended after execution. Physical tablet/dark/150%/forced process-death windows pending.

## CI-приёмка

[FullCI40502219375](https://github.com/mendelev-main/M-POS-Android/actions/runs/40502219375)/a8d8c61 SUCCESS first attempt:100JVM/134API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/13UIseed/8upgrade fixtures and actual debugAPK1→2. RECEIVING DRAFT UPDATE PASSED: invoice facts, baseline, exact preview and old save replay preserved without stock/order posting. Existing sale/split/refund/catalog/workspace/parked/home/shift/supplier/purchase assertions retained and passed. Physical tablet/dark/150%/forced cold-process receiving UI pending; whole04 remains in progress. Next04a.6 receiving commit. Main/release/signing/SQLschema unchanged.

Hosted HTML134/0/0 and downloaded JVM XML100/0/0/0 inspected. Screenshots1600×1125 reviewed: invoice fields, quantity/pack/total/footer and preview20л/3BYN readable without overlap/keyboard/foreign overlay. Saved to outputs/M-POS-receiving-draft.png and M-POS-receiving-preview.png. No tests/assertions removed, no retry required.

Актуализация после04a.6: пользователь подтвердил для новых приходов **2decimal HALF_UP**, включая preview; прежний scale12 HALF_EVEN выше относится к историческому04a.5/legacy policy1. Invoice totals и точность количества не меняются. Версии сохранённых приходов и совместимость: [RECEIVING-POST-04A](RECEIVING-POST-04A.md).
