# 086 — Native loyalty sale/reversal journal and allowed recovery

## Scope

MPosLoyaltyJournal owns claim/finish/recover and payload construction for paid-receipt loyalty sale/reversal. State is compatible `loyaltySync` / `loyaltyReversal` inside the existing receipt JSON and Room status projections; no separate backup table or v13 change. Internal attempt token/payload markers are local metadata excluded from backup. The UI adapter orchestrates existing triggers, four bounded workers and correlated Room/native HTTP calls; native UI 105 can reuse these boundaries. Sale/reversal POST transport uses OkHttp, reviewed routes/device header/body, five-second deadlines, no connection retry and six-second cancel watchdog. Backend idempotency by existing order/customer contract is retained (028/029); no new exactly-once claim.

## Durable protocol

Claim reads the current individual receipt, checks customer/status rules, constructs the same sale/reversal body, persists `sending` and an attempt token atomically before any HTTP request. Sale may be explicitly invoked for already-synced receipts as in source; normal retry selects only eligible records. Reversal skips sending/synced; settle requires a returned receipt. Returned pending sale is sent before reversal. JSON status extensions survive claim but success/failure replacement drops them as source. Source events and payload quantities/redemptions/allocations are preserved.

Finish verifies the token, current sending state and unchanged POST payload; it updates only the relevant loyalty metadata on the freshly read receipt. Current return/financial fields and other receipts remain unchanged. Sale success chains reversal only for a returned receipt with pending reversal. Invalid/no-body sale response becomes pending; reversal response data is unused as in source. A failed local claim forbids network; a failed final write leaves durable sending recoverable. Uncertain local acknowledgement blocks new work until recovery. No immediate automatic HTTP resend.

Canonical archive updates are per-receipt with lightweight revision reads, never whole-history serialization for each send. Recovery scans existing records as reviewed source does, durably converts interrupted sending to pending and emits only allowed actions. Same-process active/queued attempts are protected from recovery, preventing duplicate sends on reconnect. Four workers bound Room/native HTTP pressure; waiting work starts only while enabled and local status is safe. Rollback MPosNativeLoyaltyOutboxEnabled=false restores source functions for new work; existing attempts finish their durable protocol. Malformed/noncanonical receipt archives retain their raw document and are not silently repaired to allow network writes.

## Retry policy

Existing post-payment/post-return functions and operationalReconnect/startup paths remain the only triggers. No timer, automatic foreground receiver, WorkManager or new payment retry is added. Loyalty recovery is separate from availability: availability still retries only after the next successfully persisted payment (033). No catalogue sync is added. Interrupted mutations reuse the existing idempotent backend operations and order IDs; a lost HTTP response cannot prove the backend rejected the operation.

## Verification

Twelve actual-source sale/reversal fixtures verify status gates, bodies and complete sending/finish metadata. Room tests cover parity, active-vs-interrupted recovery, duplicate claims, stale tokens/replaced customer payload, concurrent return/other receipts, failed claim/final write rollback and returned sale-before-reversal. HTTP tests cover exact POST routes/headers/bodies and reject arbitrary mutation kinds. JS tests cover persistence-before-network, finish-before-memory, duplicate callers, timeout pending, active recovery, return/reversal chain, storage failure, rollback, bounded workers and immutable correlated bridge. Full JS/JVM/lint required. Physical acceptance pending.

## Preserved questions and limits

An explicit direct sale call may resend a synced receipt; only reversal has a synced guard in source. Retain this policy and backend idempotency. Offline sale may be complete before central bonuses are processed. JS trigger/orchestration still exists until native UI/runtime removal; this stage does not claim fully native lifecycle scheduling. This receipt mutation boundary does not replace WEB journals; next stage 087 owns their distinct intent/ACK rules.

Tablet cases: pay with customer online/offline; restart during sending, reconnect repeated while a request is active; return while sale is pending/sending/synced; repeated return/reversal; export/import/reopen v13; network success followed by local write failure; several pending receipts while another sale/return is made. Verify financial/stock totals and other receipts never change from loyalty work. Record physical cases as pending until final acceptance.

Evidence: 353 JS / 278 JVM passed, lint 0 errors / 15 existing warnings. No APK assembly.
