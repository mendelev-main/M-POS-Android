# Telegram09.11.2026

Задача: перенос Telegram Web в native. Уточнение владельца: пока только Telegram со сменами и отчётами; backend позже. Будущее подключение через URL/ключи, чтобы разные организации настраивали свои установки. Сервер компании, токен бота и ID чата не зашиваются.

Skills: mpos-phase/mpos-domain. Источник pinned pos.html/shifts.js/android-integrations.js, TelegramClient/MPosShiftReceipt/Image/MPosWarehousePdf; HTTPS multipart contract сверён по официальному Bot API. Реальные сообщения агенту отправлять не разрешено, проверки fake/loopback; владелец тестирует реальный чат.

Реализация: encrypted AtomicFile/AES-GCM/AndroidKeyStore, UI Web settings, generation/revision guards, test send, post-commit shift-open text / shift-close PNG, monthly previous-month PDF только при admin/работающем процессе; manual recovery, stable delivery markers/HTTP429 retry_after. Unknown delivery не повторяется автоматически. Backend fields хранятся зашифрованно, сейчас не используются. Нет getUpdates/webhook/backend sync;09 по-прежнему TODO. Backup токенов/конфигурации сейчас не подключён.

Детали, ограничения и проверки: docs/TELEGRAM-08.md. Локально/main без commit/push. Не считать компиляцию подтверждением реальной доставки или внешнего вида на планшете.

09.11: рабочая группа — сообщения открытия/закрытия содержат номер смены (Смена №87 открыта/закрыта) и строку 👤 Сотрудник: фамилия с инициалами. Номер открытия из ShiftDashboardRules.numbers(sequence), закрытия из сохранённого summary.number; совпадает с историей/аналитикой владельца. Подпись к картинке закрытия использует тот же formatter. ФИО в базе/картинке чека не изменяется.
