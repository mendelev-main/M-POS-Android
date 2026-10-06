# Implementation plan

1. Extract provider input accumulation into `MPosBackupInput`, preserving bytes and the existing inclusive size limit.
2. Integrate before JSON parsing and image staging; rename the touched manager to follow M POS naming.
3. Verify exact/oversized/short/zero/interrupted streams, then run existing compatibility suites and Android builds.
4. Publish to main; keep physical backup restore and Room authority gates open.

No dependencies, schema changes, backend calls or new business rules.
