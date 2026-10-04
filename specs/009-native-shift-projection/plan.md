# Implementation plan

1. Add shift and cash movement Room entities.
2. Add indexed cash movement DAO queries by parent shift.
3. Add explicit Room 3→4 migration.
4. Project nested `cashMovements` out of the existing `shifts` JSON transactionally.
5. Add `MPosShiftRepository.parityReport()`.
6. Add `shiftParity` diagnostics.
7. Extend storage diagnostics counts.
8. Leave all operational shift logic untouched.
9. Extend automated architecture tests and run CI.
