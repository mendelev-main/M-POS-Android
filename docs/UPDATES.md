# Обновления M-POS — этап 00.1

## Сценарий пользователя

Настройки → Обновления: видны установленная версия, стабильный канал, последняя успешная проверка. При открытии настроек приложение проверяет `releases/latest/download/update.json`. Доступны ручная повторная проверка, описание новой версии, скачивание с прогрессом и отменой. Отсутствие сети не мешает работе остального приложения.

После скачивания APK проверяется, затем появляется «Установить обновление». Если Android ещё не разрешает этому приложению запрос установки, открываются системные настройки разрешения. После возврата пользователь снова нажимает установку. Android показывает стандартное подтверждение обновления. Root/MDM не нужны; тихая установка не обещается.

Файл передаётся через FileProvider с read grant только системному установщику. Cancel не считается успехом. Android заменяет приложение с тем же package/signature и может завершить его процесс. После следующего запуска версия читается из PackageManager; сохранённый installTarget позволяет подтвердить успешное обновление. Пользователь открывает приложение после установки; автоматический перезапуск не обещается.

В этой итерации нет товаров/продаж и незавершённых финансовых операций. При реализации этапа 03 установка должна блокироваться на время commit/recovery и начинаться после сохранения рабочего состояния. Этот guard нельзя заменить наличием кнопки в настройках.

## Первое подключение существующего планшета

Bootstrap 0.1.3.1 не содержит updater. Новую подписанную версию с ним нужно один раз установить вручную поверх старой, без удаления приложения. Далее — настройки и системное подтверждение. Это полный APK: пропущенные промежуточные версии устанавливать по очереди не требуется; совместимость будущих данных обеспечивают migration paths.

Текущий выпуск — [0.1.16.1](https://github.com/mendelev-main/M-POS-Android/releases/tag/v0.1.16.1), первый каталог01a. Для проверки механизма вручную установите [0.1.8.1](https://github.com/mendelev-main/M-POS-Android/releases/tag/v0.1.8.1), затем в настройках скачайте и установите текущий stable release. Всегда использовать native release package и постоянный сертификат; APK Web к этому каналу не относится.

## Доверие и хранение

- Production источник фиксирован: публичный `mendelev-main/M-POS-Android`, стабильные GitHub Releases. Токен GitHub в APK отсутствует.
- Manifest ограничен 64 KiB; notes — 8000 символов, APK — 200 MiB. Учитываются размер файла, SHA-256, applicationId, versionCode/versionName, minSdk и закреплённый signer.
- Принимается только APK с versionCode выше установленного. Подмена package на Web, downgrade, другой signer и повреждённая загрузка отвергаются до запуска установщика.
- На API 28/29 запрос archive включает GET_SIGNATURES вместе с GET_SIGNING_CERTIFICATES, чтобы Android вызвал collectCertificates; сравнение точного текущего signer остаётся обязательным. Основание: [AOSP Android 9 PackageManager](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-9.0.0_r47/core/java/android/content/pm/PackageManager.java).
- Runtime проверяет архивные signing metadata через PackageManager; окончательную криптографическую проверку устанавливаемого APK выполняет Android. В CI используется `apksigner verify`.
- HTTPS и стандартная TLS-проверка; перенаправления ограничены GitHub release asset hosts. Произвольные endpoint/credentials из Intent в release не принимаются.
- `.part` не устанавливается. Готовый APK и его metadata хранятся в app-private `files/updates`. Metadata пишется атомарно. После restart готовый APK проверяется заново; повреждённый/устаревший удаляется. Незавершённая загрузка начинается заново, HTTP resume не обещается.
- Отмена загрузки проверяется между блоками IO; блокирующее чтение ограничено read timeout 15 секунд. Пока отмена не завершена, новая операция не начинается.
- Маленькие служебные markers/lastChecked хранятся в приватных SharedPreferences; это не хранилище кассы. Готовый descriptor — AtomicFile. DataStore для пользовательских preferences остаётся следующим этапом стека.
- Никакие товары/backup/ключи подписи в release assets не включаются. Android signing JKS остаётся только Actions Secret и приватной резервной копией.

## Выпуск версии

Actions → Android → Run workflow → ветка main → `publish_release=true` + описание `release_notes`. Подписанные сборки обычных push/manual с false остаются artifacts, пользовательского обновления не создают.

Порядок: harness/JVM/lint + emulator API36 с self-update → signed APK → verify identity/signature/version → assets → отдельная publish job с contents:write. Все прочие jobs остаются contents:read. Publish скачивает проверенный artifact этого run, сверяет checksum/commit, запрещает движение stable назад, создаёт draft с полным набором assets, только затем публикует latest. Уже выпущенные assets не перезаписываются; новый run/retry даёт новую версию. Прерванный publish может оставить draft — он не предлагается планшетам.

Release `v<versionName>` содержит:

- `mpos-native.apk` — один универсальный подписанный APK;
- `update.json` — schemaVersion/channel/applicationId/versionCode/versionName/minSdk/apkUrl/apkBytes/sha256/certificateSha256/notes/commit;
- `mpos-native.apk.sha256` — checksum с переносимым именем файла.

Для дополнительной приёмки `verify_upgrade_from=v<предыдущая версия>` запускает после публикации Android16 smoke: предыдущий production APK устанавливается в emulator, новое обновление обнаруживается по настоящему публичному HTTPS адресу, скачивается, Cancel сохраняет прежнюю версию, повторный install повышает versionCode. Проверяются persisted install receipt и lastChecked; debug endpoint/run-as в этом режиме не используются. Это post-publish проверка канала, а не замена pre-publish gates API36.

API Github с пользовательским токеном на планшете не используется. Latest metadata и APK берутся по публичным HTTPS asset URL. Проблемы GitHub/сети отображаются как временная ошибка с повтором; Google Play для этого канала не требуется.

## Проверки

Отдельный Actions → **Verify published update channel** → `base_release=v0.1.8.1` проверяет уже опубликованный latest на Android16, без выпуска APK и signing secrets. Driver допускает только emulator serial и отказывается работать с физическим планшетом. [Run 40075276435](https://github.com/mendelev-main/M-POS-Android/actions/runs/40075276435) прошёл на API 28/35: production 1801→1901, публичные HTTPS assets, восстановление загрузки после restart, первое разрешение источника, Cancel/retry, фактическая новая версия, persisted receipt/lastChecked.

U01–U06 в `harness/build/00.1-updates.md`. JVM tests: версия/identity/minSdk/URL/signers, ограничение manifest, short/oversized body и redirect. Emulator: проверка/загрузка, restart и восстановление готового APK, Cancel, затем реальный системный install 1→2 и сохранение app-private файла. Debug fixture endpoint допускает только loopback/фиксированный emulator host 10.0.2.2 и debug signer/package; release его игнорирует.

Physical pending: разрешение неизвестного источника на пользовательском Android/OEM, подтверждение, сохранение будущих Room данных, ограничения корпоративного устройства, отсутствие места и прерывание питания. Emulator evidence не подменяет эти проверки.

Технические основания: [PackageManager installs permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider), [Android app updates](https://developer.android.com/google/play/app-updates), [GitHub release assets](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases).


Действующая платформа с24.10.2026 — [Android16+](ANDROID-SUPPORT.md), minSdk36 в новых APK/manifest. Прежняя ветка GET_SIGNATURES для API28/29 удалена; archive signer читается современным PackageInfoFlags. Исторические отчёты выше остаются evidence прежних выпусков, не текущей матрицей.
