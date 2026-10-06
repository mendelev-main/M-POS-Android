# 077 — правила и выбор доставки

MPosDeliveryEngine выполняет pure check/select/type. Тариф хранится как amount,
не rate ID, сравнение строго по Number(amount), без округления. Delivery allowed
только selected===true, finite fee >=0 и совпадающий текущий rate; бесплатный
тариф явно выбран и присутствует. Другие orderType разрешены независимо от fee.
Выход из доставки очищает fee; смена типа сбрасывает selected, повтор того же
типа сохраняет флаг. Неконфигурированное amount ничего не меняет. JSON scalar/
one-item array coercions проверены с source, отсутствующий amount не равен нулю.

Native check встроен в существующий cartTotalsRead без нового запроса оплаты.
Payment entry/whole confirmation повторно проверяют matching native delivery
status после quote. Ведущие source guards сохранены. Source paySplitPart не
проверяет тариф: это исключение не меняется; final settlement сохраняет свои guards.
HasDeliveryTariff использует matching native status, вне quote reviewed fallback.

Type/select commands идут через DeliveryRead и OrderContext FIFO (до 32).
Сохраняются saveCurrentOrderSession → render → openOrderSettings. Continuity
разрешена лишь для собственных подтверждённых патчей, external context/cart/
modal/critical change отменяет late и queued операции. Оплата и ранее открытые
native confirmations ждут pending context; split edits также ждут. State/UI
и session save plumbing пока JS/Room v13, никаких резервов/сети/новых таблиц.
Native logic готова для будущего UI. Rate CRUD/форма настроек ещё source (100).

Rollback MPosNativeDeliveryEnabled=false возвращает исходные type/select/check;
quote исключает deliveryState. Недоступный/malformed native command не меняет
state и не сохраняет его. Source files неизменны, sync сохраняет adapters.

Проверки: 154 actual source fixtures (тип, strict selected, бесплатные/дробные/
отрицательные/nullable/string/legacy array fees), pure Kotlin input immutability,
missing amount semantics. Shared pricing quote gate, correlated bridge/worker
survival/no write, FIFO continuity, stale/rollback/error, payment pending guards.
UI preview/classes/Manrope неизменны; физическая приёмка ожидается.

Физически: доставка без выбора → оплата запрещена; выбрать 0/2/5 → сумма/чек
совпадают; сменить тип туда/обратно; удалить выбранный тариф; быстрые переключения,
Cancel/новый modal при ответе, попытка оплаты pending, restart/v13. APK локально
не собирается. Не заявляем измеренное ускорение устройства.

Сохранённые вопросы для будущего business refactor: тариф идентифицируется
стоимостью, одинаковые суммы разных названий неразличимы; удаление тарифа может
оставить selected=true при fee=0; отрицательный legacy rate можно выбрать, но
он блокирует оплату. Type/fee можно менять после paid части по исходным правилам;
это может изменить total и осложнить draft restore. Пересмотр не входит в 077.

После публикации: 76/110 выполнено (69,09%), осталось 34. Следующий 078 —
локальные поля контекста заказа; customer backend/loyalty lookup остаётся P11.

078 переносит локальное сохранение названия/клиентских полей; тип и тариф
используют тот же OrderContext FIFO. Backend customer lookup — отдельный scope.
