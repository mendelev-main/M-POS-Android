# APK, подпись и GitHub Actions

## Identity

Release: `com.mendelev.mpos`, имя M-POS. Debug: `com.mendelev.mpos.debug`, M-POS Dev. Web: `com.mendelev.mpos.web`, независимый sandbox. Разные applicationId обеспечивают соседнюю установку; для обновлений native сохраняем его package и сертификат. Новый ключ сам по себе не даёт установить APK рядом с тем же package.

Публичный fingerprint native — в `config/android-release.json`. Новый RSA-3072 JKS создан однократно вне Git; пароль и private key в репозиторий не входят. Срок сертификата — 10000 дней. Сохраните private bundle в защищённой резервной копии: без него подписать совместимое обновление вне Play App Signing не получится. Не перегенерировать ключ при потере Actions secrets.

## Однократная настройка Secrets

Settings → Secrets and variables → Actions в **M-POS-Android**, четыре repository secrets:

| Имя | Значение из private bundle |
|---|---|
| MPOS_KEYSTORE_BASE64 | Base64 целого JKS, не fingerprint и не путь |
| MPOS_KEYSTORE_PASSWORD | Пароль keystore |
| MPOS_KEY_ALIAS | mpos-native |
| MPOS_KEY_PASSWORD | Пароль ключа |

Вариант через CLI, после `gh auth login` владельцем с доступом к Actions secrets:

```sh
python3 scripts/install-signing-secrets.py --source /absolute/private/path/credentials.json
```

Скрипт сверяет публичный fingerprint перед загрузкой и не печатает значения. `credentials.json` содержит четыре поля для ручного ввода или CLI. Не использовать ключ Web. Загрузчик привязан только к `mendelev-main/M-POS-Android`. Первый workflow может успешно пройти checks и остановиться на missing secrets; это ожидаемый blocker выпуска, а не повод менять gates.

## Workflow

PR/push main/manual: harness + tooling tests; Android JVM tests/lint/debug build; отдельная emulator job запускает реальный Compose smoke и recreate. JVM business tests появятся с доменной реализацией 01. Все actions закреплены по commit SHA.

Signed job зависит от обеих jobs и запускается только main, не PR. Сначала проверяются наличие secrets, JKS/alias/password, восстановление private key и совпадение сертификата; затем assembleRelease и `apksigner verify` + проверка package/version/certificate. Artifact загружается только после verifier, содержит APK и checksum. Reports — отдельные artifacts, ключ в них не входит. Ключ временно в RUNNER_TEMP и удаляется always. Скрипты не выводят приватные ошибки keytool.

`versionCode = 1000 + run_number * 100 + run_attempt`, `versionName = 0.1.<run>.<attempt>`; retry 1..99, run до 20000000, предел Android проверен tooling tests. Версия растёт при каждом run/retry. Не пересоздавать/переименовывать workflow со сбросом счётчика без новой версии, превышающей все опубликованные APK. Локальная debug версия 1 и другой package.

Без GitHub Secrets release job завершается ошибкой и не публикует неподписанный APK. Fork PR выполняет код без secrets; не использовать pull_request_target для untrusted PR. Actions не публикует GitHub Release автоматически: APK доступен как Actions artifact 30 дней.

## Физическая проверка

Скачать artifact → распаковать → установить APK рядом с Web. Затем следующий native APK установить как обновление и проверить данные. Подтверждение записи SQLite/UX/периферии требует приёмки соответствующего этапа. Bootstrap пока не содержит пользовательских товаров.

Технические основания: [applicationId](https://developer.android.com/build/configure-app-module), [APK signing](https://developer.android.com/studio/publish/app-signing), [GitHub Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
