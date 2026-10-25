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

## 2026-10-24 — category appearance P03/schema4

b04a8d5 добавляет pinned Web palette/symbol, shared preview/picker, save transaction/revision/replay/fault seam, Room migration3→4 и цепочки1→4/2→4. Supplemental cached Kotlin2.1.20/JDK17 domain17 passed;4Python/preflight/actionlint/diff passed. Два pinned source regressions выполнены `node --test --test-name-pattern='category create and rename|category tile symbol truncation' ../reference/tests/source-product-stock.test.cjs`:2passed. Локальный `harness.py android` не дошёл до Gradle: sandbox запрещает wrapper lock в ~/.gradle; SDK также отсутствует, Android gates перенесены в CI.

Run40100861215/b04a8d5: checks success,26JVM/0failures+lint/assembleDebug/4Python. Exported schema4 retrieved from KSP artifact, не написана вручную. Emulator36:23/24passed; LaunchTest failed selecting new category outside lazy viewport as shared fixture catalogue grew. a19cb3e adds performScrollToNode before selection, assertions сохранены; runtime не менялся. Новый run40101394875 pending. Sign/publish skipped на develop; main/stable не меняем.

Run40101394875/a19cb3e attempt1: checks success26JVM+lint/assemble;24/24instrumentation36 passed, explicit UI seed2 passed. External updater stopped before settings by emulator **System UI isn't responding** dialog (screenshot/window.xml identify package android, not M-POS). UI screenshots covered by that system dialog, visual acceptance не повышена. Повторено только failed job через `gh run rerun 40101394875 --failed` на том же commit, без dismiss ANR/ослабления assert. Attempt2 pending; generated schema4 byte-equal to final checks artifact.


