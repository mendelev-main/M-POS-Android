# Implementation plan

1. Create the native Android shell and load the exact POS assets from a trusted local origin.
2. Reproduce the iPad bridge contract without changing business modules.
3. Port photos and complete backup v13 first so test data can move safely between devices.
4. Port LAN ESC/POS printing and all print routes.
5. Port purchase-order, PDF, XLSX and Android share flows.
6. Port Telegram reports and validate backend/SSE behavior.
7. Run the production regression suite and a physical tablet parity matrix.
8. Sign a release APK with a stable offline key and document update installation.

