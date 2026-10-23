# Обновления M-POS — этап 00.1

## Сценарий пользователя

Настройки → Обновления: видны установленная версия, стабильный канал, последняя успешная проверка. При открытии настроек приложение проверяет `releases/latest/download/update.json`. Доступны ручная повторная проверка, описание новой версии, скачивание с прогрессом и отменой. Отсутствие сети не мешает работе остального приложения.

После скачивания APK проверяется, затем появляется «Установить обновление». Если Android ещё не разрешает этому приложению запрос установки, открываются системные настройки разрешения. После возврата пользователь снова нажимает установку. Android показывает стандартное подтверждение обновления. Root/MDM не нужны; тихая установка не обещается.

Файл передаётся через FileProvider с read grant только системному установщику. Cancel не считается успехом. Android заменяет приложение с тем же package/signature и может завершить его процесс. После следующего запуска версия читается из PackageManager; сохранённый installTarget позволяет подтвердить успешное обновление. Пользователь открывает приложение после установки; автоматический перезапуск не обещается.

В этой итерации нет товаров/продаж и незавершённых финансовых операций. При реализации этапа 03 установка должна блокироваться на время commit/recovery и начинаться после сохранения рабочего состояния. Этот guard нельзя заменить наличием кнопки в настройках.

## Доверие и хранение

- Production источник фиксирован: публичный `mendelev-main/M-POS-Android`, стабильные GitHub Releases. Токен GitHub в APK отсутствует.
- Manifest ограничен 64 KiB; notes — 8000 символов, APK — 200 MiB. Учитываются размер файла, SHA-256, applicationId, versionCode/versionName, minSdk и закреплённый signer.
- Принимается только APK с versionCode выше установленного. Подмена package на Web, downgrade, другой signer и повреждённая загрузка отвергаются до запуска установщика.
- Runtime проверяет архивные signing metadata через PackageManager; окончательную криптографическую проверку устанавливаемого APK выполняет Android. В CI используется `apksigner verify`.
- HTTPS и стандартная TLS-проверка; перенаправления ограничены GitHub release asset hosts. Произвольные endpoint/credentials из Intent в release не принимаются.
- `.part` не устанавливается. Готовый APK и его metadata хранятся в app-private `files/updates`. Metadata пишется атомарно. После restart готовый APK проверяется заново; повреждённый/устаревший удаляется. Незавершённая загрузка начинается заново, HTTP resume не обещается.
- Отмена загрузки проверяется между блоками IO; блокирующее чтение ограничено read timeout 15 секунд. Пока отмена не завершена, новая операция не начинается.
- Никакие товары/backup/ключи подписи в release assets не включаются. Android signing JKS остаётся только Actions Secret и приватной резервной копией.

## Выпуск версии

Actions → Android → Run workflow → ветка main → `publish_release=true` + описание `release_notes`. Подписанные сборки обычных push/manual с false остаются artifacts, пользовательского обновления не создают.

Порядок: harness/JVM/lint + emulator API 28/35 с self-update → signed APK → verify identity/signature/version → assets → отдельная publish job с contents:write. Все прочие jobs остаются contents:read. Publish скачивает проверенный artifact этого run, сверяет checksum/commit, запрещает движение stable назад, создаёт draft с полным набором assets, только затем публикует latest. Уже выпущенные assets не перезаписываются; новый run/retry даёт новую версию. Прерванный publish может оставить draft — он не предлагается планшетам.

Release `v<versionName>` содержит:

- `mpos-native.apk` — один универсальный подписанный APK;
- `update.json` — schemaVersion/channel/applicationId/versionCode/versionName/minSdk/apkUrl/apkBytes/sha256/certificateSha256/notes/commit;
- `mpos-native.apk.sha256` — checksum с переносимым именем файла.

API Github с пользовательским токеном на планшете не используется. Latest metadata и APK берутся по публичным HTTPS asset URL. Проблемы GitHub/сети отображаются как временная ошибка с повтором; Google Play для этого канала не требуется.

## Проверки

U01–U06 в `harness/build/00.1-updates.md`. JVM tests: версия/identity/minSdk/URL/signers, ограничение manifest, short/oversized body и redirect. Emulator: проверка/загрузка, restart и восстановление готового APK, Cancel, затем реальный системный install 1→2 и сохранение app-private файла. Debug fixture endpoint допускает только loopback и debug signer/package; release его игнорирует.

Physical pending: разрешение неизвестного источника на пользовательском Android/OEM, подтверждение, сохранение будущих Room данных, ограничения корпоративного устройства, отсутствие места и прерывание питания. Emulator evidence не подменяет эти проверки.

Технические основания: [PackageManager installs permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider), [Android app updates](https://developer.android.com/google/play/app-updates), [GitHub release assets](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases).