Итог attempt2: [run40101394875](https://github.com/mendelev-main/M-POS-Android/actions/runs/40101394875), a19cb3e, attempt2: checks и emulator36 success;26JVM/4Python/lint/assembleDebug,24instrumentation +2explicit UI seed; CATALOG UPDATE PASSED/UPDATE E2E PASSED (1→2, stock8/data preserved). Job120896159797 success; checks retained success (job120896160971). Финальный UI artifact12563173570, category-editor просмотрен без системного окна/перекрытий, исходная палитра и общие controls видны. Первая попытка не использована как visual evidence. Schema4 byte-equal KSP final checks. После tested commit только документация/generated schema export; повторять полныйCI ради этих metadata не требуется. Main/release/key неизменны; device/full01 pending.

## 2026-10-24 — начало02a workspace/current

User authorizes next part, physical tablet acceptance deferred.4e7d301 adds pure layout/cart, current snapshot recipes/options/revisions and aggregate reserve, folder/order commands, schema5+4→5/older migrations, guards and shared AppBar/Photo/Tile/wide Dialog; no sale/parked. WORKSPACE-02A defines exact partial W scope and native differences. Local22domain cached Kotlin2.1.20/JDK17 +4Python/preflight/diff and7pinned Web tests passed; codec compiled with cached org.json. `harness.py android` unavailable before Gradle lock in sandbox/no SDK; real Kotlin2.2.21/Android gates via CI.

Run40105704419/4e7d301: checks success31JVM/lint/assembleDebug; KSPschema5 retrieved from artifact. Emulator36:30/31passed, all6workspace Room tests pass; new UI selector found both folder tile behind dialog and identically named dropdown item. Restrict selection to Popup ancestor, no checks removed. During review added regression for tiny quantity decrease with modifier0.0004 ×0.000000001; test reproduced invalid zero reserve, fixed by validating aggregate precision also for decreases/removals (shortage still not blocks decrease). New successful commands clear prior errors. Follow-up CI pending.


## 2026-10-25 — итог02a

[run40106275939](https://github.com/mendelev-main/M-POS-Android/actions/runs/40106275939), commit0e6dd0b: checks/emulator36 success;31JVM/4Python/lint/assembleDebug,31instrumentation (6workspace Room+1workspace UI), затем3explicit UI seed tests. CATALOG UPDATE PASSED/WORKSPACE UPDATE PASSED/UPDATE E2E PASSED: package1→2, stock8 и current cart quantity5/total15 сохранены. Job120908509464 checks и120908509648 emulator36 success, signing/publish/production-upgrade skipped. JVM XML31/0failures, KSPschema5 byte-equal Git export; screenshots workspace/workspace-folder/workspace-configuration reviewed. Shared UI без перекрытий; count-dependent folder width/config total preview ещё UI gap, не заявлять full Web visual parity. После runtime0e6dd0b — только документация; physical tests deferred user, full02/parked/revalidation pending. Main/stable/key неизменны.


## 2026-10-25 — 02b parked/orders

091b2f3 adds atomic park/resume/swap/delete, persistent logical session ownership, cart payload1→2 migration in actual Room transaction, registry-backed revisions/ABA, metadata and shared-reserve/deletion guards. Room SQLschema5 unchanged; no fake schema6. Source contract WORKSPACE-02B.md; six pinned Web tests passed (resume/storage failure/double park/delete/duplicate labels). Supplemental cached Kotlin2.1.20/JDK17 local26domain tests passed, codec compiled,4Python/preflight/diff passed. Android local command stopped before Gradle wrapper at sandbox ~/.gradle lock; no SDK, actual compiler/Room/UI to CI36.

New tests:4OrderRules JVM;6ParkedOrdersRepositoryTest actual write-seam rollback at all3park/3resume/2delete/4swap mutation points + metadata/reopen/legacy lookup/concurrent ownership/ABA; ParkedOrdersUiTest (details/park/cancel/swap/delete/recreate) and updated old workspace scoped API tests. External update seed now4UI tests and verifies parked label/type/total5 plus current quantity5/total15 after replacement. [run40140796798](https://github.com/mendelev-main/M-POS-Android/actions/runs/40140796798) pending. No release/main/key changes; physical still user-deferred.


## 2026-10-25 — итог02b

[run40140796798](https://github.com/mendelev-main/M-POS-Android/actions/runs/40140796798), runtime commit091b2f3: checks121011943215 и emulator36 job121011943276 success. 35 JVM tests/0 failures,4Python,lint/assembleDebug;38 instrumentation tests на Android16/API36 и4 explicit UI seed tests перед внешним updater. Реальная установка APK1→2 сохранила stock8, current quantity5/total15 BYN и parked label/type delivery/total5 BYN. Полные metadata отдельно проверены Room reopen и Compose restore, не приписываются внешнему APK assertion. SQLschema5 byte-equal KSP export, migration cart payload1→2 проверена в Room. Signing/publish/production-upgrade skipped на develop.

Просмотрены screenshots order-details/parked-swap/parked-orders: общие Close/buttons/fields/dialogs, читаемые действия без перекрытий в показанных состояниях; форма параметров прокручивается. Полная визуальная/device приёмка не заявляется. После проверенного runtime — только документация. Следующая часть02c: явный пересчёт устаревшего заказа с показом изменений и подтверждением. Fixed slots/drag, count-based folder width, modifier total preview/performance и физическая приёмка остаются открытыми. Main/stable/signing identity не менялись.

## 2026-10-25 — начало02c

Реализован pure CartRevalidator, UI review/confirm/cancel/recreate, transactional repository CAS/recompute token/replay/rollback. WORKSPACE-02C фиксирует scope W03/W06 и исключения.4Python passed; local Android blocked до wrapper на sandbox ~/.gradle lock, SDK отсутствует. Первый supplemental JVM запуск выявил ошибку fixture allowNegativeStock без balance: RecipeCalculator корректно требует balance даже для negative; fixture исправлена на явный0. Проверки pinned regression/CI и итоговые counts добавляются по факту. Main/stable не трогаем.

Supplemental cached Kotlin2.1.20/JDK17 compile(domain+codec) и31 domain tests passed (26 прежних+5Revalidation). Pinned source-product-stock.test.cjs:77tests/77passed (node --test с pattern в этой версии Node фактически выполнил весь файл).4Python/preflight/diff passed. Runtime49dc342 отправлен develop; [run40143450510](https://github.com/mendelev-main/M-POS-Android/actions/runs/40143450510) pending, не объявлять Android acceptance до результата.

Run40143450510/49dc342 attempt1: checks passed40JVM/0failures,4Python/lint/assemble. Emulator36:41instrumentation/0failures (2RevalidationRoom+1UI);4explicit UI seeds прошли. Внешний updater остановился до Settings: screen.png/window.xml показывают системный android dialog «Quickstep isn't responding», а не исключение M-POS. Запущен повтор только emulator job на том же commit, gates/assertions/runtime неизменны. APK replacement этого attempt не прошёл, не заявлять его успех.

Attempt2 того же run:41/41instrumentation прошли снова, но external updater опять перекрыт системным android:id/alertTitle «Quickstep isn't responding», android:id/aerr_close. Добавлена узкая обработка в disposable emulator driver: сохранить screenshot/XML, один раз закрыть только точный системный Quickstep ANR, не M-POS/SystemUI/общий ANR. require_emulator остаётся обязательным; gates/installer/data assertions не меняются.5Python tests passed, включая guard от закрытия M-POS и неизвестного диалога. Runtime приложения49dc342 не менялся.

Финальная сверка выявила UI gap: review расхода исключал noStockTracking ingredients, хотя они часть состава. Исправлено: отображаемый physical composition включает все simple, stock guard сохраняет per-simple flags. Unit labels до/после отдельные. Тот же доменный тест подтверждает полный0.0012кг в review при нулевом warehouse reserve. Supplemental31tests passed; run40144591508 отменён, чтобы проверить финальный runtime, а не промежуточный. Следующий CI URL фиксируется после dispatch.


## 2026-10-25 — итог проверок02c

Финальный runtime2ce31c2, [run40144720147](https://github.com/mendelev-main/M-POS-Android/actions/runs/40144720147): checks121023650500 success —40JVM/0failures,5Python,lint/assembleDebug. Emulator job121023650607:41/41instrumentation passed (включая2RevalidationRepository+1RevalidationUI);4explicit UI seed tests passed. External updater остановился до Settings: screen.png и window.xml показывают android:id/alertTitle «System UI isn't responding». Quickstep-only recovery корректно не закрыла этот другой диалог. Общий job/run failure, установка APK1→2 в этом run НЕ пройдена. Предыдущие02b updater evidence остаются историей, не доказательством финального02c.

W03/W06 revalidation domain/Room/UI automated tests passed на финальном commit; статус инкремента implemented, full CI gate pending из-за external emulator infrastructure. Не ставить full02/ci-verified/device-verified. Новые повторы после трёх завершённых emulator executions (первая попытка+retry+финальный run; промежуточный40144591508 отменён) остановлены, чтобы не расходовать ресурсы при повторяющемся SystemUI/Quickstep ANR. Android application runtime exception не наблюдалась, но это не доказывает причину системного зависания. Состав noStockTracking виден в review и исключён из warehouse reserve; physical tests deferred пользователем. Нативный пересчёт не меняет SQLschema5/payload2, stock/ledger, main/stable/signing. Дальше: устранить нестабильность disposable emulator/external updater и закрыть его gate; затем оставшиеся UI02 (fixed slots/drag/folder widths/modifier total preview).

## 2026-10-25 — диагностика emulator gate02c

Уменьшено physical render resolution AVD с сохранением1280×900dp, RAM3072M/cores2. pinned action inputs проверены через GitHub contents API, test доказывает dp/invariant и сохранение API36 image/CPU config.6Python tests/preflight/diff passed. Runtime приложения не меняется. Добавлены fail diagnostics lastanr/CPU/memory/events/display; автоматическое закрытие SystemUI ANR не добавлено. CI ещё не запускался на этой записи.

## 2026-10-25 — 02d UI implementation

Папки используют count-based widths и Fixed grid с fitting columns; common Dialog maxWidth variant. Live total использует partial selectionPrice, а CartEngine/commit strict min/max без изменений.32 supplemental domain tests cached Kotlin2.1.20/JDK17 passed (новый partial/strict/unknown option). WorkspaceUiTest дополняет assertions2.5→3 BYN, сохраняет прежний invalid-required/Add/recreate/update сценарий. Первый fixture сравнил decimal scale3.50 vs3.5; canonical fixture исправлена, не production math. preflight/diff passed; Android/Compose актуального инкремента CI pending.


## 2026-10-25 — gate02c закрыт, проверка02d

[run40172190371](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172190371), commit3a9a4d0 (runtime приложения прежний2ce31c2): checks121105200829 и emulator36 job121105200646 success.40JVM/0failures,6Python/lint/assemble;41instrumentation+4explicit UI seed. Logs: CATALOG/WORKSPACE/PARKED UPDATE PASSED и UPDATE E2E PASSED, actual package1→2, stock8/current qty5 total15/parked delivery label total5 сохранены. AVD1600×1125/200dpi; screenshot updated-workspace.png имеет1600×1125, просмотрен без системного overlay/перекрытий. Quickstep recovery в логах не вызывался. Это закрывает ранее заблокированный CI gate02c; причина прошлых ANR не доказана, но новая конфигурация успешно прошла сценарий без подавления системных диалогов. Signing/publish skipped на develop, physical deferred.

После этого реализован отдельный02d UI. Финальный runtime da8db71, [run40172821096](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172821096) pending. Не приписывать его новым UI assertions успех run3a9a4d0. Local Android попытка этого продолжения остановилась до wrapper на sandbox ~/.gradle lock (SDK отсутствует); actual Android идёт CI. Fixed-slot/drag/performance/physical остаются открытыми.


## 2026-10-25 — итог02d

[run40172821096](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172821096), final runtime da8db71: checks121107096327 и emulator36 job121107097017 success.41JVM/0failures,6Python,lint/assembleDebug;41instrumentation и4explicit UI seed tests passed. Обновлённый WorkspaceUiTest подтверждает live total2.5→3 BYN, strict required-group refusal, folder return/recreate; прежние Room/revalidation/parked regression suites проходят. External updater: actual package1→2, CATALOG stock8, current quantity5/total15 BYN, parked delivery label/total5 BYN сохранены. Signing/publish/production-upgrade skipped на develop.

Screenshots workspace-folder/workspace-configuration просмотрены: компактная папка с1товаром, единые Close/buttons, сумма3 BYN при manual2.50+milk0.50; перекрытий в показанных состояниях нет. Снимки на1600×1125/200dpi, viewport1280×900dp. Это второй последовательный успешный полный run с новой AVD конфигурацией (после40172190371); Quickstep recovery в обоих логах не вызывалась. Причина прошлых ANR окончательно не доказана, системные ошибки не подавлялись. Физическая font-scale/orientation/планшетная приёмка отложена пользователем;4колонки/прочие размеры не объявляются device-verified по одному screenshot.

02c gate закрыт,02d UI в указанном scope ci-verified. SQLschema5/payload2, main/stable/key не менялись. Следующая часть W01: persistent fixed slots и drag с atomic CAS/replay/rollback, пустые места и доступная альтернатива перемещению. Большой каталог/performance/Coil и physical acceptance остаются открытыми. Полный02 не закрыт,03 не начинать автоматически.

## 2026-10-25 — начало02e

HomeEngine/home payload1/transactional repository; long-press root slots + category/folder list reorder и accessible controls. Added4pure/2Room/1UI; WorkspaceUiTest seed+updater проверяют отдельную custom home product/slot8. Local supplemental cached Kotlin2.1.20/JDK17 domain+codec compile/36tests passed;6Python/preflight/diff passed. Actual project Kotlin/Android/Room/Compose to CI. SQLschema5 не менялась. Результаты Android ещё не получены, не заявлять ci-verified.

02e source check: полный `node --test ../reference/tests/source-product-stock.test.cjs` прошёл442tests/0failures. HEAD reference1682c883 и SHA256 теста/pos-navigation/cart-composition совпали с config/reference.json, working tree reference clean. Local Android попытка остановилась до wrapper на sandbox ~/.gradle lock, SDK отсутствует. Runtime b389b39 отправлен develop, [run40176727737](https://github.com/mendelev-main/M-POS-Android/actions/runs/40176727737) pending.

Review follow-up: drag cancellation key включает identity/positions projection (не только persisted revision), чтобы изменение автоматического списка категорий во время жеста не выбрало другой ID на том же индексе. Root product tile использует общий ProductPhoto и текст «Ввести цену» для manual0. Первый run проверяет b389b39, follow-up будет проверен отдельным финальным run; не приписывать ему новые изменения до CI.

Run40176727737/b389b39 passed:45JVM/6Python/lint/build,44instrumentation+4explicitUIseed, CATALOG/WORKSPACE/PARKED/HOME UPDATE PASSED и UPDATE E2E PASSED. Native slot8 survived actual package1→2. Reviewed updated-home screenshot: detected default Material purple surfaceVariant on empty placeholders, replaced with existing background/outline shared roles; root header uses Web «Рабочая зона», duplicate edit instructions removed. Follow-up context identity fix/photos/manual label and this visual correction need final CI. Run40177268042 launched before screenshot review, cancellation requested to test final visual state together rather than a third completed run.


## 2026-10-25 — итог02e

[run40177332732](https://github.com/mendelev-main/M-POS-Android/actions/runs/40177332732), final runtime d53328e: checks121120840559 и emulator36 job121120840445 success.45JVM/0failures,6Python,lint/assembleDebug;44instrumentation/0failures/0skipped, затем4explicit UI seed. HomeLayoutUiTest действительно выполнил long-press root drag, accessible place8, recreate и category drag;2Room tests прошли legacy init/fault/replay/reopen/unknown format/concurrent winner/delete guards. Supplemental36domain tests и442pinned source regressions прошли локально; actual Android local blocked до wrapper ~/.gradle lock.

External updater подтвердил CATALOG/WORKSPACE/PARKED/HOME UPDATE PASSED и UPDATE E2E PASSED: actual APK1→2, stock8/current qty5 total15 BYN/parked delivery label total5 BYN/custom home product slot8 сохранены. Signing/publish/production-upgrade skipped на develop. Предварительный run40176727737/b389b39 также прошёл, промежуточный40177268042 отменён после визуального review для проверки финальных изменений вместе. Final updated-home.png1600×1125 просмотрен: нейтральные empty places, product на месте8, общие кнопки, без системных overlay/перекрытий. Физическая/paired Web parity не заявляется.

02e W01/W06 в описанном scope ci-verified. Room SQLschema5, cart2 и category layout1 прежние; новый home document payload1. Main/stable/signing identity неизменны, релиз не публикуется. Следующий остаток02 — большой каталог/performance/Coil и адаптивность/font-scale/orientation; физический планшет позже по решению владельца. Полный02/device не закрыты,03 автоматически не начинать.

## 2026-10-25 — начало02f

Реализованы on-demand availability/cache и background search, owner indexes, Coil3.3.0 shared16MiB/256px image loader, adaptive cart layout.38supplemental cached Kotlin2.1.20/JDK17 domain tests passed; added2JVM/1mediaAndroid/2UI(scale+2000products) cases. Документированы CI15s budget и synthetic fixture границы, screenshots/metrics перед install. Actual pinned compiler/Room/Coil/UI to CI, нет заявления verified до результата. Local Android blocker и дальнейшие проверки записываются по факту.

Actual local Android command остановился до wrapper на sandbox ~/.gradle lock; pinned Android compiler/Coil tests не заявляются локально пройденными.6Python/preflight/diff passed. API size(Int)/MemoryCache.maxSizeBytes(Long) сверены с исходниками Coil tag3.3.0 через официальный GitHub contents API. Runtime a56382a, [run40178918977](https://github.com/mendelev-main/M-POS-Android/actions/runs/40178918977) pending.

02f run40178918977/a56382a: checks47JVM/lint/build success; instrumentation failed4/47. Wrong-thread Compose Dialog stack ends at WorkspaceViewModel IO publication: all worker UI publications now hop Main.immediate, expensive index stays IO. Synthetic cleanup explicitly deletes RESTRICT stock_balances first. Android16 large-screen requestedOrientation was ignored; fixture now uses UiAutomation display rotation, restores/unfreezes in finally. Parked timeout also remains under regression gate; no success claim. Repeat full CI required.

02f retry40182783528/21b3cc9: checks success47JVM;45/47 instrumentation passed. Wrong-thread/RESTRICT/rotation failures resolved. Font total assertion waited for DB rather than displayed state; changed to UI eventual total4. Parked add-after-park timeout retained: callback now resolves latest persisted owner via rememberUpdatedState, explicit lazy callbacks replace local function references; test asserts enabled and reports pending/message/owner on failure. Full gate still pending.

Run40183539310/2ca6b0e:47/47instrumentation passed, checks success; updater stopped while collecting artifacts after UTP had uninstalled target (JSONDecodeError from run-as output). No domain/UI test failure. Driver now follows existing explicit instrumentation lifetime:2scale UI cases/capture, then clean install before unchanged4update seed. No test gate weakened; full update still pending.


## 02f итог — 2026-10-25

[run40184227465](https://github.com/mendelev-main/M-POS-Android/actions/runs/40184227465), final runtime f1639b8: checks121141498494 и emulator36 job121141498649 success.47JVM/0failures,6Python,lint/assembleDebug;47instrumentation passed, затем2explicit scale UI cases и4explicit update seed cases. WorkspaceAvailability on-demand5000 IDs/stock+parked/stale flags; PhotoCache Success→MEMORY_CACHE/16MiB/missing Error;2000products search/Add; portrait+landscape150%/quantity2/total4 BYN проходят. Supplemental38domain tests прошли локально cached Kotlin2.1.20; actual project Kotlin2.2.21 проверен CI. Local Android остаётся blocked до wrapper на sandbox ~/.gradle lock/нет SDK.

Artifact performance.json:2000products,load306ms/search1209ms/add595ms в explicit CI fixture. Это wall-clock action timings на эмуляторе, не FPS/гарантия физических планшетов. workspace-portrait150.png1125×1600 и workspace-landscape150.png1600×1125 просмотрены: общие кнопки/Manrope/navy, portrait переключатель Товары/Заказ, landscape grid+cart, сумма4 BYN и footer доступны без перекрытий в показанных состояниях.

External updater: CATALOG/WORKSPACE/PARKED/HOME UPDATE PASSED и UPDATE E2E PASSED, actual package1→2;stock8/currentqty5total15/parked delivery label total5/home product slot8 сохранены. Signing/publish/production-upgrade skipped на develop. Предыдущие40178918977 и40182783528 выявили исправленные UI/fixture ошибки;40183539310 прошёл47instrumentation, но остановился на сборе артефактов после UTP uninstall. Final driver явно удерживает instrumentation lifetime для2scale cases/capture и делает clean install перед4seed, gates не ослаблялись.

02f W01/W03/W07/DS04 в указанном scope ci-verified. RoomSQLschema5/payloads/signing/main/stable прежние, релиз не публикуется. Physical/OEM/memory pressure/font200%/paired Web walkthrough остаются позже по решению владельца; full02/device не объявлены закрытыми. Следующий шаг — аудит оставшихся acceptance01/02 и подготовка03a (минимальная смена и денежные примеры) перед финансовым кодом.03 автоматически не реализовывался.

## 2026-10-25 — выпуск01/02 и подготовка03a

Владелец явно авторизовал merge/main/release перед новым этапом. mpos-release: RELEASE/config identity прочитаны,4signing Secrets присутствуют (значения не читались). PR1 develop→main,37commits/119files; PR checks40198146760 success, merge87f57ae308768b6793b318a12c229b19cea7ff85. Develop fast-forward и push к main merge. Main Android run40198464669 pending: не утверждать выпуск до signing/publish.

В develop подготовлены PHASE-02-REVIEW/SHIFT-03A/MONEY-03, ADR007 и cross-links: audit CI vsdevice, open/close перенос counted из pinned Web, single-owner boundary, будущие fault/replay/migration/admission gates. Пользователь подтвердил line HALF_UP2/0.135→0.14 и полный cross-shift cash refund. Остальные discount/split allocations pending. preflight/diff passed. Только docs: финансового runtime/новых тестов нет.

## 2026-10-25 — stable0.1.41.1 опубликован

[Main run40198464669](https://github.com/mendelev-main/M-POS-Android/actions/runs/40198464669), merge87f57ae: checks121184328264/emulator36 121184328457/signed121186603487/publish121187280914 success.47instrumentation и actual debug replacement1→2: CATALOG stock8/WORKSPACE currentqty5total15/PARKED deliverylabeltotal5/HOMEslot8 и UPDATE E2E PASSED. JVM/lint/build/Python gate success. Signing log: APK identity, version and pinned native signature verified.

[Releasev0.1.41.1](https://github.com/mendelev-main/M-POS-Android/releases/tag/v0.1.41.1) published/latest, versionCode5101/minSdk36/com.mendelev.mpos. Публичные APK3107719bytes/update.json/checksum скачаны в work вне Git, SHA256/bytes/commit/package/minSdk/pinned cert metadata совпали; verifier в CI проверил криптографическую подпись. Release body дополнен конкретным scope; immutable APK/manifest/checksum не переписывались. Main push production-upgrade skipped по trigger, поэтому отдельно запущен verify-update-channel40199534929 с basev0.1.16.1 на API36; pending до результата. Physical остаётся позже.

Public channel итог: [run40199534929](https://github.com/mendelev-main/M-POS-Android/actions/runs/40199534929), verify36 job121187555269 success. Production package com.mendelev.mpos, public HTTPS check/download/restore/cancel/system install2601→5101 (stable0.1.16.1→0.1.41.1), app-private settings/install receipt preserved. updated-settings.png2560×1800 просмотрен: версия0.1.41.1/code5101 и сообщение успешного обновления, без системного overlay. Это actual release channel evidence на API36; реальные товары/Room данные сохранены отдельным debug replacement gate main, physical device не заявляется. Preparation03a в develop9ba7446; финальные docs preflight/diff passed, Android повторно не запускается для docs-only.
