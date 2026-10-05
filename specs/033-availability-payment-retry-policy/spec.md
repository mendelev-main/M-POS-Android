# Spec 033 — Availability retry after the next payment

## User decision

If availability cannot be sent, the next attempt occurs only after the next successfully persisted payment. Startup, internet recovery and foreground are not retry triggers. This supersedes the automatic Android recovery behavior documented in spec 030.

## Scope and compatibility

- An Android-only adapter is loaded before `loadAll()` and overrides the availability trigger/recovery entrypoints. The reviewed shared HTML/JS baseline is retained; only the Android script tag is added to HTML.
- Current `payment.js` calls `publishAvailability(order.webOrderId ? [order.webOrderId] : [])` after the critical payment commit. This explicit-array call is the existing payment compatibility boundary. All stock-change/manual-sync callers use zero arguments. The adapter maps the former to the explicit `MPosCore.Availability.paymentCommitted` API; tests enforce that no non-payment caller uses the argument-bearing contract. If that shared contract changes, update the adapter before source synchronization.
- No failed attempt is retried because of startup, online, foreground, stock editing, receiving, inventory, return, manual menu sync or an unrelated mutation queued during that attempt. The next payment publishes the latest durable stock, not the failed stale snapshot.
- Normal existing stock-change/manual-sync publications remain supported if no failed/interrupted attempt is pending. This policy concerns retry; it does not change stock calculations or these normal business triggers.
- Persist a conservative retry gate before each attempt in the Android-only local key `mpos_availability_retry_blocked`. A failed/interrupted attempt stays blocked across process restart; a completed successful attempt clears the gate. A new persisted payment can attempt even if reading this metadata failed. Metadata errors never block payment.
- Retain the existing durable-source reads, pre-send revision write, API endpoint, timeout, request body, reservation settlements and success clearing. No queued outbox, heartbeat or automatic catalog synchronization.
- Backgrounding still aborts the network request. Foreground only updates active state, without publication.

## Business tradeoff (explicitly accepted)

After a failed send, online stock can remain stale until the next payment. Availability does not gate offline sales. This retry policy is intentionally different from WEB acceptance/ready and loyalty recovery; those contracts remain unchanged.

## Rollback and physical acceptance

Removing the adapter script restores shared-source recovery triggers; do so only with a new user decision because it changes the approved business rule. The retry metadata is separate from business/backup v13 data and is harmless when the adapter is absent. No data migration.

Physical checks: offline sale → failed snapshot → reconnect/restart/foreground (no send) → next saved payment (latest stock sent); verify stock edits/returns/manual sync cannot bypass the failed gate; payment storage failure must not publish; background abort must await another payment; successful ordinary stock-change publication remains unchanged. Physical acceptance remains pending.
