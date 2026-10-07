# 102 — нативный редактор товара, рецепта и модификаторов

Scope: Kotlin presentation of the full product editor and its contextual pickers/exit confirmations, reusing shared native form primitives and reviewed mounted actions. Dynamic fields/actions/text must refresh without dropping drafts, focus or scroll. Native catalogue/type/recipe/modifier validation from 080/081 remains authoritative. Units, inferred yield, source configuration helpers, permissions/private auth, photo lifecycle and v13 JSON remain compatibility runtime until 109; no independent financial calculations or new sync/availability trigger.

Business changes: none. Source admin-only stock/online access, readonly existing cost, linked-product type restriction, recipe error precedence, modifier normalization and dirty-exit choices remain. Native actions address mounted nodes using opaque tokens; no code evaluation or verifier copy. Saving locks duplicate gestures and awaits known async commands before releasing controls; uncertain storage remains blocked. Rollback restores reviewed editor presentation without changing persisted authority.

Automated validation is recorded below; physical acceptance remains pending. No local product APK; tablet acceptance is deferred to 110.

## Реализация

`native-settings-ui.js` recognizes the standalone `product-editor-root` and contextual modals. It reads the mounted reviewed presentation (including section visibility, disabled/readonly fields, labels and formatted monetary summary). Kotlin renders expanded product forms, contextual search/pickers, recipe/component cards, modifier options and online switches using shared Manrope palettes. Header save/back, delete, photo pick/remove, usage navigation and dirty-exit choices invoke their original handlers, not evaluated snippets.

Product field/action keys are monotonic WeakMap identities, scoped to a form token. Removed/replaced actions do not inherit an old index. Before a gesture the adapter assigns the whole field draft, then dispatches source input/change events: conversion/re-rendering cannot discard a sibling value that has not yet been assigned. Product hidden panels do not expose actions. Live search/name/numeric updates are coalesced in Kotlin; intermediate empty/negative-prefix/trailing-separator numeric input remains a draft until completed or flushed by a gesture. Native field limits retain reviewed maxlength values.

`formPatch` updates the same dialog, preserves dirty EditText instances/focus/cursor and scroll, wipes removed fields, updates converted values/options/actions/summary. Native selected section uses the shared soft-accent button state. Source photo preview is sampled in a cancellable background worker; local images/data URLs and legacy HTTP(S) image URLs are supported. The same source retains its ImageView across patches. Fresh native picker images use their existing local file ID rather than repeatedly sending JPEG data URLs through the bridge. Remote image reads have a deadline, bounded response and no transport retry; failure only affects preview, not product save. The 8 MB thumbnail input bound is independent of the unchanged 500,000,000-byte backup import limit. Media selection/save/upload remains the existing native photo/business path; preview never writes media/catalogue or starts synchronization.

Known returned async save commands are awaited. Source `_pmSaving` also locks a newly shown editor when the dirty-exit modal closes during an outstanding save. Feedback and uncertain-storage blocking follow that context; duplicate/forged/stale callbacks cannot bypass the saving state. Cancel includes unsent native fields before invoking reviewed back/dirty-exit. Private administrator checks are executed only by mounted existing handlers and are never copied into Kotlin or this specification.

## Границы

This is native presentation with an explicit compatibility controller, not removal of WebView. Reviewed configuration/unit/yield helpers, synchronous deletion/saveKey semantics, auth, mounted DOM and photo workflow remain until 109. 080/081 retain native validation ownership; no new stock, monetary, modifier normalization, rights, synchronization or availability policy is introduced. Rollback `MPosNativeProductEditorEnabled=false` restores reviewed product/contextual modal presentation; disabling all settings presentation still restores its DOM. Neither rollback changes Room/preferences/v13 authority. No performance/physical acceptance claim.

## Проверки

Actual reviewed editor/configuration renderers and inline mounted events are executed against a DOM fixture: drafts and unit conversion, readonly/admin gates, ingredients/search/add/qty/remove/inferred yield, groups/options/price/qty/product picker, stale actions, async save/duplicate rejection, dirty exit/stay/discard, cross-modal saving lock/recovery feedback, invalid ingredient quantity, online switch and rollback. Existing 080/081 business fixtures remain.

Native view tests cover stable dialog/field/focus/cursor, removed drafts, action replacement, acknowledged converted values, unsent cancel, pending saving state and fractional-input pauses. Thumbnail tests cover bounds/sample/invalid data/cancellation/local-media immutability. Shared light/dark synthetic previews are separate from physical acceptance. Full JS/JVM/lint result is recorded after final validation; no product APK assembled.

Physical pending: simple/composite create/edit/reopen; kg/g/l/ml conversion, readonly cost/admin stock/online rights; ingredient search/cycle/missing/zero/units/yield; modifier names/min/max/product/qty/delta; photo pick/remove/local/URL/offline/interrupted picker; dirty exit/stay/discard/save-and-continue; repeated save/uncertain persistence/restart; light/dark/landscape/keyboard/large fonts; full v13 import/export; no new catalogue/availability trigger.

Итоговая автоматическая проверка 102: **474 JS / 393 JVM**, 0 failures/errors/skipped;
**lint: 0 errors / 15 existing warnings**. Product APK не собирался.
Native light/dark previews inspected with synthetic data; physical acceptance pending.
Engineering progress after publication: **101/110 (91.82%)**, 9 remaining.
This ratio is not native feature coverage. Next 103: main payment UI.

Publication pending: local implementation commit `6f9c31d`; GitHub rejected main updates with Internal Server Error (read access and local object integrity verified). 102 remains in_progress until successful publication; current counted progress is 100/110 (90.91%). No force push was attempted.
