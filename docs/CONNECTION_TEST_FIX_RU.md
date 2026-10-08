# Исправление тестов Telegram, WEB-заказа и LAN-печати

Срез 8 октября 2026. Исправлены дефекты обратной связи, выявленные для APK из Actions run 37684181861. Сетевая доступность реальной Telegram-группы, backend с ключом пользователя и Wi-Fi-принтера должна быть проверена на новой сборке; этот срез не является физической приёмкой.

## Причины и изменения

- Android вызывал отсутствующий `handleTelegramResult`, хотя reviewed JS определяет `onTelegramResult`. Legacy callback исправлен. Для нового теста Telegram добавлен отдельный результат с requestId: ответ может завершить только свой запрос. Старые команды текста/смены/monthly сохраняют свои callback paths.
- Reviewed `testWebOrder` уже находился в списке tracked operations нативной формы, но возвращал undefined вместо Promise fetch. Ранее аудит ошибочно описал его как отсутствующий в списке. Android adapter теперь возвращает Promise, ждёт HTTP ответ и передаёт результат в поле сообщения native формы. Reviewed iPad исходники и их hashes не изменены.
- Тест печати раньше завершал нативный жест после настройки/постановки, до результата транспорта; browser flash мог остаться под native dialog. Adapter сохраняет reviewed обработчик и disk acknowledgement платформенных настроек, добавляет requestId и ждёт `printed`/`printError`. `printAdmission: ok` не означает завершённую отправку.

Новый adapter `native-connection-tests.js` загружается после settings/print adapters и до native-settings-ui, сохранён в scripts/sync-pos-assets.py и проверках source refresh. Включён по умолчанию; `MPosNativeConnectionTestsEnabled=false` возвращает reviewed handlers для rollback. MainActivity сохраняет исправленный legacy callback и при rollback.

## Поведение формы

- При явном пользовательском тесте форма остаётся в «Выполнение…» до результата; успех/ошибка появляются в уже существующей области сообщения с текущими theme primitives.
- Одновременные повторы одного теста не создают второй запрос/задание. В повторно открытой форме показано «Дождитесь завершения предыдущей проверки».
- WEB тест ограничен 15 секундами, Telegram и пробная печать — 30. Автоматических повторов нет. Timeout WEB/печати предупреждает проверить заказ/бумагу перед ручным повтором: сервер или принтер могли принять данные.
- Поздний ответ закрытой/заменённой формы не меняет новое окно. Correlated late printer events не уходят в общий browser toast чужой операции.
- Telegram берёт введённые в текущей форме token/chat/topic и сохраняет administrator gate. WEB использует сохранённый HTTPS backend/deviceKey и administrator gate; новый тест не синхронизирует каталог.
- Native result содержит requestId, ok и безопасное сообщение, не token или содержимое payload. WEB ошибки показывают HTTP status/сетевую ошибку, а не произвольный ответ backend. Telegram transport failure использует безопасное сообщение вместо текста исключения с возможными параметрами URL.
- Пробная печать ждёт подтверждения сохранения настроек, затем использует LAN IPv4 TCP/9100 и ESC/POS. «Пробная печать отправлена» означает завершение записи в транспорт, не аппаратное подтверждение выхода бумаги. Проверить бумажный чек необходимо.
- Продажи, arithmetic, Room schema/ownership, backup v13, availability retry gate и ручная политика catalogue sync не изменены. Эти исправления не удаляют активный WebView.

## Автоматическая проверка

**572/572 JS, 457/457 JVM; 0 failures/errors/skips. Lint: 0 ошибок, 22 предупреждения в существующих файлах** (включая dependency version advisories). Приложение APK локально не собиралось; GitHub Actions собирает APK после публикации.

JS покрывает success, HTTP отказ, offline, timeout/abort, ложный/повторный/поздний ответ Telegram, validation/auth, duplicate requests, ожидание terminal printer event вместо admission, отказ очереди, failed settings commit без отправки, реальную settings disk-ack последовательность, native form feedback, закрытие/замену/повторное открытие окна и source-sync hashes. JVM проверяет correlated credential failure, отсутствие token в результате, legacy callback и совместимый fallback отсутствующего нового callback.

Физическая приёмка pending: Telegram success/invalid credentials/topic/network; WEB success/401/timeout и наличие единственного тестового заказа; LAN printer success/недоступный IP/нет бумаги, безопасность ручного повтора, закрытие формы во время ожидания, обе темы и крупный шрифт. Не очищать рабочие данные для проверки.

## Прогресс

Крупные этапы **107/110 — 97,27%**; 109 **6/20 — 30%**; детальный общий план **113/129 — 87,60%**, осталось 16 задач. Новых migration IDs нет, 109.06 завершён независимым срезом `9ad8c00`, поверх которого перенесено это исправление. Исправление проверок интеграций не закрывает 109.16/109.17 или физическую 110.
