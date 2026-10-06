# Plan

1. Keep reviewed UI/password/report triggers; intercept open-shift/close-shift persistence only.
2. Validate employee/carryover/current shift/count/cash and reconstruct state in Kotlin.
3. Transactional shifts/projections/marker commit, restore-aware exact replay, bounded FIFO protocol.
4. Check SQLite rollback and actual JS handlers' acknowledgement/effect ordering.
5. Update migration/acceptance docs, validate and publish main.

Next cohesive boundary: Kotlin shift summary / report read model, eliminating financial duplication in report data before moving the shift screen to Compose. Authentication remains a separately specified boundary.
