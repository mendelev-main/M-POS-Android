# 063 — Native recipe consumption at settlement

## Authority and scope

MPosRecipeConsumptionEngine expands receipt lines and selected modifiers against the
transaction's authoritative Room catalogue. Default payment commands include a
transient recipeConsumption version-1 envelope. Before writing stock, native code
recalculates consumption and checks the supplied historical snapshot's quantities,
identifier types and order. The recalculated result drives existing stock deduction.
The verified original snapshot retains any extensions in the saved receipt.

No schema/key/backup v13 changes. No network calls or availability triggers. Original
JS productIngredients, checkedStockConsumption, canFulfillCart and availableStock
remain the cart preview/preflight reference. Existing ingredient stock deduction,
receipt persistence, exact replay and external effects after acknowledgement remain
on their accepted native transaction path. Cost estimation and legacy return fallback
remain JS; modern refunds continue restoring the saved historical consumption.

## Preserved rules

- Resolve the first strictly matching product ID; numeric and string IDs differ.
- Expand simple and nested composite products depth-first in component order.
- Add shared ingredients across branches, cart lines and modifier lines, preserving
  first encounter order and floating-point operation order. Repeated branches are
  allowed; only revisiting a product in the current path is a cycle.
- Main quantity must be positive/finite; modifier consumption is sale qty × modifier
  qty, followed by its own recipe expansion. No price-delta multiplier is introduced.
- Validate recipes before filtering noStockTracking simple products. Composite flags
  do not suppress their ingredients. Missing products/modifiers, empty recipes, cycles,
  invalid quantities and overflow fail without writes.
- Preserve stock units and unrounded quantities. Stock comparison retains Number(stock)||0
  semantics and EPSILON × 8 × max(abs(stock), quantity) tolerance. Final stock rounding
  remains the existing stage-048 three-decimal policy.

Explicit stack traversal avoids process-stack overflow on deeply nested recipes.
It does not impose a new arbitrary depth limit or use a global visited set that
would incorrectly discard shared branches.

## Rollback and verification

MPosNativeRecipeConsumptionEnabled=false omits only this envelope and restores the
previous supplied-consumption path. Other native financial gates stay enabled.
Malformed present envelope fails; absent envelope remains the explicit rollback path.

Shared fixtures execute original JS expansion/preflight and Kotlin with nested/shared
recipes, modifier quantities, untracked stock, coercion, tolerance, insufficient
stock, strict/duplicate IDs, missing references, empty recipes, cycles and overflow.
Native tests also cover a 2,000-level recipe without recursive stack usage.

Room integration covers composite+modifier settlement, wrong consumption and cycle
rollback, exact replay after a recipe change, unchanged historical receipt snapshot,
and stock/session/receipt integrity. Existing modern-return tests cover historical
consumption restoration. JS adapter tests verify default and independent rollback;
all prior financial and source-integrity tests remain required.

Physical acceptance pending: nested recipes sharing ingredients, multiple modifiers,
fractional quantities, exact remaining stock/shortage, noStockTracking products,
changing recipes after a sale then returning it, interrupted payment, parked/web carts
and backup v13. Local APK builds are omitted per user instruction; GitHub automation
builds after the commit. Automated JVM/JS tests and lint remain required.

Verified: 26 shared reference cases; 189 JS tests and 204 JVM tests passed
with no failures/errors/skips. Lint: 0 errors, 16 existing warnings.
No local APK assembly was run. Physical acceptance remains pending.
