---
name: mpos-release
description: "Собрать и проверить подписанный APK M-POS: штатный workflow, отдельный package, возрастающая версия и закреплённый сертификат. Использовать при выпуске APK и отладке signing CI."
---

# Выпуск M-POS

Прочитайте `docs/RELEASE.md` и `config/android-release.json`. Native package — com.mendelev.mpos, Web — com.mendelev.mpos.web, debug — com.mendelev.mpos.debug. Изменение ключа не создаёт самостоятельное приложение: identity задаёт applicationId.

Перед выпуском убедитесь, что Android checks, Python tests и emulator smoke относятся к тому же commit. Используйте `.github/workflows/android.yml`: signed job доступна только main после gates. Fork PR не должен получать secrets; не менять trigger на pull_request_target для выполнения PR кода.

Secrets брать только из существующего private bundle/Actions. Не генерировать временный ключ при их отсутствии; не печатать secret значения и не включать keystore в artifact. `scripts/release.py signing-setup` проверяет cert и доступ к private key. `verify-apk` требует ожидаемые package/version и apksigner verification с точным certificate SHA-256.

Публиковать APK только после verifier; при ошибке исправить причину, не ослаблять проверку. Сохранять отчёты checks отдельно от APK. Новый run/retry увеличивает versionCode; app identity и cert при обновлении постоянны. Не сбрасывать run counter/версии workflow без плана миграции.

Физическая приёмка: две установки native/Web одновременно; затем второй native APK обновляет первый с сохранением данных. Проверки CI не доказывают UX/принтер/данные на реальном устройстве. Результат и Actions URL добавить в build-log.
