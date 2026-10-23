# Журнал проверок

## 2026-10-23 — этап 00, подготовка

Исходный Web срез: `1682c88334e6fb13586ce4b530157c59ca062e43`. Созданы планы 00–09, источники/инварианты, repo-local skills, native Compose bootstrap, harness и Actions. Новый независимый сертификат закреплён публичным fingerprint; private bundle хранится вне Git.

Результаты проверок и ссылки на публикацию добавляются после фактического выполнения. Локальный Android SDK изначально отсутствует, gh не авторизован. Device acceptance и все продуктовые acceptance остаются pending.

## 2026-10-23 — проверки и первый Actions run

- Локально: `python3 scripts/harness.py preflight` — passed; `python3 -m unittest discover -s scripts/tests -v` — 2/2 passed.
- Все три skills прошли официальный `quick_validate.py` (PyYAML в изолированной tools venv).
- `actionlint 1.7.12 -shellcheck=` — passed; Gradle Wrapper 8.13 скачан с SHA-256 и `./gradlew --version` с JDK 17 — passed (GRADLE_USER_HOME в workspace).
- `scripts/release.py signing-setup` на созданном bundle — passed: cert fingerprint и восстановление private key. Секреты в логи не выводились.
- [Actions run 40022115927](https://github.com/mendelev-main/M-POS-Android/actions/runs/40022115927), commit `fc1c82dc5877b15a905a3786211409ca137de89f`: setup-android завершился ошибкой, потому что его default `packages: tools platform-tools` запрашивает удалённый Google package `tools`. Android compilation/emulator ещё не выполнялись. Исправлено явным `packages: platform-tools`; acceptance gates сохранены. Новый run должен подтвердить исправление.
- Локальная Android сборка не выполнялась: SDK отсутствует. Device acceptance, бизнес-функции и загрузка signing secrets pending.

## 2026-10-23 — Android checks подтверждены; выпуск заблокирован secrets

[Actions run 40022558608](https://github.com/mendelev-main/M-POS-Android/actions/runs/40022558608), commit `2ad0b3ae8f46199afaf5d7129e7cdb3fc52f09dc`:

- `checks` — success: harness, 2/2 Python tooling tests, Kotlin/Compose compilation, `assembleDebug`, `lintDebug`. `testDebugUnitTest` — NO-SOURCE: доменных JVM tests пока нет, их прохождение не заявляется.
- `emulator` — success: 1 instrumentation test, реальный запуск Compose bootstrap и Activity recreation на API 35.
- `signed-apk` — failure до assembleRelease: отсутствуют ровно четыре secrets `MPOS_KEYSTORE_BASE64`, `MPOS_KEYSTORE_PASSWORD`, `MPOS_KEY_ALIAS`, `MPOS_KEY_PASSWORD`. Неподписанный APK не опубликован, gates работают.
- Отдельно локально: missing secrets / wrong private-key password отвергнуты, временный ключ после отказа отсутствует; generated secret/password не обнаружены в tracked files; 65 SHA-256 source manifest совпали со срезом Web.

Для завершения 00: авторизовать GitHub CLI (`gh auth login`) либо владельцу вручную добавить четыре secrets из private bundle по `docs/RELEASE.md`; запустить workflow заново. Затем проверить signed release/identity/cert и физическую соседнюю установку с Web. Private bundle создан вне Git и сохранён пользователю отдельно.

Продуктовые P/W/S/I/H/C/B/D/N критерии остаются planned. Этап 01 автоматически не запускался. Запись evidence не меняет runtime/CI; documentation-only commit может пропустить новый CI через `[skip ci]`.

## 2026-10-24 — полный CI и подписанный APK подтверждены

После авторизации владельца через `gh auth login` скрипт `install-signing-secrets.py` установил четыре repository secrets; значения не выводились. Signing blocker закрыт.

[Actions run 40023385742](https://github.com/mendelev-main/M-POS-Android/actions/runs/40023385742) для commit `60bc67b5ebb4b9184c476e137c85b8f0345bb9f7` — **success** во всех jobs:

- `checks`: harness, 2 Python tests, Kotlin/Compose compile, debug APK и lint — success; доменных JVM tests пока нет.
- `emulator`: Compose launch/recreation instrumentation — success.
- `signed-apk`: permanent-key preflight, `assembleRelease`, apksigner verification и package/version/certificate verifier — success.
- Версия `0.1.3.1`, versionCode `1301`, package `com.mendelev.mpos`, fingerprint `d484cd0166df7f043f65c25e058aba4d3a2b59ea058e6f39948e3e5684b12da4`.
- Artifact `M-POS-Native-0.1.3.1`, ID `12531598043`; APK скачан, SHA-256 сверён с checksum из Actions. Private bundle остался вне Git.

Инфраструктура этапа 00 ci-verified. Реальная установка native+Web рядом и следующий update на устройстве остаются device-pending; bootstrap не содержит товаров/продаж. Следующий этап — 01 по отдельной задаче. Эта запись меняет только документацию, CI runtime соответствует проверенному commit.

## 2026-10-24 — описание целевого стека (ADR-002)

По запросу пользователя добавлен `docs/STACK.md`: установленная сборочная база, выбранные компоненты native приложения, соответствие Web-функциям, границы транзакций/сети/периферии и подключение по этапам. Обновлены ссылки README/AGENTS/ARCHITECTURE, реестр решений и контекст 00.

Источники: текущий bootstrap, pinned Web source `1682c88334e6fb13586ce4b530157c59ca062e43`, документация Android/Kotlin/Coil и исходные Kotlin adapters Web. Внешние документы использованы для уточнения API/границ, а не как доказательство уже подключённых зависимостей.

Проверки: `python3 scripts/harness.py preflight` — passed; `git diff --check` — passed. Изменена только документация, сборка/dependencies/runtime не менялись; Android tests/APK повторно не запускались. Этап 01 и продуктовые acceptance не начаты. Documentation-only commit с `[skip ci]` сохраняет последнее runtime CI evidence в предыдущей записи.
