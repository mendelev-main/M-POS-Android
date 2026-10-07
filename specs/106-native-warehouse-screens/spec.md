# 106 — нативные склад, закупки, приёмка и инвентаризация

Scope: native warehouse reporting/date/section/export presentation; supplier forms,
purchase order/history/quantity/detail/share, receiving standalone/ordered forms,
draft save/resume/confirmation/history and inventory settings/work/summary/cancel.
Existing 091–096 Kotlin storage/command/report authorities stay unchanged.
All source permissions, units/packing, stock/cost formulas, receipt history and
backup v13 shapes remain. No extra catalogue/availability or print retry trigger.
DOM runtime compatibility remains 109; physical acceptance 110 pending.

Shared MPosNativeTheme/Manrope, readable document cards/metrics, date picker,
opaque mounted actions and stable live fields. Warehouse tables bind visible
native rows rather than one native view per archived table cell. Save/confirm
wait for known native/source promises; failed/uncertain result retains document
and blocks unsafe resubmit. Receiving Back invokes reviewed draft save.

Source policies preserved: supplier create/edit unrestricted, delete admin open
shift; empty supplier bindings allow previously filled purchase cart (user
confirmed); receiving shortage closes order without automatic remainder order;
fix inventory against current stock, completion preserves later sales.

Business ambiguity already documented in 095: fixing 10 l to 8 l immediately
changes stock; subsequent inventory cancellation removes draft but does not
restore 10 l. Original cancellation text contradicts this. Retain behavior/text
and record for a separate policy/text clarification; never overwrite later sales.

## Implementation and verification

Shared Kotlin date fields, three-column purchase keypad, themed document cards
and recycled ListView table rows are bound to reviewed mounted actions. Page
patches now update inline receiving as well as modal forms, retaining focus,
cursor, dirty draft values and scroll. This is a presentation cutover; source
DOM/model generation remains until 109.

Eight new JS scenarios cover keypad/stock preview, acknowledged purchase save,
duplicate/failed commit, supplier rights and hidden selected IDs, simultaneous
quantity/total flush before unit changes, acknowledged draft Back/failure/retry,
inventory fixation/completion preserving later sales, warehouse table/date/export
parity and stale/rollback actions. Five Android view tests cover DatePicker ISO
confirmation/clear/disabled state, 10,000-row bounded view count and adapter recycling,
restored position, receiving deferred Back/failure/retry, inline field patch/focus
and synthetic light/dark compositions. Android scroll gesture performance itself
remains a physical acceptance case; Robolectric is not device evidence.

Rollback: MPosNativeWarehouseUiEnabled=false restores source presentation; it
does not undo acknowledged operations or native storage/commands. No backup v13,
stock/cost/rights changes, extra availability publication or automatic print retry.

512 JS / 406 JVM tests passed, no failures/errors/skips. Lint: 0 errors / 15 existing warnings. No local APK assembly.
Synthetic previews are in docs/design/106; physical acceptance 110 remains pending.

Status: complete. After main publication: 105/110 engineering tasks (95.45%),
5 remaining. This is not native feature coverage or physical acceptance.
Next 107 — native analytics presentation.
