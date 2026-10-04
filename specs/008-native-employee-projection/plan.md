# Implementation plan

1. Add `EmployeeProjectionEntity` and DAO.
2. Add explicit Room 2→3 migration.
3. Project mirrored `employees` writes transactionally.
4. Add `MPosEmployeeRepository.parityReport()`.
5. Expose `employeeParity` through the existing secure storage bridge.
6. Extend diagnostics counts.
7. Leave employee UI/admin/shift behavior untouched.
8. Extend architecture tests and run CI.
