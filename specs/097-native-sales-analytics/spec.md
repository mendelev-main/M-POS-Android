# 097 — Room-backed sales aggregates and calendar periods

## Native scope and parity

MPosAnalyticsEngine selects receipts by inclusive local from/to calendar boundaries and excludes every receipt with truthy returnedAt, regardless of refund date. It reconstructs revenue, cash/card payments, receipt count/average, historical item cost/profit, employee revenues, current-category and snapshot-name product groups, and current inventory value. Payments arrays (including empty arrays) supersede legacy method/total; unknown payment methods stay ignored, and totals are not rebalanced. Missing/invalid item quantity/price/cost follow the reviewed Number(...)||0 rules. Category uses current catalogue then receipt snapshot; product grouping remains by displayed historical name, so identical names merge even across IDs. Employee grouping uses shift employeeName, never a new employee rename. Group ties preserve source insertion order and numeric Object.entries key ordering.

Inventory valuation includes simple tracked products only, clamps negative quantity/cost to zero and remains independent of selected sales period. Receipt totals may differ from item price×quantity because discounts/delivery are already in totals; this distinction is retained. No stock, receipt, shift or setting is modified.

Analytics date behavior differs from warehouse validation and stays compatible: empty filters mean today, invalid/reversed ranges yield no sales, short-month ISO dates normalize as JS Date does, and explicitly selected future dates are allowed. Native local boundaries include DST and the last millisecond of the selected day. No new date policy is introduced.

## Room read and presentation

MPosAnalyticsRepository aggregates full authoritative order/catalogue/shift JSON in one Room transaction on the native worker. This stage uses Kotlin aggregation over Room rows rather than SQL SUM on lossy projection defaults: raw returnedAt truthiness, nullable fields, legacy payments and unknown extensions govern parity. It does not introduce JSON1 dependence or a database schema change. The bridge receives only filters/clock/timezone and returns count/aggregates, not full receipt history. Existing projections remain compatible.

The adapter renders cached matching native data while refreshing or a themed loading card on first request, applies only the latest visible-period result and never invokes JS financial calculations on native failure. Failures show a retry action. The main render no longer calculates hidden analytics or prefetches loyalty data on unrelated screens; existing loyalty analytics API is still invoked by the reviewed visible analytics renderer with its admin/key/error rules. No new network trigger, sync or availability publication is introduced. Report refresh replaces only the analytics screen, retains its scroll, and defers replacement while a date input has focus. Admin-only financial KPI/value visibility remains the reviewed renderer; category/product/payment charts stay available as before.

MPosNativeAnalyticsEnabled=false restores synchronous reviewed calculations/markup. Source global helpers remain rollback compatibility. Native analyticsData presentation supplies the count required by the renderer; it does not ship archived receipts back into WebView. Loading/error uses existing Manrope, card, text, button and both theme tokens under mpos-native-design; no screen redesign. Full UI migration is 107, not this stage.

## Business clarification pending

A 1 October sale returned on 7 October disappears from the report for 1 October after the return; no separate return amount appears on 7 October in this analytics model. A user question with the 1,000 ₽ example was sent for potential future refund-date accounting. Current policy is retained while the answer is pending, independently of warehouse movement reporting.

## Verification

Actual-source independent fixtures cover inclusive bounds, mixed/empty/legacy payment arrays, truthy later returns, current category/historical names, same-name merges, missing product/shift fields, numeric coercion, negative/untracked/composite stock valuation, numeric group ties, default today, Moscow/DST boundaries, reversed/invalid/normalized/future dates. JVM compares every aggregate; Room verifies authoritative sources, unchanged documents, later refund exclusion and no empty fallback. JS checks no hidden read/prefetch, native rendering, stale date/tab handling, failure/retry, date focus/scroll, non-admin visibility and rollback. Native protocol captures dates before initialization and sends no archive. Physical acceptance remains pending; automatic evidence follows.

Data-integrity boundary: non-finite financial aggregates serialize as unknown values in the native JSON model. Presentation rejects these values with a data error instead of showing plausible zero statistics; this is a technical guard for corrupt/non-finite sums, not a change to valid receipts or missing item fields coerced by the reviewed rules.

Verified: 422 JS / 345 JVM tests passed, zero failures/errors/skips; lintDebug zero errors / 15 existing warnings. All source fixtures also passed after actual Room import/projection/read. Non-finite financial model rejection is covered in UI tests. No local APK assembly; physical acceptance pending.
