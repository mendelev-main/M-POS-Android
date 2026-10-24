# Системное проектирование — 2026-10-24

Задача пользователя: продолжить документацию системы до реализации. Использованы mpos-phase/mpos-domain. Изучены native architecture/data/contracts/plans и pinned Web1682c88334e6fb13586ce4b530157c59ca062e43: android-safety.js (journal/write guard/recovery), administration session documentation/guards employees, inventory baseline documentation и web-orders recovery hooks. Это чтение исходников, не запуск Web.

Созданы DOMAIN-MODEL, STATE-MACHINES, RESILIENCE, ACCESS-AND-AUDIT, SYSTEM-ACCEPTANCE. Требования связаны с P/W/S/I/H/C/B/D/N; SYS/RES/ACC только уточняют тестовые сценарии. Native использует планируемые Room transactions, не перенос JS journal. Role matrix, currency/precision, минимальная смена до05, restore activation checkpoints и backend idempotency остаются gates. Изменений приложения/схемы/dependencies/APK нет; все продуктовые статусы planned.
