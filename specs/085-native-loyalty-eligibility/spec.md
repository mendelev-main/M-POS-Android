# 085 — Native online gift eligibility and existing offline policy

## Scope

Native OkHttp GET of the central customer loyalty profile and Kotlin eligibility comparison replace the reviewed profile fetch and selected-gift revalidation. All profile reads, including 084 profile refresh, use the native transport. Search/create/admin adjustments and sale/reversal POST routes remain on their existing paths; mutations/retries are 086. The synchronous UI hint `hasSelectedLoyaltyReward` and the existing payment/offline dialog remain reviewed handlers; final async eligibility decision belongs to Kotlin, usable by native UI 105/103. No new local gift cache, reservations or offline gift entitlement.

## Transport

HTTPS configured backend prefix, encoded customer path, JSON content header and device-key header; runtime credentials are neither persisted by this transport nor logged. Read-only correlated callbacks, 5-second call/connect/read timeout, 6-second bridge watchdog, explicit abort/native cancel, no automatic retry. Call cancellation on Activity close. Strict JSON validation rejects permissive JSONTokener extensions; invalid body becomes null as reviewed fetch JSON catch. HTTP errors preserve server error/fallback HTTP status. Existing diagnostic SSE routes remain separate and unchanged. Surrogate-safe JSON serializer also protects new native network responses. Only the exact profile route with default options/optional abort signal is intercepted; other routes/options delegate. Rollback: MPosNativeLoyaltyGuardEnabled=false restores original API/guard.

## Eligibility and parity

Requested quantities use max(0, trunc(Number(value) || 0)); program IDs use JS string conversion, duplicate program IDs keep the last record. Compare requested > Number(rewards || 0), preserving scalar/array coercion, numeric strings, fractions, missing programs and source NaN semantics. Selected programs replace state only after a valid correlated native result and unchanged order/customer/redemptions/program identity/modal generation. Late results refuse further payment continuation. Native model failure/timeout never grants a gift or marks local storage uncertain.

The reviewed offline dialog remains: no connection offers cancel or explicit continue without gift. Continue clears redemption and then invokes the original payment action; cancellation does not pay. Insufficient confirmed rewards refuses payment. Sales without selected gifts bypass online validation. No catalogue/availability publication is added, and payment/receipt Room persistence remains unchanged.

## Preserved business questions

The server remains authoritative; revalidation is not a reservation and another till may consume a gift before sale publication. Non-numeric truthy server `rewards` yields NaN, and the original `requested > NaN` comparison does not refuse the gift. This stage preserves that exact comparison rather than silently changing policy. A strict server schema/atomic reservation needs separate backend/business review. Missing customer with selected redemption follows the original early success path. The bridge response/transport validity still must pass.

## Verification

123 source-derived eligibility fixtures include coercion, missing/duplicate/numeric IDs and whitespace; JVM parity/input immutability. HTTP tests exercise headers/path prefix/encoding, bounded no-retry configuration, strict malformed JSON, success/error and actual injected OkHttp calls without production requests. JS tests cover read/model/ack order, insufficient rewards, timeout/cancel/late responses, changed context, abort, authority, exact-route delegation/rollback and original offline continue. Full JS/JVM/lint required. Physical acceptance pending: online available/unavailable gifts, offline cancel/continue, timeout, rapid customer change, reconnect with no automatic gift/payment work, completed sale/return and restart. 086 is next.

Evidence: 343 JS / 271 JVM passed, lint 0 errors / 15 existing warnings. No APK assembly.
