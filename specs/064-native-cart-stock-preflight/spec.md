# 064 — Room stock preflight before cart addition

## Scope and authority

MPosStockPreflightRepository reads the authoritative Room catalogue in a read-only
transaction and applies MPosRecipeConsumptionEngine to the proposed cart. Native
stockPreflightRead returns authoritative allowed=true/false. Business refusal is an
ok response with a local reason; malformed protocol/unavailable storage is a failed
read. Insufficient stock identifies the ingredient by name. Other messages are fixed
local recipe explanations; raw parser/SQL/source data is never exposed.

The existing native-price addition queue now awaits native stock approval before
mutating the cart or saving its session. Requests contain only product IDs, quantities
and modifier IDs/quantities; no archive, customer, pricing or catalogue payload is sent.
All added-position variants share the boundary: plain, selected modifiers, manual price,
and normal quantity merging. FIFO additions remain ordered and payment stays blocked
while either price or stock approval is pending.

This check does not reserve or deduct stock and does not emit availability or any
network operation. Settlement still recalculates/deducts atomically (063). No JSON
shape/key/backup v13 changes. Quantity-stepper increases, payment-screen preflight,
product availability display and stock checks outside configured addition remain JS;
this stage does not claim every canFulfillCart caller is migrated.

## Stale responses and failure behavior

Before a stock request, the adapter captures the current cart and the stock-relevant
local catalogue fields. A late response after an in-place quantity/cart edit, recipe
or stock change, product price/name change, session replacement, modal/manual-context
cancellation or critical operation does not add a line. A fresh tap is required; no
silent recheck/repricing or mutation based on a stale existing-row reference.

A native read failure leaves cart/session and manual context intact for retry, and
never silently runs JS as fallback or marks local storage broken. A business refusal
retains the reviewed manual-context clearing behavior after a valid manual-price
submission. Existing line matching, frozen price, stock units/tolerance, whole recipe
validation and noStockTracking semantics remain unchanged.

Preflight remains advisory: a later stock change can invalidate approval; the final
payment transaction is the reservation/deduction boundary. The repository requires
an initialized authoritative catalogue (normal loadAll already establishes this).

## Rollback and verification

MPosNativeStockPreflightEnabled=false restores the stage-062 JS stock preflight inside
native-priced addition; price/settlement gates remain enabled. Disabling the broader
configured-price adapter restores the complete original synchronous add handler.

The repository runs all 26 shared original recipe/stock fixtures against Room and
asserts no catalogue/cart/receipt writes. Additional tests verify ingredient names,
fresh reads after stock changes, malformed protocol, FIFO correlation and worker
survival. JS tests cover default native approval/refusal, FIFO quantity proposals,
manual/modifier inputs, payment waiting, quantity/recipe/stock/session/modal races,
read-failure retry, minimal frozen payloads and explicit stock rollback. Existing
native recipe settlement, modern return and original-source tests remain required.

Physical acceptance pending: rapid taps near stock limit, modifiers/recipes sharing
ingredients, manual price, quantity edits/cancellation while adding, shortage messages,
stock updates, offline use, payment after addition and backup v13 import. Per user
instruction, no local APK build; GitHub builds after commits.

Verified: 195 JS and 208 JVM tests passed, no failures/errors/skips.
Lint: 0 errors, 16 existing warnings. Repository exercised all 26 shared
recipe cases. No local APK assembly; physical acceptance remains pending.
