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


## 2026-10-24 — системная документация перед кодом

Добавлены логическая модель/версии/ownership, состояния и переходы, границы commit/restart, фото staging, миграции/restore activation, outbox/reconciliation, доступ/аудит и сквозные SYS cases. Связаны планы01/02/03/05/07/09 и существующая спецификация. Открытые gates обозначены явно: деньги каталога, precision при1/3, минимальная смена до05, окончательные роли, backend и restore protocols. Источник Web pin сохранён; продуктовые IDs planned. APK/код/dependencies не менялись. Проверки: `python3 scripts/harness.py preflight`, локальные Markdown links, парность code fences новых документов и `git diff --check` — passed. Android checks не запускались: изменена только документация.


## 2026-10-24 — карта модулей и граница достаточной документации

Добавлены21 логический паспорт, dependency/rollout plan,8 пакетов документации, контракт01a с выбранной пользователемBYN/schema candidate/validation/F01–F10, подробные warehouse/management/reporting/customer/hall specs. Минимальная смена03a явно поставлена передsale, складское ядро начинается01a. Архитектура initial:app сохранена. Семь поздних пакетов не блокируют01a; first code по отдельной задаче пользователя. Проверки: `python3 scripts/harness.py preflight`, локальные Markdown links, парность code fences, уникальность21 module IDs/8 package IDs и `git diff --check` — passed. Android tests не запускались: docs-only. APK/бизнес-код не менялись, parity planned.


## 2026-10-24 — требования пользователя: stock editor и общий дизайн

ADR-005 отменил запрет stock в карточке: UI без причины, внутри transactional delta/history/revision; docs/F11–F13 обновлены. По pinned Web описаны Manrope/light-dark/palette/shapes/buttons/dialog/close/input patterns, shared Compose components и DS01–DS07, config/ui-design.json сохраняет source hash/tokens и пока не используется app. Источники CSS изучены, paired screenshot/device evidence pending. Проверки: harness preflight, локальные Markdown links/code fences, JSON tokens/обязательные роли/source SHA и git diff --check — passed. Kotlin/Gradle/APK не менялись.


## 2026-10-24 — реализация01a и пользовательская иконка

Commit68e8266/run40082159622: checks success (11 JVM tests, lint/debug build); API28 actual instrumentation5/5 success. E2E driver затем не получил временный window XML на переходе Activity — обработано как transient empty UI, без ослабления проверок приложения.

Финальная реализация commit2d7e707 включает core UI/Manrope/palette, category/simple CRUD, Room schema1, decimal/command/revision, ledger/audit, draft queue и direct stock без причины, application icon. Дополнены category UI, nullable fingerprint, repeated stock adjustment, screenshot capture и реальное сохранение Room продукта через APK replacement. Run40082878290 выполняет checks/API28/API35/sign/publish; доказательство результата добавляется после выполнения. Новые secrets/ключи не создавались.


Run40082878290: checks success; API28/35 actual instrumentation5/5 success в каждом. После suite Android UTP удалил target/test packages (подтверждено logcat), поэтому read QA fixture до install был невозможен. Driver теперь после свежего install выполняет один explicit LaunchTest через am instrument (без UTP cleanup), сохраняет реальный продукт/stock8/screenshots и затем проверяет APK replacement. Гейты Room/UI и production security не ослаблены; APK runtime2d7e707 не меняется.


Run40083703323/commit19e9345: checks/API28/API35/signed/publish success, stable0.1.14.1/code2401. Оба emulator выдали CATALOG UPDATE PASSED для сохранённого товара/stock8 через реальную1→2 замену, затем UPDATE E2E PASSED. Просмотреныcatalog/editor/shared-dialog/icon PNG; иконка чёткая/не обрезана. На UI выявлены реальные visual defects: default variable-font axis делал Manrope слишком тонким, onPrimaryContainer оставался Material-purple. Исправляются явным FontVariation.weight и M-POS semantic color; новый выпуск должен пройти те же gates и повторный визуальный просмотр.


