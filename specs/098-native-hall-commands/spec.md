# 098 — Native hall and booking commands

## Authority and transaction boundary

hallTables and bookings move to authoritative Room full-JSON documents with per-key one-time ownership markers. Array/null/absence, unknown nested fields and v13 backup keys remain compatible. Generic delayed put/remove messages cannot overwrite owned documents. Native read errors are propagated rather than replaced by a plausible empty hall. No database schema change.

MPosHallEngine constructs table creation/rename/rotation/move/deletion and booking creation/edit/cancellation from command fields and current persisted JSON. MPosHallCommand checks both expected documents and the recovery journal within one Room transaction. Table deletion and removal of every linked booking commit together; receipts, parked orders and order-context snapshots are not cascaded. Commands acknowledge only after persistence. Duplicate creates and stale snapshots are refused. UI double taps are guarded; uncertain commit requires recovery before another command.

## Reviewed business rules retained

No new role, open-shift, capacity, past-date, phone or maximum-guests restriction. Table numbering is maximum numeric number plus one, shape normalizes to square/rectangle, initial map placement uses the existing four-column/five-row formula, rotation uses the existing remainder rule, movement is clamped to x 0–94 and y 0–88. Renames preserve unknown fields and do not rename archived receipt snapshots.

Booking overlap is strictly startA < endB and endA > startB across all dates, so touching intervals are permitted and cross-midnight conflicts are checked. Only status exactly cancelled frees an interval; other legacy statuses still block. Editing ignores every record with the same ID as reviewed, preserves status/createdAt/extensions and allows editing cancelled or orphan bookings. The reviewed create handler has no table-existence guard; no new guard is introduced. Editing cancelled bookings does not reactivate them. Guest name remains required; guests are at least one, with the UI stepper capped at 30 but direct saved input not newly capped. Invalid existing date intervals do not become conflicts. Native local dates preserve short-month normalization and DST gap/overlap behavior; end is elapsed minutes, not local clock addition.

## Presentation, compatibility and safeguards

Existing hall cards, modals, theme and typography remain. Only business commands and storage ownership are changed; complete native UI is 108. Drag coordinates remain temporary visual preview, then restore the persisted baseline before the native request. Failed drag restores original coordinates; table/booking state and success UI follow acknowledgement. Input is captured before storage initialization. Blank edited guest names now fail before mutating memory; the reviewed invalid edit accidentally changed memory without saving, and the native boundary avoids that non-durable side effect while retaining the required-name rule.

MPosNativeHallCommandsEnabled=false delegates to the reviewed handlers while authoritative storage stays Room. Reviewed source is untouched. No network, catalogue synchronization, stock changes or availability trigger. Non-finite coordinates/guest counts/duration fail as corrupt input rather than becoming SQL/JSON values. Backup imports remain compatible through owned storage.

## Verification

Source fixtures cover table number/shape/name/rotation, cascading booking deletion, cancellation, touching/conflicting intervals, cancelled/orphan edits, guest bounds, midnight, Moscow, DST gap/repeat, short-month normalization and invalid existing dates. JS checks acknowledgement before UI, immutable form capture, double tap/failure/uncertainty, blank edit, drag failure and rollback. Room tests compare every fixture, inject last-write failure for atomic deletion, refuse stale/journal/duplicate/conflict commands, retain archived receipts/extensions and verify null/absent/reopen. FIFO mirror tests verify ownership and obsolete shadow rejection. Physical acceptance pending. Actual v13 validator/import/restart also preserves nonempty hall documents and unknown fields despite stale browser caches.

Verified: 430 JS / 352 JVM tests passed, zero failures/errors/skips; lintDebug zero errors / 15 existing warnings. Source fixture checks caught and corrected create-booking validation ordering: missing guest name is checked before overlap, as reviewed. No local APK assembly.
