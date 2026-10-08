# CI, версии и постоянная подпись APK

По решению владельца проекта агент пишет код, рефакторинг и новые тесты, но не запускает локально обычные JS/JVM тесты, lint и сборку APK. Эти проверки выполняет GitHub Actions; владелец просматривает результаты и сообщает о сбоях. Не считать проверку успешной до реального результата Actions.

## Две отдельные jobs

`tests`: JS, Python проверки нумерации версий, Kotlin unit tests и Android lint. Отчёты сохраняются даже при ошибке.
`build`: после успешной `tests` собирает APK. Для main публикуется только подписанный release APK с JSON метаданными и SHA-256. PR компилирует debug APK без публикации установочного артефакта. Секреты из fork PR недоступны.

## Постоянный ключ

Создан один RSA 3072 / SHA256withRSA ключ, срок 12000 дней, alias `mpos-release`, PKCS12. Закрытый ключ и пароли находятся вне Git, в приватной папке outputs/mpos-signing-private рабочего чата. Сохранить защищённую резервную копию папки: потеря ключа лишит возможности обновлять установки с этим сертификатом.

В Settings → Secrets and variables → Actions → Repository secrets добавить четыре значения из одноимённых `.txt` файлов:

- `MPOS_KEYSTORE_BASE64`
- `MPOS_KEYSTORE_PASSWORD`
- `MPOS_KEY_ALIAS`
- `MPOS_KEY_PASSWORD`

Ключ нельзя вставлять в чат, коммитить, прикладывать к issues или публиковать как Actions artifact. CI восстанавливает его в RUNNER_TEMP и удаляет после сборки. Отсутствующие секреты вызывают явную ошибку; новый случайный ключ никогда не создаётся. Публичный отпечаток закреплён в config/android-release.json; проверяется фактический подписант APK, package ID, версия и отсутствие debuggable.

Старые Actions публиковали debug APK с временным ключом runner. Новый release APK имеет другой сертификат, поэтому Android не позволит обновить такую установку поверх старой. Перед первым переходом сделать резервную копию данных, затем переустановить приложение и восстановить данные. Следующие release APK используют один ключ и более высокий versionCode.

## Версия

versionCode = 1000000 + GITHUB_RUN_NUMBER × 1000 + GITHUB_RUN_ATTEMPT. Повторный запуск тоже увеличивает версию; допустимы попытки 1–999. versionName = 0.1.<run_number>-<run_attempt>. Gradle получает значения через MPOS_VERSION_CODE и MPOS_VERSION_NAME. Локальные development defaults: 1 / 0.1.0.

Сохранять идентичность workflow android.yml. При сбросе счётчика или переносе workflow сначала увеличить versionCodeBase так, чтобы новый APK превышал все ранее распространённые версии. Не устанавливать старый успешно собранный APK поверх более новой версии.

## Состояние этапа

Подготовлены workflow, конфигурация Gradle, ключ, проверка артефакта и тесты нумерации. Подключение Secrets и первый успешный подписанный APK пока ожидают выполнения. Это инфраструктура для этапа 111; нативная проверка скачанного обновления внутри приложения ещё не реализована. Счётчик завершённых этапов миграции не увеличивается.

Источники: [Android signing](https://developer.android.com/studio/publish/app-signing), [Android versioning](https://developer.android.com/studio/publish/versioning), [GitHub contexts](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts).
