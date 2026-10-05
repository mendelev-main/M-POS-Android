# WEB ready durable local-state gate

## Goal
Close the crash window between creating the ready journal and durably saving `currentOrderSession`.

## Contract
Ready recovery uses `prepared → pending → confirmed`. `prepared` means intent exists but must never be sent. Only after `currentOrderSession` is durably saved is the journal promoted and durably persisted as `pending`; only pending records may call the idempotent backend `/ready` endpoint. Confirmed leftovers are cleanup-only.
