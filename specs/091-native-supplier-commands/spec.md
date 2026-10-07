# 091 — Supplier commands and authoritative storage

Room owns the existing suppliers JSON document through MPosSupplyStorage: one-time import, raw array/null/absence, unknown fields and key/backup v13 compatibility. No schema or source-file change. Generic late shadow writes/removes cannot overwrite owned data. Read failure propagates through native storage/loadKey instead of substituting an empty supplier list.

MPosSupplierCommand reconstructs create/edit/delete, compares the caller's expected list with live Room and checks the reviewed candidate exactly before an atomic write. Edits retain extensions, duplicate names and checkbox binding order/duplicates. Delete removes matching suppliers only, retaining purchase/receiving history and supplierName snapshots. No cascade, network publication or stock effect.

User decision in this session explicitly retains current rights: create/edit require no administrator gate; deletion requires an administrator's open shift. Native delete checks persisted shifts and employees, preventing stale UI permission after closing a shift or changing role. Existing modal/validation/busy flags/messages remain reviewed; the adapter forwards only source supplier gestures, with immutable facade delegation for unrelated keys/imports.

Rollback MPosNativeSupplierCommandsEnabled=false restores reviewed mutation construction; supplier persistence remains native authority, including import/export. Uncertain commit blocks further critical work until restart as existing adapters do; no fallback to obsolete cache. Kotlin reconstructs only permitted fields, preventing extension/unrelated-row mutation and stale whole-list overwrite.

Tests: actual-source save/delete, no-admin create/edit, busy/failure/no early memory update, frozen delegation/import/rollback, supplier read failure; Room command expected/candidate checks and persisted role/shift/history; FIFO authority import/late shadow rejection/file-backed reopen. Full JS/JVM/lint evidence below. Tablet cases pending.

Business examples retained: two suppliers may both be named “Молоко”; unbinding a product does not erase historical orders; deleting a supplier leaves its saved name in old purchase/receiving records. Selected supplier ID may become dangling until UI reselects; getPurchaseOrderSupplier returns null. No automatic cleanup policy is introduced.

Verified: 385 JS / 306 JVM passed, no failures/errors/skips, lint 0 errors / 15 existing warnings; no local APK assembly.
