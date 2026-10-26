# M-POS — нативный Android

Новое приложение на Kotlin и Jetpack Compose, Android 16+ (API 36). Поведение переносим поэтапно из [M-POS-Android-Web](https://github.com/mendelev-main/M-POS-Android-Web). WebView и JS runtime в новом приложении не используются.

**Сейчас:** реализован первый каталог **01a**: категории и простые товары в BYN, поиск/фильтр, редактор остатка без причины, Room ledger/revisions и восстановление черновика. Подключены общие UI-компоненты/Manrope и иконка пользователя. CI-приёмка: [run40085597352](https://github.com/mendelev-main/M-POS-Android/actions/runs/40085597352), Android9/15 и signed release0.1.16.1. Updater00.1 сохранён. **В develop:**01b — единицы, рецептуры и флаги с Room migration/editor; CI-verified Android9/15 ([run40094842882](https://github.com/mendelev-main/M-POS-Android/actions/runs/40094842882)), [контракт01b](docs/CATALOG-01B.md). 01c — модификаторы и фото с миграцией schema3, [контракт01c](docs/CATALOG-01C.md), [CI Android9/15](https://github.com/mendelev-main/M-POS-Android/actions/runs/40098187895). Ручной порядок категорий добавлен; актуальная [проверка Android16](https://github.com/mendelev-main/M-POS-Android/actions/runs/40099706934). До02 остаются цвет/символ категорий и [финальная приёмка01](docs/CATALOG-ACCEPTANCE.md). Продажи пока не реализованы.

Harness — наш рабочий контур для агента: план → реализация → проверки → сохранение результата. [Кратко: зачем, как и где](harness/README.md).

Действующая платформа — [Android16+](docs/ANDROID-SUPPORT.md); старые CI-ссылки9/15 ниже описывают историю. Текущая проверка выполняется только наAPI36.

## Начать работу

1. Откройте корень этого репозитория в Codex; прочитайте [AGENTS.md](AGENTS.md).
2. Изучите [план](PLANS.md), [карту переноса](docs/PARITY.md) и файл текущего этапа в `harness/build/`.
3. Проверка инфраструктуры: `python3 scripts/harness.py preflight` и `python3 -m unittest discover -s scripts/tests -v`.
4. Android Studio: откройте репозиторий как Gradle project, JDK 17; SDK Platform 36, Build Tools 35.0.0. `python3 scripts/harness.py android` запускает JVM tests, Android lint и debug build. Первый запуск скачивает зависимости.
5. UI smoke test: запущенный emulator/device API 36+ → `./gradlew connectedDebugAndroidTest`.

[GitHub Actions](https://github.com/mendelev-main/M-POS-Android/actions) прогоняет проверки, Android tests/lint/debug и emulator smoke; после успешных проверок main — signed release APK. PR из fork не получают signing secrets. Настройка ключа: [docs/RELEASE.md](docs/RELEASE.md). Работа в `develop`, готовый PR → `main`, полный CI и автоматическая публикация после merge; [основной workflow](docs/DEVELOPMENT-WORKFLOW.md). Ручной повторный выпуск требует `publish_release=true`, [инструкция](docs/UPDATES.md).

Фундамент первоначально проверен [Actions run](https://github.com/mendelev-main/M-POS-Android/actions/runs/40023385742); механизм обновлений и первый stable APK `0.1.8.1` — [полным run](https://github.com/mendelev-main/M-POS-Android/actions/runs/40071871635). Последний проверенный stable выпуск — [0.1.61.1](https://github.com/mendelev-main/M-POS-Android/releases/tag/v0.1.61.1); обновление 0.1.8.1→0.1.9.1 через публичный канал исторически проверялось на Android 9/15 в [run 40075276435](https://github.com/mendelev-main/M-POS-Android/actions/runs/40075276435). [Готовые выпуски](https://github.com/mendelev-main/M-POS-Android/releases). Актуальная платформа — Android16+/API36; физическая приёмка отложена владельцем. Реализованный и проверенный объём см. в PLANS/PARITY, история старых runs не является текущей матрицей.

Приложения устанавливаются рядом: native `com.mendelev.mpos`, Web `com.mendelev.mpos.web`. Debug имеет дополнительный `.debug`. Данные из Web сами не появляются; перенос через проверенный импорт будет отдельным этапом.

## Локально на Mac

Подготовленная среда Android16/ARM64, команды сборки/запуска планшета и ограничения первого запуска: [LOCAL-ANDROID.md](docs/LOCAL-ANDROID.md). Из обычного Terminal в папке репозитория:

```sh
/bin/bash ./scripts/local-android.sh run
```

Скрипт использует отдельные SDK/AVD/Gradle cache и debug package. Функциональность полного возврата и её проверенный объём — [REFUND-03C.md](docs/REFUND-03C.md).

## Навигация

- [Первый каталог01a: возможности и ограничения](docs/CATALOG-01A.md), [иконка/Manrope](docs/BRANDING.md).

- [Единый дизайн из старого приложения](docs/DESIGN-SYSTEM.md), [источники визуальных стандартов](docs/UI-REFERENCE.md).

- [21 логический модуль и зависимости](docs/MODULES.md), [порядок построения](docs/DELIVERY-ROADMAP.md), [сколько документации осталось](docs/DOCUMENTATION-PLAN.md), [контракт первого01a](docs/FOUNDATION-CONTRACTS.md).

- [Функциональная спецификация и маршрут реализации](docs/FUNCTIONAL-SPEC.md).

- [Цели и границы](GOALS.md), [этапы](PLANS.md), [запросы для агента](PROMPTS.md).
- [Harness](harness/README.md), [журнал проверок](harness/build-log.md).
- [Источник и происхождение](docs/SOURCE.md), [требования товаров и кассы](docs/PRODUCTS-WORKSPACE.md).
- [Модель данных](docs/DOMAIN-MODEL.md), [состояния](docs/STATE-MACHINES.md), [восстановление](docs/RESILIENCE.md), [доступ/аудит](docs/ACCESS-AND-AUDIT.md), [системная приёмка](docs/SYSTEM-ACCEPTANCE.md).
- [Технологический стек](docs/STACK.md), [архитектура](docs/ARCHITECTURE.md), [данные и расчёты](docs/DATA.md), [тестирование](docs/TESTING.md).
- [Обновление с планшета](docs/UPDATES.md).
- [Открытые решения](docs/DECISIONS.md), [сборки и подпись](docs/RELEASE.md).
