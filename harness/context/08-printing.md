# Печать09.11.2026

Текущая задача владельца: перенос всей LAN-печати из Web, локально без push. Источники: pinned network-printer.js, Android MPosReceiptLayout/EscPosPrinter/CashDrawerCommand, payment/parked/shifts. Skills mpos-phase и mpos-domain. Основной контракт: docs/PRINTING-08.md.

Выбор: финансовые суммы только из сохранённого receipt; печать — после commit, никогда не повторяет оплату. AtomicFile device-local конфигурации + claims вне dataset; при неизвестном сетевом результате автоматического retry нет, ручной повтор явный. Кухня при park печатает delta точных количеств, при pay уже запланированная кухня не дублируется. Native manual ящик разрешён при активной смене или admin по принятому владельцем правилу; источник Web manual требует admin.

Не запускать реальные принтеры ради проверки. JVM/fake transport + compile instrumented; D05 требует бумажных образцов владельца. Модули06/09, Telegram, printer backup/import не включены. Локальная сетка настроек и модальный редактор сохранены по последнему решению владельца.

2026-11-10: по запросу текущего Web исправлены row geometry, bitmap margins, split/cash, loyalty и LF перед cut. Источники/нюансы/проверки: ../../docs/PRINT-CATEGORIES-TOPBAR-PARITY.md. D05 физическая приёмка pending. Без remote.
