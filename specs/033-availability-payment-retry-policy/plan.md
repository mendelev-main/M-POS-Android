# Plan

1. Keep reviewed source checksums unchanged by implementing an Android-only adapter before initialization.
2. Use the existing post-commit payment argument contract, enforce its unique usage in tests, and expose an explicit MPosCore payment entrypoint.
3. Serialize/coalesce successful requests, stop after failure, and persist the retry gate before attempts for process-restart safety.
4. Replace lifecycle recovery triggers with visibility/background state only.
5. Execute the shared snapshot implementation plus adapter in VM tests, asserting real request counts/body/revision writes across failure, restart and next payment.
6. Document the superseded spec, user decision, rollback and physical checks. Run all tests and Android builds.
