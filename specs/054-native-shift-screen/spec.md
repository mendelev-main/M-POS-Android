# 054 — Native Kotlin shift summary screen

Authorized continuation of 053; comprehensive physical acceptance remains at migration end. Kotlin Android Views provide the first native shift screen without new dependencies. Compose is not introduced in this boundary.

## Source and presentation

A read-only Room transaction loads authoritative shifts, compatible canonical/legacy receipt archive and recovery journal through the same FIFO as writes. Pending recovery rejects the read. The first open shift and latest 20 closed shifts retain original numbering, ordering and accounting semantics. Native summary includes drawer, cash/card, gross sales/refunds/net revenue, product item discounts and reversed movements with notes. Discount calculation matches receiptItemDiscount including percentage clamp/fixed per-quantity/missing-name handling. Financial values from WebView are not inputs. The screen model omits receipt lists; full archive reconstruction remains internal for compatibility, with large-archive performance pending tablet evidence.

A Kotlin ScrollView overlays only the measured shift content rectangle. WebView navigation stays accessible. Bounds scale to the current viewport and clip to host, with no manufacturer/resolution assumptions. Adapter sends geometry and currency/establishment presentation only; array identity changes request fresh models without sending archives. Modal opening, other tab, unloaded state and session rollback hide the overlay. Request IDs invalidate obsolete models after hide/refresh.

## Business actions

Native buttons delegate to existing opening, cash movement, closing and report forms. Adapter whitelists actions, checks current shift identity, loaded/tab/modal/busy state. Existing administrator verification, native transactional commands, counted difference acceptance and post-commit PNG/print behavior stay intact. Forms and authorization are still JS; P8 remains partial. Reads introduce no catalogue synchronization, availability trigger, network retry or data/schema/v13 changes.

## Error / rollback

Failed native reads show an error with manual retry and explicit "Открыть прежний экран". No stale financial model is rendered automatically. Rollback disables the native screen for this session and exposes reviewed HTML; restart re-enables native by default. Removing the adapter restores legacy screen without data conversion. Other native commands/report model remain active. Navigation/hide cancels pending presentation, not durable writes.

## Verification

Actual JS adapter tests cover native-only presentation requests, change deduplication, tab/modal/unloaded/busy guards, stale shift actions, form delegation, explicit rollback and load order. Native SQLite tests cover current persisted balances, cross-shift refunds, discounts, original numbering/latest 20 history, source preservation, ignored caller finance and pending recovery rejection. Robolectric View tests exercise rendered expected cash, buttons, cancellation/error/retry/rollback and scaled/clipped/invalid bounds. Full existing suites, source hashes, lint, debug and minified unsigned release required.

Physical navigation/rotation/font size/modal/back/keyboard and large archive cases remain documented and pending.
