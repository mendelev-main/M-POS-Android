# 096 — Warehouse period reports and authoritative sources

## Scope and business parity

MPosWarehouseEngine calculates the warehouse report model: local calendar day range with exclusive next-day end, incoming quantities/actual invoice sums, stockConsumption v1 sales/returns, estimated historical balances, current-cost valuations, recorded sales cost/missing-cost counters, suppliers/documents, low/idle counts and incomplete-history warnings. It retains legacy single-line receiving, adminDeleted exclusion, name/unit/catalogue fallbacks, absent/null/zero differences, duplicate/invalid snapshot refusal and reviewed unit conversion.

Dates reject bad format/nonexistent/reversed/future days. The request captures now, validationNow and WebView IANA timezone; Kotlin uses local next-day boundaries, including DST, rather than fixed 24-hour end. Events after now are invalid; the end date is inclusive. Return movements use returnedAt independently of sale date. Legacy warnings cover known history from period start through now, including events after the requested end, as before. Recorded sale cost uses receipt item costs; outgoing/returned/current/end valuations use current product cost.

Historical start/end are current stock minus known movements after each boundary. Inventory fixation/manual edits are not reconstructed as report movements; existing notes explicitly describe that limitation. Untracked/deleted/non-simple current balances stay unknown. No balance/stock/cost/config/history is written by the report. Unknown source fields/backup v13 remain unchanged.

## Native read and presentation

MPosWarehouseRepository reads authoritative products/receivings/orders in one Room transaction and runs the model off the UI thread through the native FIFO. Client request contains dates/clock/zone, never catalogue or receipt histories. No new schema or storage authority migration is needed. Catalogue/order initial authority setup may maintain existing internal indexes; reports never mutate business data.

The adapter routes page rendering, PDF/XLSX generation and the existing monthly Telegram report through native reads. Read failure has no JS calculation fallback; the prior page remains and an error is shown. Only the latest render request applies; closed pages cannot be reopened by delayed results. Export captures date/format/section choices before awaiting, suppresses repeated clicks while pending and rechecks the existing UI admin gate. Formatting, RU display sorting, unit grouping for formatted summaries, table/export section presentation and native PDF/XLSX/Telegram transports remain their existing handlers. No visual redesign.

Monthly report keeps existing admin/config/period/success-marker rules and trigger; the wrapper prepares the model and calls the reviewed dispatch handler. No new automatic send/sync/availability trigger is added. MPosNativeWarehouseEnabled=false restores reviewed synchronous handlers. The full report shell remains WebView pending 106.

## Verification

Independent actual-source fixtures cover empty report, inclusive/exclusive time boundaries, incoming unit conversions/suppliers, returns from older sales, future/invalid events, legacy receipts/snapshots, duplicate numeric IDs, deleted/untracked/missing-cost products, explicit nulls, Russian name sorting, Moscow local days, DST and leap/invalid/reversed/future dates. JVM compares the full model and verifies authoritative Room data/read-only behavior/failure. JS compares source fixtures and tests stale-filter/close cancellation, error without fallback, export selection freeze/double click, PDF/monthly routing/admin/rollback. Native protocol freezes period and initializes only report source documents. Physical acceptance remains pending; automatic evidence follows.

Verified: 415 JS / 340 JVM tests passed, zero failures/errors/skips; lintDebug zero errors / 15 existing warnings. Native RU sorting uses Android ICU to match reviewed localeCompare ordering, including Cyrillic/Latin and Е/Ё. No local APK assembly; physical acceptance pending.
