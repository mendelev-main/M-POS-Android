# 065 — Native quantity decision and stock preflight

## Scope

MPosCartQuantityRepository computes the reviewed qty + delta operation and removal
condition, then checks the proposed whole cart using Room stock preflight. Read-only
cartQuantityRead returns quantity, remove, allowed and a local refusal reason. The
adapter applies only an accepted result and saves/renders through the existing session
path. It sends only product/modifier IDs and quantities, target/matching indices and
delta; no prices, catalogue, customer or receipt archive. No reservation/stock write.

The quantity handler shares the existing FIFO with price/stock additions. Payment
waits for the entire pending cart-operation queue. A failed read/refusal or a reply
made stale by external cart edit/removal, session replacement, catalogue stock/recipe
change or critical operation cannot mutate/resurrect a line. A next tap can retry.

Internal stepper deletion keeps order continuity across array replacement via weak
successor links. Thus a later queued tile addition survives the accepted deletion,
while queued edits targeting the removed row are cancelled. External/session array
replacement is not linked and invalidates stale operations.

## Preserved business rules

- Positive resulting qty, including decreases, checks the whole proposed cart.
- qty <= 0 deletes the matching row(s) without a stock check.
- Existing line price, modifiers and metadata are unchanged by qty updates.
- Removing the last row through the stepper saves an empty cart without resetting
  order label/type/customer/delivery/loyalty. This differs from swipe removal and is
  preserved for possible future business refactoring.
- A positive decrease can be refused because another cart item is short/invalid;
  preserved, rather than silently permitting partial cleanup.
- Legacy numeric strings retain JS addition semantics (e.g. '1' + 1 becomes '11'),
  not silent normalization of imported data. Malformed/nonfinite qty cannot be approved.
- Duplicate legacy cart keys preserve the original full-cart proposal (all matching
  rows receive proposed qty for checking) but only first row changes after approval;
  zero removal filters all matching keys. Covered, not silently deduplicated.

## Boundaries and rollback

Cart UI/selection, swipe removal, explicit new-order reset, stock availability display,
payment entry preflight and receipt metadata remain on their existing paths. Final
settlement remains authoritative and atomic. Existing JSON shapes/keys and backup v13
are unchanged; no availability/network trigger is introduced.

MPosNativeCartQuantityEnabled=false restores the original synchronous changeQty. This
is independent of native price/stock-addition/settlement flags; no implicit fallback on
native errors. No local APK assembly per user instruction.

## Verification and pending device cases

Room tests cover increase/refusal, removal, positive decrease with another short row,
fresh stock/no writes, scalar/string and duplicate-key semantics, malformed protocol,
and correlated bridge replies. JS differential tests compare full cart with original
stepper, FIFO +/− and mixed additions, payment waiting, refusal/failure/retry, external
removal/qty/stock races, last-row context retention, deletion-followed-by-addition,
duplicate/string cases and explicit rollback. All earlier financial tests remain.

Physical acceptance pending: rapid +/− near stock limit, fractional qty, modifiers,
last-row deletion versus swipe, mixed tile taps and stepper operations, external
stock changes, cancellation/restart, parked/web orders and backup v13 import.

Verified: 203 JS and 213 JVM tests passed without failures/errors/skips.
Lint: 0 errors, 16 existing warnings. No local APK assembly.
Physical acceptance remains pending.
