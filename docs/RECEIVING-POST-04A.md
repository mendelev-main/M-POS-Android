# 04a.6 — проведение приёмки

Статус: in progress, acceptance RP01–RP05. Source pinned1682c883 receiving.js applyReceivingDocument, source snapshot unchanged.

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
