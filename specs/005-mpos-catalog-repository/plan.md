# Implementation plan

1. Add ordered read queries to the catalog projection DAO.
2. Add `MPosCatalogRepository` with a semantic parity report against mirrored legacy JSON.
3. Add `catalogParity` to the secure native storage bridge handler.
4. Promote `MPosCore.Storage` as the Android runtime namespace.
5. Keep `PrilavokCore.Storage` as a compatibility alias only for unchanged shared runtime callers.
6. Update constitution, AGENTS and roadmap with the M POS-only naming rule.
7. Extend architecture tests.
8. Run CI.
9. Perform physical parity diagnostics before any Room-authoritative catalog cutover.
