# Plan

1. Add transactional persisted shift summary/report read model preserving reviewed JSON shape.
2. Match scalar financial coercion without changing source records.
3. Route active output consumers/report dialog through native reads; keep existing formatters/printers/config gates.
4. Coalesce pending reads, isolate consumer copies and prevent late modal resurrection.
5. Compare shared fixtures in both engines, test real storage/runtime, build and publish main.

Next cohesive boundary: native shift screen using this model and existing Kotlin lifecycle/cash commands, with legacy administrator authentication retained until a separate credential design is specified. Main navigation, parity callbacks and rollback must be explicit before screen cutover.
