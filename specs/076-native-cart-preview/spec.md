# 076 — пересчёт и preview корзины при всех финансовых изменениях

Котлин-движок 067 теперь запрашивается при отображении POS корзины после
изменения qty/price/discount definitions/discountId/delivery/gift programs/
redemptions. native-cart-preview применяет только matching quote к существующим
денежным DOM nodes (строки/итог/подарок, итог в форме доставки). Не перестраивает
app/cart/modal, не меняет фокус, scroll, click handlers, комментарии или ввод.
Существующие POS classes/palette/Manrope сохранены по mpos-native-design.

На запрос обновления одна scheduled задача; renders во время ожидания объединены
до текущего снимка. Устаревший результат не отображается, следующий snapshot
получает quote. In-flight одинаковые quote/given разделяют один запрос (включая
payment preflight). Неизменённая корзина использует уже matching quote. Поздний preview без tender
не вытесняет cash quote для подтверждения той же корзины; оба порядка ответа проверены. Для
применения UI используется один O(n) presentation snapshot без проверки/JSON
fingerprint для каждой строки. Ни Room archive, ни каталог не отправляются.

Source render синхронно сохраняет reviewed preview до готовности native quote;
read/protocol failure оставляет его. Это явный preview compatibility путь,
а payment/settlement по-прежнему проходят native authority/stock gates.
UI/cart state ещё JS, полное устранение source helper fallback не заявляется.
Нет резерва/записи/внешних действий, backup v13 не меняется. Source файлы
неизменны. Rollback MPosNativeCartPreviewEnabled=false; общий flag
MPosNativeCartTotalsEnabled=false тоже отключает preview native ownership.

Проверки: все 273 JS tests, существующие pricing/gift golden JVM tests; отдельные
DOM tests для всех финансовых input mutations, coalescing/shared request,
неизменности markup/scroll identity, gift row show/hide, stale/tab/hidden/error/
rollback. Native UI дизайн не меняется, synthetic DOM tests не являются
физическими планшетными скриншотами. APK локально не собирается.

Физические кейсы: быстро добавить/уменьшить/удалить позиции; изменить скидку,
подарок, тариф; подтвердить одинаковые суммы строки/итога/оплаты/чека. Длинный
чек, scroll/swipe/комментарий/keypad при ответе; dark/light/large font; измерить
время/отзывчивость на планшете. Пока pending, без обещания ускорения устройства.

После публикации: 75/110 задач выполнено (68,18%), осталось 35. Далее 077 —
выбор и проверки тарифа доставки, затем 078 — context заказа.
