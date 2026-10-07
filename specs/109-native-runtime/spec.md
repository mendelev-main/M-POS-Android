# 109 — Native runtime authority

Status: in_progress. Engineering completion remains 107/110.

## Scope and sequence

1. Read coherent Room documents without DOM, writes, initialization or invented defaults.
2. Replace JS session/bootstrap/navigation with native state and explicit domain authority checks.
3. Build screen models from domain repositories instead of mounted HTML.
4. Dispatch native actions directly to command repositories; preserve roles, recovery gates and persisted-before-effects ordering.
5. Replace post-commit orchestration for printing, loyalty, WEB and availability; availability retry only after the next saved payment, no automatic print retry.
6. Replace remaining settings/authentication, import/export and lifecycle handlers without changing backup v13 or exposing credentials.
7. Remove active WebView, JavascriptInterface and DOM adapters only after all preceding boundaries pass parity tests.

## Current increment

MPosRuntimeSnapshot reads an explicitly requested set of raw Room documents in one transaction. Missing documents stay absent; unknown fields and original bytes are preserved. It does not initialize authority or repair corrupt JSON. It is now used by the production native session restore repository; full JS runtime replacement is still pending.

## Acceptance

Automated: exact payload retention, missing versus stored JSON null, read-only behavior, invalid key rejection and multi-document retrieval. Concurrent atomic multi-document writers/readers are covered by a Room test. Subsequent native bootstrap must additionally verify per-domain authority and critical recovery journals before allowing mutations.

Physical acceptance stays in 110: offline startup, employee/shift restore, payment and refund, restart, import/export, all screens and performance. Do not mark 109 done while a hidden WebView still constructs models or dispatches actions.

## Verification of bootstrap reader increment

525/525 JS tests and 415/415 JVM tests passed (no failures/errors/skips); lint: 0 errors, 15 existing warnings. No APK assembly and no physical tablet acceptance. At that first increment (2d89c02), the reader was not connected to production startup; the following increment connects it for current-order restoration.


## Session restoration authority increment

The current-order load block now awaits MPosSessionRestoreEngine via the storage queue. Kotlin projects cart record filtering, customer defaults/extensions, delivery, labels/comments, WEB identity, loyalty metadata and prior kitchen-print marks. It is read-only and has no print/network/payment trigger. The existing split-draft validator runs afterward with the restored cart total, preserving paid parts and invalid-draft warnings.

Rollback: MPosNativeSessionRestoreEnabled=false or a failed/unsupported native read selects the byte-preserved reviewed restoration block. Unusual legacy coercions (non-finite fees, non-string customer identity) intentionally retain that path. No Room schema, v13 shape, authority initialization or write semantics change. The source hash check strips only the exact reviewed native hook and still verifies all other original HTML bytes.

Navigation and full bootstrap recovery remain pending. This increment removes session field projection from active JS authority for supported saved sessions; it does not remove WebView or JS lifecycle orchestration.


The production session read now uses MPosSessionRestoreRepository: it checks existing current-session authority and reads MPosRuntimeSnapshot within one transaction. A missing or changed document rejects native projection and retains the reviewed compatibility path; no authority is created implicitly. Tests cover owned/unowned documents, newer/removal conflicts, preservation of stored paid metadata and concurrent paired snapshot generations. Critical journal replay and navigation remain existing runtime responsibilities.


## Internal checklist (does not add engineering task IDs)

Detailed stable checklist: [109.01–109.20](tasks.md). Completed **5/20 (25.00%)**, remaining 15. Expanded whole-program progress **112/129 (86.82%)**; milestone progress **107/110 (97.27%)**. Parent 109 remains in progress until all children pass their acceptance criteria.

## Verification of production session increment

530/530 JS tests, 422/422 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. Shared fixtures exercise the actual reviewed restore block and Kotlin projection; paid split restart, invalid draft warnings, bridge rollback, owned Room data/conflicts and concurrent multi-document snapshots are covered. No local APK assembly; physical acceptance remains pending in 110.


