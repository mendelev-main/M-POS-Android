# Plan

1. Inspect the exact iPad native image and photo request implementation.
2. Extract a pure Kotlin presentation model from committed report fields; preserve iPad count and cash-movement semantics.
3. Render locally using Bitmap/Canvas, wrapped text and bounded image allocation; free bitmap resources.
4. Route existing Telegram shift-close action through a photo multipart request and dedicated callback, keeping all other actions.
5. Test model/wire format, PNG native graphics, and existing shared close transaction triggers without actual Telegram sends.
6. Run lint/debug/release checks and update parity/user-decision documentation; leave physical acceptance explicitly pending.
