# 101 — Native POS workspace

## Approved presentation boundary

Kotlin renders the normal-operation workspace: category/product/folder tiles, original root grid positions, toolbar, native folder browsing, current order metadata/lines/totals/actions and removal gesture. Shared MPosNativeTheme/Manrope, light/dark palettes, 48 dp touch targets, responsive catalogue/cart panels and scroll retention. Source financial/availability/navigation/order commands remain the existing approved Kotlin boundaries and reviewed compatibility handlers; no monetary formula is copied into the renderer. Values are read from the currently mounted reviewed presentation, including matching native totals preview updates.

The original DOM remains mounted as a compatibility/action registry until 109. Native callbacks contain generation-scoped keys, never JavaScript snippets. Detached/disabled nodes, wrong tabs, modal interference, edit mode, inactive document and busy/uncertain storage are rejected. Actions delegate to original mounted buttons or known reviewed tile/cart handlers. Cart removal uses the same reviewed removal function and cart-line identity, not product ID. There is no network request, automatic catalogue sync, availability publication, print trigger or storage owner change introduced by presentation.

Editing layout/dragging, folder configuration and the remaining modifier/manual-price/customer/cart-options/payment/parked modals explicitly retain reviewed presentation. Native overlay hides for these boundaries; layout editing remains functional, and completing it restores normal native presentation. These compatibility surfaces must be removed/resolved before 109; completion of 101 is not full native feature coverage. Independent product editor/payment/parked UI are 102/103/105. `MPosNativeWorkspaceEnabled=false` restores the original normal workspace without changing authority or data.

## Models/lifecycle

The adapter observes only the mounted workspace/modal boundaries, coalesces mutations into one animation frame, hides for other tabs and exports only visible workspace data. A stable signature ignores unchanged mutations. A generation changes whenever the mounted action registry changes, so old callbacks cannot act on a new cart/product context. Folder contents use the existing source folder modal while displaying a native grid; other modals retain their own owners. Original DOM styles are restored when native rendering hides or fails. No verifier/password constants are read/copied.

Kotlin preserves workspace scroll for in-place totals/cart updates and resets catalogue scroll when category/folder context changes. Financial numbers arrive as reviewed formatted strings; Kotlin does not round/reprice/recount. Blank/manual-priced, composite/modifier, unavailable and no-tracking products retain their original handlers and inventory guards. Root tile coordinates/spans remain sourced from the reviewed CSS grid; category/folder grids retain source order.

## Verification

JS action/model/lifecycle tests and existing native business parity suites; JVM real view/geometry/grid/busy/stale/action tests and light/dark synthetic previews; full JS/JVM/lint without local product APK assembly. Physical HONOR tablet, gesture, font scale/keyboard and printer acceptance remain pending. Task denominator remains 110, engineering progress is distinct from WebView removal and physical acceptance.

## Implemented boundary

`native-workspace.js` emits only a visible normal-operation snapshot, coalesces mutations and compares both presentation and mounted action identities. A template/geometry failure restores source presentation. It hides for payment pages, other modals, layout editing and other page overlays; closing these restores the native workspace. Original toolbar operations, manual price/modifiers, category/folder handlers and command promises remain intact. No source HTML/feature-module changes or new network/storage triggers. The original runtime remains required until its explicit removal scope.

`MPosWorkspaceController` displays formatted strings without money arithmetic, source grid coordinates/spans, normal/folder tiles, metadata, compact current-cart rows and accessible removal. Left swipe uses the same removal key and never also invokes the editor; vertical movement does not arm removal. Wide/narrow layouts and both shared palettes use native views, scroll restoration and recovery locks; narrow cart content scrolls to its actions. Declared folder/layout editing and modal compatibility limits remain intentional, not removed functionality. No measured performance/battery or physical-acceptance claim.

Verification: 463 JS tests and full 384 JVM tests passed, 0 failed/skipped; final workspace-specific JVM checks repeated after the recovery-caption adjustment. Lint and publication are recorded after final completion below. Synthetic native light/dark previews inspected; tablet/printer acceptance remains pending. No local product APK assembled.

Final checks: full JS 463/463, full JVM 384/384, final workspace rerun 5/5; 0 failed/skipped. Lint 0 errors / 15 existing warnings. Previews are synthetic; physical acceptance pending.
