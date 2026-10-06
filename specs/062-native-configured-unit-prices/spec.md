# 062 — Kotlin formation of configured unit prices

## Scope

MPosConfiguredPriceEngine forms the configured unit price from catalogue/base price,
ordered modifier deltas and an optional raw manual-price entry. It runs through the
existing bounded native FIFO as a read-only configuredPriceRead command, without
initializing/writing Room documents or making network calls.

The Android adapter now uses the native response for actual cart addition. Base and
unit price come from Kotlin. Existing JSON line shape, signature comparison, stock
availability check, quantity increment, animation, session save and render remain
compatible with reviewed cart-composition.js. Original source files are unchanged;
the new adapter is included in source sync and source-integrity checks.

Manual entry passes raw text to Kotlin; comma replacement, positive/finite check and
cent rounding occur there. JS retains immediate invalid-input feedback. The original
modifier selection and manual entry forms and their price previews remain JS.

Native payment settlement also validates the frozen base price plus modifier deltas
before loyalty/pricing checks. It never reprices existing lines from today's catalogue.
Imported/pre-migration lines without basePrice retain their historical price. No new
receipt fields, storage migrations or changes to backup v13.

## Preserved arithmetic and business behavior

- Catalogue path uses Number(price)||0. Explicit manual base uses Number(base) and
  is not rounded again; manual input is rounded once after its positive guard.
- Modifier extra is the ordered sum of Number(delta||0). Modifier qty is not a price
  multiplier (it still belongs to stock consumption). No additional unit-price rounding.
- Negative modifier deltas and negative final unit prices retain source arithmetic;
  downstream item totals keep their existing clamp.
- A small positive manual input, e.g. 0.001, passes the source guard and rounds to
  zero. Preserved for possible later business refactoring, not silently prohibited.
- Normal additions merge by strict product ID and unchanged JS modifier signature,
  provided the existing row has no comment/discount. Existing row price stays frozen.
- Manual additions always create a separate row. However, a later normal addition can
  merge into a manual row because the original candidate filter does not exclude it.
  This surprising legacy rule is covered and preserved for future discussion.

## Async boundary and failure behavior

Repeated ordinary taps queue in arrival order; the next calculation begins after the
previous cart mutation. Payment entry/finalization waits while additions are pending.
Late replies after a cart/session replacement, modal cancellation/replacement, manual
context cancellation, or critical operation do not add a line. Catalogue price/name
changes during calculation require a fresh tap. Stock is checked against the current
cart after calculation, before any mutation. Native calculation failure leaves the
cart/session untouched and does not mark storage broken or send availability.
Nonfinite arithmetic cannot produce a valid persisted financial command and is
rejected before inserting a corrupt priced line. No implicit JS fallback on failure.

## Rollback and verification

MPosNativeConfiguredPricesEnabled=false restores the original synchronous addition
and manual-price form handler and omits the configured-price settlement envelope.
Pricing/loyalty gates remain independently enabled. A malformed present settlement
envelope must fail rather than use the compatibility path.

Shared fixtures execute original addConfiguredCartItem/confirmManualPrice and Kotlin
formation. Coverage includes cents/floating-point ties, comma input, tiny positive
manual values, modifier quantity independence, negative/fractional deltas, coercion,
null/missing inputs and invalid manual entry. JS differential tests compare complete
cart lines/merging with the reference, queued taps, payment guard, cancellation,
stock failure, failed native read, recovery, stale products and explicit rollback.

Protocol tests cover frozen/correlated read-only requests and native FIFO surviving
invalid input. Room tests cover canonical price settlement, compatibility for legacy
lines, and mismatch rollback without changing stock/receipts/session. Existing tests
cover original-source hashes, backup boundaries and all preceding financial stages.

Physical tablet acceptance remains pending: repeated taps, manual comma/rounding,
modifier min/max selection, normal/manual row merging, stock shortage, modal cancel,
payment while adding, offline use, parked/resumed/web carts and backup v13 round trip.

Settlement validation preserves imported numeric-string fields and extensions
when their arithmetic matches; it does not normalize receipt line JSON types.

Verified: 20 shared reference cases; 187 JS and 200 JVM tests passed
without failures/skips. Both debug and minified release builds succeeded.
Lint: 0 errors, 16 existing warnings. Physical cases remain pending.
