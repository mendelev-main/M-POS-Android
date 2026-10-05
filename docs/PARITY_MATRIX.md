# Android parity matrix

`implemented` means code exists. Only `accepted` means it passed the physical Android tablet cases.

| Domain | Current state | Remaining evidence |
|---|---|---|
| POS interface and business modules | implemented from iPad commit `44fbf37` | Screenshot and interaction comparison on multiple tablet sizes, aspect ratios and densities |
| Local keys and JSON records | implemented unchanged | Restart, storage failure and large-data checks |
| Products, recipes and stock | web runtime implemented | Full physical sale/return matrix |
| Payments, receipts and shifts | web runtime and native shift PDF printing implemented | Cash/card/split, restart recovery and printed output |
| Purchasing, receiving and inventory | web runtime implemented | Weighted cost, draft restart and reports |
| Product photos | native implementation | Picker, rotation, large image, restart and backup |
| Android platform settings mirror | native implementation; compatibility cache remains in WebView | Change printer/notification settings, restart app, verify UI + native snapshot parity |
| Complete backup v13 | native implementation | iPad → Android and Android → clean Android restore |
| LAN ESC/POS | native raster implementation | 58/80 mm printers, routing, copies and timeouts |
| Warehouse PDF/XLSX | native implementation | Exact values and visual comparison with iPad |
| Purchase-order sharing | native PDF implementation | Android share sheet and multi-page document |
| Telegram text/shift/monthly reports | implemented; shift close is a native PNG receipt via sendPhoto (spec 034), matching the approved iPad format | Physical image/content comparison and Telegram group/topic delivery, offline failure isolation |
| WEB orders and availability | shared runtime implemented; Android availability retry policy in spec 033 | SSE reconnect, acceptance recovery, post-payment stock and failed-send retry only after next persisted payment |
| Release/update installation | pending | Stable signing key and `adb install -r` data retention |
| Native diagnostic report | implemented; manual metadata-only JSON export, spec 032 | Offline save, cancellation/recreation/provider failure, 200-event retention and private-data exclusion on a tablet |

The iPad application remains the production source of truth until every critical row is accepted.
