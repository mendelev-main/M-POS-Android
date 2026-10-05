# Plan
1. Persist `sending` before sale/reversal network calls.
2. Abort the network call if that local write fails and restore in-memory pending state.
3. Await durable `synced` persistence after success.
4. Await durable `pending` persistence after failure.
5. Keep backend endpoints/idempotency and retry ownership unchanged.
6. Synchronize canonical iPad and Android source and update parity manifest.
7. Check existing spec022 projection and all source parity tests.
