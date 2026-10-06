# Plan

1. Keep reviewed manual cash UI and JSON shape.
2. Add native snapshot/open-shift/drawer validation and one transactional movement + marker commit.
3. Wire command through existing bounded FIFO bridge; exact uncertainty retry and cache invalidation.
4. Verify actual JS acknowledgement behavior and SQLite rollback/replay/restore.
5. Update migration/acceptance docs, validate and publish main.

Next: native shift open/close lifecycle, retaining employee rules, expected cash/difference and report/Telegram timing after persistence. Compose shift screen follows stable native state.
