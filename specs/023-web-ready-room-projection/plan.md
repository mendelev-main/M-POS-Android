# Plan
1. Add `web_ready_projection` entity and DAO.
2. Add explicit Room migration 10→11.
3. Project writes/removes for `webOrderReadyJournal` in NativeStorageMirror.
4. Keep the existing JS recovery loop authoritative.
5. Verify source tests/lint/build; physical offline restart remains pending.