## Workspace route authority increment

MPosWorkspaceRouteEngine now decides open category, open normalized folder, Back precedence and edit-mode toggles. It reuses the reviewed Kotlin folder normalization from 082. Production native-navigation routes these calls through the existing storage queue; native workspace captures category/folder/edit promises before releasing its action lock. No cart, payment, shift, stock or stored navigation writes occur.

Back preserves exact precedence: close the modal folder first; otherwise clear a legacy inline folder without leaving its category/edit mode; otherwise return to the root and reset search/edit mode. Folder opening does not invent IDs or permissions. Edit-mode entry retains the existing delayed drag initialization. Search filtering, tab navigation and rendering remain JS responsibilities for now.

View requests are FIFO. Each reply is checked against the current route, tab/payment page, query, folder modal, mounted modal identity and navigation data. Stale replies cannot override a newer view. A failed/unsupported pure read falls back to the reviewed function only if the view is still current. MPosNativeWorkspaceRouteEnabled=false provides independent rollback without changing native persisted navigation authority.

Shared fixtures compare actual reviewed navigation functions and the Kotlin transition engine. Integration tests cover delayed/stale replies, order-data retention, malformed replies, FIFO category/Back and explicit rollback. This is another part of 109, not an additional completed task or removal of WebView.

## Verification of workspace route increment

536/536 JS tests and 425/425 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. No APK assembly; physical route/keyboard/drag checks are documented for 110. Overall engineering completion remains 107/110.


## Paired active-session bootstrap increment

After the existing critical-journal recovery completes, loadAll uses MPosActiveSession.bootstrap. It initializes the existing shift/employee authority boundaries through their established one-time migration, then MPosActiveSessionRepository reads both owned documents in one Room transaction using MPosRuntimeSnapshot. Neither the repository nor projection writes or repairs data. Bridge/read failure or MPosNativeActiveSessionEnabled=false retains the original per-key reads.

MPosActiveSessionEngine preserves missing versus stored null, record filtering/warnings, unknown employee fields and exact admin-role normalization. It prepares first-open-shift and first-matching-employee indices using reviewed strict scalar ID equality (missing differs from null; object IDs do not match by JSON equality). Existing synchronous currentShift/currentShiftEmployeeIsAdmin remain active; prepared indices are a native bootstrap model for later native handlers, not an alternative cached authorization authority. No permission policy changes or role-cache serialization per UI action.

The production loadAll prefix and shared fixtures are exercised with both native and rollback paths. Repository tests cover missing/unowned authority, exact raw document retention, null warnings and concurrent paired generations. Source hash checks still verify original HTML after removing only exact reviewed native hooks. No automatic printing, payment, catalogue sync or availability action is added.

Internal progress: paired shift/employee normalization is active; complete native root session/authorization/navigation lifecycle and WebView removal remain pending.

## Verification of paired bootstrap increment

541/541 JS tests, 430/430 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. No local APK assembly; tablet acceptance is pending in 110. Completed tasks remain 107/110.

## Native shift opening authority increment

Default production opening uses MPosShiftOpenCommand through a native-only
MPosStorageMirror.openShift entry on the existing bounded FIFO. The native dialog
retains the transient credential while JS reserves criticalOperationBusy and
returns its expected employees/shifts plus the existing UID/time. Kotlin checks
both owned documents in one transaction, verifies the selected live admin using
the exact reviewed credential policy, obtains carryover/recovery gates through
MPosShiftOpeningRepository and commits via MPosShiftLifecycleCommand. The
credential is a separate transient argument: it is never serialized into the
command, lifecycle hash/marker, backup, result or diagnostics. Credential policy
is unchanged; using a native digest is not a new authentication/security policy.

The transaction retains the full historical documents/extensions and cart,
rechecks employee/shift state and rejects concurrent changes, pending critical
journals, invalid carryover, existing open shift and projection/marker failures.
No print/network/availability/catalogue trigger runs in the command. Saved state
survives process restart; automatic retry is absent. A repeated gesture is locked
out; an already saved/stale opening must reload instead of creating another shift.

