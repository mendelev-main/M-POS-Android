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

## 2026-10-24 — 00.1, реализация и начало проверок

Пользователь авторизовал реализацию обновления APK. Добавлены settings UI, ViewModel/OkHttp, ограниченные manifest/download, archive identity/signer/hash checks, FileProvider/system installer, private ready cache и restart recovery. Actions расширены отдельным stable publish, release assets и emulator self-update API 28/35. Новые keys не создавались.

Локально: harness preflight, 3 Python tests, py_compile scripts и actionlint — passed. Android SDK локально отсутствует, Android проверки выполняет [run 40027720047](https://github.com/mendelev-main/M-POS-Android/actions/runs/40027720047). Результаты и исправления будут записаны после фактического выполнения.

Первый run 40027720047: Kotlin/Compose compile и JVM tests прошли; lint нашёл два отсутствующих `includeSubdomains` в debug-only network security config. Исправлено явным `false` для localhost/127.0.0.1, release TLS-политика не ослаблялась. Дополнительно добавлены cancellation test и e2e corruption-before-install scenario.

Run 40028108139: checks success (8/8 JVM + 3 Python, compile/lint/debug). API 35 e2e прошёл check/download/restart/corruption/cancel/install 1→2 и сохранил sentinel, затем не увидел success receipt из-за init/openSettings race; исправлена повторная initial check. API 28 остановился на IO download failure, добавлена debug-only diagnostic trace для выяснения причины. Установка/release заблокированы gates до зелёного e2e; успешный продуктовый выпуск пока не заявляется.

Run 40029014261: API 28 download diagnostic показал ProtocolException/unexpected end of stream на большом APK через adb reverse; файл корректно отвергнут и удалён. E2e fixture переводится на фиксированный emulator host 10.0.2.2 без adb reverse, с теми же size/hash/signature gates. Разрешение HTTP этого адреса существует только в debug manifest/fixture branch, release остаётся HTTPS-only. Добавлен отдельный optional post-publish smoke между двумя production-signed releases через реальный публичный HTTPS канал (без debug hook/run-as); сохранность состояния определяется installed version и persisted receipt/lastChecked.

API 35 run 40029014261 подтвердил self-install и data sentinel, но e2e driver запустил Activity в промежутке после смены versionCode и до завершения package replacement; screenshot был launcher. Driver теперь повторяет `am start -W` с ограниченным ожиданием видимого приложения. Это не обход installed-version/data проверок.

Run 40071404333: API 35 полный e2e success. Переход fixture на emulator host устранил EOF API 28, затем APK корректно остановился на пустом archive.signingInfo. В AOSP Android 9/10 getPackageArchiveInfo вызывает collectCertificates только при GET_SIGNATURES; исправлен набор flags для API≤29 (GET_SIGNATURES | GET_SIGNING_CERTIFICATES). Signer equality, версия, hash и package gates сохранены. Новый run должен подтвердить API 28 installation.

Run 40071871635 (commit 9ed4ed2120ec528694bd75d173185e4273a3eb61): checks success; оба emulator jobs API 28/35 — success. В каждом пройдены check/download/restart recovery, corruption rejection, Cancel, system self-install 1→2, сохранённый app-private sentinel и сообщение после обновления. Скриншот API 35 просмотрен: версия 2, success receipt, настройки доступны. Signed release/publish проверяются следующими jobs этого же run.

## 2026-10-24 — 00.1, первый полный выпуск

[Run 40071871635](https://github.com/mendelev-main/M-POS-Android/actions/runs/40071871635), commit 9ed4ed2120ec528694bd75d173185e4273a3eb61: checks, emulator API 28/35, signed-apk и publish — success. 8 JVM tests и 3 Python tests, compile/lint, actual debug self-update 1→2 с restart/corruption/Cancel/data sentinel. Signed stable APK 0.1.8.1, versionCode 1801, package com.mendelev.mpos, прежний pinned certificate. Release v0.1.8.1 опубликован с APK/update.json/checksum; анонимный public latest endpoint проверяется отдельно после загрузки assets.

Следующая приёмка: второй выпуск и production-upgrade job из v0.1.8.1 через настоящий HTTPS stable endpoint, без debug hooks. Пользовательский планшет/OEM permission остаётся physical-pending.

Второй release 0.1.9.1/versionCode1901 опубликован (run40073039039); checks/API28/API35/signature/publish success. Post-publish test остановился на первом Android unknown-source permission UI: приложение правильно открыло settings, а driver ожидал Cancel установщика. Driver дополнен реальным включением разрешения. В verify run40074134110 production API35 полностью прошёл 1801→1901 (HTTPS, source permission, Cancel/retry, receipt/lastChecked). API28 задержался после boot; live GitHub log подтвердил запуск driver. Зависший запуск отменён, ADB/screenshot calls ограничены timeout и добавлены step diagnostics. APK 0.1.9.1 не меняется; повторяется только verification существующих релизов.

Verify run40074952217: API35 production success. API28 также прошёл HTTPS download, restart recovery, first source permission, Cancel/retry и установил1901; затем `adb am start -W` попал между PackageManager version publication и финальным stop приложения. Logcat: новая Activity стартует, system_server завершает её в package replacement, shell waiter не возвращается. Driver убрал -W и ограниченно повторяет запуск по наблюдаемому UI, сохраняя проверки версии/receipt/lastChecked. Runtime APK остаётся тем же.


## 2026-10-24 — 00.1, production приёмка завершена в CI

[Run 40075276435](https://github.com/mendelev-main/M-POS-Android/actions/runs/40075276435), driver commit e4a5756cfd6812917fa5a6c3d39e41e7c8376558: verify (28) job120817170338 и verify (35) job120817170303 — success. Оба выдали UPDATE E2E PASSED для com.mendelev.mpos, 1801→1901. Проверены публичные HTTPS manifest/APK, restart/restore, первое разрешение unknown source, Cancel/retry, системная установка, версия и persisted receipt/lastChecked. 4 Python tests в каждой job passed. Удаление -W и повтор открытия по видимому UI устранили гонку CI driver Android 9 без изменения APK.

Текущий stable v0.1.9.1, постоянные package/certificate сохранены. U01–U06 ci-verified; физический планшет/OEM, будущие Room migrations и защита кассового commit остаются своими отдельными критериями. Локально финально выполняются preflight, 4 Python tests, actionlint и diff-check; SDK локально отсутствует, Android evidence — указанные Actions runs.


## 2026-10-24 — спецификация функциональности перед реализацией

По запросу пользователя расширена документация 01–09: каталог/карточки/единицы/рецепты/фото/модификаторы, persistent cart/резервы/parked, операции склада/продаж/ролей/клиентов/backup/оборудования/сайта. Созданы пять связанных спецификаций, примеры Given/When/Then и команды с transaction/revision/idempotency. Планы и navigation обновлены, native отличия от Web зафиксированы ADR-003. Исходный pin сохранён; все продуктовые IDs planned. APK/бизнес-код не менялись. Проверки этой итерации: `python3 scripts/harness.py preflight`, локальные Markdown links, `git diff --check` — passed. Android tests не запускались: изменена только документация.
