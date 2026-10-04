# Implementation plan

1. Add Room runtime/compiler dependencies.
2. Add `MPosDatabase`, shadow entity and DAO.
3. Add lifecycle-scoped `NativeStorageMirror`.
4. Add secure `storage` bridge route.
5. Add Android-only `native-storage-shadow.js` immediately after the shared storage adapter.
6. Mirror existing localStorage keys and subsequent successful writes/removes.
7. Do not read business state from Room in this stage.
8. Extend source-parity and architecture tests.
9. Run CI and then perform physical restart/shadow diagnostics before promoting any domain to Room ownership.
