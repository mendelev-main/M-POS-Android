# Проверки и приёмка

Visual DS01–DS07: [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md), парные screenshots/источники [UI-REFERENCE.md](UI-REFERENCE.md). Stock editor F11–F13: direct change без причины, atomic history/retry/conflict. Пока planned.

Сквозные маршруты SYS-01–SYS-13, проектные RES/ACC cases и gates: [SYSTEM-ACCEPTANCE.md](SYSTEM-ACCEPTANCE.md), [RESILIENCE.md](RESILIENCE.md), [ACCESS-AND-AUDIT.md](ACCESS-AND-AUDIT.md). Это будущие проверки, не прошедшие тесты.

| Уровень | Что доказывает | Когда |
|---|---|---|
| Harness preflight | Документы/ссылки, skills, планы, package и native граница | Локально по изменению; готовый PR/main |
| Python unit tests | Версионирование и fail-closed проверка APK metadata/signature | Локально по изменению; готовый PR/main |
| JVM unit | Доменные сценарии: количества/деньги/резервы/идемпотентность | С 01 по реализации; Gradle job уже готова |
| Android lint + assembleDebug | Анализ и компилируемость | Локально по изменению; готовый PR/main |
| Emulator instrumentation | Compose/Room/migration/media tests | main или ручная диагностика develop |
| Signed release validation | APK package, версия, certificate SHA-256, apksigner verify | Только main после зелёных checks и secrets |
| Device acceptance | Планшет UX, restart, два приложения рядом, update, принтер/файлы | На соответствующем этапе |

В01a есть11 JVM tests (3 catalog/parser/fingerprint,8 updater) и5 instrumentation cases:4 actual Room сценария (rollback/duplicates/revisions/reopen/constraints) и1 сквозной Compose каталог/category/stock/recreate/common-close. External updater scenario отдельно создаёт данные explicit instrumentation после UTP cleanup и проверяет Room после package replacement. Фактический результат и ссылки — [CATALOG-01A.md](CATALOG-01A.md). Будущие изменения Room schema требуют migration tests; schema1 — первая база, не имитация миграции из несуществующейv0.

Подробный каталог будущих fixtures и уровней проверок: [IMPLEMENTATION-CONTRACTS.md](IMPLEMENTATION-CONTRACTS.md). Это требования к новым тестам, не evidence их выполнения.

Golden regression examples для следующих этапов:
- Остаток 1 кг; 1000 операций по 0.0004 кг → 0.6 кг; возврат по историческому расходу → исходный запас.
- Composite: A 0.2 без учёта, B 0.1 с запасом 10 → доступно 100; при B=0 продажа блокируется, если B не разрешён в минус.
- Разрешённый минус: 0−2=−2, выключение флага не меняет запас; возврат восстанавливает первоначальный расход.
- Продажа после ввода инвентаризации → stale baseline отклонён, повторный подсчёт разрешён.
- Отказ commit → нет успешного UI/печати/HTTP; повтор idempotency key не создаёт дубль.
- Правила округления/частичных оплат/скидок фиксировать отдельными examples перед 03.

Device checklist 00: установить Web и native release одновременно; проверить разные имена/package и независимые данные; поставить следующий native APK поверх первого и проверить данные/подпись; проверить debug рядом с release. До фактического выполнения статус pending.

Отчёт всегда содержит commit, команду или Actions URL, результат и непроверенные случаи. Исторические CI другого репозитория не являются evidence.


Проверки01c и границы их доказательств: [CATALOG-01C.md](CATALOG-01C.md). Push develop не запускает CI автоматически, [основной workflow](DEVELOPMENT-WORKFLOW.md).


Действующий gate с24.10.2026: Android16/API36, [run40099706934](https://github.com/mendelev-main/M-POS-Android/actions/runs/40099706934).24 JVM,4Python,20instrumentation +2 explicit UI setup +updateE2E. Ранее API28/35 — история; новые тесты на этих ОС не запускаются. Платформа — [ANDROID-SUPPORT.md](ANDROID-SUPPORT.md).
