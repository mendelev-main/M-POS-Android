# Loyalty durable retry-state boundary

## Goal
Make the existing idempotent loyalty sale/reversal recovery state durable around each network attempt.

## Contract
Before a loyalty sale or reversal request, the `sending` state must be persisted to `orders`. After success, `synced` must be durably persisted; after failure, `pending` must be durably persisted. If the pre-send local write fails, no network request is allowed. Existing JS retry authority and backend idempotency remain unchanged; native Room remains shadow-only.
