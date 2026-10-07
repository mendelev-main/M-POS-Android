# 107 — нативные экраны аналитики

Scope: native sales analytics presentation, period/date controls and reviewed
period modal, presets, admin-only financial/loyalty KPIs, employee/payment/category
and top-product bars, loading/error/retry and empty results.

097 remains the sole native calculation/read authority. Source formatting,
period semantics, role visibility and central loyalty read/key/error rules stay.
No stock, receipts, refunds, payments, permissions or backup v13 changes; no
catalogue sync, availability publication or new network trigger.

Shared MPosNativeTheme/Manrope and both palettes; responsive metric/chart cards,
Android dates, recycled chart rows, readable values and wrapped actions. Hidden
non-admin employee amounts must not appear in the native model/accessibility.
Native date editing must defer asynchronous screen replacement until the picker
closes, without applying a stale period result. Preserve focus/scroll and scoped
loading/retry. Presentation rollback does not disable 097 Room authority.

Known business policies retained: truthy returnedAt removes a sale from its
original period, with no refund-day aggregate; period bounds/defaults match 097.
Source setAnalyticsDate updates the selected state before reversed-range warning,
and returns without rendering. This existing validation-state ambiguity is
explicitly retained by the user (7 October 2026); do not change it in UI migration.

DOM generation/mounted handlers remain compatible until 109; physical acceptance
110 remains pending. Engineering task ratio is not native feature coverage.
## Implementation / verification

Shared Kotlin responsive grid, metric cards and MPosSettingsBars recycled native
rows cover all reviewed charts. Native DatePicker open/close is scoped to the
mounted token; 097 presentation defers replacement and rechecks latest period
when released. Removed/replaced pickers close and cannot change a later screen.
No finance/calculation/storage/permission/business-policy changes.

Seven actual-renderer JS UI scenarios cover native/admin data, hidden cashier
amounts/percentages, top ten/order, period modal validation/apply, preset/empty
and user-confirmed reversed inline state, date-edit deferral and stale release,
read error/retry/tab/stale action and presentation-only rollback. Three Android
view scenarios verify 10,000-row bounded view count/reuse/restoration/accessibility,
picker open/change/dismiss order and replacement, responsive grids and both themes.
Synthetic palettes inspected; these are not physical acceptance evidence.

Verified: 519 JS / 409 full JVM tests passed, 0 failures/errors/skips; lint 0 errors
/ 15 existing warnings. No local APK. MPosNativeAnalyticsUiEnabled=false restores
reviewed presentation without disabling native calculations; DOM removal 109 and
physical acceptance 110 pending. Publication: 106/110 engineering tasks (96.36%),
4 remaining, not native coverage. Next 108 — hall/tables/bookings.
Status: complete.
