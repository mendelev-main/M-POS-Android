# 060 — Kotlin pricing at settlement

## Scope

MPosPricingEngine recalculates configured item prices × quantities, first matching
product discount, cart subtotal, delivery contribution and total after the already
allocated loyalty discount. It also calculates the rounded receipt gross and
product-discount aggregates. Original floating-point operation order is retained;
cart totals are not prematurely rounded to cents. No new financial policy.

Native paymentCommit receives transient version-1 pricing input (discount
configuration snapshot and loyalty discount). Before writes inside its existing
Room transaction, it compares the receipt financial fields with native calculation
and assigns the matching calculated fields. A mismatch fails the transaction;
cart, stock, receipt and post-payment effects remain untouched. Retry hashes include
this input; the existing idempotency marker is retained. Pricing inputs are not
saved into receipt JSON or backups. No storage/schema changes.

## Boundaries and rollback

Displayed cart arithmetic remains the reviewed JS implementation; native arithmetic
now gates settlement. Item.price is the already configured/manual price including
modifier deltas. Discount configuration comes from the session snapshot, not an
independent authoritative Room discount store. Recipe expansion and gift allocation,
loyalty transport, manual-price entry and cart UI remain follow-up domains.
MPosNativePricingEnabled=false omits the pricing envelope and retains stage-048
settlement compatibility; absent input remains accepted for that explicit rollback.
Malformed present input must fail, never silently bypass pricing validation.

## Preserved rules and business observations

- Fixed discount is per unit, capped by base; percent is clamped to 0–100.
- Lookup uses strict identifier types, first match; missing definition gives zero.
- Invalid discount numeric text becomes zero, matching Number(value)||0.
- Delivery applies only to the exact Доставка order type.
- Gift discount subtracts from subtotal plus delivery, clamped at zero. The reviewed
  allocation uses undiscounted unit prices, so a gift can absorb delivery after a
  product discount; preserved pending a separate business decision.
- Empty discount names still apply in the live cart, but historical receipt display
  requires a nonempty discountName. This legacy discrepancy is documented for
  possible future refactoring; no behavior changed in this stage.
- Negative legacy item prices retain original arithmetic; no new catalogue rules.

## Verification

Shared golden fixtures execute original JS functions and the Kotlin engine: scalar
coercion, fractions, caps, missing/duplicate/type-distinct IDs, manual/modifier
prices, delivery, gift and no premature rounding. Room tests cover native settlement,
exact replay, discounted delivery with an allocated gift, and mismatch rollback.
Adapter tests verify default envelope and explicit rollback; existing payment
coverage retains ack-before-external-effects and uncertain-commit retries.
Physical tablet acceptance remains pending: cash/card/split, percent/fixed discount,
manual/modifier prices, gifts plus delivery, restart and backup v13 round trip.

Validated: 175 JS tests, 189 JVM tests; no failures/skips. Debug and minified
release builds successful. Lint: 0 errors, 16 existing warnings. Shared
pricing fixture includes 19 cases. Physical acceptance remains pending.
