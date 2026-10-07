# 108 — нативный зал, столы и бронирования

Scope: native responsive hall map/table selection and drag editing, table
create/rename/rotation/delete forms, edit menu, booking date/list/card and
create/edit/cancel/time/duration/guest stepper forms. Shared native theme/Manrope.
098 Room commands remain authoritative; preserve all documented table/booking
rules, overlap/status/date/guest semantics, no new permissions/capacity checks.

Native map keeps source relative placement/shape/rotation and booked/selected
state; normal mode cannot drag. Drag invokes reviewed pointer handlers against
the mounted table and map, restoring persisted baseline before native commit.
Failed move leaves saved coordinates, duplicate gestures are blocked, uncertain
commit requires recovery. Native pointer release consumes source synthetic click
suppression, matching the browser's post-pointer click without a second action.

Native time picker retains HH:mm and clear/fallback semantics; fields/guest
steppers remain mapped to mounted reviewed handlers. Async commands update state
only after persistence. Table deletion removes linked bookings atomically, not
receipt/parked snapshots; booking cancellation remains the original direct action, without a new confirmation.

MPosNativeHallUiEnabled=false restores presentation but keeps 098 Room authority.
No network/catalogue/stock/availability/print changes; backup v13 remains.
Known source UI quirk retained: when selectedHallTableId is absent, the side card
shows the first table but booking creation still asks the user to select it on
the map. Do not silently auto-select a table in this presentation migration.
DOM compatibility 109 and physical acceptance 110 remain pending.
## Verification

Six actual-source JS UI cases cover selection/shape/rotation/booked state and
normal-mode drag refusal, acknowledged creation/double tap/failure/retry, native
drag through source pointer/Room baseline and failure/success/click suppression,
time/duration/guest stepper capture and acknowledged booking, cancel versus editor
and table-delete boundaries, uncertain/stale/rollback behavior. Three Android view
cases cover relative map bounds/rotation/edit-only drag/busy gate, time selection/
clear/dismiss and shared native floor/card palette previews. Layout measurement
reuses existing layout params rather than repeatedly allocating/requesting layout.

525 JS / 412 full JVM tests passed, 0 failures/errors/skips; lint 0 errors / 15
existing warnings. Final native light/dark synthetic previews inspected; not
physical tablet evidence. No local APK assembly. DOM 109 / physical 110 pending.
After main publication: 107/110 engineering tasks (97.27%), 3 remaining; this is
not native feature/code coverage. Next 109 — remove active WebView runtime.
Status: complete.
