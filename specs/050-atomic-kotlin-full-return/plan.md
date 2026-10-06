# Plan

1. Retain reviewed full-return UI and preparation, redirect historical receipts to a compact native command.
2. Independently validate/recompute stock, receipt flags and cash movement inside Room transaction.
3. Replace one canonical receipt without serializing the archive; preserve position/revision and projections.
4. Add exact request replay marker, restore-safe timestamp scope and retry/reload behavior.
5. Verify SQLite rollback and actual runtime integration; run automated suites/builds and publish main.

Next cohesive migration boundary: Kotlin shift cash-movement commands (deposit/withdrawal), followed by open/close lifecycle. Native financial boundary first; Compose screens later.
