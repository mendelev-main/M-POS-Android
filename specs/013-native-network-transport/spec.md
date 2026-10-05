# Feature Specification: Native network transport boundary

## Goal
Introduce Kotlin/OkHttp as the Android network transport foundation without changing WEB-order business semantics.

## Initial scope
- Secure bridge channel `network`.
- Kotlin `NativeNetworkTransport`.
- HTTPS-only backend probe using the existing orders SSE endpoint.
- JS `MPosCore.Network` contract for diagnostics.

## Safety boundary
- `EventSource` remains active and authoritative.
- Existing accept/ready/live-report fetch calls remain authoritative.
- No duplicate SSE subscription is started by Kotlin.
- No ACK, retry, order persistence or business transition is moved yet.
- Network failure can never gate local payment/storage.
- No automatic catalogue synchronization.

## Acceptance
Automated source tests, lint and debug build must pass. Physical backend probe/reconnect testing remains required before native SSE is enabled.