The compatibility adapter no longer invokes submitOpenShift or touches its DOM
password field in native-command mode. After a correlated successful result,
it verifies live expected state and result identity/history, applies the saved
shift, closes/renders and retains existing Telegram/monthly actions. Wrong
credentials allow correction; stale/uncertain outcomes block critical work.
Close or showModal cannot abandon an in-flight native operation. Ambiguous
dispatch failure is blocked; explicit non-dispatch rejection can retry safely.
The active credential is cleared after native dispatch/cancel/result/destruction.

Rollback before submission: MPosNativeShiftOpenCommandEnabled=false retains the
existing native form + reviewed JS verification/lifecycle path; form fallback
retains reviewed HTML. No schema/storage keys/v13 formats change. Existing
legacy password bytes are not duplicated in new code or fixtures. Other role,
employee-edit/delete, company/network authentication and full lifecycle/effect
authority remain pending. The hidden HTML form is still mounted; this is not
WebView removal or completed root authentication.

Automated scope: actual reviewed submitOpenShift fixtures (credential categories
only), exact/case/whitespace authentication, first/tied/missing carryover, normal
and admin staff; Room restart/stale data/pending journal/real SQLite rollback and
credential non-persistence; UI native/legacy mode, native-only credential handoff,
correlated/stale results; adapter ack ordering, duplicate locks, modal replacement,
wrong password, state conflict, dispatch ambiguity and external-effect failure.
Physical cases remain pending in 110.

Remaining work and milestone counting: [Russian runtime analysis](../../docs/NATIVE_RUNTIME_REMAINING_RU.md).

## Verification of native opening increment

551/551 JS tests and 438/438 JVM tests passed; failures/errors/skips: 0.
Android lint: 0 errors, 22 warnings in unchanged files (15 existing style/platform
warnings plus 7 dependency-version advisories from the online check). Full
testDebugUnitTest and lintDebug passed after the final callback-delivery guard.
The real SQLite/FIFO tests include an acknowledged database commit with failed
result delivery: saved shift remains, response requires reload, no resubmission.

Local JS validation used Node 24 with TZ=UTC and an available python command
for the existing source-sync fixture. Robolectric used a writable test-only
user.home outside the repository; no product APK assembled and no physical
tablet acceptance. The original reviewed source bytes/hash still pass.
Engineering completion remains **107/110 (97.27%)**; no task IDs or denominator
were added for internal 109 increments.


## 109.06 — fresh native root session context (in progress)

MPosRootSessionRepository reads owned shifts, employees and critical journal together in one Room transaction. It exposes native currentShift, selectedEmployee, isAdmin and recoveryPending plus raw compatible arrays for commands. It does not cache across role edits, database replacement or restart, initialize documents, replay a journal or emit effects. View records are detached copies; original stored extensions/roles remain unchanged.

Production ActiveSession.bootstrap now initializes established domain boundaries and requests rootSessionBootstrap after existing recovery. The paired compatibility bootstrap API remains for rollback/compatibility. Native opening form metadata and MPosShiftOpenCommand use the same root context; existing password policy, expected document conflicts, carryover validation and lifecycle transaction still apply. Opening checks recovery before mutation; reporting isAdmin does not authorize a blocked critical operation.

Tests cover root restart, owned record replacement/role changes, missing authority, detached records, pending journal without replay and concurrent three-document coherence. Existing opening parity and real SQLite tests remain required. Complete root startup ownership, synchronous JS currentShift/role helper replacement and import/lifecycle orchestration remain pending; 109.06 is not complete.

Opening carryover metadata reuses the command's existing root transaction snapshot; there is no second root document read/parse within that command. No tablet performance claim is made.

## Verification of 109.06 root-context increment

551/551 JS tests and 442/442 JVM tests passed after final snapshot reuse; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings in this environment. No APK assembly or physical acceptance. 109.06 remains in_progress; counters: 5/20 within 109, 112/129 expanded, 107/110 major milestones.
