# 078 — локальные команды контекста заказа

MPosOrderContextEngine обрабатывает одну команду save для orderLabel и полей
customer.name/phone/address. Точные правила ECMAScript trim перенесены в Kotlin:
BOM/NBSP и ECMAScript whitespace удаляются по краям, NEL/NUL и внутренние
пробелы/переводы строк сохраняются. Телефон не нормализуется.

Android adapter сохраняет исходный customer object, id и дополнительные поля,
loyaltyPrograms/redemptions, orderComment, WEB metadata и paid split parts.
Тип и тариф уже перенесены в 077. Редактирование comment не добавляется: в
исходной локальной форме такого setter нет. Customer lookup/attach/detach,
backend loyalty и profile association остаются будущими 084/085, не входят в
эту границу. Это контракт для будущего native UI, не измеренное ускорение trim.

OrderContext FIFO защищает от параллельной оплаты/критической операции.
Результат применяется только к тому же заказу, клиенту, контексту и форме с
неизменными значениями. Close/new modal, field replacement/edit, rollback,
external context/cart/customer change отменяют late response. После valid reply:
state patch → saveCurrentOrderSession → closeModal → render, как source.
Close увеличивает generation и отменяет queued obsolete saves.
Существующий saveCurrentOrderSession/Room path неизменен; helper source void,
этап не добавляет подтверждение долговечности записи или сетевой запрос.
Pure bridge не пишет данные сам. JSON keys/backup v13 и source files неизменны.

Rollback: MPosNativeOrderSettingsEnabled=false возвращает source handler.
Bridge failure/malformed reply сохраняет форму и state, показывает retry.

Автоматические проверки: 31 ECMAScript trim fixture, сверенные с actual source
form, JVM parity/input immutability/malformed field; JS state identity/metadata,
source side-effect order, late field/modal/cart/customer changes, rollback,
double save cancellation; correlated frozen native read без записи.
Полный JS/JVM suite и lint обязательны; физическая приёмка pending.

Планшет: сохранить пробелы/кириллицу/телефон/адрес; сменить доставку и тип;
редактировать уже привязанного клиента, проверить прежние бонусы; быстро
сохранить дважды/закрыть/изменить поле; restart и backup v13 проверить поля,
комментарий WEB и split draft. Локальная APK не собирается.

Сохранённый бизнес-вопрос: ручное изменение имени/телефона не пересвязывает
customer id и бонусы. Это исходное поведение; возможное изменение требует
отдельного решения. Unsaved поля при смене типа/тарифа остаются по source UI.

После публикации: 77/110 (70%), осталось 33. Следующий этап 079 —
отложенные заказы: hold/resume/delete и сохранение контекста.

Название 078 уточнено по фактическому source scope: локальные настройки и
сохранение comment, без нового setter. ID и знаменатель 110 не меняются.

079 защищает parked commit от pending OrderContext и атомарно сохраняет
parked/currentOrderSession. Локальная форма 078 и её metadata остаются совместимы.
