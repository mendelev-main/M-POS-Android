# Spec 034 — Telegram shift-close receipt image

## User decision and reference

Send the shift-close receipt as an image in Telegram, with iPad behavior, rather than a text summary. Reference inspected: `mendelev-main/prilavok-pos-ipad` commit `67d039c3216a2f4422ca0616074653ccff586107`, `PrilavokPOS/PrilavokPOSApp.swift`, `telegramSendShiftCloseReport`, `makeShiftReceiptImage`, `telegramPhotoRequest`.

## Contract

- Retain shared `submitCloseShift` → critical local commit → `sendTelegramShiftClosed` sequence, notification enabled/notifyShiftClosed checks and chat/topic configuration.
- Kotlin renders one white 720-pixel-wide PNG receipt from the existing report; sends it using Telegram `sendPhoto`, multipart field `photo`, filename `shift-report.png`, `image/png`, HTML caption, chat ID and valid positive topic ID.
- Preserve iPad sections: establishment, report title/status/id, employee, opening/closing dates, sales/check count, cash/card, opening cash, deposits/withdrawals, expected/counted cash, discrepancy, cash movements and closing footer.
- Monetary values come from the already calculated report, formatted to two decimals with report currency. Do not recalculate financial totals in Kotlin.
- Receipt count is `orders.length`, including returned receipts, as on iPad; `count` in the report is an active-sale count and must not substitute it. This distinction is preserved for a separate possible business refactor.
- Movement order/notes and deposit `+` / withdrawal `−` signs are preserved. Discrepancy emphasis threshold is `abs(difference) > 0.009`, as on iPad.
- Dates use the tablet's timezone and Russian day/month/year formatting. Caption includes escaped employee and close time. Missing close time shows a dash in the receipt and current time in caption/footer, matching iPad.
- Use M POS as an empty-establishment branding fallback. Android uses its system monospace font; exact font raster equality with UIKit is not claimed.
- Rendering and sending run on the existing background Telegram executor. Results use `onTelegramShiftClosedResult` with flash fallback when the shared runtime has no handler. Rendering/network/API failure never changes the closed shift or retries closure; no new automatic delivery retries or text fallback.
- Long names and movement notes wrap rather than overlap totals. Reject images exceeding Telegram's 10,000 combined dimension limit before allocation; do not truncate movements or report success. Extremely long receipts need a separate delivery-format decision.
- General Telegram text/test messages, monthly PDF reports, LAN printing, shift calculations, backup v13 and keys remain unchanged.

## Acceptance and rollback

Automated: report field/value/count/date/caption parity; multipart image/topic/bytes; real Android native-graphics PNG generation on the minimum supported API via Robolectric; executed shared shift-close order/storage-failure/Telegram-failure/disabled-notification cases; lint and debug/minified unsigned release builds.

Physical/Telegram acceptance pending: compare against iPad using the same synthetic shift (cash/card/split, return, deposits/withdrawals, discrepancy, long names/notes), verify one photo in configured group/topic, and verify offline close/printing with failed delivery. No production credentials or real Telegram sends are required for automated checks.

Rollback replaces photo delivery with the previous text path; no local data/schema migration. Restoring text format requires a user decision because image delivery is now the approved requirement.
