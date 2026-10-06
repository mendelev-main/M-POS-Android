# Spec 036 — Bounded Kotlin backup input

## P2 recovery benefit

The Android backup importer previously called `readBytes()` and checked the existing 500,000,000-byte limit only after reading the entire document. A large or endless document-provider stream could accumulate unbounded input before rejection. `MPosBackupInput` owns bounded byte ingestion in Kotlin and is used by the renamed `MPosBackupManager`.

## Preserved contract

- Existing 500,000,000-byte limit, inclusive; same Russian oversized-file message.
- Byte-for-byte UTF-8 input, existing JSON parser, backup v13 representation and all product/image restoration mappings remain unchanged.
- Read at most the limit plus one byte; reject excess before appending it. Do not rely on provider length metadata or `available()`.
- Short reads continue; zero bulk reads use a single-byte fallback without spinning. Provider IO errors propagate to the existing import failure/cleanup path, and no partial backup is returned.
- Caller owns stream closure through the existing `use` block. No staged image or business data mutation occurs before bounded read and JSON parsing complete.
- No database authority cutover, networking, automatic synchronization or payment/shift changes.

This remains an in-memory JSON importer: accepted large files, JSON parsing, Base64 and bitmap decoding can still require substantial memory. This is an input-bound enforcement fix, not a claim that every backup below 500 MB fits every tablet. Lowering the accepted limit or changing the backup format requires a separate decision and compatibility work.

## Acceptance and rollback

JVM tests exercise exact-limit Unicode bytes, empty input, unknown-length endless oversize, short/zero reads, provider failure, caller-owned closure and fallback oversize. Run existing JS parity suite, all JVM tests, lint and debug/minified release builds.

Physical acceptance pending: Android document providers, large real backups, interrupted reads and v13 restore/restart on tablets. This stage does not establish overall P2 physical acceptance.

Rollback can restore the former reader and class name; no persisted data migration is needed. Prefer retaining the bounded reader.
