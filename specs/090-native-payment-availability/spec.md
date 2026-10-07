# 090 — Native availability calculation and payment-only publication

## Current policy and scope

Current AGENTS rule 7 is the authority: availability publication may run only after a successfully persisted payment. The explicit-array call after commit/state application in reviewed payment.js remains the only production compatibility trigger. Zero-argument stock/edit/return/receiving/manual-sync calls now return false, even after a successful publication. Startup, online and foreground never publish. This supersedes the older 033 allowance for ordinary stock/manual-sync sends; its failed-send retry rule remains unchanged. No catalogue sync is added.

Kotlin calculates items from authoritative Room products, including simple fractional stock, unlimited null, invalid/negative/non-finite values, recursive compositions, repeated ingredients, tolerance/flooring, typed/duplicate identifiers and malformed/cyclic recipes. Iterative expansion avoids a native recursive stack dependency. Source localeCompare sorting remains a thin adapter step; the native journal verifies a multiset of exact native item values before accepting that reordering. Financial/payment/stock-deduction rules and reviewed source files are unchanged.

## Durable gate, snapshot and HTTP

MPosAvailabilityJournal verifies the triggering receipt exists in the authoritative Room archive and has not claimed an attempt. In one transaction it calculates the current durable catalogue, gathers up to 100 settlement IDs (caller IDs then recent durable WEB receipts, including returned receipts as source does), assigns a safe monotonic revision, and stores a small per-receipt attempted marker plus one replaceable publication ticket. The revision incorporates the compatible durable revision and native revision; sampledAt retains millisecond ISO formatting. No catalogue snapshot is stored per receipt. A ticket carries the exact network configuration and body for the one current attempt.

The adapter reads durable network/revision and writes the compatible webAvailabilityRevision before sending, preserving rollback state. Preparation/write failure produces no HTTP and never undoes or blocks payment. Native consumption verifies token, receipt presence, unchanged revision/time/settlements/items (only item order may differ) and deletes the ticket before HTTP. A network failure, lost response, cancellation or process exit cannot consume it again. The next newly saved receipt grants a fresh attempt using current stock; no lifecycle reader discovers/replays old tickets. Consuming a permit is not proof of server delivery.

OkHttp posts only `/api/availability/snapshot`, using the configured HTTPS prefix, device header, no-store and the reviewed thirty-second deadline, with no underlying automatic retry. JS cancellation/watchdog closes the native call; native background/destruction also cancels active jobs/socket calls, including preparation-to-call cancellation races. Local work remains independent of this fire-and-forget request. Successful response clears sent settlement IDs in memory. Source coalescing is retained: payments during a successful send coalesce to latest pending receipt; a failed send drops pending triggers and waits for a payment after failure. Durable WEB receipts restore settlements on that later payment.

## Storage/compatibility and rollback

Room schema/version, existing products/orders/backup v13 shape are unchanged. Internal mpos_availability_* metadata is excluded from business backup; receipt markers are tiny and the ticket is constant-count rather than an archived snapshot outbox. Legacy mpos_availability_retry_blocked is set conservatively for older-runtime rollback, not used to authorize native sends. Existing webAvailabilityRevision is still written through compatible storage.

MPosNativeAvailabilityTransportEnabled=false rolls HTTP back to reviewed fetch with thirty-second abort, retaining native receipt gate/calculation/consumption and payment-only policy. It cannot restore old reconnect/stock triggers. Change transport mode with no active attempt. Removing this adapter entirely would change the accepted policy and is not the rollback path. No exactly-once server publication, immediate offline stock freshness, or physical acceptance is claimed.

## Verification

Actual reviewed productIngredients/availableStock/buildAvailabilityItems generate fixtures independently checked by JS and JVM. Room tests cover missing/duplicate receipt, fresh durable products, monotonic revisions/ISO/settlements, wrong tokens, modified payload rejection, one-time consume, atomic failed-write rollback and file-backed close/reopen with no repeat permission. HTTP tests verify route/prefix/header/deadline/no automatic retry. Adapter tests cover local-before-network, revision failure, next receipt, failure coalescing, cancellation/late result, lifecycle/manual no-op and transport rollback. Existing post-commit source-trigger tests remain enforced. Full suite evidence below; comprehensive physical cases remain pending.

## Business points to retain

After a failed send the WEB stock may stay stale until the next payment, as the user accepted. A payment that was already queued before the failure does not trigger an immediate retry; this follows the existing 033 coalescing policy. Settlements retain the reviewed maximum 100 IDs, so exceptionally old pending reservations beyond that window need a separate backend/business decision. Backend revision handling/idempotence remains necessary when a response is lost. Replaying transient requests/printing and loyalty recovery remain separate policies.

Verified: 379 JS and 302 JVM tests passed; zero failures/errors/skips, lint zero errors / 15 existing warnings. File-backed restart case executed. No local APK assembly; physical acceptance pending.
