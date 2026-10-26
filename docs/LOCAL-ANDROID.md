# Локальный Android 16 на Mac

## Текущий статус —2026-10-27

Android Studio Rabbit1/2026.2.1 находится в `/Applications/Android Studio.app`; signature `codesign --verify --deep --strict` прошла. Первичная настройка Studio завершена с существующим SDK, без повторной загрузки компонентов и без отправки usage statistics.

Планшет **M-POS Android 16 Tablet** зарегистрирован в графическом Device Manager. Запущен именно из Android Studio кнопкой ▶: adb подтвердил `sys.boot_completed=1`, release16/API36, AVD `M_POS_API36`, serial `emulator-5554`. Debug M-POS установлен и открыт, Activity start Status:ok; screenshot1600×1125 просмотрен: каталог/навигация полностью отображаются, системного overlay нет.

Локальная instrumentation проверка `RefundViewModelTest` —**OK(3tests)** на этом ARM64 Android16. Это отдельный local smoke, не весь CI-suite и не physical tablet acceptance. Test APK после проверки удалён, основное debug приложение оставлено открытым; товары/чеки пользователя тестом не создавались.

## Запуск без Terminal

1. Открыть **Android Studio** из «Программы».
2. На стартовом экране: **More Actions → Virtual Device Manager**.
3. Нажать ▶ у **M-POS Android 16 Tablet**.
4. Открыть M-POS на планшете. Новую debug-сборку можно установить, перетащив APK на окно Android.

[Официальная инструкция Device Manager](https://developer.android.com/studio/run/managing-avds). SDK/профиль уже установлены; новый проект или повторное скачивание Android для этого не требуется.

## Пути и идентичность

- SDK: `/Users/Aleksandr/Library/Android/sdk`.
- AVD: `/Users/Aleksandr/.android/avd/M_POS_API36.avd`, pointer `M_POS_API36.ini` в той же папке.
- SDK и AVD перемещены из `../android-local` в стандартные папки с выданным владельцем разрешением. Старые пути сохранены символическими ссылками для совместимости команд.
- Gradle8.13/tools/cache остаются в `../android-local`. JDK17 —Homebrew `/opt/homebrew/opt/openjdk@17`.
- Android16/API36 Google APIs ARM64 image revision7, Emulator37.2.12/build16428233, Pixel C1600×1125px/200dpi (1280×900dp),4096MiB RAM/4cores.
- Package `com.mendelev.mpos.debug`; production signing/package и физические устройства не изменены.
- Official Android Studio ARM64 DMG SHA256 `16d4a0f8a52413b51869fc18d5bdcdf9cecd38615cd7bb7cf14d51b99d2fef54` проверен по [странице Google](https://developer.android.com/studio).
- Official command-line tools15859902 SHA256 `835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e`; ARM64 image SHA1 из official sys-img manifest `5a99183b6d924da606260e45fd41a3eb8eca6eb7`. Gradle SHA256 совпадает с wrapper properties.

## Команды для разработки

```sh
/bin/bash scripts/local-android.sh status
/bin/bash scripts/local-android.sh build
/bin/bash scripts/local-android.sh launch
/bin/bash scripts/local-android.sh test
/bin/bash scripts/local-android.sh stop
```

Скрипт обнаруживает уже запущенный **именно M_POS_API36** на emulator serial, включая5554 из Studio; повторный AVD на5560 не создаётся. Если профиль не запущен, fallback стартует на5560, сохраняя проверку имени/API36. Другие emulator/physical devices не используются. Передача `MPOS_ANDROID_LOCAL_ROOT`/`MPOS_JAVA_HOME` поддерживается.

`launch` с открытым Studio-планшетом проверен: reuse5554→APK install Success→Activity Status:ok. Local build:77JVM/0failures/0errors/0skipped, lintDebug/assembleDebug/assembleDebugAndroidTest SUCCESS. Android CI API36 обязателен независимо от local smoke.

## История первых попыток

2026-10-26 restricted agent process не получил CPU sysctl, Qt abort/NEON и headless SIGILL зафиксированы crash reports. Пользовательский `.command` открывался редактором, затем direct `.sh` exec получил Operation not permitted. Эти попытки не считались успешным Android boot. Графический запуск через настроенную Android Studio2026-10-27 решил задачу; security protections/TCC/quarantine не отключались. Существующие shell launchers используют явный Bash, но основной пользовательский маршрут теперь Device Manager.

### Дубликаты генерируемых файлов в Documents

2026-10-27 локальный D8 обнаружил `MainActivityKt 2.dex` рядом с `MainActivityKt.dex`; также появились дубликаты XML/HTML с суффиксом `2`. Это конфликт производных файлов в каталоге сборки, не две реализации класса в Kotlin. Только локальный `app/build` перенесён в `/private/tmp/mpos-native-build-*/app` и заменён symlink; исходники/SDK/AVD/данные приложения не переносились. Удалены15 дубликатов только внутри генерируемого build. Последующая сборка прошла. Gitignore исключает и обычный каталог, и symlink `app/build`.

`/private/tmp` — временный cache: если macOS его очистит и ссылка `app/build` станет нерабочей, удалить только нерабочую ссылку и повторить сборку (Gradle создаст производный каталог заново). Не удалять исходники, SDK/AVD или базу приложения ради ошибки D8. Эта локальная настройка не переносится на CI; CI использует чистый workspace.
