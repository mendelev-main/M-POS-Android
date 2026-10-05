# Tasks

- [x] Inspect current P2 implementation and identify independent IO writes/raw-projection commit gaps.
- [x] Add bounded Kotlin FIFO queue, close/cancellation handling and immutable command capture.
- [x] Make native puts/deletes atomic and reads transactional.
- [x] Add write-version catch-up tracking and stale diagnostic guards.
- [x] Add FIFO/backpressure/state, actual Room/SQLite and executable JS compatibility tests.
- [x] Complete all tests, lint and debug/release builds: 58 Node and 28 JVM tests pass; lint has 0 errors/14 warnings; both APK variants build.
- [x] Publish validated commit to main.
- [ ] Physical recovery/large-history/backup parity acceptance before authority cutover.
