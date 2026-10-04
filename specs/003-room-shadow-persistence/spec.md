# Feature Specification: Core local persistence shadow foundation

## Goal

Introduce Room/SQLite as a non-authoritative shadow persistence layer for existing `prilavok_` data. This stage proves database integration and migration observability without changing POS business behavior or the current source of truth.

## Safety boundary

- Existing `PrilavokCore.Storage` remains authoritative.
- Reads continue to come from the existing storage adapter.
- Successful writes/removes are mirrored to Room after local persistence succeeds.
- Existing localStorage contents are mirrored on Android startup.
- Failure of the Room shadow must not block a local POS write.
- No backup schema changes in this stage.

## Functional requirements

- Add Room database `mpos.db`.
- Add a generic `legacy_storage_shadow` table keyed by the existing storage key.
- Preserve serialized JSON payloads exactly for shadow copies.
- Add secure origin-restricted native bridge channel `storage`.
- Add Android-only adapter that wraps `PrilavokCore.Storage` without changing read semantics.
- Report shadow row count and explicitly mark Room as non-authoritative.
- Keep all shared iPad business modules unchanged.

## Acceptance

- Source parity tests remain green.
- Room database and compiler build successfully.
- Existing storage facade still reports `sourceOfTruth: local-pos`.
- Critical save journal remains on the existing storage path.
- Debug build/lint pass.
- Physical acceptance later verifies restart behavior and shadow row population.
