# Availability restart and reconnect recovery

> Superseded on Android by spec 033 following the user's explicit business decision: no start/online/foreground retry; a failed availability attempt waits for the next successfully persisted payment. This document records the historical shared-source implementation, not the active Android policy.

## Goal
Ensure the web availability snapshot is refreshed after cold start, connectivity recovery, and foreground resume without requiring a new sale or stock mutation.

## Contract
The existing snapshot/revision mechanism remains authoritative. Recovery only triggers a fresh snapshot from durable local products/orders. No network result gates POS storage or payment. No new outbox is introduced because snapshots are replaceable and backend revision ordering rejects stale state.
