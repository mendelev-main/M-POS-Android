# Spec 037 — Kotlin backup image preparation boundary

## Scope and benefit

Continue P2 backup recovery by extracting image packaging/staging from Activity-coupled `MPosBackupManager` into `MPosBackupImages`. This is a focused refactor of an existing native boundary, not a migration of payment or restoration business logic. Image IO is injected so rollback and representation can be exercised with real Android Base64/bitmap decoding and deterministic storage failures.

The manager retains document picker IO, bounded input, callbacks, confirmation, cancellation and final pruning. The module does not write local POS data, activate Room, contact a backend or confirm restoration.

## Compatibility contract

- Import parses the same UTF-8 JSON string once. Export clones the caller's JSON, preserving unknown fields and business payloads.
- Preserve v13 shapes, `productImages`, `imageCount` and both 500,000,000-byte document and 2,000,000-byte decoded-image limits.
- Missing/blank/unpackaged photo IDs remove `localImageId` and `imageUploadPending` only.
- Packaged photos use existing Android Base64 and bitmap decodability validation, then save under fresh IDs. Preserve `imageUploadPending` for valid photos.
- Export deduplicates an existing ID. Import preserves the existing behavior of staging a separate fresh file for every product, even when multiple products share the old ID. Export imageCount counts packaged IDs; import imageCount counts staged files. No storage-sharing optimization in this stage.
- Remove packaged image bytes before forwarding the prepared document to existing JS confirmation/restoration.
- Failed preparation deletes all successfully staged IDs before propagating the original error. Cancellation/callback failure uses the same cleanup operation. Attempt every deletion even if one removal throws; filesystem deletion success is not guaranteed.
- Successful preparation hands off IDs for existing JS cancel/finish protocol. No existing active photo is pruned before finishImport.

## Validation and rollback

Native API 28 tests: round-trip decodable PNG with Unicode/business/unknown fields; duplicate export and per-product import; absent images; invalid later image rollback; disk-full rollback; missing products and oversized image; cleanup continues after one deletion error. Existing reviewed-source JS tests, all JVM tests, lint and debug/minified release builds are required.

Physical pending: v13 export/import/cancel/finish, real content providers, low-storage failures, process death and recovery on tablets. This stage does not establish Room authority or atomic business backup restoration. Large accepted backups remain in-memory; a failed save that creates a partial file before returning an ID remains a media-store concern outside the staged-ID rollback contract.

Rollback inlines the preparation back into the manager; no format, schema or persisted-state migration is needed.
