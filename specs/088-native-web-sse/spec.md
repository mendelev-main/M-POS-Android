# 088 — Primary native WEB SSE transport

## Scope and cutover

Kotlin/OkHttp owns the configured HTTPS `/api/orders/events?deviceKey=…` stream, parsing, reconnect delay, Last-Event-ID, cancellation and foreground/background lifecycle. `native-web-sse.js` replaces EventSource only for that exact configured URL. Other EventSource URLs and `MPosNativeWebSseEnabled=false` use the reviewed browser implementation. Stop the existing stream/restart it when changing rollback mode. Source business handlers remain unchanged: order normalization/merge, persistence to webEvents, notification and owner_live_report_request response use the reviewed runtime. This is transport authority, not native WEB business mapping or native webEvents storage. Diagnostic shadow SSE stays independent and non-authoritative.

## Delivery and recovery

Separate primary framing supports UTF-8/BOM, LF/CR/CRLF, one optional ASCII space, multiline/empty data, named events, persistent/reset IDs, NUL ID rejection, ASCII retry values and unfinished-EOF discard. No diagnostic 1 MB frame limit is imposed on business data. One event at a time crosses the bridge; the JS adapter acknowledges completion of the async handler in finally. This is backpressure, not durable server acknowledgement. Session ID and sequence reject stale acknowledgements, closed/replaced streams ignore late events. Business storage failures retain reviewed behavior; no exactly-once delivery or server replay capability is claimed.

Default reconnect delay is 3000 ms, overridden by server retry. Valid Last-Event-ID is retained between connections, including UTF-8 IDs. Network failure/EOF reconnect; non-200 (including 204) or wrong MIME permanently closes as EventSource does. No automatic HTTP retry underneath the reconnect loop. Background cancels the active socket; foreground reconnects only if the stream is still requested. Closing/replacing a source clears the request. Slow async business handling pauses native reading instead of retaining an unbounded JS message queue. Native event JSON uses the existing UTF-16-safe bridge serializer.

This stream never triggers catalogue synchronization or availability publication. Existing loyalty/WEB journal recovery and manual catalogue triggers remain separate. Orders/backup v13 JSON and financial calculations are unchanged.

## Verification

JVM tests cover primary framing, IDs/retry, exact prefix and credential encoding, delivery backpressure, stale ACK, reconnect Last-Event-ID, HTTP/MIME permanent close, background and replacement cancellation. JS tests exercise the actual reviewed startWebOrderEvents handler for saved incoming orders and owner live report, plus rollback, unrelated URLs, named events, states and late callbacks. Full JS/JVM/lint evidence is recorded below. Tablet network transitions remain pending.

## Preserved business questions

The reviewed order change detector compares ID/status/total/updated_at/customer_id but normalizeWebOrder does not retain updated_at. A change only to phone/address/comment/items may therefore update memory without saving it. Preserved for parity; a separate WEB business refactor should define which server changes must be durable. Last-Event-ID helps only if the backend provides IDs and supports replay; reconnect alone cannot guarantee recovery of transient owner-report requests while offline.

Verified: 368 JS and 293 JVM tests passed; zero failures/skips, lint zero errors / 15 existing warnings. No local APK assembly. Physical acceptance pending.
