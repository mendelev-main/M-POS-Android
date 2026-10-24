# Клиенты, лояльность, зал и бронирования

Паспорта M16/M17, этап 06, planned. Дополняет [OPERATIONS-SPEC.md](OPERATIONS-SPEC.md) и [SETTINGS-SITE-SPEC.md](SETTINGS-SITE-SPEC.md). Источник: pinned Web `loyalty.js`, `hall-bookings.js`, `docs/ORDER_CUSTOMER_PROFILE_RU.md`. Remote contracts не считаются известными по одному экрану.

## M16: клиент текущего заказа

Состояния: без клиента → поиск → нет результатов/список/ошибка → явный выбор → профиль. Ввод последних 4 цифр нормализуется; несколько совпадений не выбираются автоматически. Profile показывает имя/нужный контакт, loyalty status, consent state, применимость подарка и доступную ссылку Telegram. Отказ сервера не отображается как «у клиента 0 бонусов».

SelectCustomer фиксирует customerRef/подтверждённые данные и пересчитывает preview. Смена/удаление клиента очищает прежние username/allocations/status; order lines не исчезают. Персональные данные не копируются в каждый служебный log. Нужно определить минимальный receipt snapshot клиента для аудита без бесконечного копирования полного профиля.

Consent: отдельное действие с version/source/time; отсутствие/отказ не заменяется автоматическим true при поиске. Текст, история отзыва и разрешённый функционал при отсутствии согласия — решение владельца до D05. Native не придумывает юридическую политику.

## M16: подарок и начисление

Eligibility server response + выбранные строки → allocation preview. Checkbox «Использовать» — намерение, не окончательное списание. Если нет подходящего товара/право недоступно/сервер неизвестен, показать причину; не делать доставку или другие строки бесплатными. Удаление qualifying строки/смена клиента требует revalidation allocation.

Checkout принимает только allocation с подтверждённой допустимостью по согласованному контракту. Комбинация gift+discount и распределение суммы фиксируются fixtures до кода. Sale сохраняет allocation snapshot и durable effectId. После HTTP timeout server entitlement не считается возвращённым/повторно доступным автоматически; reconciliation по тому же ключу.

LoyaltyTransport имеет отдельную binding/secret provider зависимость раньше online orders09: endpoint/auth/errors/idempotency/lookup result описываются в D05. Пока это не готово, локальный клиент/зал возможны, реальное начисление/подарок не выдаются за работающие. Remote failure не откатывает committed обычную продажу; политика разрешения конкретного подарка offline требует отдельного решения.

## M17: столы и план зала

HallTable: стабильный ID, label/number, seats, position/shape/rotation, active/revision. UI режим просмотра и редактирования явно различается; обычный tap не перетаскивает стол. Сохранение layout проверяет revision и диапазоны, rotation/process death не теряют committed layout. Удаление стола с активными booking/order refs запрещается либо требует утверждённого переноса, не cascade delete.

Связь order↔table хранится отдельно от текстовой подписи: переименование стола не меняет orderId. Table selection не оплачивает заказ и не резервирует stock второй раз. Правила «несколько заказов на столе»/объединение чеков не предполагаются по умолчанию; до реализации зафиксировать scope, чтобы не потерять два current заказа.

## M17: бронь

CreateBooking: tableId, date/time/zone, duration>0, guestName, guests>0, contact optional по policy, note, expected table/booking revision. Validate → preview window → commit. Подтверждённое имя обязательно в Web. Native не повторяет молчаливую подмену невалидных duration/guests default значениями.

Интервалы полуоткрытые[start,end): Web bookingOverlap использует startA<endB && endA>startB. Значит 10:00–11:00 и 11:00–12:00 не пересекаются;10:30–11:30 конфликтует. Conflict проверяется внутри transaction; два одновременных save не получают один стол. Cancelled booking не блокирует окно. Редактирование booking исключает собственный интервал из conflict check и сохраняет revision.

Часовой пояс заведения, переходы дат/DST, бронь через полночь, допустимая длительность, seats/overcapacity и статусы no-show/completed определяются D05. Текущее время устройства не даёт права молча сдвинуть уже сохранённую бронь. Историческая отмена сохраняет факт, не меняет stock/sale.

Открыть заказ по брони — явная команда с уникальной связью; повтор возвращает прежний order. Отмена брони не очищает оплаченный receipt. Связь с server bookings не входит в offline зал без отдельного контракта.

## Приёмка

CL01:2 клиента с одинаковыми 4 цифрами →явный выбор; CL02:смена клиента очищает прошлый gift/username; CL03:gift без qualifying строки не уменьшает delivery; CL04:timeout/restart →одна server mutation по контракту. HL01:смежные окна разрешены/пересечение запрещено; HL02:два конкурентных booking →один конфликт; HL03:delete стола со ссылкой отклонён; HL04:повтор open order не создаёт второй reserve. Mapping C01–C05/W04/N03; planned, fake contacts/server fixtures.
