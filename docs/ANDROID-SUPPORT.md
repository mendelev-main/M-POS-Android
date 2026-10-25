# Поддерживаемая платформа

Решение владельца от24.10.2026: приложение предназначено для **Android16 и новее**. Минимум minSdk36, compileSdk/targetSdk36. [Официальное соответствие Android16/API36](https://developer.android.com/about/versions/16/setup-sdk).

Текущий обязательный emulator gate — API36. Матрица Android9/15 (API28/35) удалена из Android и Verify published update channel workflows; production upgrade smoke также использует36. Новые версии Android проверяются отдельно при их выборе для целевых устройств; один успешный API36 run не означает проверку всех будущих ОС.

minSdk36 закреплён в app/build.gradle.kts и config/android-release.json; harness отклоняет рассогласование. Генератор update.json берёт minSdk из config, release verifier сверяет реальный sdkVersion APK с config. Package, signing key и счётчик версий не меняются. Устройства с Android ниже16 не являются целевыми и не смогут установить новый APK.

Старые отчёты API28/35 и выпуски сохраняются как исторические доказательства. Updater на Android16 всё ещё умеет прочитать minSdk старого manifest, чтобы корректно определить, что старая версия не новее; это совместимость формата метаданных, а не прогон приложения на старой ОС. Эндпоинты/manifest новых debug fixtures и production assets используют36.

Build Tools35.0.0 — версия сборочных утилит, не Android15 emulator; она остаётся закреплённой. Её изменение не требуется для сокращения runtime-матрицы. Обновление AGP/Kotlin/BuildTools — отдельная проверка.

Вся работа идёт в develop. Публикация на планшеты — после готового блока и merge в main, по DEVELOPMENT-WORKFLOW. Применение minSdk к релизу произойдёт с этим merge; эта итерация не выпускает APK.


Проверено на commit7cf27d9: [run40099706934](https://github.com/mendelev-main/M-POS-Android/actions/runs/40099706934) —24 JVM,20 instrumentation на Android16/API36 и self-update E2E success. В run нет emulator28/35. Это текущая runtime evidence.
