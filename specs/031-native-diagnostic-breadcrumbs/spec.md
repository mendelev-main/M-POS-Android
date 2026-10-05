# Spec 031 — Native diagnostic breadcrumbs

## Goal
Keep a bounded local metadata-only breadcrumb trail for Android printer, network and storage boundaries.

## Contract
- Maximum 200 entries.
- Store timestamp, category, event and optional success flag only.
- Never store order/customer/item/request payloads or device keys.
- Diagnostics are non-authoritative and cannot gate POS operations.
