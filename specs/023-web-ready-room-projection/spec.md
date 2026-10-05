# WEB ready recovery Room projection

## Goal
Mirror the durable `webOrderReadyJournal` into Room so restart/recovery evidence is observable natively without moving `/ready` authority to Kotlin.

## Contract
Legacy journal remains authoritative. Room stores web order id, stage/timestamps and full record payload. Kotlin does not send `/ready`, mutate order state, or replay recovery.
