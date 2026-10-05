# Plan
1. Add singleton Room projection and DAO.
2. Add explicit migration 11→12.
3. Mirror `criticalStorageJournal` writes and null-clear operations.
4. Keep legacy replay authority in `pos.html`.
5. Review every schema-version assertion before commit.
6. Verify full source tests, parity, lint and debug build.
