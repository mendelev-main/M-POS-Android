# 053 — Native shift summary / report read model

User authorizes continuation after 052. Native derived data now, physical acceptance at the migration end. No schema, primary keys or backup v13 changes.

## Native source

MPosShiftReportRepository reads shifts, canonical/legacy receipt archives and the recovery journal in one Room transaction, through the same FIFO bridge as local commands. Native authorities must be initialized and no critical recovery journal pending. Missing/ambiguous shift or malformed financial source fails rather than inventing a report. closedOnly prevents automatic close-report output for a shift not durably closed. Manual report reading/printing can retain the prior open-shift case.

The report shape matches reviewed buildShiftReportPayload: employee/open/close facts, opening/expected/counted/difference, cash/card/net revenue/count, cash movements in archive order and original-shift receipts sorted by timestamp with item name/productName/default aliases. Historical returns use 049 attribution. All original sale receipts remain in report.orders, including returned ones. Additional summary contains refunds/cash refunds/gross/net sales for subsequent native screen work. Source JSON/extensions are untouched and private receipt fields are not added to the report. Legacy noncanonical archive arrays are read through the compatible document rather than losing duplicate-ID records in diagnostic indexes. Null archive yields no sales.

Currency and establishment name remain presentation context from current settings; financial values, receipts, employees and movements are read from Room. Only shift ID, presentation context and closedOnly enter the read request; caller financial values are not inputs.

## Runtime outputs

An Android adapter loads after network-printer.js and routes automatic Telegram close image, LAN close receipt, manual PDF print and report modal to the native model. Existing PNG/ESC-POS/PDF formatters and printer routing/copies stay unchanged. Automatic Telegram gates (enabled/notify/token/chat) remain intact. Closely concurrent outputs share one pending read and get separate report copies. No persistent report cache: subsequent manual reads/restart/import obtain current data. Failure emits a message and no stale financial output; it does not undo locally committed closure or invent network retries.

Report dialog remains reviewed HTML markup with native values; loading is asynchronous. Closing/replacing a modal invalidates its pending view generation. Main shift dashboard/return UI still use the tested JS accounting mirror; this stage does not claim the Compose screen or authentication has moved.

## Numeric compatibility

MPosJsonNumbers matches reviewed JSON scalar Number coercion for numeric strings, whitespace, booleans, missing/null values and supported radix strings. MPosShiftAccounting now uses it, keeping native cash guards consistent for these legacy values. Non-numeric accounting data propagates an invalid balance instead of being silently counted as zero. Report-level Number(value)||0 mapping retains item/default behavior and normalizes negative zero. Non-finite results reject report construction. No persisted numeric value is rewritten.

## Compatibility / rollback

Removing only the report adapter restores reviewed report entry points. Saved domain records require no conversion. Other native commands/cutovers remain independent. Removing the new adapter changes report reads back to WebView memory and loses current native freshness/error guarantees; use rollback as diagnosis, not a data repair. No data migration or report snapshot persistence introduced.

## Preserved presentation rules

PNG/close receipt displays all sale receipt entries, including returned receipts (iPad rule), while report.count/"Заказов" uses the existing net same-shift count. Preserve the separate labels/semantics rather than silently changing totals. Future unification is a business/UI decision. Original administrator gate, unpaid session, availability gate and manual catalogue synchronization remain unchanged.

## Validation

14 shared golden fixtures against reviewed JS and native model: same/cross cash/card/split refunds, missing return attribution/movement, orphan/duplicate timestamp movements, receipt/movement ordering, item aliases, unknown fields, scalar numeric coercion and legacy duplicate-ID archives.

SQLite tests cover no source mutations, restart/current rows, revisions, caller financial values ignored, pending journal/missing/open close target, invalid finance without zero fallback, null archive/restore and receipt row presentation (all sale receipts/negative zero).

Actual JS + storage/printer runtime tests cover coalesced snapshot, request fields, copies/routing, durable close before report before network/print, manual PDF/modal authoritative values, no output on read failure, modal cancellation, fresh subsequent reads, disabled notification gates and script load order. Full JS/JVM suites, hash/source sync, lint and both builds required. Physical output acceptance pending.

Full archive reconstruction is currently used for compatibility. Large-archive latency/memory remains a pending tablet test and later query optimization.
