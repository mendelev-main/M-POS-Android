# Plan
1. Detect persisted sale/reversal `sending` states in the existing retry entrypoint.
2. Normalize them to pending before dispatch.
3. Reuse existing sale/reversal retry functions and backend idempotency.
4. Synchronize iPad/Android shared source.
5. Add a regression test covering both sale and reversal interrupted states.
