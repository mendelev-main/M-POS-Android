# Источник поведения

Исследованный срез: [M-POS-Android-Web, 1682c88334e6fb13586ce4b530157c59ca062e43](https://github.com/mendelev-main/M-POS-Android-Web/tree/1682c88334e6fb13586ce4b530157c59ca062e43), получен 2026-10-23. Проверены исходники каталога/корзины, storage, Android adapters, документация и тестовые сценарии. Это code review среза, а не запуск Web на устройстве.

`config/reference.json` содержит commit, пути и SHA-256 для ключевых исходников. Не включаем копию всего проекта и его историю заявлений о готовности. При переносе сравнивать код и Android adapters вместе: адаптеры меняют поведение исходного JS. Документы Web иногда описывают старые ревизии; при противоречии фиксировать фактическое поведение и решение в context.

| Область | Пути в исходнике |
|---|---|
| Товары | `app/src/main/assets/pos/Web/js/features/product-catalog.js`, `product-persistence.js`, `product-configuration.js`, `product-categories.js` |
| Рабочая зона | `pos.html`, `cart-composition.js`, `cart-presentation.js`, `receipts.js` |
| Деньги/склад | `payment.js`, `inventory.js`, `receiving-*.js`, `android-pos-stock.js`, `android-safety.js`, `android-inventory.js` |
| Хранение | `Web/js/core/storage.js`, `docs/STORAGE_WRITE_BOUNDARY_RU.md` |
| Регрессии | `tests/source-product-stock.test.cjs`, `cart-actions.test.cjs`, `parked-runtime.test.cjs`, `delivery-tariffs.test.cjs` |
| Роли/настройки | `employees.js`, `docs/ADMINISTRATION_PERIMETER_2026_10_20_RU.md`, `ADMINISTRATION_SESSION_RU.md` |
| Backend/платформа | `web-orders.js`, `availability.js`, `android-integrations.js`, `android-bridge.js`, Kotlin host |

Поведенческие спецификации пишем заново; не переносим механически localStorage/journal в Room. Формат совместимого импорта специфицируется отдельно. В исходнике backup v13 и ключи `prilavok_`; это формат обмена, а не требования к внутренней native БД.

Источники технических решений:
- [Codex: AGENTS, repo-local skills и CI](https://developers.openai.com/blog/skills-agents-sdk).
- [Android applicationId](https://developer.android.com/build/configure-app-module).
- [Подпись APK](https://developer.android.com/studio/publish/app-signing).
- [AGP 8.13: JDK/Gradle/SDK compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes).


Уточнение модульных паспортов2026-10-24: дополнительно прочитаны suppliers/delete guards, purchase-orders/receiving draft, shifts totals/cash handlers, analyticsOrders/analyticsData и hall-bookings/bookingOverlap/saveNewBooking из того же pin. Выбранные native правила и открытые контракты указаны в WAREHOUSE-SPEC, MANAGEMENT-REPORTS-SPEC, CUSTOMERS-HALL-SPEC. Это чтение исходников, не запуск/верификация Web; небезопасные literal credentials из Web не переносятся.
