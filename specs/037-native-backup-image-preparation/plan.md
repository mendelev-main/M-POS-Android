# Plan

1. Extract existing backup photo packaging and staging into a Kotlin module with injected image IO.
2. Keep Activity/file-picker and shared JS confirmation/business restore boundaries unchanged.
3. Test actual Android image decoding plus deterministic rollback and representation compatibility.
4. Validate full suites/builds, document physical gates, and publish to main.
