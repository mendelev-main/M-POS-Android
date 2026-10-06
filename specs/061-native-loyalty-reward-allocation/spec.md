# 061 — Native loyalty gift allocation at settlement

## Scope and authority

MPosLoyaltyRewardEngine calculates the selected reward allocation, rounded total
and per-program discounts, and the receipt program snapshot. It runs before
pricing validation inside the existing atomic Room payment transaction. Default
Android payment commands include a transient version-1 loyalty envelope containing
only the current program snapshot; redemptions/items come from the receipt.
Native results must match the displayed receipt; matching native fields become
the persisted receipt. Incorrect allocation, discount, program snapshot or request
count rejects the transaction before writes and external effects. The command hash
includes the envelope, preserving exact-retry idempotency.

No Room/backup v13 schema changes. No new network calls. Program inputs are not a
new authoritative balance store: the existing JS online eligibility guard remains.
Customer search, customer identity, fetching balances, offline confirmation without
gifts, post-commit sale publication, reversal/retry and cart display remain unchanged.
Configured prices (including modifiers/manual entries) and recipe expansion remain
follow-up domains. This is settlement authority, not a completed loyalty UI migration.

## Preserved rules

- Traverse programs in their existing order; one gift per positively selected program.
- Traverse whole units only (floor positive quantity); fractional remainder is ineligible.
- Choose the cheapest allowed available unit. Equal prices use original cart order.
- Used units cannot be selected by another program. IDs use reviewed String coercion.
- Gift price uses the configured unit price before product discounts, clamped at zero.
- Round the total and each program discount separately, then preserve receipt-snapshot
  rounding and name fallback. Zero-price gifts still allocate a unit but omit the
  receipt program discount row, matching the source.
- Match the original finalization check: truncated nonnegative requested count must
  equal allocated count. Unknown positive requests and requests for two gifts fail.
- Duplicate program IDs preserve source overwrite/accumulation behavior; no new ID policy.

The engine retains line capacity instead of expanding quantities into one object per
unit. Selection is equivalent because each program takes at most one unit, prices
are constant within a line, and ties retain line order. Work/memory do not scale with
quantity. Nonfinite quantities/prices cannot form a valid local financial command.

## Business observations for possible future refactoring

A broad program can consume the only unit eligible for a later narrow program,
even if a different arrangement could satisfy both. Existing program order is
preserved; this stage does not optimize gift combinations or alter prices.
Existing gift-before-product-discount and possible delivery absorption (060) remains.
Duplicate imported program IDs can accumulate a total while overwriting the per-ID
allocation. This legacy inconsistency is covered and preserved, not silently corrected.

## Rollback

MPosNativeLoyaltyRewardsEnabled=false omits the new envelope and retains stage-060
settlement with a supplied loyalty scalar. It is independent of the pricing toggle.
Missing envelope is accepted for compatibility; a malformed present envelope fails.

## Verification and pending device cases

Shared fixtures execute the unchanged reviewed JS allocation/snapshot and Kotlin:
cheapest/ties, program order, whole units, request coercion, unknown/multiple requests,
numeric/string IDs, duplicate IDs, zero prices, independent rounding, manual/modifier
prices and product discounts. Calculation leaves input data untouched.

Room tests cover no-gift settlement, gift+product discount+delivery including zero
final amount, receipt compatibility, stock/drawer/session atomicity, exact replay,
and rollback after incorrect allocation/discount/snapshot/configuration or malformed
version. Adapter tests cover frozen program input, default enablement, independent
rollback, and all existing payment acknowledgement/external-effect behavior.

On a physical tablet still check: two overlapping programs, equal-priced products,
fractional quantity, gift+product discount+delivery, offline continue without gift,
server rejection of unavailable gift, payment/return/reversal, restart and backup v13.

Verified: 22 shared golden cases; 177 JS and 194 JVM tests passed, no
failures/errors/skips. Debug and minified release builds succeeded.
Lint: 0 errors, 16 existing warnings. Physical device cases remain pending.
