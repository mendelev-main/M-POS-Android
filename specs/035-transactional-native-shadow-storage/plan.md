# Plan

1. Inspect existing Room projections and JS storage ordering; keep the shared business runtime unchanged.
2. Rename the rewritten native boundary to MPosStorageMirror, add a bounded FIFO worker and cancellation-aware error handling.
3. Enclose raw/projection puts and deletes in complete Room transactions, and diagnostic reads in read transactions.
4. Track requested/committed versions and prevent stale success after failure/rejection; preserve legacy source and catalog authority gate.
5. Validate actor ordering plus actual Room/SQLite rollback using synthetic SQL triggers, recovery after failure and file-backed reopen.
6. Run compatibility/unit/graphics/lint/debug/release checks, document physical gates and push the validated change to main.
