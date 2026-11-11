#06 — Клиенты и лояльность: текущая итерация09.11.2026

Владелец возобновил06 и запросил parity онлайн-заказов по текущему Web. Срез05db2b7 указан в docs/SOURCE.md. Бэкенд не редактировать/не развёртывать. Все правки локально, отдельного разрешения push нет. Прошедший pipeline не проверять.

Правила, реализация и чеклист: docs/ONLINE-LOYALTY-PARITY.md. Данные организаций изолированы target URL/key/routes и dataset, база не заменяет server balance. BuyN→1, одна доступная награда, накопление делает сервер. Денежные строки native HALF_UP; правило Web gross cheapest/stable tie + no reuse сохранено.

Consent берём с сервера, с актуальной версией и employee открытой смены. Админ-корректировка CAS/reason/requestId. Credential из успешного входа только в памяти, wipe при logout/копии при запросе. Никаких реальных телефонов/ключей/паролей в fixtures.

Новые Room tests используют только собственную in-memory базу и interceptor, без singleton dataset. На пользовательском эмуляторе полный connected suite не запускать: существующий runner и MposApplication могут активировать фоновые Telegram эффекты и изменять пользовательскую базу. Компиляция тестов отдельно от факта их прохождения; ручные UI/live проверки владельцем pending.

09.11 следующая UI-правка ON04: зелёная компактная ready перед типом в Отложенных; current ready перенесена из лишнего верхнего блока в footer между итогом и checkout по Web. Владелец подтвердил live accept→parked, другие live стадии не отмечать проверенными.

09.11 LO02: перенесён profile header/summary/action layout560dp Web; выбранная иконка клиента зелёная при attached ID, включая онлайн-заказы без active loyalty. Логика green не должна зависеть от доступного подарка. Новый pure CustomerProfileWindow отделён от сетевого CustomerWindow для fixture проверок.


10.11: LO03 admin customer presentation перенесена на fullscreen/sidebar/programs/tabs и inline correction. Отправляем consentVersion и CAS; подарок снимается отдельно, reason500. HTTP403 классифицируется по известному admin error без утечки raw response. Native session proof не обязательно равен паролю текущего Web: после явного admin отказа разрешён ввод Web-пароля только в памяти, локальный verifier не меняется, legacy fallback нет. Причина конкретного production403 остаётся неподтверждённой до повтора владельцем; реальных корректировок не выполняли. Подробности и ограничения — docs/ONLINE-LOYALTY-PARITY.md.


10.11 LO02 follow-up: CustomerPickerWindow560 и CustomerCreateWindow460 по loyalty.js+android-customer-input.js. Pill56/fixed +375 prefix/9 national digits, suffix4/stale search guard/cards/avatar/select, no redundant footer; optional enrollment after create. Selected/create без enrollment закрывает modal; GET profile failure не теряет identity и не выдумывает rewards. После create+enroll сначала durable attach через workspace command, затем consent; cancel сохраняет attach. Подтверждённый server ID кэшируется в окне при ошибке local attach, no second create. Gift checkbox persist immediate/rollback. Подробности ONLINE-LOYALTY-PARITY.md; UI/live acceptance pending.

## 11.11 — цена исторической строки в событии лояльности

По подтверждённому сообщению владельца один веб-товар давал два очка. Найдены
два независимых сообщения: inventory settledWebOrderIds и durable receipt
/api/loyalty/sales. Исправление backend вынесено в отдельный пакет: начисляет
только receipt, snapshot освобождает резерв. Android сохраняет unitPrice каждой
строки в неизменяемом body LoyaltyEffectCodec: backend исключает нулевую цену
уже подаренной на сайте позиции, без повторного списания подарка. Идентификатор
чека/время/количества/redemptions не меняются, outbox replay остаётся идентичным.
Существующие сохранённые тела не переписываются. Нужна установка обновления для
новых событий с ценами; старые события без поля сохраняют legacy semantics.

LO03/LO04 implemented locally: две JVM регрессии (paid+quoted zero и all-free),
пять существующих LoyaltyRulesTest и компиляция androidTestKotlin пройдены.
Live/device приёмка и публикация pending; реальные продажи/синхронизация не запускались.
