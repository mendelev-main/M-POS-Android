# Implementation plan

1. Add held-check and held-line Room entities.
2. Add parent held-order index.
3. Add explicit Room 5→6 migration.
4. Project the existing `parked` JSON transactionally.
5. Preserve customer/web/kitchen state and original JSON.
6. Add `MPosParkedOrderRepository.parityReport()`.
7. Add held-check parity/count diagnostics.
8. Leave park/resume/delete/printing/current-cart behavior untouched.
9. Extend architecture tests and run CI.
