# M-POS — нативный Android

Новое приложение на Kotlin и Jetpack Compose, Android 9+ (API 28). Поведение переносим поэтапно из [M-POS-Android-Web](https://github.com/mendelev-main/M-POS-Android-Web). WebView и JS runtime в новом приложении не используются.

**Сейчас:** этап 00 — harness, документация, native bootstrap и CI. Начальный экран подтверждает запуск; каталог и продажи пока не реализованы. Следующий этап: **01 — товары**, затем **02 — рабочая зона**. Полного функционального соответствия пока нет.

## Начать работу

1. Откройте корень этого репозитория в Codex; прочитайте [AGENTS.md](AGENTS.md).
2. Изучите [план](PLANS.md), [карту переноса](docs/PARITY.md) и файл текущего этапа в `harness/build/`.
3. Проверка инфраструктуры: `python3 scripts/harness.py preflight` и `python3 -m unittest discover -s scripts/tests -v`.
4. Android Studio: откройте репозиторий как Gradle project, JDK 17; SDK Platform 36, Build Tools 35.0.0. `python3 scripts/harness.py android` запускает JVM tests, Android lint и debug build. Первый запуск скачивает зависимости.
5. UI smoke test: запущенный emulator/device API 28+ → `./gradlew connectedDebugAndroidTest`.

[GitHub Actions](https://github.com/mendelev-main/M-POS-Android/actions) прогоняет проверки, Android tests/lint/debug и emulator smoke; после успешных проверок main — signed release APK. PR из fork не получают signing secrets. Настройка ключа: [docs/RELEASE.md](docs/RELEASE.md).

Фундамент проверен [полным успешным Actions run](https://github.com/mendelev-main/M-POS-Android/actions/runs/40023385742): bootstrap APK `0.1.3.1` подписан новым постоянным ключом. Физическая установка и функциональные этапы ещё впереди.

Приложения устанавливаются рядом: native `com.mendelev.mpos`, Web `com.mendelev.mpos.web`. Debug имеет дополнительный `.debug`. Данные из Web сами не появляются; перенос через проверенный импорт будет отдельным этапом.

## Навигация

- [Цели и границы](GOALS.md), [этапы](PLANS.md), [запросы для агента](PROMPTS.md).
- [Harness](harness/README.md), [журнал проверок](harness/build-log.md).
- [Источник и происхождение](docs/SOURCE.md), [требования товаров и кассы](docs/PRODUCTS-WORKSPACE.md).
- [Технологический стек](docs/STACK.md), [архитектура](docs/ARCHITECTURE.md), [данные и расчёты](docs/DATA.md), [тестирование](docs/TESTING.md).
- [Открытые решения](docs/DECISIONS.md), [сборки и подпись](docs/RELEASE.md).
