# Implementation plan
1. Add stock event/event-line entities and DAO.
2. Add explicit Room 6→7 migration.
3. Project existing `receivings` and `inventoryHistory` independently.
4. Preserve source key and full JSON.
5. Add parity counts per source.
6. Keep product stock and warehouse workflows legacy-authoritative.
7. Extend diagnostics/tests and run CI.
