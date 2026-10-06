# Spec 038 — Bounded Kotlin SSE message reader

## P3 boundary and benefit

Extract diagnostic SSE framing into `MPosSseReader`, integrated with the renamed `MPosNetworkTransport`. The prior inline reader used trimStart(), which changed payload whitespace, missed empty data frames and only handled LF/CRLF. Kotlin now interprets message.data with EventSource framing semantics, allowing more useful parity fingerprints before a future native transport cutover.

## Contract

- Native transport remains opt-in, diagnostic-only and non-authoritative. Shared EventSource still dispatches WEB orders, ACK/ready/recovery and business triggers.
- Accept LF, CRLF and CR; consume a leading UTF-8 BOM only at the start of a connection.
- Remove exactly one ASCII space following a field colon; preserve remaining spaces, tabs and UTF-8 data. Join data fields with LF and remove the last inserted LF on dispatch.
- Empty/colonless data fields dispatch empty messages. Blank/comment-only frames do not dispatch. EOF discards an unfinished frame.
- Observe only default/message event types, matching the existing legacy addEventListener('message') observer. Reset event type after each frame. Ignore unknown fields, id and retry in this diagnostic observer; no native Last-Event-ID or server retry policy is introduced.
- Bound each line to 1,048,576 bytes and buffered event data to 1,048,576 UTF-16 characters including inserted LF. These are new native diagnostic limits, not POS/backend/backup limits. Oversize raises an IOException and enters the existing shadow reconnect path; the authoritative browser stream is unaffected.
- Caller owns connection/source closure. Provider errors propagate; never dispatch a partial frame. No payloads enter diagnostics beyond existing SHA-256 fingerprints.
- Existing diagnostic counters, explicit configuration and foreground/background semantics remain unchanged. No claim that asynchronous last-hash comparisons correlate concurrent streams perfectly.

## Acceptance and rollback

Ten JVM tests cover whitespace/multiline data, line endings, empty fields, named/default messages, metadata/comments, incomplete EOF, fragmented UTF-8/BOM, byte/character bounds and interrupted source. Existing JS source/authority tests, all JVM tests, lint and both APK builds remain required.

Physical gates remain open: same-stream parity, disconnection/reconnect, lifecycle and multiple tablets. This stage alone does not authorize SSE authority cutover.

Rollback restores the former inline parser and class name; no persistent data migration. Retain the corrected parsing unless a specific compatibility regression requires otherwise.
