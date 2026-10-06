# Android parity matrix

User decision 2026-10-06 allows immediate native cutovers with automated compatibility checks and defers comprehensive physical acceptance to the end. `implemented` means code exists. Only `accepted` means it passed the physical Android tablet cases.

| Domain | Current state | Remaining evidence |
|---|---|---|
| POS interface and business modules | implemented from iPad commit `44fbf37` | Screenshot and interaction comparison on multiple tablet sizes, aspect ratios and densities |
| Local keys and JSON records | compatible shapes retained; products/layout/posNavigation/employees/shifts/orders source is now Kotlin/Room (040–044), other domains legacy | Restart, storage failure and large-data checks |
| Ordered transactional Room shadow | implemented, spec 035; raw/projection writes and deletes are atomic; bounded FIFO, stale diagnostic guards | Final physical rapid writes, process exit, large histories and restore parity; products authority already switched under user decision |
| Products, recipes and stock | web runtime implemented | Full physical sale/return matrix |
| Payments, receipts and shifts | web runtime and native shift PDF printing implemented | Cash/card/split, restart recovery and printed output |
| Purchasing, receiving and inventory | web runtime implemented | Weighted cost, draft restart and reports |
| Product photos | native implementation | Picker, rotation, large image, restart and backup |
| Android platform settings mirror | ordered disk-confirmed Kotlin mirror, spec 039; legacy settings remain authoritative | Change printer/notification settings, restart app, verify UI + native snapshot parity |
| Complete backup v13 | native implementation | iPad → Android and Android → clean Android restore |
| LAN ESC/POS | native raster implementation | 58/80 mm printers, routing, copies and timeouts |
| Warehouse PDF/XLSX | native implementation | Exact values and visual comparison with iPad |
| Purchase-order sharing | native PDF implementation | Android share sheet and multi-page document |
| Telegram text/shift/monthly reports | implemented; shift close is a native PNG receipt via sendPhoto (spec 034), matching the approved iPad format | Physical image/content comparison and Telegram group/topic delivery, offline failure isolation |
| WEB orders and availability | shared runtime implemented; Android availability retry policy in spec 033 | SSE reconnect, acceptance recovery, post-payment stock and failed-send retry only after next persisted payment |
| Release/update installation | pending | Stable signing key and `adb install -r` data retention |
| Native diagnostic report | implemented; manual metadata-only JSON export, spec 032 | Offline save, cancellation/recreation/provider failure, 200-event retention and private-data exclusion on a tablet |

The iPad application remains the production source of truth until every critical row is accepted. Record physical results using [native tablet acceptance scenarios](NATIVE_TABLET_ACCEPTANCE.md).

## Bounded backup input (spec 036)

Kotlin enforces the existing inclusive 500,000,000-byte limit while reading, rather than after unbounded allocation. Exact bytes, v13 parsing and restoration remain unchanged. Automated boundary/short-read/zero-read/provider-failure tests cover the input layer; physical document-provider and large-backup restore/restart acceptance remains pending.

## Backup image preparation (spec 037)

Native API 28 tests cover real PNG round-trip, untouched business/extension fields, export ID deduplication, existing per-product import IDs, missing images, corrupt-image and disk-full cleanup, oversized images and continuing cleanup after a deletion failure. Physical provider/import/cancel/finish/restart evidence remains pending; business restore is still shared-runtime owned.

## Kotlin SSE framing (spec 038)

Functional JVM tests cover exact whitespace/UTF-8 data, fragmented CR/LF/CRLF, BOM, empty and named messages, EOF discard, line/frame limits and interrupted reads. Native observation is still diagnostic-only; same-stream parity, reconnect and lifecycle acceptance remain physical gates.

## Ordered settings persistence (spec 039)

Native tests cover checked/delayed/failed commits, immutable commands, FIFO, corrupt snapshots, bounded backpressure and cancellation. Executed JS tests preserve completed local saves despite native failure. Physical process restart, low storage and printer/notification UI/native parity remain pending; native settings are not authoritative.

## Catalog authority (spec 040)

Kotlin/Room now owns the compatible products document. One-time migration imports current legacy data over stale shadows; marker/document/indexes are atomic. Reads use full JSON, preserving unknown fields/order; writes acknowledge native commit before the secondary cache. Actual JS v13 import and critical journal recovery are automated. Layout/navigation/employees/shifts are also native (041–043); remaining domains stay legacy. Physical acceptance is deferred, not claimed complete.

## Workspace authority (041)

Kotlin/Room owns category layout and navigation configuration. Independent migration markers, full JSON/order preservation, native SQL rollback/reopen and actual v13/journal replay are automated. Existing rendering, normalization and permission checks stay unchanged; final tablet acceptance is pending.

## Employee and shift ownership (042–043)

Employees and shifts (including cash movements) now use authoritative Kotlin/Room persistence, alongside products/layout/posNavigation/employees/shifts/orders. Full compatible documents remain the source; typed indexes are supporting structures. Atomic migrations and failed-write rollback, stale shadow protection, restart, actual v13 import and journal recovery are covered automatically. Employee authorization and shift financial engines remain reviewed JS. Other business documents remain on legacy storage; final physical checks remain pending.

## Paid receipt ownership (044)

Orders now use Kotlin/Room document authority with transactional receipt/line/payment indexes. Original sale, split payments, historical consumption and full-return/loyalty fields remain compatible. Actual JS payment-journal recovery, v13 import and full return are tested. Financial engines remain reviewed JS; full-array writes/index refresh remain a performance limitation. See [receipt storage model](RECEIPT_STORAGE_RU.md) for the proposed per-receipt repository and return ledger, which are not yet implemented. Final physical acceptance remains pending.
