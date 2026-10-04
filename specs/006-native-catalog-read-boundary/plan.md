# Implementation plan

1. Add parity-guarded snapshot read to `MPosCatalogRepository`.
2. Add `catalogSnapshot` action to the existing secure storage bridge.
3. Upgrade Android-only storage adapter with promise-based native request/response correlation.
4. Add `MPosCore.Catalog` read API.
5. Keep native catalog reads feature-gated off.
6. Extend architecture tests.
7. Run CI.
8. Physically verify parity and snapshot behavior before any cutover.
