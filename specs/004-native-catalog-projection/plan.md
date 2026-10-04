# Implementation plan

1. Add product/category projection entities and DAO.
2. Upgrade Room schema to v2 with explicit migration from v1.
3. Extend NativeStorageMirror so only the `products` key triggers structured catalog projection.
4. Preserve full original product JSON in each product projection row for lossless compatibility.
5. Keep all reads in the existing POS runtime unchanged.
6. Extend architecture tests to verify explicit migration and non-authoritative status.
7. Run CI.
8. After physical shadow validation, define the authoritative catalog repository cutover separately.
