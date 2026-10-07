# 087 — Authoritative WEB journals, local gates and native ACK

## Scope

Kotlin/Room owns webOrderAcceptances and webOrderReadyJournal with separate authority markers, exact object/null/absence semantics and atomic document/projection writes. Initial ownership imports current reviewed storage once, not an old mirror. Obsolete generic shadow put/remove is ignored after cutover. No Room schema, existing key or backup v13 shape change. Acceptance remains included in v13; ready/internal ACK markers remain existing local recovery metadata, not new backup sections.

Kotlin commands enforce local acceptance/ready gates, record successful ACK evidence and merge reviewed journal changes without overwriting unrelated concurrent records. OkHttp sends only the exact accept/ready POST routes, encoded IDs, configured HTTPS prefix and device header. Existing acceptance building/stock check/kitchen printing/legacy-estimate prompt/event cleanup and recovery orchestration stay reviewed handlers; this is a journal/ACK boundary, not all WEB business mapping or native UI. WEB event/SSE authority is 088 and printing triggers 099.

## Protocol

Acceptance prepared cannot ACK. Local must have its parked record durably present in authoritative Room and a valid ready estimate matching the request. Confirmed is already acknowledged. Ready prepared cannot ACK; only durable pending records are eligible. Once pending is established, ACK remains permitted even after the current session moves to another order, as reviewed recovery requires.

A gate records a local correlation token and exact journal-record snapshot before HTTP. After successful HTTP, an ack command verifies the token and unchanged record and records evidence. The reviewed handler then persists confirmed through a native patch, which requires that evidence and consumes it. HTTP success followed by a failed local write leaves a recoverable local/pending record; backend idempotent endpoints may be retried only on existing recovery triggers. Lost responses are not proof that the server rejected the request. No local/server distributed transaction or exactly-once printing is claimed.

Compatibility facade tracks snapshots for documents actually read by reviewed handlers. Patches compute changed record IDs, compare each changed ID with its prior value and merge into the live native document. Unrelated records survive concurrent writers; same-record conflicts fail safely. Ready deletion is cleanup-only for a durably confirmed native record. Acceptance event cleanup rereads Room, ignoring in-memory confirmation left by a failed write. This fixes the reviewed handler failure window in accordance with the existing durable-confirmation contract. Full import/new document writes use compatible native replacement. Printing preparation can update parked metadata in the same confirmed write as source does, including when kitchen printing is disabled; no new formatting/printing policy is imposed.

Native markReady uses one transaction for currentOrderSession ready and the pending journal intent, removing the prior crash gap between separate saves. It checks the expected Room session and WEB identity; the reviewed current-session snapshot remains the candidate, retaining normal state/default/backup normalization semantics. UI applies ready only after durable acknowledgement. Network runs after releasing the context queue, so a failed ACK does not hold local POS editing/payment. If the order changed during an uncertain durable write, restart recovery is required. Prepared leftovers from old code remain unsent until reviewed manual ready action; no automatic new promotion policy.

Unreadable WEB journals propagate rather than becoming empty recovery documents, including through loadKey. Unknown local business commits (patch/markReady) require recovery; uncertain proof-only gate/ack never blocks unrelated payments. ACK HTTP calls have a 30-second native bound and 31-second watchdog; source 5/30-second abort signals remain honored, and cancellation closes the native call. The formerly unbounded ready fetch now has a bounded failure retaining pending. No transport automatic retry, catalogue sync or availability trigger is added. Existing startup/online/operational recovery triggers remain unchanged.

## Rollback and remaining runtime

MPosNativeWebJournalEnabled=false restores reviewed command/fetch/markReady behavior for new work. Native persistence ownership remains; never fall back to stale compatibility caches. Change the business flag only with no active WEB attempt. Source UI/printing/events/SSE and recovery scheduling still exist; 087 does not remove WebView or complete 088/099/105. Secondary caches are compatibility data, not authority.

## Verification

Real Room tests cover both-document migration/null/absence/projections, prepared/local/estimate gates, evidence before confirmed, conflicting/unrelated patches, atomic ready/session commit and rollback, pending ACK after session advances, stale tokens, native FIFO stale-shadow rejection and file-backed reopen. HTTP request tests verify exact encoded POST routes, headers and bounded no-retry policy. Actual-source JS confirm/accept with printing absent, durable-before-state/network, failure/abort, frozen facade/import, read failure without empty fallback, and rollback/delegation; bridge ownership/read propagation. Full JS/JVM/lint required. Physical acceptance remains pending.

## Preserved questions

Kitchen printing still precedes backend acceptance as source requires; a crash after physical print before saving its printed snapshot can still duplicate a print on retry. Solving external print acknowledgement belongs to 099 and cannot be guaranteed by Room alone. Prepared ready intent alone never sends an ACK. WEB ready retry may occur after the order moved away from the current session. These policies are preserved, not silently generalized to payment or availability retry.

Tablet cases: accept online/offline/timeout with valid estimates; crash at prepared/local/ACK/confirmed; no duplicate parked order, legacy missing-estimate prompt; kitchen print enabled/disabled; mark ready and restart/reconnect before/after session moves; storage/read failure; concurrent unrelated WEB journals; v13 import/export/reopen. Verify stock checks, paid split metadata, current session and event cleanup. Next boundary 088: native SSE delivery/recovery.

Evidence: 364 JS / 287 JVM passed; lint 0 errors / 15 existing warnings. No local APK assembly.
