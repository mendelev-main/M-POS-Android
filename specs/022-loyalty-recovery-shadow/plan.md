# Plan
1. Extend `order_projection` with sale and reversal recovery status fields.
2. Add explicit Room migration 9→10.
3. Populate fields from existing order JSON on every orders shadow write.
4. Keep existing JS `retryPendingLoyalty()` authoritative.
5. Verify architecture/build; physical offline/restart loyalty recovery remains required.
