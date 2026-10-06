# Plan

1. Move canonical receipt ownership to complete per-receipt rows with compatible fallback.
2. Reconcile snapshots incrementally, add CAS upsert and bounded page queries.
3. Integrate Android page controls around retained renderer and preserve source refresh.
4. Test real SQL rollback/unchanged rows/history races, validate builds and publish.
