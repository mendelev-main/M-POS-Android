# Plan

1. Add Kotlin document ownership for whitelisted layout/navigation keys.
2. Reuse native FIFO/bridge/one-time bootstrap with separate per-key migration markers.
3. Preserve JSON and v13 journal behavior; test native rollback and actual JS restoration.
4. Run full suites/builds, enforce native tests in CI, update status and publish.
