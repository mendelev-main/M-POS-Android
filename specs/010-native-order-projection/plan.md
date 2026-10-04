# Implementation plan

1. Add order, line item and payment Room entities.
2. Add parent-order indices for lines/payments.
3. Add explicit Room 4→5 migration.
4. Project `orders` JSON transactionally into normalized tables.
5. Use deterministic projection IDs for nested line/payment arrays.
6. Add `MPosOrderRepository.parityReport()`.
7. Add `orderParity` diagnostics and counts.
8. Leave payment/returns/printing/stock/loyalty logic untouched.
9. Extend architecture tests and run CI.
