# 104 — нативная история чеков, детали и возвраты

Scope: Kotlin presentation of paged receipt history, selection/detail, receipt modal, full return confirmation and result. Existing Room receipt paging and native return authority remain; no financial/data schema changes. Shared MPosNativeTheme/Manrope, responsive columns, independent history/detail scrolling and wrapping actions. Physical acceptance deferred to 110; DOM/runtime removal remains 109.

Preserve source: full returns only; open shift required; duplicate returns refused; cash component needs sufficient drawer balance; saved stockConsumption restores historical ingredients, legacy receipts keep existing fallback. Return belongs to current shift without rewriting sale shift. Card refund is performed on the bank terminal separately. Local commit must complete before state/network effects. Availability remains payment-only via existing gate; no automatic print retry. Backup v13 unchanged.

Opaque mounted node/token actions invoke reviewed handlers; no executable snippets. Async return stays locked until acknowledgement; failure/uncertain result stays visible and uncertain recovery blocks resubmission. Pagination remains 50 rows with existing loading/error/retry semantics, no new archive scan or network request. Rollback flag restores reviewed UI without changing native storage authority.

Status: implementation complete; automated verification recorded below.

## Реализация

Existing settings screen bridge/controller render the approved receipt surface.
The adapter preserves opaque mounted node identities and formats from reviewed
receipt renderers, including line discounts, modifiers/comments, customer,
delivery, payments/change and returned status. Native columns use actual surface
bounds, stack below 760dp, and keep independent page/detail scroll positions in a
bounded 32-entry memory cache. Full-width history rows use shared selected state.
Known return promises keep the controls locked across replacements; source
messages remain visible in page/modal. Recovery blocks resubmission. No new
storage/network authority, dependencies, backup keys or sync triggers.

`MPosNativeReceiptUiEnabled=false` restores reviewed receipt presentation;
`MPosNativeSettingsUiEnabled=false` disables the shared presentation adapter.
Neither flag reverses native Room receipt/return authority. Both production and
asset-sync adapter orderings retain original 50-row paging and button identity.

## Проверки

Actual reviewed receipts/payment-detail renderers and processFullReturn execute
against fictional receipts with controlled local commit. Coverage: page loading,
error/retry/empty, selection/modifiers/customer/delivery/manual printing, duplicate
gesture, stale token, cancel/rollback, closed shift/insufficient cash/duplicate
return, failed/uncertain commit, historical consumption and current-shift cash,
cash/card/split cash-component distinction and bank-terminal notice. Existing
native return/history/accounting suites remain. Kotlin views cover responsive
columns, independent scroll restoration and duplicate action locking. Synthetic
light/dark previews inspected, physical acceptance pending.

## Итоговая проверка

491 JS tests passed; full JVM 399 passed, 0 failures/errors/skipped. After final presentation polish: targeted UI 17/17 and lint 0 errors / 15 existing warnings. Full JVM run precedes the final targeted UI run; the targeted run is not a second full suite. No local product APK; physical acceptance pending. After main publication: 103/110 engineering tasks (93.64%), 7 remaining; this is not native feature coverage. Next 105.
