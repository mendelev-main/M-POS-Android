# Availability restart and reconnect recovery

## Goal
Ensure the web availability snapshot is refreshed after cold start, connectivity recovery, and foreground resume without requiring a new sale or stock mutation.

## Contract
The existing snapshot/revision mechanism remains authoritative. Recovery only triggers a fresh snapshot from durable local products/orders. No network result gates POS storage or payment. No new outbox is introduced because snapshots are replaceable and backend revision ordering rejects stale state.
