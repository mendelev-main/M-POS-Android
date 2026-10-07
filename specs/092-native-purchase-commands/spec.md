# 092 — Purchase calculations, statuses and atomic commands

## Scope

Kotlin reconstructs makePurchaseLine: positive finite quantity/package size, base/content units, bottle/box/pack with optional size, compatible unit conversion and reviewed 15-significant-digit rounding. Unknown package size remains expectedQty=null and qty=0 for later receiving clarification. Order construction validates authoritative catalogue/supplier bindings, builds pending order and purchase-unit defaults and verifies the exact reviewed candidate. No stock/cost deduction or receiving occurs when ordering.

Room now owns existing purchaseOrders and receivings full JSON through the supply document store, with one-time legacy import and array/null/absence/extensions compatibility. Receivings ownership is required here for atomic administrative deletion history; actual receiving/valuation business remains 093. No schema/key/backup v13 change. Native critical journal must be empty before a command. Create writes products/order together; delete writes order/history together in one Room transaction. Expected lists reject stale overwrites. UI applies candidates/clears cart/opens order only after native success. Reviewed editor/line preview/history labels/text/PDF sharing remain presentation handlers, including synchronous quantity preview; native rules are authoritative at commit.

## Deletion and compatibility

Native deletion checks persisted open shift and administrator, refuses received/deletedAt records, and reconstructs tombstone actor/time/status fields. It removes receivingDraft/receivingDraftV2 and clears receivingIncomplete, retains unknown order extensions and adds the reviewed zero-cost adminDeleted audit event only when one does not already exist. It never deletes paid receipts or adjusts product stock. Existing history/supplierName snapshots survive supplier deletion.

Receivings indexes are built by the existing stock-event projector extracted into MPosStockEventRepository for reuse. Missing/non-finite projection numbers become zero so optional/malformed historical fields cannot violate SQLite NOT NULL constraints during ownership import; full JSON remains unchanged and business calculations still read full records. Null/absence clears indexes. Generic obsolete shadow put/remove cannot overwrite owned documents. Critical reads propagate rather than returning an empty history.

MPosNativePurchaseCommandsEnabled=false restores reviewed critical-storage command construction, retaining native document ownership and the existing compatible recovery journal. Uncertain command result blocks further critical work until restart; no stale-cache fallback. Commands are offline and add no catalogue/availability publication. Supply/cart/current UI state is captured before initialization awaits.

## Business examples retained

3 bottles × 500 ml → 1.5 l expected; 2 boxes without size → expectedQty=null until receiving. Creating either order leaves stock untouched. A received order cannot be administratively removed; a pending order is retained as deleted plus a zero-cost history record. Empty supplier productIds remains “no restriction” in finalize, even though the new-order screen lists no products: removing all bindings after filling a cart still permits that old cart. User explicitly confirmed retaining this behavior after the milk example in this session.

## Verification

Independent actual-source fixtures for units/packing/rounding/errors and complete create/delete candidates are checked in JVM. Actual-source JS tests cover commit-before-state, quantity/defaults/cart, deletion/history, failure/double tap/received guard and rollback. Room tests cover exact create/delete, stale catalogue/bindings, roles/received refusal, history projection and injected SQL failure rolling back both documents. Supply authority tests include all three keys, late shadows and file-backed reopen. Full JS/JVM/lint evidence below; physical acceptance remains pending.

Verified: 390 JS / 313 JVM passed, no failures/errors/skips; lint zero errors / 15 existing warnings. User-approved empty-bindings case is covered. No local APK assembly.
