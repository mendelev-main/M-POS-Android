# 094 — Receiving drafts and restoration

## Scope

MPosReceivingDraftCommand owns ordered and standalone draft open/save. It reconstructs the reviewed receivingDraftForOrder defaults using persisted purchase orders/catalogue and the captured standalone cart. Saved receivingDraftV2/standalone receivingDraft are cloned, unknown extensions retained, and orderId normalized as before. Legacy per-product qty/totalCost drafts are restored; null expectedQty keeps quantity blank, requested quantities/packing/stock/content units use the reviewed nullish fallbacks. Zero and explicit null are distinct from absent properties. No invoice validation is introduced when saving: blank/incomplete/zero inputs remain editable, with validation reserved for 093 confirmation.

Opening a pending order persists receivingDraftV2/receivingIncomplete before rendering if necessary. Already marked saved orders restore without another write. Saving an ordered draft sets the same fields, then returns to the list; standalone drafts remain editable after save. Received/deleted/missing orders are rejected, and expected order snapshots reject stale overwrites. Open/save reads and optional writes happen in a Room transaction; native critical recovery must be complete. No stock, cost, history, role restriction or network trigger is changed.

## Runtime and compatibility

MPosCore.ReceivingDraft captures input before native initialization awaits. The native adapter retains UI busy flags, disabled save buttons, page scroll, form collection, modal closing, messages and source success/failure sequencing. Memory and rendering change after native acknowledgement. Failure retains input/orders and enables retry; uncertain commit blocks critical work until restart. Existing rendering/input handlers remain JS presentation, and the original builder remains only for rollback compatibility.

MPosNativeReceivingDraftEnabled=false restores reviewed open/save handlers while keeping native document ownership from 093. Existing purchaseOrders/receivingDraft keys and backup v13 shapes stay unchanged. One-time ownership initialization, null/absence, imports and unknown fields remain supported. No Room schema change and no local APK build.

## Verification

Independent actual-source fixtures cover new/legacy/saved orders, incomplete marker, unknown package size, cart, explicit-null cart fields, saved/empty standalone drafts and partial ordered/standalone save. JVM compares all results and tests reopen without writing, partial restoration, stale/received/deleted orders, SQL failure and recovery guard. JS covers native ack before state/render, double tap, buttons/input retained on failure, standalone save, uncertainty and rollback. Native protocol tests freeze incomplete input before initialization. Physical acceptance is pending; final automatic counts recorded below.

Verified: 403 JS / 327 JVM tests passed, no failures/errors/skips; lintDebug zero errors / 15 existing warnings. Physical acceptance remains pending; no local APK assembly.
