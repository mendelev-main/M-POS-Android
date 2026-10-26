# Контекст 00 — 2026-10-23

Новый репозиторий был пуст (нет commits). Web срез `1682c88334e6fb13586ce4b530157c59ca062e43`. Web package уже `.web`, поэтому native берёт `com.mendelev.mpos`; новый ключ создаётся отдельно. На рабочем компьютере есть JDK 17, отсутствует стандартный Android SDK, gh не авторизован. GitHub connector доступен с admin/push; публикация может использовать Contents/Git API. Отсутствие local SDK не следует наследовать как вечное правило запрета тестов.

Пользователь просит инфраструктуру и документы до этапной реализации. В 00 входит только минимальный Compose экран для проверки APK; реальная каталогизация начинается в 01. Правила чужого репозитория не копируются. Статус проверки — исключительно build-log.

## Обновление 2026-10-24

Владелец авторизовал gh, четыре signing secrets установлены подготовленным скриптом. Полный run 40023385742 — success, подписанный APK 0.1.3.1/versionCode 1301 с закреплённым native certificate опубликован в Actions artifact. Блокер secrets снят; hardware acceptance остаётся pending. Исследование и реализация этапа 01 не начаты.

## Целевой стек, 2026-10-24

Создан `docs/STACK.md` (ADR-002) по запросу описать технологии для полного native parity. Это техническое решение, а не реализация 01. Отделены фактически установленные versions/dependencies от выбранных будущих компонентов. Все стадии сохраняют Kotlin domain, локальную Room transaction и независимую identity. Дополнительно определены Hilt/KSP, Coil, typed navigation/JSON, OkHttp/SSE, WorkManager, нативные PDF/XLSX/ESC-POS и Keystore. Версии будущих библиотек проверять при начале соответствующего этапа.

## 2026-10-27 — единая верхняя панель до следующих модулей

По запросу владельца переносим pinned renderTopbar, не начинаем склад04. NAV01–NAV03 в docs/TOP-NAVIGATION.md: M POS/Заказы/Приёмка/Чеки/Аналитика/Бронирования/События/смена/settings. Source Заказы=purchaseOrders (заказы поставщикам), не parked/web orders. Товары/debug доступны через настройки. Общая панель через navigation context с локальными command/editor locks; route saveable, смена из существующего ShiftViewModel/Room. Будущие разделы явно не реализованы, событий/инвентаризации без источника данных нет. Room8/снимки/финансовая логика/подпись/stable/main без изменений.

Локально API36 emulator-5554: TopNavigationUiTest и TopBarLayoutTest OK2; narrow520dp прокрутка/settings62dp, отключённые callbacks, все маршруты/recreation/editor lock. testDebugUnitTest/lintDebug/assembleDebug/assembleDebugAndroidTest passed. Сравнение исходного extracted Web header в Chrome1470px и native1600px/200dpi1280dp выполнено визуально с учётом density; это не автоматический pixel diff одинаковых viewport. Полная CI/regression/font-scale/dark/physical пока pending.
