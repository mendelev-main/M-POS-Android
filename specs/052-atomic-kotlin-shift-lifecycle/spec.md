# 052 — Atomic Kotlin shift opening / closing

User authorizes continuation after 051, immediate native cutover after automated checks and physical testing at the end. Preserve JSON keys/schema v13 and reviewed business rules.

## Open boundary

Reviewed submitOpenShift keeps its UI, busy/current-shift guards and administrator password verification. No password is added to Kotlin, requests, markers or logs. This stage does not migrate authentication or establish a new native authorization boundary.

The adapter sends one new shift plus expected/proposed shifts and expected employees. Kotlin requires unchanged durable shift/employee snapshots, no existing open shift, unique shift ID, a current employee and no pending critical journal. All employee roles remain eligible as in the reviewed selector; do not introduce an active/role filter. Employee name/phone are frozen from the current employee record. Opening cash comes from countedCash of the closed shift with greatest closedAt (first in archive for ties), not its expected balance or array tail. No prior shift or missing countedCash means zero. Opening must be finite/nonnegative; source shift ID is retained. Generated fields are compared against the offered shift.

## Close boundary

Reviewed submitCloseShift keeps counted-cash parsing and UI. Kotlin requires the target to be the first current open shift, unchanged durable shifts, matching receipt count and expected drawer calculation. 049 cross-shift refund accounting applies. Expected and counted cash must be finite/nonnegative; counted cash can differ from expected, including either sign of difference, and remains unrounded. Kotlin reconstructs only status/closedAt/countedCash; extension fields, movements and employee snapshot survive. Difference is acknowledged but not added to the persisted schema. Clock corrections are not rejected merely because closedAt is earlier than openedAt.

One Room transaction saves shifts/projections and request marker. Orders, stock, employees, cart/session, parked orders and critical journal are untouched. Existing UI applies state after durable acknowledgement, then invokes Telegram/monthly report for opening and Telegram shift-close image/receipt printing for closing. No new network effect, availability publication or catalogue sync. Existing PNG/print/PDF report payload construction remains the Android runtime boundary; native reporting data is a following migration, not claimed completed here.

## Replay / restore / rollback

Marker key includes operation, shift ID and event timestamp; hash identifies exact serialized request. Exact replay cannot append a duplicate shift. Opening replay can be acknowledged after that shift was subsequently closed if its original opening facts still match. Closing replay requires the saved closed record to match. Changed requests reject. Restore-before-open/close makes an old marker reject; a fresh operation/time can proceed. One uncertainty retry uses identical bytes; unresolved status blocks critical operations until reload. No exactly-once promise for Telegram or printers.

Remove the lifecycle adapter to restore the reviewed critical journal entry points; saved shapes need no conversion. No destructive data migration. Malformed/ambiguous native snapshots reject rather than receive guessed repairs.

## Preserved cases for later business review

- Administrator password checking remains in legacy UI. Replacing it with native credential storage/authentication needs a separate specification; do not duplicate its existing constant.
- Closing a shift does not clear or prohibit an unpaid cart or parked orders. Preserve this; the next shift continues with the existing order session.
- Counted cash need not equal expected and is the next shift's carryover. Precision rules remain as documented in 051.

## Validation

SQLite: most-recent counted carryover/employee fields, first/missing counted zero, closure/difference, exact replay/reopen/open replay after close, duplicate/open guards, stale employees/shift/receipt count/cash, invalid carryover/count, preserved unpaid session and next opening from actual counted cash, pending journal, synthetic shift/movement/marker failures, restored backups, cross-shift refunds and preserved JSON.

Actual shared JS handlers: ack before state/Telegram/monthly/print, failure without effects, busy/repeat guards, identical uncertainty retry/block, initial zero, unchanged administrator rejection, invalid counted input, comma fractional count and corrected refund report values. Source refresh/hash, full JS/JVM suites, lint and both builds required. Physical tablet/Telegram PNG/printer evidence deferred.

Native drawer still reads canonical receipt payloads; large-archive performance remains a pending tablet check.
