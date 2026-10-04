# Feature Specification: Android parity baseline

## Goal

Deliver M POS for Android tablets with the same interface, local data model and production behavior as the current iPad application. The implementation must remain independent of manufacturer, tablet model, screen resolution and chipset. The port must not introduce delays into payment or depend on network connectivity for local work.

## Functional requirements

- Bundle the current POS UI and all business modules in the APK.
- Preserve every existing localStorage key, prefix and JSON shape.
- Preserve payment, receipts, returns, shifts, products, recipes, stock, purchasing, receiving, inventory, loyalty, hall bookings and manual catalogue synchronization.
- Implement LAN ESC/POS printing with the same printer configuration and receipt semantics.
- Implement product photo selection and durable local JPEG storage.
- Import and export complete backup schema v13, including photos and explicitly accepted secrets.
- Implement PDF/XLSX and purchase-order sharing through Android system UI.
- Preserve Telegram reports and backend/SSE behavior.
- Never load the POS UI from the network.
- Never add automatic catalogue synchronization or heartbeat traffic.

## Acceptance

- Automated JavaScript regression tests continue to pass against the copied web runtime.
- Android debug and release variants compile.
- A clean target tablet can import an iPad `.mposbackup` file and show matching products, photos, folders, employees, receipts, shifts, stock and settings.
- Offline sale, split payment, return, shift close, receiving and LAN printing pass on physical Android tablets.
- The main POS flows remain usable at different tablet resolutions, aspect ratios and display densities.
