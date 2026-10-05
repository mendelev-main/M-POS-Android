# WEB ready durable recovery

## Goal
Make the WEB order `ready` transition survive offline/restart without blocking local POS operation.

## Safety contract
The backend `/api/orders/:id/ready` is idempotent for an already-ready order. Persist a dedicated local journal before changing the local session status. Retry only this endpoint on startup/online recovery. Do not use the lossy/coalescing operational snapshot outbox. Do not generalize retries to payments or unrelated endpoints.
