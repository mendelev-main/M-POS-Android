# Локальный Android 16 на Mac

Среда подготовлена отдельно от Git checkout: соседняя папка `../android-local`. Production signing/данные физического планшета не используются. Нативное приложение устанавливается как `com.mendelev.mpos.debug`.

Официальные источники: [Android SDK tools](https://developer.android.com/studio), [ARM64 image manifest](https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-3.xml).

## Что установлено

- Apple Silicon ARM64, JDK17; официальный Android Emulator37.2.12/build16428233.
- SDK platform API36, platform-tools/adb, build-tools35.0.0.
- Google APIs ARM64 Android36 revision7. Архив с официального dl.google.com проверен по checksum из sys-img2-3.xml: SHA1 `5a99183b6d924da606260e45fd41a3eb8eca6eb7`.
- Официальные command-line tools15859902 ARM64: SHA256 `835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e`.
- Gradle8.13: SHA256 совпадает с gradle-wrapper.properties; отдельный local GRADLE_USER_HOME. Кэш зависимостей скопирован из существующего read-only host cache, файлы SDK/AVD/Gradle не коммитятся.
- AVD `M_POS_API36`, Pixel C,1600×1125px/200dpi (1280×900dp как CI),4096MiB RAM/4cores, порт5560.

Первичная загрузка SDK лицензирована через sdkmanager; скачанные пакеты/архивы в `../android-local`. Большие архивы загружены с официальных адресов частями с проверкой итоговой checksum. System image извлечён в стандартный SDK path; avdmanager успешно создал профиль.

## Запуск из Terminal в папке репозитория

```sh
./scripts/local-android.sh status
./scripts/local-android.sh run
```

`run` запускает планшет, собирает debug APK, устанавливает его с сохранением данных и открывает M-POS. Повторные сборки инкрементальные. Данные AVD сохраняются между запусками.

```sh
./scripts/local-android.sh launch  # установить/открыть уже собранный APK
./scripts/local-android.sh build   # JVM, lint, debug APK
./scripts/local-android.sh test    # Android instrumentation на этом AVD
./scripts/local-android.sh stop   # остановить только M_POS_API36
```

Скрипт проверяет имя AVD и API36, использует только emulator-5560; другой эмулятор на этом порту не затрагивает. `MPOS_ANDROID_LOCAL_ROOT` позволяет указать другую папку SDK, `MPOS_JAVA_HOME` — другой JDK17. `MPOS_ANDROID_HEADLESS=1` — стандартный режим без окна, когда среда допускает запуск виртуальной машины. В обычном Mac запускать без него.

Для Finder подготовлены локальные `.command` launchers в outputs чата: «Запустить M-POS Android 16» (сборка+запуск), «Открыть M-POS без сборки» и «Остановить Android 16». Первые два перезапускают только этот AVD, чтобы после headless проверки получить обычное окно. Архивы и готовый APK локальны и не являются новым production release.

## Наблюдённые проверки и ограничения

2026-10-26: первая локальная сборка закреплёнными Gradle8.13/Kotlin2.2.21/JDK17 выполнила `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL,87tasks. Это первый local Android build evidence, прежний blocker ~/.gradle устранён отдельным writable Gradle cache. `emulator -accel-check` сообщает Hypervisor.Framework доступен; `-list-avds` видит M_POS_API36.

Окно эмулятора **ещё не подтверждено**: процесс агента не может читать CPU sysctl (`hw.optional.neon`: Operation not permitted), Qt GUI прекращается с сообщением NEON, headless запуск получил Illegal instruction. Не изменять бинарники/не отключать CPU checks. Требуется запуск `.command` из обычного Finder/Terminal, вне ограниченного процесса агента; после успешного boot подтвердить `adb ... getprop ro.build.version.sdk`=36 и открыть APK. Локальные instrumentation/GUI acceptance до этого pending. Установка Android Studio для работы этих команд не обязательна; в будущем IDE может использовать этот же SDK/AVD.

Журналы: `../android-local/build.log`, `logs/emulator.log`, `start.log`, `start-headless.log`. Вызов SDK tools из ограниченного агента не является доказательством успешной загрузки Android. CI API36 остаётся обязательным независимо от local environment.

Повторный запуск через готовый скрипт `./scripts/local-android.sh build assembleDebugAndroidTest`: BUILD SUCCESSFUL5s/87tasks (1executed,86up-to-date),77JVM/0failures. Скрипт закрепляет JDK17, отключает неподдержанное отслеживание FSEvents для Gradle и удаляет конфликтующие deprecated prefs variables только из собственного процесса. Bash3.2 syntax/status/build проверены.

Если двойное нажатие `.command` открывает редактор: контекстное меню →«Открыть в программе»→Terminal, либо `/bin/bash "/полный/путь/к/файлу.command"` в Terminal. В outputs также создан `.app` launcher с корректным Info.plist: явно открывает `.command` через системный Terminal независимо от ассоциации. Launcher ещё требует пользовательского первого запуска; он не отключает macOS protections и не меняет ассоциации файлов.
