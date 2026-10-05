# Feature Specification: Native shadow SSE

## Goal
Exercise Android SSE connectivity and reconnect behavior without changing WEB-order semantics.

## Rules
- Shadow SSE is opt-in and non-authoritative.
- Legacy EventSource remains the only business event source.
- Native events expose only state, counters and SHA-256 payload hashes to diagnostics.
- Native transport must not write orders, parked checks, acceptance journals or stock.
- Reconnect uses bounded exponential backoff up to 30 seconds.
- Stopping the shadow cancels the active HTTP call.

## Acceptance
Automated build must pass. Physical disconnect/reconnect and duplicate-observation testing is required before any cutover.
