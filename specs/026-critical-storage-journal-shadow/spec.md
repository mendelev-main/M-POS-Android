# Critical storage journal Room shadow

## Goal
Project the existing `criticalStorageJournal` into Room so unfinished local critical operations have native crash/restart evidence before any storage authority cutover.

## Contract
The legacy journal and `recoverCriticalStorageJournal()` remain authoritative. Room stores only a singleton diagnostic snapshot: journal id/type, created time, write-key list and raw payload. Kotlin must not replay writes, clear business keys, or participate in payment/shift/warehouse commit decisions.
