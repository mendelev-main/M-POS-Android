# Implementation plan

1. Add Android-only `native-catalog-cutover.js` after the M POS native storage adapter.
2. Add `legacy` and `compare` modes.
3. Default to `compare`.
4. Expose parity comparison/health diagnostics.
5. Hard-block `room` mode until a later accepted spec.
6. Keep the current POS read path unchanged.
7. Extend architecture/source-parity tests.
8. Run CI.
