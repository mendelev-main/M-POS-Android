# Implementation plan
1. Add long-lived OkHttp SSE reader.
2. Parse SSE data frames for observation only.
3. Add bounded reconnect/backoff.
4. Add start/stop/status bridge actions.
5. Emit diagnostic state/counters/hashes only.
6. Keep legacy EventSource authoritative.
7. Run CI and later physical network interruption tests.
