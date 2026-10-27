# 04a.6 — проведение приёмки

Статус: ci-verified RP01–RP05 (order-bound posting/history). Source pinned1682c883 receiving.js applyReceivingDocument, source snapshot unchanged.

В scope только order-bound saved receiving: подтвердить расчёт → атомарный приход → read-only история и статус received. Standalone, per-unit-price mode, charges/storno, inventory вне этой части. Средневзвешенная себестоимость,3/10 закрывает заказ с недопоставкой7,12/10 требует отдельного подтверждения (ответы владельца). Policy v1: final cost scale12 HALF_EVEN, positive-value underflow refused; exact line total minor units retained. Applied existing warehouse precision as implementation choice; optional owner preference question remains open.

## Транзакция и конкуренция

InstallationGate → Room transaction → local owner → immutable fingerprinted command replay → exact saved draft revision/payload → pending order snapshot → live economic baseline validation → recompute. Native rejects changed preview rather than applying unseen stock/cost. Two commands for same order cannot both receive. No order edit/cancel after received. Lost reply reuses same command and blocks dismissal/navigation; known commit/read failure cannot turn into new posting.

Immutable `receivings` payload1 in existing Room8 documents stores original pending order/request/history, raw invoice facts, complete economic baseline, actor/time/draft revision/command/overage approval. Deterministic calculation from immutable facts uses versioned policy; old records never recomputed from live catalog. Order payload3 reads1/2 and stores received link/actor/time; receiving drafts payload1 unchanged, clear uses tombstone revision.

Atomic writes: positive tracked lines → product cost+revision, exact positive stock movement+balance revision; positive untracked lines → product cost+revision only, no stock/balance/movement; zero rows no catalog/stock writes. Then receiving record, order received, draft tombstone, result and audit. Any write failure rolls everything back. Sale price/pack defaults/current purchase draft/cash/shift remain unchanged. Cost changes invalidate dependent checkout via product revisions. Composite products never receive direct stock.

## Acceptance

- RP01: exact qty/weighted cost/negative baseline/noTracking/zero/shortage/overage; cost bounds.
- RP02: per-write rollback, SQL abort, repeated/competing command, owner/installation gate.
- RP03: stale order/stock/product/draft rejected; edits/cancel refused after receipt; facts/history retained.
- RP04: shared confirm/overage dialog, UI unknown recovery, history/recreate.
- RP05: reopen, old payload1/2 and actual APK replacement preserve posting, movements, revisions and original command replay.

Checks and unresolved physical device cases recorded after execution.

## Policy v1 и формат

Себестоимость вычисляется BigDecimal с точными промежуточными произведениями, округляется только итог до scale12 HALF_EVEN (существующая warehouse precision).3шт за10BYN при нулевом остатке →3.333333333333BYN/шт; invoice total1000minor остаётся точным. Positive economic value, округлившийся в0, отказывается; явно бесплатная строка при отсутствии старой положительной стоимости допустима. Итог cost18integer/12fraction, stock exact18integer/12fraction; overflow и underflow — отказ до записи. Отрицательный oldStock увеличивается на actual, valuation использует max(oldStock,0). NoStockTracking cost только по поступлению, balance/revision/ledger неизменны. Zero actual ничего не меняет по товару; all-zero нельзя провести.

`receivings` payload1 является policy-v1 snapshot: stored original raw facts +original pending order +economic baseline +actor/at/draft revision/command/overage approval. Snapshot также хранит рассчитанные changes/totalMinor; история показывает именно сохранённые изменения. Decoder сверяет их с policy-v1 из неизменных фактов, не из текущих карточек. Изменять алгоритм policy-v1 задним числом запрещено; другая policy потребует нового versioned decoder. Все historical refs покрыты current/historical purchase guards. No SQL migration; payload3 order stores receivingId/receivedAt/receivedBy, reads1/2 без автоматической перезаписи при чтении.

UI требует Save перед проведением, показывает расчёт и отдельный confirm; при overage confirm заблокирован до явного подтверждения превышения. При отказе ввод и saved draft остаются. После известного commit история остаётся доступной даже при ошибке перечитывания; повторное нажатие не создаёт второй приход. Физический планшет, dark/150% receiving и принудительный process kill — отдельная проверка. Saved posting/replay проверяется реальной заменой APK в CI.

## CI-приёмка

[FullCI40512178440](https://github.com/mendelev-main/M-POS-Android/actions/runs/40512178440)/2d8a183 SUCCESS first attempt:103JVM/145API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/14UIseed/9upgrade fixtures и actual debugAPK1→2. RECEIVING POST UPDATE PASSED: immutable invoice/posted changes, exact stock13/cost2.461538461538, received order/actor/link, draft tombstone and original post replay retained without another stock movement. All existing financial/catalog/workspace/parked/home/shift/supplier/purchase assertions retained and passed. Physical tablet/dark/150%/forced cold-process receiving UI pending. Whole04 remains in progress; main/stable/signing/SQLschema unchanged.

Downloaded103JVM XML and145instrumentation HTML inspected: zero failures/errors/skips. Screenshots1600×1125 confirmation/posted history inspected: shared Close/actions, overage approval12/10, stock10→22 and historical cost readable; no overlap/keyboard/foreign overlay. Copied outputs/M-POS-receiving-post-confirm.png and M-POS-receiving-posted.png. No reduced assertions, retry or physical database seed. Next bounded04a.7: standalone receiving and invoice entry improvements, before charges/storno/inventory; scope and rounding for per-unit price must be specified before that mode.
