# Plan
1. Persist ready intent as `prepared`.
2. Save local current-order session with status ready.
3. Promote and persist journal to `pending`.
4. Allow backend confirmation only for pending records.
5. Clean stale confirmed records on recovery even when no new confirmation occurred.
6. Keep iPad source and Android bundled source identical; update manifest.
7. Review old ready/parity/schema tests for changed stage semantics.
