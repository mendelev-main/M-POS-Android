# Этап07 — контекст

2026-11-01: владелец начал07 с обязательной совместимостью Web. Решения: replace с защитной native-копией+подтверждением; source open shifts/paid partials переносить; unsupported modules preview/explicit skip, original unchanged. BackupManager source проверен: Android .mposbackup — JSON+Base64, не ZIP; первоначальное предположение об архиве исправлено до кода. Существующий source silently drops missing images; native этого не повторяет.

07.1 WP01–WP06 — bounded read/strict exact JSON/photos/preview, implemented/validation pending. Никакого live restore/activate/export/native converter. BACKUP-07.md содержит source matrix и нюансы full financial history/partial40→60/unknown legacy facts/lost-ack/idempotency. Source-generated synthetic v13 fixture shared JVM/androidTest. Schema9/main/signing unchanged. Native backup/restore и Web mappings идут следующими инкрементами; B02–B04 не заявлены. Target policy уточнена владельцем2026-11-02 ниже.

2026-11-02: продолжение07.1. Все3 owner decisions остаются действующими. Дополнительно прочитан privacySanitizeBackup: source backend privacy replay и особый paid loyalty-gift case, включены в BACKUP-07 matrix; никаких source endpoint/token requests в native preview. Local build/lint/debug/testAPK179JVM/0failures/0skipped,16Python/preflight passed; API36 ещё pending. UI seed24→25 добавляет BackupUiTest при прежнем360s budget/строгом OK25; SettingsVisualUiTest проверяетреальный backup route/recreation и planned info на другом ещё неподключённом разделе. Полный restore по-прежнему отсутствует и в UI явно не обещается.

2026-11-02: через GitHub contents API проверены текущие Web backup.js и Android BackupManager.kt: JS version13 и точное совпадение обоих файлов с reference. Reference commit не изменялся, HEAD не подставлялся в требования.

2026-11-02: владелец разрешил replace активного target native с защитной копией и дополнительным подтверждением. Не вводить постоянный запрет target active/partial. Защитная копия должна включать текущую целевую смену и paid progress, например30BYN; Web paid40 восстанавливается как отдельный source dataset, не суммируется и не теряется. Before activation нужны точный native snapshot/verified recovery copy и повторный preview/confirm при изменении target generation/revision.07.1 по-прежнему только чтение.


2026-11-02: CI41405536715/ec5ac24 checks179JVM SUCCESS; Android251 suite failed only SettingsVisualUiTest: после backup route/recreate тест искал локальный Text Назад, которого в main-shell нет (MposAppBar использует общий top navigation). Исправлен тестовый переход через unique topNav-settings; backup screen/recreation/planned-window/admin assertions сохранены, runtime/gates не ослаблены. Полный повтор готовится; restore/export всё ещё не заявлены.