Run40084614575/commit95672bf: checks success, API35 полный instrumentation+catalog update+updater success. Новые screenshots35 просмотрены: Manrope400/700/800 корректен, price panel зелёный, clipping нет. API28 instrumentation5/5 прошёл, external updater послеrestart остался на каталоге: driver переиспользовал старый update-window.xml при transient UI dump, считая heading Settings кнопкой. Исправлен driver: удаляет прежний XML перед dump и подтверждает реальный экран «Обновления приложения» после перехода. Runtime APK не меняется; gates повторяются для окончательного выпуска.


## 2026-10-24 — итог01a/иконка

[Run40085597352](https://github.com/mendelev-main/M-POS-Android/actions/runs/40085597352), commitd74444c: checks/API28/API35/signed-apk/publish success.11 JVM и4Python;5 instrumentation наAPI28/35. Оба external E2E подтвердили CATALOG UPDATE PASSED (Room product/stock8), затем UPDATE E2E PASSED (check/download/restore/corruption/Cancel/system install1→2). Stablev0.1.16.1/code2601. СкачанныйAPK2669060bytes совпал с manifest SHA256/size/package/version/cert pin. PNGcatalog/editor/dialog/icon просмотрены; исправлены тонкий variable font и Material-purple panel. Иконка не обрезана; full physical parity pending.

Паритет повышен только для реализованных частей01a/P01/P02/P03/P09/P10; весь01 не закрыт. Рецептуры/единицы/модификаторы/фото и ручная перестановка категорий впереди. До release новые ключи не создавались. Локальные финальные docs/preflight/link/diff checks фиксируются в этой итерации; Android SDK локально отсутствует, evidence толькоCI.


## 2026-10-24 — основной workflow develop → main

По решению владельца: инкременты в develop без push CI; готовый PR — быстрые Android checks, draft пропускается; main — полный emulator/sign/publish. Сохранены signing gates, identity и счётчик android.yml. Однократная активация инфраструктуры без выпуска APK. Локальные результаты проверки записываются после запуска; Android SDK на этом компьютере отсутствует.

Проверено: harness preflight passed; 4 Python tests passed; actionlint android.yml/android-pr.yml и git diff --check без ошибок. Android build/emulator не запускались: runtime не менялся, локально нет SDK.


## 2026-10-24 — 01b pure domain, develop

Добавлены RecipeCalculator/QuantityUnit и6 поведенческих тестов: exact conversion, дробный выход/вложенность/агрегирование,0.4г, per-ingredient flags/reservations/cost, invalid graph/input, precision underflow. Начат P04/P05/P07; persistence/migration/UI pending, schema1 не менялась. Источники и precision policy: docs/CATALOG-01B.md. Harness кратко описан в harness/README.md, ссылка из README; исправлено устаревшее описание ручного выпуска в README.

Проверки: harness preflight passed;4 Python tests passed; git diff --check passed. Pure domain отдельно скомпилирован локальным cached Kotlin compiler2.1.20/JDK17 (только дополнительный SDK-free check, не замена закреплённому проектному2.2.21); JUnit4.13.2 RecipeCalculatorTest + CatalogRulesTest: OK(9 tests). Компилятор запущен через org.jetbrains.kotlin.cli.jvm.K2JVMCompiler с Catalog.kt/RecipeCalculator.kt и двумя test sources, output build/recipe-domain-check, затем org.junit.runner.JUnitCore. Полный python3 scripts/harness.py android attempted: wrapper остановлен до сборки — Could not create parent directory for lock file ~/.gradle/wrapper/dists/gradle-8.13-bin/.../gradle-8.13-bin.zip.lck (sandbox). Android SDK также отсутствует. Проектный Gradle/lint/Android и Room/UI tests для01b не заявлены пройденными. Push develop без CI/релиза по принятой политике.


## 2026-10-24 — 01b Room schema2 и редактор

Runtime267bc23, fixes/schema6acfe83. Реализованы units/display/stock flags/minimum/pack defaults, recipe table/yield/components и live cost preview; old type/base immutable; graph save/delete guards и transactional editor read. Миграция1→2 сохраняет identity/config/product/balance/movement/results/draft. Legacy fingerprints остаются replayable. Новые shared MposSelect/MposFlag, expanded JSON draft; composite без balance. Подробный контракт/ограничения в docs/CATALOG-01B.md.

Локально: harness preflight,4 Python tests, git diff --check passed. Дополнительный SDK-free compile cached Kotlin2.1.20/JDK17/JUnit4.13.2:11 domain tests passed (python3 ../local-domain-check.py; helper вне репозитория, не вместо закреплённого toolchain). Android SDK локально отсутствует; проектный toolchain проверял CI.

Первый run40094594582 (267bc23): checks success; Android fixtures failed — display piece в kg input, indices отсутствуют у части schema JSON. Исправленыfixtures; production snapshotread/validation/accessibility уточнены, schema2 из KSPartifact сохранена. Повторный [run40094842882](https://github.com/mendelev-main/M-POS-Android/actions/runs/40094842882), commit6acfe83: checks success (19 JVM +4Python +lint/assembleDebug), API28 job120874829924 и API35 job120874829676 success.10 instrumentation/API включают4 старых repository,4 configuration/migration,LaunchTest,new configuration UI. Каждый затем explicit seeded LaunchTest/update-e2e: CATALOG UPDATE PASSED stock8 и UPDATE E2E PASSED actual system install1→2/data preserved. Подпись/публикация skipped на develop. Joblogs и test XML artifact просмотрены; итоговая schema2 получена KSP, не написана вручную.

После CI только текст поля цены уточнён до «BYN за base unit»; полного повторного CI ради текста не было, его не приписываем run6acfe83. Финальные docs/preflight/Python/diff проверяются перед отправкой. Device/visual/font scale pending;01c modifiers/photo, category ordering и остальная приёмка01 впереди. В main/stable не публикуем этот инкремент.


## 2026-10-24 — 01c modifiers/photo, develop

682ec0c — schema3/ModifierRules/config editor/private photo pipeline;4890d0e — Android directory flag fix, Web-parity geometry retry and committed KSP schema3. ScopeP06/P08/P09/P10; docs/CATALOG-01C.md defines limits, activation/cleanup and test boundaries. Local preflight/4Python/git diff passed; SDK-free domain check cached Kotlin2.1.20/JDK17:15 tests passed (additional check only). Main/stable unchanged.

First run40096819346 stopped at compileDebugKotlin: OsConstants.O_DIRECTORY is not public SDK. Fixed to Os.open(directory,O_RDONLY) +fsync; schema3 was already generated by KSP and retrieved from reports. Retry [run40097122743](https://github.com/mendelev-main/M-POS-Android/actions/runs/40097122743) on4890d0e: checks success,23 JVM tests/0failures +lint/assembleDebug +4Python. Retrieved schema3 exactly matches committed export. Android28/35 still pending at this record; don't claim media/migration/UI acceptance yet. No signed APK/publish on develop.


Run40097122743/4890d0e: JVM/lint/build passed; media/Room/migration tests passed API28/35, UI suites failed.35 dropdown interaction raced IME; subsequent tests saw unfinished draft from preceding failed test.28 photo picker monitor lacked MIME/openable category (hits0). Fixed5b13d8d: keyboard hide before select and wait for options; MIME-aware cancelled ActivityResult monitor; independent editor setup/await close between UI cases. Added production legacy-draft session persistence before enabling picker and CatalogPhotoSessionTest (new ViewModel callback during restoration). Repeat [run40097725989](https://github.com/mendelev-main/M-POS-Android/actions/runs/40097725989) pending; не снижены assertions/gates.


Run40097725989/5b13d8d: checks success;16 instrumentation tests passed API28/35 (including new durable legacy session test). API28 external setup2UI/update-e2e passed. API35 external setup exposed an async editor race: after Add product, test typed before productName appeared (CatalogExtrasUiTest line34). eb40b22 adds explicit editor readiness waits for Add/Edit in all UI scenarios; assertions unchanged. Repeat [run40098187895](https://github.com/mendelev-main/M-POS-Android/actions/runs/40098187895) pending. API28 extras-editor.png from prior successful setup reviewed: Manrope/common buttons/layout and prepared image visible, no overlaps; full tablet/provider/font-scale acceptance pending.


Финальный [run40098187895](https://github.com/mendelev-main/M-POS-Android/actions/runs/40098187895), commit eb40b22: checks job120884740790 success (23JVM/0failures,4Python,lint/assembleDebug). API28 job120884740526 и API35 job120884740685 success:16 instrumentation tests each, включая4Extras media/repository/migration +1ExtrasUI +1legacySession и прежние10. External driver на fresh-installed base проходит2UI setup tests, собирает extras-editor screenshot; затем обаAPI печатают CATALOG UPDATE PASSED (stock8 survived replacement), UPDATE E2E PASSED (check/download/restore/cancel/system install1→2/private data preserved). Signing/publish/production-upgrade skipped на develop. Отчёты test XML/joblogs и KSPschema3 сверены; screenshotextras28/35 просмотрен без перекрытий. Runtime/test после eb40b22 не менялся, финальные изменения docs-only. preflight/4Python/diff проверяются перед финальным push. Физический picker/provider/HEIF/orientation/font scale/low-storage всё ещё device pending; category order и остальная финальная приёмка01 отдельно.01c не публиковался. ADR-006 фиксирует текущий bounded native loader/GetContent и будущий Coil/PickVisualMedia, без заявления о неустановленных dependencies.


## 2026-10-24 — Android16+ и manual category order

Владелец изменил целевую платформу: minSdk36, CI emulator36 вместо28/35 (такжеpublished-channel и production upgrade). compileSdk/targetSdk36/BuildTools35.0.0 остаются. config→release manifest minSdk36; verifier проверяет sdkVersion реальногоAPK; harness следит за Gradle/config. Старые manifest читаются наAPI36 для сравнения версий, это не поддержка старых устройств.

P03 reorder implemented: full IDs/revisions CAS, unique list, atomic order/revisions/result/audit, fault seam, append MAX+1 после holes.3 Room tests +1 Compose, stock/categoryId не изменяются. docs/ANDROID-SUPPORT и CATALOG-ACCEPTANCE фиксируют политику/remaining scope. Локальные preflight/4Python/actionlint/diff passed. Android SDK локально отсутствует; новый API36 CI pending. Main/stable не меняем.


Итог [run40099706934](https://github.com/mendelev-main/M-POS-Android/actions/runs/40099706934), commit7cf27d9: checks120889156006 success (24JVM/0failures,4Python,lint/assembleDebug), emulator36 job120889156179 success (20instrumentation incl3orderRoom+1orderCompose),2explicit UI setup tests и external updater. Логи: Finished20tests on emulator-5554-16, CATALOG UPDATE PASSED stock8 survived replacement, UPDATE E2E PASSED check/download/restore/cancel/system install1→2/private data preserved. Modern archive signer API и minSdk36 manifest/APK прошли настоящую проверку установщика наAPI36. Signing/publish/production-upgrade skipped; толькоAPI36 emulator, прежних28/35jobs нет. Joblogs и JVM XML просмотрены; после проверенного commit только docs. Финальные preflight/4Python/actionlint/diff checks перед push. Полный01 не закрыт: category colour/symbol и device acceptance описаны в CATALOG-ACCEPTANCE.
